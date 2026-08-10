package com.twlan.backend.repo;

import com.twlan.backend.domain.ResearchQueueItem;
import com.twlan.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ResearchQueueItemRepository extends JpaRepository<ResearchQueueItem, Long> {
    List<ResearchQueueItem> findByVillageOrderByPositionAsc(Village village);

    @Query("select r from ResearchQueueItem r where r.completesAt is not null and r.completesAt <= ?1")
    List<ResearchQueueItem> findDue(Instant now);
}
