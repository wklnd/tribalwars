package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.BuildingType;
import se.oscarwiklund.twlan2.backend.domain.TrainQueueItem;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.repo.TrainQueueItemRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Every recruiting building (Barracks, Stable, Workshop, Statue, Academy) runs its own queue: a unit is queued in
// the building of its type (UnitType.recruitBuilding), and only the head of each building's queue is worked on.
// Queue positions are numbered per building.
@Component
public class TrainQueues {

    private final TrainQueueItemRepository items;

    public TrainQueues(TrainQueueItemRepository items) {
        this.items = items;
    }

    public List<TrainQueueItem> of(Village village, BuildingType building) {
        return items.findByVillageOrderByPositionAsc(village).stream()
                .filter(q -> q.getType().recruitBuilding == building).toList();
    }

    // Numbering restarts from 0 per building, not globally.
    public void resequence(Village village, Instant now) {
        Map<BuildingType, List<TrainQueueItem>> byBuilding = items.findByVillageOrderByPositionAsc(village).stream()
                .collect(Collectors.groupingBy(q -> q.getType().recruitBuilding));
        for (List<TrainQueueItem> queue : byBuilding.values()) {
            for (int i = 0; i < queue.size(); i++) {
                TrainQueueItem q = queue.get(i);
                q.setPosition(i);
                if (i == 0 && q.getStartedAt() == null) {
                    q.setStartedAt(now);
                    q.setCompletesAt(now.plusSeconds(q.getPerUnitSeconds() * (q.getTotalCount() - q.getProducedCount())));
                }
                items.save(q);
            }
        }
    }
}
