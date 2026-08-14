package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.BuildQueueItem;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface BuildQueueItemRepository extends JpaRepository<BuildQueueItem, Long> {
    List<BuildQueueItem> findByVillageOrderByPositionAsc(Village village);
    List<BuildQueueItem> findByCompletesAtLessThanEqual(Instant now);
}
