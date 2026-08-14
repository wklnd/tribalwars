package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.ResearchQueueItem;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ResearchQueueItemRepository extends JpaRepository<ResearchQueueItem, Long> {
    List<ResearchQueueItem> findByVillageOrderByPositionAsc(Village village);

    @Query("select r from ResearchQueueItem r where r.completesAt is not null and r.completesAt <= ?1")
    List<ResearchQueueItem> findDue(Instant now);
}
