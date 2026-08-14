package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.BuildQueueItemRepository;
import se.oscarwiklund.twlan2.backend.repo.BuildingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BuildService {

    private final BuildingRepository buildingRepository;
    private final BuildQueueItemRepository buildQueueItemRepository;
    private final VillageService villageService;
    private final GameSettings settings;
    private final TickService tickService;
    private final AchievementService achievements;

    // Real seconds, not game seconds.
    public static final long FREE_FINISH_SECONDS = 180;

    public BuildService(BuildingRepository buildingRepository, BuildQueueItemRepository buildQueueItemRepository,
                         VillageService villageService, GameSettings settings, TickService tickService,
                        AchievementService achievements) {
        this.achievements = achievements;
        this.settings = settings;
        this.tickService = tickService;
        this.buildingRepository = buildingRepository;
        this.buildQueueItemRepository = buildQueueItemRepository;
        this.villageService = villageService;
    }

    public static class BuildException extends RuntimeException {
        public BuildException(String message) { super(message); }
    }

    // a refused order (BuildException) must not doom a caller's transaction, e.g. the NPC tick trying several buildings
    @Transactional(noRollbackFor = BuildException.class)
    public BuildQueueItem enqueue(Village village, BuildingType type) {
        villageService.settleResources(village);

        int currentLevel = villageService.levelOf(village, type);
        List<BuildQueueItem> queue = buildQueueItemRepository.findByVillageOrderByPositionAsc(village);
        long alreadyQueuedOfType = queue.stream().filter(q -> q.getType() == type).count();
        int targetLevel = currentLevel + (int) alreadyQueuedOfType + 1;

        if (targetLevel > type.maxLevel) {
            throw new BuildException(type.displayName() + " is already at its maximum level (" + type.maxLevel + ")");
        }

        for (var req : type.requirements().entrySet()) {
            if (villageService.levelOf(village, req.getKey()) < req.getValue()) {
                throw new BuildException(type.displayName() + " requires " + req.getKey().displayName() + " level " + req.getValue());
            }
        }

        double costMultiplier = queueCostMultiplier(village.getWorld(), queue.size());
        double wood = type.woodCost(targetLevel) * costMultiplier;
        double clay = type.clayCost(targetLevel) * costMultiplier;
        double iron = type.ironCost(targetLevel) * costMultiplier;

        int capacity = villageService.warehouseCapacity(village);
        if (wood > capacity || clay > capacity || iron > capacity) {
            throw new BuildException("Warehouse too small for " + type.displayName() + " level " + targetLevel);
        }

        int pendingPop = queue.stream().mapToInt(q -> q.getType().popIncrease(q.getTargetLevel())).sum();
        int freePop = villageService.populationCapacity(village) - villageService.populationUsed(village) - pendingPop;
        if (type.popIncrease(targetLevel) > freePop) {
            throw new BuildException("Farm too small for " + type.displayName() + " level " + targetLevel
                    + " (needs " + type.popIncrease(targetLevel) + " free population, only " + Math.max(0, freePop) + " left)");
        }

        if (village.getWood() < wood || village.getClay() < clay || village.getIron() < iron) {
            throw new BuildException("Not enough resources for " + type.displayName() + " level " + targetLevel);
        }

        village.setWood(village.getWood() - wood);
        village.setClay(village.getClay() - clay);
        village.setIron(village.getIron() - iron);

        BuildQueueItem item = new BuildQueueItem();
        item.setVillage(village);
        item.setType(type);
        item.setTargetLevel(targetLevel);
        item.setPosition(queue.size());
        if (costMultiplier != 1.0) {
            item.setCostMultiplier(costMultiplier);
        }

        if (queue.isEmpty()) {
            int hqLevel = villageService.levelOf(village, BuildingType.HEADQUARTERS);
            Instant now = Instant.now();
            item.setStartedAt(now);
            item.setCompletesAt(now.plusSeconds(
                    type.buildTimeSeconds(targetLevel, hqLevel, settings.speedOf(village), WorldSettings.number(village.getWorld(), "buildMainFactor"))));
        }

        return buildQueueItemRepository.save(item);
    }

    // World settings "buildqueueStart"/"buildqueueMultiply".
    private double queueCostMultiplier(World world, int position) {
        int freeSlots = (int) WorldSettings.number(world, "buildqueueStart");
        double multiply = WorldSettings.number(world, "buildqueueMultiply");
        int extra = position - freeSlots + 1;
        return extra <= 0 ? 1.0 : Math.pow(multiply, extra);
    }

    @Transactional
    public void finishFree(Village village, Long itemId) {
        BuildQueueItem item = buildQueueItemRepository.findByVillageOrderByPositionAsc(village).stream()
                .filter(q -> q.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BuildException("No such construction order"));
        Instant now = Instant.now();
        if (item.getCompletesAt() == null) {
            throw new BuildException("Only the order that is currently being built can be completed");
        }
        long left = item.getCompletesAt().getEpochSecond() - now.getEpochSecond();
        if (left > FREE_FINISH_SECONDS) {
            throw new BuildException("Orders can only be completed for free when less than 3 minutes remain");
        }
        item.setCompletesAt(now);
        buildQueueItemRepository.save(item);
        tickService.processBuildQueues(now);
        achievements.count(village.getOwner(), village.getWorld(), "instant", 1);
    }

    // Also cancels every later order for the same building (they depend on it), refunding their full cost
    // (capped by warehouse capacity). Then re-numbers the queue and starts the next order if the active one was removed.
    @Transactional
    public void cancel(Village village, Long itemId) {
        villageService.settleResources(village);
        List<BuildQueueItem> queue = buildQueueItemRepository.findByVillageOrderByPositionAsc(village);
        BuildQueueItem target = queue.stream().filter(q -> q.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BuildException("No such construction order"));

        int capacity = villageService.warehouseCapacity(village);
        List<BuildQueueItem> removed = queue.stream()
                .filter(q -> q.getType() == target.getType() && q.getTargetLevel() >= target.getTargetLevel())
                .toList();
        for (BuildQueueItem q : removed) {
            double mult = q.getCostMultiplier() != null ? q.getCostMultiplier() : 1.0;
            village.setWood(Math.max(village.getWood(), Math.min(capacity, village.getWood() + q.getType().woodCost(q.getTargetLevel()) * mult)));
            village.setClay(Math.max(village.getClay(), Math.min(capacity, village.getClay() + q.getType().clayCost(q.getTargetLevel()) * mult)));
            village.setIron(Math.max(village.getIron(), Math.min(capacity, village.getIron() + q.getType().ironCost(q.getTargetLevel()) * mult)));
        }
        buildQueueItemRepository.deleteAll(removed);

        List<BuildQueueItem> rest = queue.stream().filter(q -> !removed.contains(q)).toList();
        Instant now = Instant.now();
        for (int i = 0; i < rest.size(); i++) {
            BuildQueueItem q = rest.get(i);
            q.setPosition(i);
            if (i == 0 && q.getStartedAt() == null) {
                int hqLevel = villageService.levelOf(village, BuildingType.HEADQUARTERS);
                q.setStartedAt(now);
                q.setCompletesAt(now.plusSeconds(q.getType().buildTimeSeconds(
                        q.getTargetLevel(), hqLevel, settings.speedOf(village), WorldSettings.number(village.getWorld(), "buildMainFactor"))));
            }
            buildQueueItemRepository.save(q);
        }
    }
}
