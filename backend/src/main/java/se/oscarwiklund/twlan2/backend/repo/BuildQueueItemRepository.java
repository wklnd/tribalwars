package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.BuildQueueItem;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BuildQueueItemRepository extends JpaRepository<BuildQueueItem, Long> {
    List<BuildQueueItem> findByVillageOrderByPositionAsc(Village village);
    List<BuildQueueItem> findByCompletesAtLessThanEqual(Instant now);

    // A plain JPQL delete-by-id, unlike delete(entity): it does not assert a row was actually affected, so it
    // is safe when the tick's own completion and a player's cancel/finish race to delete the same row - one of
    // them loses the race and finds it already gone, which is fine (see TickService/BuildService for why the
    // entity-based delete() throwing StaleStateException there rolled back the whole tick's transaction).
    @Modifying
    @Query("delete from BuildQueueItem b where b.id = :id")
    void deleteByIdSafe(@Param("id") Long id);
}
