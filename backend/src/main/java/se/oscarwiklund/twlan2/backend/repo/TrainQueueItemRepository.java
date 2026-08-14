package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.TrainQueueItem;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TrainQueueItemRepository extends JpaRepository<TrainQueueItem, Long> {
    List<TrainQueueItem> findByVillageOrderByPositionAsc(Village village);

    @Query("select t from TrainQueueItem t where t.producedCount < t.totalCount")
    List<TrainQueueItem> findUnfinished();
}
