package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TickService {

    private final LiveUpdates live;
    private final BuildQueueItemRepository buildQueueItemRepository;
    private final BuildingRepository buildingRepository;
    private final TrainQueueItemRepository trainQueueItemRepository;
    private final TrainQueues trainQueues;
    private final ResearchService research;
    private final UnitStockRepository unitStockRepository;
    private final MovementRepository movementRepository;
    private final VillageService villageService;
    private final CombatService combatService;
    private final GameSettings settings;
    private final AchievementService achievements;
    private final TribeService tribes;
    private final SupportService support;
    private final MarketService market;

    public TickService(LiveUpdates live, BuildQueueItemRepository buildQueueItemRepository, BuildingRepository buildingRepository,
                        TrainQueueItemRepository trainQueueItemRepository, UnitStockRepository unitStockRepository,
                        MovementRepository movementRepository, VillageService villageService,
                        CombatService combatService, GameSettings settings, AchievementService achievements,
                        TrainQueues trainQueues, ResearchService research, TribeService tribes, SupportService support, MarketService market) {
        this.live = live;
        this.market = market;
        this.tribes = tribes;
        this.support = support;
        this.research = research;
        this.trainQueues = trainQueues;
        this.achievements = achievements;
        this.settings = settings;
        this.buildQueueItemRepository = buildQueueItemRepository;
        this.buildingRepository = buildingRepository;
        this.trainQueueItemRepository = trainQueueItemRepository;
        this.unitStockRepository = unitStockRepository;
        this.movementRepository = movementRepository;
        this.villageService = villageService;
        this.combatService = combatService;
    }

    @Scheduled(fixedDelayString = "${game.tick-rate-ms:1000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        processBuildQueues(now);
        processTrainQueues(now);
        research.process(now);
        processMovements(now);
        market.process(now);
    }

    void processBuildQueues(Instant now) {
        List<BuildQueueItem> due = buildQueueItemRepository.findByCompletesAtLessThanEqual(now);
        for (BuildQueueItem item : due) {
            Village village = item.getVillage();
            Building building = buildingRepository.findByVillageAndType(village, item.getType())
                    .orElseGet(() -> {
                        Building b = new Building();
                        b.setVillage(village);
                        b.setType(item.getType());
                        b.setLevel(0);
                        return b;
                    });
            building.setLevel(item.getTargetLevel());
            buildingRepository.save(building);
            buildQueueItemRepository.delete(item);
            achievements.count(village.getOwner(), village.getWorld(), "levels", 1);
            live.village(village);
            if (village.getOwner() != null && !village.getOwner().isNpc() && village.getWorld() != null) live.toWorld(village.getWorld().getId(), LiveUpdates.MAP); // (its points changed)

            List<BuildQueueItem> rest = buildQueueItemRepository.findByVillageOrderByPositionAsc(village);
            for (int i = 0; i < rest.size(); i++) {
                BuildQueueItem next = rest.get(i);
                next.setPosition(i);
                if (i == 0 && next.getStartedAt() == null) {
                    int hqLevel = villageService.levelOf(village, BuildingType.HEADQUARTERS);
                    next.setStartedAt(now);
                    next.setCompletesAt(now.plusSeconds(next.getType().buildTimeSeconds(
                            next.getTargetLevel(), hqLevel, settings.speedOf(village), WorldSettings.number(village.getWorld(), "buildMainFactor"))));
                }
                buildQueueItemRepository.save(next);
            }
        }
    }

    // Every unfinished order of the whole server is looked at every second (with world speed 500 a unit comes out every
    // fraction of a second), so this must stay cheap: first work out what came out, then update the stock rows of those
    // villages together (one query instead of one per order, each of which made Hibernate flush the whole session).
    private void processTrainQueues(Instant now) {
        Map<Long, Village> villageById = new HashMap<>();
        Map<Long, Map<UnitType, Integer>> produced = new HashMap<>();
        List<TrainQueueItem> finished = new ArrayList<>();
        for (TrainQueueItem item : trainQueueItemRepository.findUnfinished()) {
            if (item.getStartedAt() == null) {
                continue;
            }
            long elapsedSeconds = Math.max(0, now.getEpochSecond() - item.getStartedAt().getEpochSecond());
            int newProduced = (int) Math.min(item.getTotalCount(), elapsedSeconds / item.getPerUnitSeconds());
            if (newProduced > item.getProducedCount()) {
                int delta = newProduced - item.getProducedCount();
                Village village = item.getVillage();
                villageById.putIfAbsent(village.getId(), village);
                // a decommission order removes units instead of adding them, so its delta is negative
                produced.computeIfAbsent(village.getId(), k -> new EnumMap<>(UnitType.class))
                        .merge(item.getType(), item.isDecommission() ? -delta : delta, Integer::sum);
                if (!item.isDecommission()) achievements.count(village.getOwner(), village.getWorld(), "recruited", delta);
                item.setProducedCount(newProduced);
            }
            if (item.getProducedCount() >= item.getTotalCount()) {
                finished.add(item);
            }
        }
        if (!produced.isEmpty()) {
            Map<Long, Map<UnitType, UnitStock>> stocks = new HashMap<>();
            for (UnitStock s : unitStockRepository.findByVillageIn(villageById.values())) {
                stocks.computeIfAbsent(s.getVillage().getId(), k -> new EnumMap<>(UnitType.class)).put(s.getType(), s);
            }
            for (var byVillage : produced.entrySet()) {
                Village village = villageById.get(byVillage.getKey());
                live.village(village);
                Map<UnitType, UnitStock> mine = stocks.getOrDefault(byVillage.getKey(), Map.of());
                for (var e : byVillage.getValue().entrySet()) {
                    UnitStock stock = mine.get(e.getKey());
                    if (stock == null) {
                        stock = new UnitStock();
                        stock.setVillage(village);
                        stock.setType(e.getKey());
                    }
                    stock.setCount(Math.max(0, stock.getCount() + e.getValue())); // clamp: a decommission delta is negative
                    unitStockRepository.save(stock);
                }
            }
        }
        for (TrainQueueItem item : finished) {
            Village village = item.getVillage();
            trainQueueItemRepository.delete(item);
            trainQueues.resequence(village, now);
        }
    }

    private void addUnits(Village village, UnitType type, int delta) {
        UnitStock stock = unitStockRepository.findByVillageAndType(village, type).orElseGet(() -> {
            UnitStock s = new UnitStock();
            s.setVillage(village);
            s.setType(type);
            s.setCount(0);
            return s;
        });
        stock.setCount(stock.getCount() + delta);
        unitStockRepository.save(stock);
    }

    private void processMovements(Instant now) {
        for (Movement movement : movementRepository.findByArrivesAtLessThanEqual(now)) {
            live.village(movement.getOriginVillage());
            live.village(movement.getTargetVillage());
            if (movement.getType() == MovementType.SUPPORT) {
                Village origin = movement.getOriginVillage();
                Village target = movement.getTargetVillage();
                if (MovementService.isSameOwner(origin, target)) {
                    for (Map.Entry<UnitType, Integer> e : movement.getUnits().entrySet()) {
                        addUnits(target, e.getKey(), e.getValue());
                    }
                } else if (tribes.sameTribe(origin.getOwner(), target.getOwner(), origin.getWorld())) {
                    support.station(origin, target, movement.getUnits());
                } else {
                    // the target changed hands (or the tribe was left) while the troops were on the road: they turn around
                    Movement back = new Movement();
                    back.setOriginVillage(target);
                    back.setTargetVillage(origin);
                    back.setType(MovementType.RETURN);
                    back.getUnits().putAll(movement.getUnits());
                    back.setDepartedAt(now);
                    back.setArrivesAt(now.plusSeconds(MovementService.travelSeconds(target, origin, movement.getUnits(), settings.travelSpeedOf(origin))));
                    movementRepository.save(back);
                }
                movementRepository.delete(movement);
            } else if (movement.getType() == MovementType.ATTACK && movement.getTargetVillage().getOwner() != null && movement.getOriginVillage().getOwner() != null
                    && movement.getTargetVillage().getOwner().getId().equals(movement.getOriginVillage().getOwner().getId())) {
                // the target was conquered (by this very player) while the troops were on their way: they move in
                for (Map.Entry<UnitType, Integer> e : movement.getUnits().entrySet()) {
                    addUnits(movement.getTargetVillage(), e.getKey(), e.getValue());
                }
                movementRepository.delete(movement);
            } else if (movement.getType() == MovementType.ATTACK) {
                CombatService.Result result = combatService.resolve(movement);
                if (!result.survivors().isEmpty()) {
                    Movement returnTrip = new Movement();
                    returnTrip.setOriginVillage(movement.getTargetVillage());
                    returnTrip.setTargetVillage(movement.getOriginVillage());
                    returnTrip.setType(MovementType.RETURN);
                    returnTrip.getUnits().putAll(result.survivors());
                    returnTrip.setCarriedWood(result.lootWood());
                    returnTrip.setCarriedClay(result.lootClay());
                    returnTrip.setCarriedIron(result.lootIron());
                    long seconds = MovementService.travelSeconds(movement.getOriginVillage(), movement.getTargetVillage(), result.survivors(), settings.travelSpeedOf(movement.getOriginVillage()));
                    returnTrip.setDepartedAt(now);
                    returnTrip.setArrivesAt(now.plusSeconds(seconds));
                    movementRepository.save(returnTrip);
                }
                movementRepository.delete(movement);
            } else {
                Village home = movement.getTargetVillage();
                for (Map.Entry<UnitType, Integer> e : movement.getUnits().entrySet()) {
                    addUnits(home, e.getKey(), e.getValue());
                }
                if (movement.getCarriedWood() > 0 || movement.getCarriedClay() > 0 || movement.getCarriedIron() > 0) {
                    villageService.settleResources(home);
                    int capacity = villageService.warehouseCapacity(home);
                    home.setWood(Math.min(capacity, home.getWood() + movement.getCarriedWood()));
                    home.setClay(Math.min(capacity, home.getClay() + movement.getCarriedClay()));
                    home.setIron(Math.min(capacity, home.getIron() + movement.getCarriedIron()));
                }
                movementRepository.delete(movement);
            }
        }
    }
}
