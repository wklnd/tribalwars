package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.BuildQueueItemRepository;
import se.oscarwiklund.twlan2.backend.repo.BuildingRepository;
import se.oscarwiklund.twlan2.backend.repo.TrainQueueItemRepository;
import se.oscarwiklund.twlan2.backend.repo.UnitStockRepository;
import se.oscarwiklund.twlan2.backend.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// The economy of an NPC village: what to build next (the archetype's goal list, see NpcGoals), when to save up for
// something instead of building something cheaper, when the farm or warehouse has to grow, what to recruit (the
// archetype's army mix, see NpcArmy) and when to ask the market for a missing resource. Everything is ordered
// through BuildService / TrainService, so an NPC obeys the same rules as a player.
@Service
public class NpcEconomy {

    // What build() did.
    public enum Build { BUILT, SAVING, IDLE, NOTHING_LEFT }

    private final BuildingRepository buildings;
    private final UnitStockRepository stock;
    private final BuildQueueItemRepository buildQueue;
    private final TrainQueueItemRepository trainQueue;
    private final VillageService villageService;
    private final BuildService buildService;
    private final TrainService trainService;
    private final GameSettings settings;
    private final NpcMarketService npcMarket;
    private final NpcLogService npcLog;

    public NpcEconomy(BuildingRepository buildings, UnitStockRepository stock, BuildQueueItemRepository buildQueue,
                      TrainQueueItemRepository trainQueue, VillageService villageService, BuildService buildService,
                      TrainService trainService, GameSettings settings, NpcMarketService npcMarket, NpcLogService npcLog) {
        this.buildings = buildings;
        this.stock = stock;
        this.buildQueue = buildQueue;
        this.trainQueue = trainQueue;
        this.villageService = villageService;
        this.buildService = buildService;
        this.trainService = trainService;
        this.settings = settings;
        this.npcMarket = npcMarket;
        this.npcLog = npcLog;
    }

    // What the economy knows about one village this tick (loaded once, so a step costs a handful of queries).
    public static final class Snapshot {
        final EnumMap<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        public final EnumMap<UnitType, Integer> homeUnits = new EnumMap<>(UnitType.class);
        public List<BuildQueueItem> build = List.of();
        public List<TrainQueueItem> train = List.of();
        int popNow;       // standing buildings + units + units in training (what TrainService counts)
        int popPending;   // farm room already promised to buildings in the queue
        // wood/clay/iron the village is saving for its next building; troops are not paid from it
        final double[] reserve = new double[3];
        // what the next nobleman plus its gold coin cost (wood, clay, iron) while the village is meant to educate one, else zeros
        public final double[] nobleCost = new double[3];

        public int level(BuildingType t) { return levels.getOrDefault(t, 0); }

        int levelWithQueue(BuildingType t) {
            int n = level(t);
            for (BuildQueueItem q : build) if (q.getType() == t) n++;
            return n;
        }
    }

    public Snapshot load(Village v) {
        Snapshot s = new Snapshot();
        for (Building b : buildings.findByVillage(v)) s.levels.merge(b.getType(), b.getLevel(), Math::max);
        for (UnitStock u : stock.findByVillage(v)) s.homeUnits.merge(u.getType(), u.getCount(), Integer::sum);
        s.build = buildQueue.findByVillageOrderByPositionAsc(v);
        s.train = trainQueue.findByVillageOrderByPositionAsc(v);
        for (var e : s.levels.entrySet()) s.popNow += e.getKey().popCost(e.getValue());
        for (var e : s.homeUnits.entrySet()) s.popNow += e.getKey().popCost * e.getValue();
        for (TrainQueueItem q : s.train) s.popNow += q.getType().popCost * (q.getTotalCount() - q.getProducedCount());
        for (BuildQueueItem q : s.build) s.popPending += q.getType().popIncrease(q.getTargetLevel());
        return s;
    }

    // ---- building --------------------------------------------------------------------------------------------------

    // Orders the next building of the plan, saves for it, or builds something cheaper while it waits. NOTHING_LEFT
    // means the plan is finished (the caller may let the old weighted-random logic add more).
    @Transactional(noRollbackFor = {BuildService.BuildException.class, TrainService.TrainException.class})
    public Build build(Village v, NpcArchetype archetype, NpcDifficulty d, Snapshot s, Random rnd) {
        if (s.build.size() >= (d.keepBusy() ? 3 : 2)) return Build.IDLE;
        villageService.settleResources(v);

        List<NpcGoals.Step> order = new ArrayList<>();
        int capacity = (int) Math.round(BuildingType.warehouseCapacity(s.level(BuildingType.WAREHOUSE)) * BonusType.capacityFactor(v));
        int popCap = (int) Math.floor(BuildingType.farmCapacity(s.level(BuildingType.FARM)) * BonusType.populationFactor(v));
        List<NpcGoals.Step> plan = NpcGoals.next(archetype, s::levelWithQueue, 6);
        if (plan.isEmpty()) return Build.NOTHING_LEFT;

        NpcGoals.Step first = plan.get(0);
        boolean warehouseRoom = s.levelWithQueue(BuildingType.WAREHOUSE) < BuildingType.WAREHOUSE.maxLevel;
        boolean farmRoom = s.levelWithQueue(BuildingType.FARM) < BuildingType.FARM.maxLevel;
        boolean warehouseQueued = s.levelWithQueue(BuildingType.WAREHOUSE) > s.level(BuildingType.WAREHOUSE);
        boolean farmQueued = s.levelWithQueue(BuildingType.FARM) > s.level(BuildingType.FARM);
        if (first.type() != BuildingType.WAREHOUSE && warehouseRoom && !warehouseQueued
                && NpcGoals.needsWarehouse(v.getWood(), v.getClay(), v.getIron(), capacity,
                        Math.max(NpcGoals.peakCost(first.type(), first.level()), (int) Math.max(s.nobleCost[0], Math.max(s.nobleCost[1], s.nobleCost[2]))))) {
            order.add(new NpcGoals.Step(BuildingType.WAREHOUSE, s.levelWithQueue(BuildingType.WAREHOUSE) + 1, first.goal()));
        }
        boolean popShort = first.type().popIncrease(first.level()) > popCap - s.popNow - s.popPending;
        if (first.type() != BuildingType.FARM && farmRoom && !farmQueued
                && (popShort || NpcGoals.needsFarm(s.popNow + s.popPending, popCap))) {
            order.add(new NpcGoals.Step(BuildingType.FARM, s.levelWithQueue(BuildingType.FARM) + 1, first.goal()));
        }
        order.addAll(plan);

        NpcGoals.Step target = order.get(0);
        if (affordable(v, target, capacity)) return order(v, target) ? Build.BUILT : Build.SAVING;

        // cannot pay for the target yet: wait for it if that is soon, otherwise build the first thing that can be paid now
        double waitHours = hoursUntilAffordable(v, target, s);
        double horizon = d.keepBusy() ? 3 : d.skill() >= 0.5 ? 1 : 0.25;
        if (waitHours > 0.2 && rnd.nextDouble() < 0.3) askMarket(v, target, s);
        if (waitHours > horizon || s.build.isEmpty()) {
            for (int i = 1; i < order.size(); i++) {
                NpcGoals.Step alt = order.get(i);
                if (affordable(v, alt, capacity) && order(v, alt)) return Build.BUILT;
            }
        }
        if (d.keepBusy() || d.skill() >= 0.5) { // a careful NPC keeps what it is saving for out of its troops' budget
            s.reserve[0] = target.type().woodCost(target.level());
            s.reserve[1] = target.type().clayCost(target.level());
            s.reserve[2] = target.type().ironCost(target.level());
        }
        return Build.SAVING;
    }

    private boolean affordable(Village v, NpcGoals.Step step, int capacity) {
        BuildingType t = step.type();
        return v.getWood() >= t.woodCost(step.level()) && v.getClay() >= t.clayCost(step.level()) && v.getIron() >= t.ironCost(step.level())
                && NpcGoals.peakCost(t, step.level()) <= capacity;
    }

    private boolean order(Village v, NpcGoals.Step step) {
        try {
            buildService.enqueue(v, step.type());
            return true;
        } catch (BuildService.BuildException e) {
            return false; // pop / requirements changed under us: try again next step
        }
    }

    // Hours of production until the step's cost is covered (world speed included); huge if a resource does not grow.
    private double hoursUntilAffordable(Village v, NpcGoals.Step step, Snapshot s) {
        double speed = settings.speedOf(v);
        double h = 0;
        h = Math.max(h, hours(step.type().woodCost(step.level()) - v.getWood(), BuildingType.TIMBER_CAMP.productionPerHour(s.level(BuildingType.TIMBER_CAMP)) * BonusType.productionFactor(v, BuildingType.TIMBER_CAMP) * speed));
        h = Math.max(h, hours(step.type().clayCost(step.level()) - v.getClay(), BuildingType.CLAY_PIT.productionPerHour(s.level(BuildingType.CLAY_PIT)) * BonusType.productionFactor(v, BuildingType.CLAY_PIT) * speed));
        h = Math.max(h, hours(step.type().ironCost(step.level()) - v.getIron(), BuildingType.IRON_MINE.productionPerHour(s.level(BuildingType.IRON_MINE)) * BonusType.productionFactor(v, BuildingType.IRON_MINE) * speed));
        return h;
    }

    private static double hours(double missing, double perHour) {
        if (missing <= 0) return 0;
        return perHour <= 0 ? 1e9 : missing / perHour;
    }

    // Short of one resource while another is plentiful: trade.
    private void askMarket(Village v, NpcGoals.Step step, Snapshot s) {
        if (s.level(BuildingType.MARKET) < 1) return;
        BuildingType t = step.type();
        double[] missing = {t.woodCost(step.level()) - v.getWood(), t.clayCost(step.level()) - v.getClay(), t.ironCost(step.level()) - v.getIron()};
        int worst = 0;
        for (int i = 1; i < 3; i++) if (missing[i] > missing[worst]) worst = i;
        if (missing[worst] <= 0) return;
        String done = npcMarket.tradeFor(v, Resource.values()[worst], (int) Math.ceil(missing[worst]));
        if (done != null) npcLog.add(v.getWorld(), v.getOwner(), v, "MARKET", v.getName() + " " + done + " (to build " + t.displayName() + " " + step.level() + ")");
    }

    // ---- recruiting ------------------------------------------------------------------------------------------------

    // Trains the unit that brings the army closest to the archetype's mix, in the recruit building with room in its queue,
    // until the army takes its share of the farm. `away` are the troops out on the road (they count as the army).
    @Transactional(noRollbackFor = TrainService.TrainException.class)
    public void recruit(Village v, NpcArchetype archetype, double skill, NpcDifficulty d, Snapshot s, Collection<Movement> away, boolean threatened, Random rnd) {
        if (!d.keepBusy() && rnd.nextDouble() > 0.6) return;
        EnumMap<UnitType, Integer> popByType = new EnumMap<>(UnitType.class);
        int scouts = 0;
        for (var e : s.homeUnits.entrySet()) {
            popByType.merge(e.getKey(), e.getKey().popCost * e.getValue(), Integer::sum);
            if (e.getKey() == UnitType.SCOUT) scouts += e.getValue();
        }
        for (Movement m : away) {
            for (var e : m.getUnits().entrySet()) {
                popByType.merge(e.getKey(), e.getKey().popCost * e.getValue(), Integer::sum);
                if (e.getKey() == UnitType.SCOUT) scouts += e.getValue();
            }
        }
        Map<BuildingType, Integer> queued = new EnumMap<>(BuildingType.class);
        for (TrainQueueItem q : s.train) {
            int left = q.getTotalCount() - q.getProducedCount();
            popByType.merge(q.getType(), q.getType().popCost * left, Integer::sum);
            if (q.getType() == UnitType.SCOUT) scouts += left;
            queued.merge(q.getType().recruitBuilding, 1, Integer::sum);
        }
        int popCap = (int) Math.floor(BuildingType.farmCapacity(s.level(BuildingType.FARM)) * BonusType.populationFactor(v));
        double targetPop = popCap * NpcArmy.armyShare(archetype, skill) * (threatened ? 1.4 : 1);

        Set<UnitType> available = EnumSet.noneOf(UnitType.class);
        int queueRoom = d.keepBusy() ? 2 : 1;
        for (UnitType t : UnitType.values()) {
            if (!t.isRegularTroop() || s.level(t.recruitBuilding) < 1 || queued.getOrDefault(t.recruitBuilding, 0) >= queueRoom) continue;
            if (t.requirementsMet(s::level) && (!threatened || DodgeDecision.DEFENDERS.contains(t))) available.add(t); // under attack: defenders only
        }
        UnitType type = NpcArmy.choose(archetype, available, popByType, scouts, d.scouts(), targetPop);
        if (type == null) return;

        int freePop = popCap - s.popNow - s.popPending;
        for (int r = 0; r < 3; r++) s.reserve[r] = Math.max(s.reserve[r], s.nobleCost[r]); // noblemen are saved for, not spent on troops
        double share = 0.6; // never spend everything on troops: buildings need their resources too
        long perUnit = settings.scaleSeconds(v, type.buildTimeSeconds * Math.max(0.1, 1 - s.level(type.recruitBuilding) * 0.02) * BonusType.recruitTimeFactor(v, type.recruitBuilding));
        int n = NpcArmy.batch(type, Math.max(0, v.getWood() - s.reserve[0]) * share, Math.max(0, v.getClay() - s.reserve[1]) * share,
                Math.max(0, v.getIron() - s.reserve[2]) * share, freePop, perUnit, d.keepBusy() ? 3600 : 1800);
        if (n < 1) return;
        try {
            trainService.enqueue(v, type, n);
        } catch (TrainService.TrainException ignored) {
            // something changed since the snapshot (resources, population)
        }
    }
}
