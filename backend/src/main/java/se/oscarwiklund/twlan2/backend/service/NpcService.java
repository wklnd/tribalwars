package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import se.oscarwiklund.twlan2.backend.service.npc.ConquestPlan;
import se.oscarwiklund.twlan2.backend.service.npc.NpcArchetype;
import se.oscarwiklund.twlan2.backend.service.npc.NpcDefence;
import se.oscarwiklund.twlan2.backend.service.npc.NpcDifficulty;
import se.oscarwiklund.twlan2.backend.service.npc.NpcEconomy;
import se.oscarwiklund.twlan2.backend.service.npc.NpcLogService;
import se.oscarwiklund.twlan2.backend.service.npc.NpcMilitary;
import se.oscarwiklund.twlan2.backend.service.npc.NpcOverrunCooldown;
import se.oscarwiklund.twlan2.backend.service.npc.NpcProfiles;
import se.oscarwiklund.twlan2.backend.service.npc.NpcRetirement;
import se.oscarwiklund.twlan2.backend.service.npc.NpcRhythm;
import se.oscarwiklund.twlan2.backend.service.npc.WorldView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.time.Instant;

// Small AI for NPC players. Every NPC has a stable personality (derived from its account id): how busy it is, whether it
// favours economy or military, and which troops it likes. Each tick an NPC village may queue a weighted-random building
// (only ones whose requirements are met, favouring low levels of the buildings the personality prefers, waiting for
// resources sometimes) and recruit troops. So NPC villages start different (see VillageGenerator) and keep
// diverging. With the world setting npcDifficulty above "off", NPCs also send attacks (see attack): raids on barbarian
// villages, other NPCs and real players, and on "normal" nobles that conquer. World speed applies automatically because
// orders use the normal build/train/movement services.
@Service
public class NpcService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NpcService.class);

    // The same account id always derives the same personality.
    private record Personality(double activity, double military, Map<BuildingType, Double> buildingWeight, Map<UnitType, Double> unitWeight,
                               double aggression) {}

    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final BuildQueueItemRepository buildQueue;
    private final TrainQueueItemRepository trainQueue;
    private final UnitStockRepository unitStock;
    private final VillageService villageService;
    private final BuildService buildService;
    private final TrainService trainService;
    private final MovementRepository movements;
    private final MovementService movementService;
    private final NobleService nobles;
    private final NpcTribeService npcTribes;
    private final NpcMarketService npcMarket;
    private final NpcLogService npcLog;
    private final NpcProfiles npcProfiles;
    private final NpcEconomy economy;
    private final NpcMilitary military;
    private final NpcDefence defence;
    private final NpcRhythm rhythm;
    private final NpcOverrunCooldown overrun;
    private final Map<Long, Personality> personalities = new java.util.concurrent.ConcurrentHashMap<>();
    private final WorldPoints worldPoints;
    private final TribeService tribeService;
    private final Random rnd = new Random();
    private final TransactionTemplate tx;
    private final long budgetMs;
    private long lastSocial;
    private long nsView, nsEconomy, nsMilitary, nsOther, nsTribes, nsMarket; // where a tick spends its time (logged when it is slow)

    public NpcService(AccountRepository accounts, VillageRepository villages, BuildQueueItemRepository buildQueue,
                      TrainQueueItemRepository trainQueue, UnitStockRepository unitStock, VillageService villageService,
                      BuildService buildService, TrainService trainService, MovementRepository movements,
                      MovementService movementService, NobleService nobles, NpcTribeService npcTribes, TribeService tribeService, NpcMarketService npcMarket, NpcLogService npcLog,
                      NpcProfiles npcProfiles, NpcEconomy economy, NpcMilitary military, NpcDefence defence, NpcRhythm rhythm, WorldPoints worldPoints,
                      NpcOverrunCooldown overrun, PlatformTransactionManager transactionManager, @Value("${game.npc-budget-ms:2500}") long budgetMs) {
        this.tx = new TransactionTemplate(transactionManager);
        this.budgetMs = budgetMs;
        this.defence = defence;
        this.rhythm = rhythm;
        this.overrun = overrun;
        this.military = military;
        this.worldPoints = worldPoints;
        this.npcProfiles = npcProfiles;
        this.economy = economy;
        this.npcLog = npcLog;
        this.npcMarket = npcMarket;
        this.npcTribes = npcTribes;
        this.tribeService = tribeService;
        this.movements = movements;
        this.movementService = movementService;
        this.nobles = nobles;
        this.accounts = accounts;
        this.villages = villages;
        this.buildQueue = buildQueue;
        this.trainQueue = trainQueue;
        this.unitStock = unitStock;
        this.villageService = villageService;
        this.buildService = buildService;
        this.trainService = trainService;
    }

    // Deliberately NOT one big transaction: every village step runs in its own small one, because Hibernate
    // dirty-checks the whole session before each query, so a session that holds a whole world made a tick cost more and more per
    // village (17 s for 180 villages on the real save). A tick also stops after game.npc-budget-ms; the villages that
    // did not get their turn are simply first in line next time (the order is shuffled).
    @Scheduled(fixedDelayString = "${game.npc-tick-ms:5000}", initialDelay = 10000)
    public void tick() {
        long started = System.nanoTime();
        nsView = nsEconomy = nsMilitary = nsOther = nsTribes = nsMarket = 0;
        int stepped = 0, asleep = 0, offline = 0, online = 0;
        Map<Long, WorldView> views = new HashMap<>(); // one picture of each world per tick, shared by every NPC
        Map<Long, World> worldsWithNpc = new HashMap<>();
        List<Account> npcs = new ArrayList<>(accounts.findByNpc(true));
        Collections.shuffle(npcs, rnd);
        Map<Long, List<Village>> villagesOf = new HashMap<>(); // one query for everybody's villages (a findByOwner per NPC per tick cost seconds)
        for (Village v : villages.findByOwnerType(OwnerType.PLAYER)) {
            if (v.getOwner() != null && v.getOwner().isNpc()) villagesOf.computeIfAbsent(v.getOwner().getId(), k -> new ArrayList<>()).add(v);
        }
        boolean overBudget = false;
        for (Account npc : npcs) {
            if (overBudget) break;
            List<Village> mine = villagesOf.getOrDefault(npc.getId(), List.of());
            if (mine.isEmpty() || mine.get(0).getWorld() == null) continue;
            if (NpcRetirement.isRetired(npc, mine.get(0).getWorld(), Instant.now())) continue; // stopped playing for good; AbandonmentService picks up its villages
            worldsWithNpc.putIfAbsent(mine.get(0).getWorld().getId(), mine.get(0).getWorld()); // (their defence is looked at even when they are offline)
            Personality me = personalities.computeIfAbsent(npc.getId(), id -> personality(npc));
            NpcRhythm.Mood mood = rhythm.check(npc.getId(), mine.get(0).getWorld(), me.activity(),
                    () -> npcProfiles.profileOf(npc).getSkill(), mine.stream().map(Village::getId).toList());
            if (mood == NpcRhythm.Mood.ASLEEP) asleep++; else if (mood == NpcRhythm.Mood.OFFLINE) offline++; else online++;
            if (mood == NpcRhythm.Mood.ASLEEP || mood == NpcRhythm.Mood.OFFLINE) continue;
            NpcProfile profile = null;
            for (Village v : mine) {
                if ((System.nanoTime() - started) / 1_000_000 > budgetMs) { overBudget = true; break; }
                World world = v.getWorld();
                if (world == null) continue;
                worldsWithNpc.putIfAbsent(world.getId(), world);
                NpcDifficulty difficulty = NpcDifficulty.of(world);
                double chance = mood == NpcRhythm.Mood.LOGIN ? 1 // logging in: does its round of every village
                        : Math.min(1, me.activity() * difficulty.actionRate() * NpcDifficulty.speedFactor(world));
                if (rnd.nextDouble() < chance) {
                    long t0 = System.nanoTime();
                    WorldView view = views.computeIfAbsent(world.getId(), id -> buildView(world));
                    nsView += System.nanoTime() - t0;
                    if (profile == null) profile = npcProfiles.profileOf(npc);
                    final NpcArchetype archetype = NpcArchetype.parse(profile.getArchetype());
                    final double skill = NpcProfiles.effectiveSkill(difficulty, profile.getSkill());
                    try {
                        tx.executeWithoutResult(status -> {
                            Village fresh = villages.findById(v.getId()).orElse(null); // the copy this transaction works on
                            if (fresh != null && fresh.getOwner() != null && fresh.getOwner().getId().equals(npc.getId())) {
                                step(fresh, me, archetype, skill, difficulty, view);
                            }
                        });
                    } catch (RuntimeException e) {
                        LOG.warn("NPC {} stumbled in {}: {}", npc.getUsername(), v.getName(), e.toString()); // one NPC's mistake must not stop the others
                    }
                    stepped++;
                }
            }
        }
        // NPCs notice attacks on their way (also in worlds where no village happened to act this tick)
        for (World w : worldsWithNpc.values()) {
            if (NpcDifficulty.of(w).notice() > 0) views.computeIfAbsent(w.getId(), id -> buildView(w));
        }
        try {
            defence.react(views.values());
        } catch (RuntimeException e) {
            LOG.warn("NPC defence failed: {}", e.toString());
        }
        // tribes and the market are slow social moves: at most once per 5 s however fast the tick runs
        if (System.currentTimeMillis() - lastSocial >= 4900) {
            lastSocial = System.currentTimeMillis();
            long tt = System.nanoTime();
            npcTribes.tick();
            long tm = System.nanoTime();
            npcMarket.tick();
            nsTribes = tm - tt;
            nsMarket = System.nanoTime() - tm;
        }
        long ms = (System.nanoTime() - started) / 1_000_000;
        LOG.debug("NPC rhythm: {} asleep, {} away, {} online; {} villages acted in {} ms", asleep, offline, online, stepped, ms);
        if (ms > 500) {
            LOG.info("NPC tick took {} ms{} ({} villages acted; world view {} ms, economy {} ms, military {} ms, nobles {} ms, tribes {} ms, market {} ms)",
                    ms, overBudget ? " (stopped at the budget)" : "", stepped, nsView / 1_000_000, nsEconomy / 1_000_000, nsMilitary / 1_000_000, nsOther / 1_000_000, nsTribes / 1_000_000, nsMarket / 1_000_000);
        }
    }

    // The snapshot is built in one read-only transaction (its movements' troop lists are loaded there); the steps then read it detached.
    private WorldView buildView(World world) {
        TransactionTemplate readOnly = new TransactionTemplate(tx.getTransactionManager());
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> WorldView.build(world, villages, movements, tribeService, worldPoints));
    }

    private static Personality personality(Account npc) {
        Random r = new Random(npc.getId() * 7919L + 17);
        double economy = 0.6 + r.nextDouble();      // 0.6 .. 1.6
        double military = 0.2 + r.nextDouble() * 1.4;
        Map<BuildingType, Double> w = new EnumMap<>(BuildingType.class);
        w.put(BuildingType.HEADQUARTERS, 0.7 + r.nextDouble() * 0.6);
        w.put(BuildingType.TIMBER_CAMP, economy * (0.7 + r.nextDouble() * 0.6));
        w.put(BuildingType.CLAY_PIT, economy * (0.7 + r.nextDouble() * 0.6));
        w.put(BuildingType.IRON_MINE, economy * (0.7 + r.nextDouble() * 0.6));
        w.put(BuildingType.FARM, 0.4 + r.nextDouble() * 0.4);
        w.put(BuildingType.WAREHOUSE, 0.4 + r.nextDouble() * 0.4);
        w.put(BuildingType.BARRACKS, military * (0.6 + r.nextDouble() * 0.8));
        w.put(BuildingType.WALL, r.nextDouble() * 0.9);
        w.put(BuildingType.SMITHY, military * r.nextDouble());
        w.put(BuildingType.MARKET, r.nextDouble() * 0.6);
        w.put(BuildingType.STABLE, military * r.nextDouble() * 0.6);
        w.put(BuildingType.WORKSHOP, military * r.nextDouble() * 0.3);
        w.put(BuildingType.STATUE, r.nextDouble() * 0.15);
        w.put(BuildingType.HIDING_PLACE, r.nextDouble() * 0.2);
        Map<UnitType, Double> u = new EnumMap<>(UnitType.class);
        // the first seven units keep drawing from the same sequence as before, so existing NPCs keep their personality
        Random extra = new Random(npc.getId() * 104729L + 3);
        for (UnitType t : UnitType.values()) u.put(t, 0.1 + (t.ordinal() < UnitType.SNOB.ordinal() + 1 ? r : extra).nextDouble());
        double aggression = 0.4 + extra.nextDouble() * 1.2; // how eager it is to send troops out
        return new Personality(0.25 + r.nextDouble() * 0.65, military, w, u, aggression);
    }

    private void step(Village v, Personality me, NpcArchetype archetype, double skill, NpcDifficulty difficulty, WorldView view) {
        long t0 = System.nanoTime();
        // snap = what the village looked like when the step began (levels do not change during a step, only the queues).
        NpcEconomy.Snapshot snap = economy.load(v);
        noblesWanted(v, archetype, difficulty, snap);
        // just wiped out: hold off recruiting for a real-world grace period so a follow-up attack can snipe it
        boolean recovering = overrun.isRecovering(v.getId());
        if (rnd.nextDouble() < (1 - skill) * 0.5) {
            // a slip: does something off the plan (the visible mistakes of a weak NPC)
            if (snap.build.size() < 2) build(v, snap, me);
            if (!recovering) recruit(v, me, snap);
        } else {
            if (economy.build(v, archetype, difficulty, snap, rnd) == NpcEconomy.Build.NOTHING_LEFT && snap.build.size() < 2) {
                build(v, snap, me); // the plan is finished: keep developing at random
            }
            if (!recovering) economy.recruit(v, archetype, skill, difficulty, snap, view.from(v), view.underAttack(v), rnd);
        }
        long t1 = System.nanoTime();
        educateNoble(v, archetype, difficulty, snap);
        long t2 = System.nanoTime();
        military.step(v, archetype, skill, difficulty, view, rnd);
        rhythm.noteDue(v.getOwner().getId(), v.getId(), nextDone(v, view));
        long t3 = System.nanoTime();
        nsEconomy += t1 - t0;
        nsOther += t2 - t1;
        nsMilitary += t3 - t2;
    }

    // When the first thing this village is waiting for is done (an order, or troops back from a raid): the NPC logs in then.
    private long nextDone(Village v, WorldView view) {
        long due = Long.MAX_VALUE;
        for (BuildQueueItem q : buildQueue.findByVillageOrderByPositionAsc(v)) if (q.getCompletesAt() != null) due = Math.min(due, q.getCompletesAt().toEpochMilli());
        for (TrainQueueItem q : trainQueue.findByVillageOrderByPositionAsc(v)) if (q.getCompletesAt() != null) due = Math.min(due, q.getCompletesAt().toEpochMilli());
        for (Movement m : view.from(v)) {
            long home = m.getArrivesAt().toEpochMilli();
            if (m.getType() == MovementType.ATTACK && m.getDepartedAt() != null) home += home - m.getDepartedAt().toEpochMilli(); // and back again
            if (m.getType() == MovementType.RETURN || m.getType() == MovementType.ATTACK) due = Math.min(due, home);
        }
        return due;
    }

    private void build(Village v, NpcEconomy.Snapshot snap, Personality me) {
        List<BuildQueueItem> queue = snap.build;
        List<BuildingType> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (var e : me.buildingWeight().entrySet()) {
            BuildingType t = e.getKey();
            long queued = queue.stream().filter(q -> q.getType() == t).count();
            int level = snap.level(t);
            if (level + queued >= t.maxLevel || !requirementsMet(snap, t)) continue;
            // favour buildings that are behind: weight fades as the level nears the maximum
            double room = 1.0 - (double) (level + queued) / t.maxLevel;
            candidates.add(t);
            weights.add(e.getValue() * Math.max(0.05, room));
        }
        for (int attempt = 0; attempt < 3 && !candidates.isEmpty(); attempt++) {
            int i = pick(weights);
            BuildingType t = candidates.get(i);
            try {
                buildService.enqueue(v, t);
                return;
            } catch (BuildService.BuildException ex) {
                String m = ex.getMessage() == null ? "" : ex.getMessage();
                if (m.startsWith("Farm too small")) { tryBuild(v, BuildingType.FARM); return; }
                if (m.startsWith("Warehouse too small")) { tryBuild(v, BuildingType.WAREHOUSE); return; }
                if (m.startsWith("Not enough resources") && rnd.nextDouble() < 0.6) return; // save up for it
                candidates.remove(i); // otherwise look at something else
                weights.remove(i);
            }
        }
    }

    private boolean requirementsMet(NpcEconomy.Snapshot snap, BuildingType t) {
        for (var req : t.requirements().entrySet()) {
            if (snap.level(req.getKey()) < req.getValue()) return false;
        }
        return true;
    }

    private int pick(List<Double> weights) {
        double sum = 0;
        for (double w : weights) sum += w;
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < weights.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return i;
        }
        return weights.size() - 1;
    }

    private void tryBuild(Village v, BuildingType t) {
        try {
            buildService.enqueue(v, t);
        } catch (BuildService.BuildException ignored) {
            // wait for resources
        }
    }

    // Trains a batch of a favoured troop type, keeping the garrison within a share of the farm that suits the personality.
    private void recruit(Village v, Personality me, NpcEconomy.Snapshot snap) {
        int barracks = snap.level(BuildingType.BARRACKS);
        if (!snap.train.isEmpty()) return;
        if (rnd.nextDouble() > 0.35 + 0.4 * me.military()) return;
        List<UnitType> allowed = new ArrayList<>();
        for (UnitType t : UnitType.values()) {
            if (t.isRegularTroop() && snap.level(t.recruitBuilding) >= 1
                    && t.requirementsMet(snap::level)) allowed.add(t);
        }
        if (allowed.isEmpty()) return;

        int garrison = 0;
        for (var e : snap.homeUnits.entrySet()) garrison += e.getValue() * e.getKey().popCost;
        if (garrison >= villageService.populationCapacity(v) * (0.15 + 0.25 * Math.min(1, me.military()))) return;

        List<Double> weights = new ArrayList<>();
        for (UnitType t : allowed) weights.add(me.unitWeight().get(t));
        UnitType type = allowed.get(pick(weights));
        try {
            trainService.enqueue(v, type, Math.max(1, (3 + rnd.nextInt(3 + Math.max(1, barracks))) / Math.max(1, type.popCost)));
        } catch (TrainService.TrainException ignored) {
            // not enough resources / population yet
        }
    }

    // Conquering archetypes (and some raiders and balanced NPCs, decided by account) expand with noblemen, if the world lets NPCs conquer.
    private static boolean expands(NpcArchetype archetype, Account owner) {
        return archetype == NpcArchetype.CONQUEROR
                || ((archetype == NpcArchetype.RAIDER || archetype == NpcArchetype.BALANCED) && owner.getId() % 3 == 0);
    }

    private static int noblesNeeded(World world) {
        return ConquestPlan.wavesNeeded(100, (int) WorldSettings.number(world, "noblemanMinDecrease"),
                (int) WorldSettings.number(world, "noblemanMaxDecrease"), false);
    }

    private static int noblesHave(NpcEconomy.Snapshot snap) {
        int have = snap.homeUnits.getOrDefault(UnitType.SNOB, 0);
        for (TrainQueueItem q : snap.train) if (q.getType() == UnitType.SNOB) have += q.getTotalCount() - q.getProducedCount();
        return have;
    }

    // Sets what the village has to keep aside for its next nobleman (troops are not paid from it), if it is meant to educate one.
    private void noblesWanted(Village v, NpcArchetype archetype, NpcDifficulty difficulty, NpcEconomy.Snapshot snap) {
        World world = v.getWorld();
        if (world == null || difficulty.expansionFor(world) == NpcDifficulty.Expansion.NONE || !expands(archetype, v.getOwner())) return;
        if (snap.level(BuildingType.ACADEMY) < 1 || noblesHave(snap) >= noblesNeeded(world)) return;
        snap.nobleCost[0] = UnitType.SNOB.woodCost + WorldSettings.number(world, "coinWood");
        snap.nobleCost[1] = UnitType.SNOB.clayCost + WorldSettings.number(world, "coinClay");
        snap.nobleCost[2] = UnitType.SNOB.ironCost + WorldSettings.number(world, "coinIron");
    }

    // Educates a nobleman when the village has an Academy, wants one (see noblesWanted) and the world's noble limit allows.
    private void educateNoble(Village v, NpcArchetype archetype, NpcDifficulty difficulty, NpcEconomy.Snapshot snap) {
        World world = v.getWorld();
        if (snap.nobleCost[0] <= 0 || rnd.nextDouble() > 0.5) return;
        Account owner = v.getOwner();
        int need = noblesNeeded(world), have = noblesHave(snap);
        try {
            NobleService.Info info = nobles.info(owner, world);
            if (info.possible() <= 0) {
                nobles.mint(v);
                info = nobles.info(owner, world);
            }
            if (info.possible() > 0) {
                trainService.enqueue(v, UnitType.SNOB, 1);
                npcLog.add(world, owner, v, "NOBLE", "Educating a nobleman in " + v.getName() + " (" + (have + 1) + " of " + need + " for a conquest)");
            }
        } catch (NobleService.NobleException | TrainService.TrainException ignored) {
            // no resources / warehouse too small / a nobleman is already being educated
        }
    }

}
