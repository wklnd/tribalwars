package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.MovementRepository;
import se.oscarwiklund.twlan2.backend.repo.TrainQueueItemRepository;
import se.oscarwiklund.twlan2.backend.repo.UnitStockRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TrainService {

    private final TrainQueueItemRepository trainQueueItemRepository;
    private final VillageService villageService;
    private final GameSettings settings;
    private final VillageRepository villageRepository;
    private final UnitStockRepository unitStockRepository;
    private final MovementRepository movementRepository;
    private final NobleService nobleService;
    private final TrainQueues trainQueues;
    private final ResearchService research;
    private final SupportService support;

    public TrainService(TrainQueueItemRepository trainQueueItemRepository, VillageService villageService, GameSettings settings,
                        VillageRepository villageRepository, UnitStockRepository unitStockRepository,
                        MovementRepository movementRepository, NobleService nobleService, TrainQueues trainQueues,
                        ResearchService research, SupportService support) {
        this.support = support;
        this.research = research;
        this.trainQueues = trainQueues;
        this.nobleService = nobleService;
        this.settings = settings;
        this.villageRepository = villageRepository;
        this.unitStockRepository = unitStockRepository;
        this.movementRepository = movementRepository;
        this.trainQueueItemRepository = trainQueueItemRepository;
        this.villageService = villageService;
    }

    public static class TrainException extends RuntimeException {
        public TrainException(String message) { super(message); }
    }

    @Transactional(noRollbackFor = TrainException.class)
    public TrainQueueItem enqueue(Village village, UnitType type, int count) {
        if (count <= 0) {
            throw new TrainException("Count must be positive");
        }
        villageService.settleResources(village);

        boolean paladin = type == UnitType.PALADIN;
        BuildingType building = type.recruitBuilding;
        int buildingLevel = villageService.levelOf(village, building);
        if (paladin) {
            if (!WorldSettings.bool(village.getWorld(), "knightActive")) {
                throw new TrainException("Paladins are disabled on this world");
            }
            if (buildingLevel < 1) {
                throw new TrainException("Requires a Statue to train a Paladin");
            }
            if (count != 1) {
                throw new TrainException("You can only have one Paladin");
            }
            if (paladinExists(village)) {
                throw new TrainException("You already have a Paladin");
            }
        } else if (type == UnitType.SNOB) {
            nobleService.checkCanEducate(village, count);
        } else {
            if (buildingLevel < 1) {
                throw new TrainException("Requires a " + building.displayName() + " to train " + type.displayName());
            }
            for (var req : type.requirements().entrySet()) {
                if (villageService.levelOf(village, req.getKey()) < req.getValue()) {
                    throw new TrainException(type.displayName() + " requires " + req.getKey().displayName() + " level " + req.getValue());
                }
            }
            if (!research.isResearched(village, type)) {
                throw new TrainException(type.displayName() + " has not been researched in the Smithy yet");
            }
        }

        double wood = (double) type.woodCost * count;
        double clay = (double) type.clayCost * count;
        double iron = (double) type.ironCost * count;
        if (village.getWood() < wood || village.getClay() < clay || village.getIron() < iron) {
            throw new TrainException("Not enough resources to train " + count + " x " + type.displayName());
        }

        int popNeeded = type.popCost * count;
        int freePop = villageService.populationCapacity(village) - villageService.populationUsed(village);
        if (popNeeded > freePop) {
            throw new TrainException("Not enough free population (need " + popNeeded + ", have " + freePop + ")");
        }

        village.setWood(village.getWood() - wood);
        village.setClay(village.getClay() - clay);
        village.setIron(village.getIron() - iron);

        // the original's nobleman time: base * 0.48 / 1.06^academy level
        double speedFactor = paladin ? 1
                : type == UnitType.SNOB ? 0.48 / Math.pow(1.06, buildingLevel)
                : Math.max(0.1, 1 - (buildingLevel * 0.02)) * BonusType.recruitTimeFactor(village, building);
        long perUnitSeconds = settings.scaleSeconds(village, type.buildTimeSeconds * speedFactor);

        List<TrainQueueItem> queue = trainQueues.of(village, building);

        TrainQueueItem item = new TrainQueueItem();
        item.setVillage(village);
        item.setType(type);
        item.setTotalCount(count);
        item.setProducedCount(0);
        item.setPerUnitSeconds(perUnitSeconds);
        item.setPosition(queue.size());

        if (queue.isEmpty()) {
            Instant now = Instant.now();
            item.setStartedAt(now);
            item.setCompletesAt(now.plusSeconds(perUnitSeconds * count));
        }

        return trainQueueItemRepository.save(item);
    }

    // No resource cost and no refund on cancel: the units are removed from the village's stock only once the
    // order completes, exactly like recruiting in reverse (TickService.processTrainQueues). Shares the recruit
    // building's queue with ordinary recruiting (see TrainQueues, and the original's queue.php which renders
    // both together). The timer reuses the classic recruit-time formula - decommission timing is compiled in
    // the original and not readable, so this is the natural symmetric default, not a verified value.
    @Transactional(noRollbackFor = TrainException.class)
    public TrainQueueItem decommission(Village village, UnitType type, int count) {
        if (count <= 0) {
            throw new TrainException("Count must be positive");
        }
        if (type == UnitType.PALADIN || type == UnitType.SNOB) {
            throw new TrainException(type.displayName() + " cannot be decommissioned");
        }
        BuildingType building = type.recruitBuilding;
        int buildingLevel = villageService.levelOf(village, building);
        if (buildingLevel < 1) {
            throw new TrainException("Requires a " + building.displayName());
        }

        int atHome = unitStockRepository.findByVillageAndType(village, type).map(UnitStock::getCount).orElse(0);
        int alreadyQueued = trainQueueItemRepository.findByVillageOrderByPositionAsc(village).stream()
                .filter(q -> q.isDecommission() && q.getType() == type)
                .mapToInt(q -> q.getTotalCount() - q.getProducedCount())
                .sum();
        if (count > atHome - alreadyQueued) {
            throw new TrainException("Not enough " + type.displayName() + " at home to decommission");
        }

        double speedFactor = Math.max(0.1, 1 - (buildingLevel * 0.02)) * BonusType.recruitTimeFactor(village, building);
        long perUnitSeconds = settings.scaleSeconds(village, type.buildTimeSeconds * speedFactor);

        List<TrainQueueItem> queue = trainQueues.of(village, building);

        TrainQueueItem item = new TrainQueueItem();
        item.setVillage(village);
        item.setType(type);
        item.setTotalCount(count);
        item.setProducedCount(0);
        item.setPerUnitSeconds(perUnitSeconds);
        item.setPosition(queue.size());
        item.setDecommission(true);

        if (queue.isEmpty()) {
            Instant now = Instant.now();
            item.setStartedAt(now);
            item.setCompletesAt(now.plusSeconds(perUnitSeconds * count));
        }

        return trainQueueItemRepository.save(item);
    }

    // state = HOME/AWAY/TRAINING for the village's owner, or the record is null when there is none.
    public record PaladinStatus(String state, Village at) {}

    public boolean paladinExists(Village village) {
        return paladinStatus(village) != null;
    }

    @Transactional(readOnly = true)
    public PaladinStatus paladinStatus(Village village) {
        if (village.getOwner() == null) return null;
        List<Village> owned = villageRepository.findByWorldAndOwner(village.getWorld(), village.getOwner());
        for (Village v : owned) {
            if (unitStockRepository.findByVillageAndType(v, UnitType.PALADIN).map(UnitStock::getCount).orElse(0) > 0) {
                return new PaladinStatus("HOME", v);
            }
        }
        for (Village v : owned) {
            if (trainQueueItemRepository.findByVillageOrderByPositionAsc(v).stream().anyMatch(t -> t.getType() == UnitType.PALADIN)) {
                return new PaladinStatus("TRAINING", v);
            }
        }
        for (Village v : owned) {
            for (Movement m : movementRepository.findByOriginVillageOrTargetVillage(v, v)) {
                if (m.getUnits().getOrDefault(UnitType.PALADIN, 0) > 0) return new PaladinStatus("AWAY", v);
            }
        }
        for (Village v : owned) {
            for (StationedTroops s : support.stationedFrom(v)) {
                if (s.getUnits().getOrDefault(UnitType.PALADIN, 0) > 0) return new PaladinStatus("AWAY", v);
            }
        }
        return null;
    }

    // 90% of the resources for the units not yet trained come back.
    @Transactional
    public void cancel(Village village, Long itemId) {
        villageService.settleResources(village);
        List<TrainQueueItem> queue = trainQueueItemRepository.findByVillageOrderByPositionAsc(village);
        TrainQueueItem target = queue.stream().filter(q -> q.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new TrainException("No such recruitment order"));
        if (!target.isDecommission()) {
            // nothing was spent to queue a decommission order, so there is nothing to refund on cancel
            int remaining = target.getTotalCount() - target.getProducedCount();
            int capacity = villageService.warehouseCapacity(village);
            UnitType t = target.getType();
            village.setWood(Math.max(village.getWood(), Math.min(capacity, village.getWood() + Math.floor(t.woodCost * remaining * 0.9))));
            village.setClay(Math.max(village.getClay(), Math.min(capacity, village.getClay() + Math.floor(t.clayCost * remaining * 0.9))));
            village.setIron(Math.max(village.getIron(), Math.min(capacity, village.getIron() + Math.floor(t.ironCost * remaining * 0.9))));
        }
        trainQueueItemRepository.deleteByIdSafe(target.getId());

        trainQueues.resequence(village, Instant.now());
    }
}
