package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.TrainQueueItem;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrainQueueItemRepository extends JpaRepository<TrainQueueItem, Long> {
    List<TrainQueueItem> findByVillageOrderByPositionAsc(Village village);

    @Query("select t from TrainQueueItem t where t.producedCount < t.totalCount")
    List<TrainQueueItem> findUnfinished();

    // See BuildQueueItemRepository.deleteByIdSafe: avoids StaleStateException when the tick's own completion
    // and a player's cancel race to delete the same row.
    @Modifying
    @Query("delete from TrainQueueItem t where t.id = :id")
    void deleteByIdSafe(@Param("id") Long id);
}
