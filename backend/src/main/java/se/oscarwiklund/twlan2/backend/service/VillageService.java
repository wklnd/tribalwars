package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.BuildingRepository;
import se.oscarwiklund.twlan2.backend.repo.TrainQueueItemRepository;
import se.oscarwiklund.twlan2.backend.repo.UnitStockRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class VillageService {

    private final BuildingRepository buildingRepository;
    private final UnitStockRepository unitStockRepository;
    private final TrainQueueItemRepository trainQueueItemRepository;
    private final GameSettings settings;

    public VillageService(BuildingRepository buildingRepository, UnitStockRepository unitStockRepository,
                           TrainQueueItemRepository trainQueueItemRepository, GameSettings settings) {
        this.settings = settings;
        this.buildingRepository = buildingRepository;
        this.unitStockRepository = unitStockRepository;
        this.trainQueueItemRepository = trainQueueItemRepository;
    }

    public int levelOf(Village village, BuildingType type) {
        return buildingRepository.findByVillageAndType(village, type)
                .map(Building::getLevel)
                .orElse(0);
    }

    // 0..100: the stored value plus what regrew since, world speed included.
    public double loyaltyOf(Village village) {
        double stored = village.getLoyalty() == null ? 100 : village.getLoyalty();
        if (stored >= 100 || village.getLoyaltyUpdatedAt() == null) return Math.min(100, stored);
        double hours = Duration.between(village.getLoyaltyUpdatedAt(), Instant.now()).toMillis() / 3_600_000.0;
        double perHour = WorldSettings.number(village.getWorld(), "loyaltyIncrease") * Math.max(1, settingsSpeed(village));
        return Math.min(100, stored + Math.max(0, hours) * perHour);
    }

    private double settingsSpeed(Village village) {
        return village.getWorld() == null ? 1 : village.getWorld().getSpeed();
    }

    public void setLoyalty(Village village, double value) {
        village.setLoyalty(Math.max(0, Math.min(100, value)));
        village.setLoyaltyUpdatedAt(Instant.now());
    }

    // Barbarian villages produce like any other village (their loot regrows).
    public void settleResources(Village village) {
        Instant now = Instant.now();
        if (village.getResourcesSettledAt() == null) {
            village.setResourcesSettledAt(now);
            return;
        }
        double hours = Duration.between(village.getResourcesSettledAt(), now).toMillis() / 3_600_000.0;
        if (hours <= 0) {
            return;
        }
        int capacity = warehouseCapacity(village);
        int woodLevel = levelOf(village, BuildingType.TIMBER_CAMP);
        int clayLevel = levelOf(village, BuildingType.CLAY_PIT);
        int ironLevel = levelOf(village, BuildingType.IRON_MINE);

        village.setWood(Math.max(village.getWood(), Math.min(capacity, village.getWood() + BuildingType.TIMBER_CAMP.productionPerHour(woodLevel) * BonusType.productionFactor(village, BuildingType.TIMBER_CAMP) * settings.speedOf(village) * hours)));
        village.setClay(Math.max(village.getClay(), Math.min(capacity, village.getClay() + BuildingType.CLAY_PIT.productionPerHour(clayLevel) * BonusType.productionFactor(village, BuildingType.CLAY_PIT) * settings.speedOf(village) * hours)));
        village.setIron(Math.max(village.getIron(), Math.min(capacity, village.getIron() + BuildingType.IRON_MINE.productionPerHour(ironLevel) * BonusType.productionFactor(village, BuildingType.IRON_MINE) * settings.speedOf(village) * hours)));
        village.setResourcesSettledAt(now);
    }

    public int warehouseCapacity(Village village) {
        return (int) Math.round(BuildingType.warehouseCapacity(levelOf(village, BuildingType.WAREHOUSE)) * BonusType.capacityFactor(village));
    }

    public int populationCapacity(Village village) {
        return (int) Math.floor(BuildingType.farmCapacity(levelOf(village, BuildingType.FARM)) * BonusType.populationFactor(village));
    }

    // Only the current level of standing buildings; pending build orders are excluded.
    public int populationUsed(Village village) {
        int used = 0;
        for (Building b : buildingRepository.findByVillage(village)) {
            used += b.getType().popCost(b.getLevel());
        }
        for (UnitStock stock : unitStockRepository.findByVillage(village)) {
            used += stock.getType().popCost * stock.getCount();
        }
        List<TrainQueueItem> queued = trainQueueItemRepository.findByVillageOrderByPositionAsc(village);
        for (TrainQueueItem item : queued) {
            used += item.getType().popCost * (item.getTotalCount() - item.getProducedCount());
        }
        return used;
    }
}
