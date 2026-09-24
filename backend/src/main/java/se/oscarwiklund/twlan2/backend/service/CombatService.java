package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.service.npc.NpcDifficulty;
import se.oscarwiklund.twlan2.backend.service.npc.NpcIntelService;
import se.oscarwiklund.twlan2.backend.service.npc.NpcLogService;
import se.oscarwiklund.twlan2.backend.service.npc.NpcOverrunCooldown;
import se.oscarwiklund.twlan2.backend.repo.BuildingRepository;
import se.oscarwiklund.twlan2.backend.repo.CombatReportRepository;
import se.oscarwiklund.twlan2.backend.repo.MovementRepository;
import se.oscarwiklund.twlan2.backend.repo.UnitStockRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CombatService {

    private final LiveUpdates live;
    private final UnitStockRepository unitStockRepository;
    private final CombatReportRepository combatReportRepository;
    private final VillageService villageService;
    private final AchievementService achievements;
    private final ConquestService conquests;
    private final BuildingRepository buildingRepository;
    private final VillageRepository villageRepository;
    private final MovementRepository movementRepository;
    private final SupportService support;
    private final WarService wars;
    private final NpcLogService npcLog;
    private final NpcIntelService npcIntel;
    private final NpcOverrunCooldown overrun;

    public CombatService(LiveUpdates live, UnitStockRepository unitStockRepository, CombatReportRepository combatReportRepository,
                          VillageService villageService, AchievementService achievements, ConquestService conquests,
                          BuildingRepository buildingRepository, VillageRepository villageRepository,
                          MovementRepository movementRepository, SupportService support, WarService wars, NpcLogService npcLog,
                          NpcIntelService npcIntel, NpcOverrunCooldown overrun) {
        this.live = live;
        this.npcIntel = npcIntel;
        this.npcLog = npcLog;
        this.overrun = overrun;
        this.support = support;
        this.wars = wars;
        this.buildingRepository = buildingRepository;
        this.villageRepository = villageRepository;
        this.movementRepository = movementRepository;
        this.achievements = achievements;
        this.conquests = conquests;
        this.unitStockRepository = unitStockRepository;
        this.combatReportRepository = combatReportRepository;
        this.villageService = villageService;
    }

    public record Result(CombatReport report, Map<UnitType, Integer> survivors, double lootWood, double lootClay, double lootIron) {}

    @Transactional
    public Result resolve(Movement attack) {
        Village defenderVillage = attack.getTargetVillage();
        Account previousOwner = defenderVillage.getOwner();
        villageService.settleResources(defenderVillage);

        Map<UnitType, Integer> attackerUnits = attack.getUnits();
        Map<UnitType, Integer> defenderUnits = new EnumMap<>(UnitType.class);
        for (UnitStock stock : unitStockRepository.findByVillage(defenderVillage)) {
            if (stock.getCount() > 0) {
                defenderUnits.put(stock.getType(), stock.getCount());
            }
        }
        // troops of tribe-mates stationed here fight alongside the garrison; their owners get a report too
        Map<Long, Account> supporters = new java.util.LinkedHashMap<>();
        for (StationedTroops guest : support.guestsAt(defenderVillage)) {
            guest.getUnits().forEach((t, n) -> { if (n != null && n > 0) defenderUnits.merge(t, n, Integer::sum); });
            Account owner = guest.getOriginVillage().getOwner();
            if (owner != null) supporters.putIfAbsent(owner.getId(), owner);
        }

        World world = defenderVillage.getWorld();
        Account attackerOwner = attack.getOriginVillage().getOwner();
        Map<String, Integer> detail = new HashMap<>();
        attackerUnits.forEach((t, n) -> detail.put("att:" + t.name(), n));
        defenderUnits.forEach((t, n) -> detail.put("def:" + t.name(), n));
        // which villages fought (the names alone are ambiguous: every barbarian village is an "Abandoned Camp")
        detail.put("att_village", attack.getOriginVillage().getId().intValue());
        detail.put("def_village", defenderVillage.getId().intValue());

        boolean scoutsOnly = attackerUnits.keySet().stream().allMatch(t -> t == UnitType.SCOUT);
        int wallBefore = villageService.levelOf(defenderVillage, BuildingType.WALL);
        int wallAfter = wallBefore;
        BattleOutcome outcome;
        Map<UnitType, Integer> attackerLosses;
        Map<UnitType, Integer> defenderLosses;
        Map<UnitType, Integer> survivors = new EnumMap<>(UnitType.class);
        Map<BuildingType, Integer> destroyed = new EnumMap<>(BuildingType.class);

        if (scoutsOnly) {
            // an attack of scouts only is spying: no battle, no loot, just the scout duel
            BattleCalculator.Battle duel = BattleCalculator.scoutDuel(attackerUnits.getOrDefault(UnitType.SCOUT, 0),
                    defenderUnits.getOrDefault(UnitType.SCOUT, 0));
            outcome = duel.attackerWins() ? BattleOutcome.ATTACKER_WIN : BattleOutcome.DEFENDER_WIN;
            attackerLosses = duel.attackerLosses();
            defenderLosses = duel.defenderLosses();
            if (outcome == BattleOutcome.DEFENDER_WIN && previousOwner != null) {
                achievements.count(previousOwner, world, "scout_defended", 1);
            }
        } else {
            // morale only weakens attacks on players who are much smaller than the attacker; barbarians never do
            double morale = 1;
            if (!"off".equals(WorldSettings.get(world, "morale")) && previousOwner != null && attackerOwner != null) {
                morale = BattleCalculator.morale(pointsOf(attackerOwner, world), pointsOf(previousOwner, world));
            }
            double luck = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * BattleCalculator.MAX_LUCK;
            // night bonus: the defenders fight harder while the world's clock is inside its night hours
            double night = WorldSettings.isNight(world, java.time.LocalTime.now()) ? WorldSettings.number(world, "nightBonus") : 1;
            BattleCalculator.Battle battle = BattleCalculator.fight(attackerUnits, defenderUnits, wallBefore, morale, luck,
                    night, WorldSettings.number(world, "basicDefense"));
            outcome = battle.attackerWins() ? BattleOutcome.ATTACKER_WIN : BattleOutcome.DEFENDER_WIN;
            attackerLosses = battle.attackerLosses();
            defenderLosses = battle.defenderLosses();
            detail.put("luck", (int) Math.round(luck * 100));
            detail.put("morale", (int) Math.round(morale * 100));
        }

        for (Map.Entry<UnitType, Integer> e : attackerUnits.entrySet()) {
            int alive = e.getValue() - attackerLosses.getOrDefault(e.getKey(), 0);
            if (alive > 0 && (outcome == BattleOutcome.ATTACKER_WIN || scoutsOnly)) survivors.put(e.getKey(), alive);
        }
        // every attacking unit appears in the losses (0 when none died), like the report shows them
        for (UnitType t : attackerUnits.keySet()) attackerLosses.putIfAbsent(t, 0);

        // the garrison and every guest army lose their share of the defence's losses
        Map<Long, Long> supporterLosses = support.applyDefenderLosses(defenderVillage, defenderLosses);
        long attackerLossesSum = attackerLosses.values().stream().mapToLong(Integer::longValue).sum();
        if (previousOwner != null) achievements.onDefend(previousOwner, world, attackerLossesSum);
        for (Account supporter : supporters.values()) {
            achievements.onSupport(supporter, world, attackerLossesSum);
            achievements.count(supporter, world, "support_battles", 1);
            Long lost = supporterLosses.get(supporter.getId());
            if (lost != null) achievements.count(supporter, world, "support_losses", lost);
        }
        // every defender (garrison + guests) was wiped out: give the NPC a real-world grace period before it starts
        // recruiting again, so a follow-up attack has a genuine chance to snipe the village while it's still weak
        if (outcome == BattleOutcome.ATTACKER_WIN && !scoutsOnly && previousOwner != null && previousOwner.isNpc()
                && !defenderUnits.isEmpty()
                && defenderUnits.entrySet().stream().allMatch(e -> defenderLosses.getOrDefault(e.getKey(), 0) >= e.getValue())) {
            overrun.markOverrun(defenderVillage.getId());
        }

        // scouts that made it through report what they saw on arrival: the village as it was before the battle
        int spyScouts = survivors.getOrDefault(UnitType.SCOUT, 0);
        if (spyScouts > 0 && outcome == BattleOutcome.ATTACKER_WIN) {
            spy(defenderVillage, BattleCalculator.spyLevel(spyScouts), defenderUnits, detail);
        }

        // rams and catapults that survive a won battle damage the wall / a building
        if (outcome == BattleOutcome.ATTACKER_WIN && !scoutsOnly && WorldSettings.bool(world, "destroyBuildings")) {
            int rams = survivors.getOrDefault(UnitType.RAM, 0);
            if (rams > 0 && wallBefore > 0) {
                int lost = BattleCalculator.levelsDestroyed(rams, wallBefore);
                if (lost > 0) {
                    wallAfter = wallBefore - lost;
                    damage(defenderVillage, BuildingType.WALL, wallAfter);
                    destroyed.merge(BuildingType.WALL, lost, Integer::sum);
                    achievements.count(attackerOwner, world, "wall_levels_destroyed", lost);
                }
            }
            int catapults = survivors.getOrDefault(UnitType.CATAPULT, 0);
            if (catapults > 0) {
                List<Building> candidates = new ArrayList<>();
                for (Building b : buildingRepository.findByVillage(defenderVillage)) {
                    if (b.getLevel() > 0 && b.getType() != BuildingType.WALL) candidates.add(b);
                }
                if (!candidates.isEmpty()) {
                    Building target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                    int lost = BattleCalculator.levelsDestroyed(catapults, target.getLevel());
                    if (lost > 0) {
                        damage(defenderVillage, target.getType(), target.getLevel() - lost);
                        destroyed.merge(target.getType(), lost, Integer::sum);
                        achievements.count(attackerOwner, world, "building_levels_destroyed", lost);
                    }
                }
            }
        }
        detail.put("wall_before", wallBefore);
        detail.put("wall_after", wallAfter);
        destroyed.forEach((t, n) -> detail.put("dmg:" + t.name(), n));

        // noblemen: however many survive a won battle, the group acts as ONE nobleman: a single loyalty drop. Lowering the
        // loyalty does not use a nobleman up: they die only in the battle itself (the normal losses) and otherwise come home
        Integer loyaltyFrom = null;
        Integer loyaltyTo = null;
        boolean conquered = false;
        int nobles = outcome == BattleOutcome.ATTACKER_WIN && !scoutsOnly ? survivors.getOrDefault(UnitType.SNOB, 0) : 0;
        // an NPC's noblemen only conquer what the world's NPC difficulty / npcConquest setting allows (by default never a real
        // player's last village: the game would be over): they do nothing there
        boolean npcMayNotConquer = false;
        if (nobles > 0 && attackerOwner != null && attackerOwner.isNpc()) {
            boolean human = previousOwner != null && !previousOwner.isNpc();
            boolean last = human && villageRepository.findByWorldAndOwner(world, previousOwner).size() <= 1;
            npcMayNotConquer = !NpcDifficulty.of(world).expansionFor(world)
                    .allows(defenderVillage.getOwnerType() == OwnerType.BARBARIAN, human, last);
        }
        if (nobles > 0 && attackerOwner != null && defenderVillage.getWorld() != null && !npcMayNotConquer) {
            World w = world;
            int min = (int) WorldSettings.number(w, "noblemanMinDecrease");
            int max = Math.max(min, (int) WorldSettings.number(w, "noblemanMaxDecrease"));
            double loyalty = villageService.loyaltyOf(defenderVillage);
            loyaltyFrom = (int) Math.ceil(loyalty);
            int decrease = min + (int) (Math.random() * (max - min + 1));
            loyalty -= decrease;
            if (loyalty <= 0) {
                conquered = true;
                loyaltyTo = 0;
            } else {
                villageService.setLoyalty(defenderVillage, loyalty);
                loyaltyTo = (int) Math.ceil(loyalty);
            }
            achievements.onNobles(attackerOwner, w, loyaltyFrom - decrease, conquered);
        }

        double lootWood = 0, lootClay = 0, lootIron = 0;
        double carryCapacity = survivors.entrySet().stream()
                .mapToDouble(e -> e.getKey().carryCapacity * e.getValue()).sum();
        if (outcome == BattleOutcome.ATTACKER_WIN && !scoutsOnly) {
            // the hiding place keeps its capacity of every resource out of reach
            double hidden = BuildingType.hidingCapacity(villageService.levelOf(defenderVillage, BuildingType.HIDING_PLACE));
            double availWood = Math.max(0, defenderVillage.getWood() - hidden);
            double availClay = Math.max(0, defenderVillage.getClay() - hidden);
            double availIron = Math.max(0, defenderVillage.getIron() - hidden);
            double totalAvailable = availWood + availClay + availIron;
            double totalLoot = Math.min(carryCapacity, totalAvailable);
            if (totalAvailable > 0) {
                lootWood = totalLoot * (availWood / totalAvailable);
                lootClay = totalLoot * (availClay / totalAvailable);
                lootIron = totalLoot * (availIron / totalAvailable);
                defenderVillage.setWood(defenderVillage.getWood() - lootWood);
                defenderVillage.setClay(defenderVillage.getClay() - lootClay);
                defenderVillage.setIron(defenderVillage.getIron() - lootIron);
            }
        }
        detail.put("capacity", (int) Math.round(carryCapacity));

        CombatReport report = new CombatReport();
        report.setWorldId(attack.getOriginVillage().getWorld() == null ? null : attack.getOriginVillage().getWorld().getId());
        report.setAccountId(attackerOwner == null ? null : attackerOwner.getId());
        report.setAttackerVillageName(attack.getOriginVillage().getName());
        report.setDefenderVillageName(defenderVillage.getName());
        report.setAttackerPlayer(attackerOwner == null ? null : attackerOwner.getUsername());
        report.setDefenderPlayer(previousOwner == null ? null : previousOwner.getUsername());
        report.setOutcome(outcome);
        report.setAttackerLosses(attackerLosses);
        report.setDefenderLosses(defenderLosses);
        report.setLootWood(lootWood);
        report.setLootClay(lootClay);
        report.setLootIron(lootIron);
        report.setOccurredAt(Instant.now());
        report.setLoyaltyFrom(loyaltyFrom);
        report.setLoyaltyTo(loyaltyTo);
        report.setConquered(conquered);
        report.setDetail(detail);
        // reports are only written for real players: to the attacker, and (a copy of it) to the defender when a
        // different real player was attacked; NPC-vs-NPC and NPC-vs-barbarian battles leave none behind
        if (attackerOwner != null && !attackerOwner.isNpc()) combatReportRepository.save(report);
        if (previousOwner != null && !previousOwner.isNpc() && (attackerOwner == null || !previousOwner.getId().equals(attackerOwner.getId()))) {
            combatReportRepository.save(defenderCopy(report, previousOwner));
        }
        for (Account supporter : supporters.values()) {
            boolean seen = (previousOwner != null && supporter.getId().equals(previousOwner.getId()))
                    || (attackerOwner != null && supporter.getId().equals(attackerOwner.getId()));
            if (!supporter.isNpc() && !seen) combatReportRepository.save(defenderCopy(report, supporter));
        }
        Long worldId = report.getWorldId();
        live.village(attack.getOriginVillage());
        live.village(defenderVillage);
        for (Account a : new Account[] {attackerOwner, previousOwner}) {
            live.toAccount(a, worldId, LiveUpdates.REPORTS);
            live.toAccount(a, worldId, LiveUpdates.VILLAGE);
        }
        for (Account supporter : supporters.values()) {
            live.toAccount(supporter, worldId, LiveUpdates.REPORTS);
            live.toAccount(supporter, worldId, LiveUpdates.VILLAGE);
        }
        if (conquered) {
            if (previousOwner != null && attackerOwner != null) {
                if (previousOwner.getId().equals(attackerOwner.getId())) {
                    achievements.count(attackerOwner, world, "self_conquest", 1);
                } else {
                    achievements.onConquered(previousOwner, world);
                }
            }
            conquests.conquer(defenderVillage, attackerOwner);
            achievements.onConquest(attackerOwner, defenderVillage.getWorld());
        }

        if (attackerOwner != null && attackerOwner.isNpc()) {
            long sent = attackerUnits.values().stream().mapToLong(Integer::longValue).sum();
            long lost = attackerLosses.values().stream().mapToLong(Integer::longValue).sum();
            String victim = defenderVillage.getName() + " (" + defenderVillage.getX() + "|" + defenderVillage.getY() + ")"
                    + (previousOwner == null ? "" : " of " + previousOwner.getUsername());
            String what = scoutsOnly ? "Scouting " : "Attack on ";
            npcLog.add(world, attackerOwner, attack.getOriginVillage(), conquered ? "CONQUEST" : scoutsOnly ? "SCOUT" : "BATTLE",
                    (conquered ? "Conquered " : what) + victim + (conquered ? "" : (outcome == BattleOutcome.ATTACKER_WIN ? ": won" : ": lost"))
                            + ", lost " + lost + " of " + sent + " units");
            // what the NPC learned: scouts report the garrison, a battle shows how the raid went
            if (scoutsOnly) {
                int spyLevel = detail.getOrDefault("spy_level", 0);
                if (spyLevel > 0) {
                    npcIntel.spy(world, attackerOwner, defenderVillage, spyLevel, defenderUnits, spyLevel >= 2 ? detail.getOrDefault("spy_building:WALL", 0) : null,
                            detail.getOrDefault("spy_wood", 0), detail.getOrDefault("spy_clay", 0), detail.getOrDefault("spy_iron", 0));
                }
            } else {
                npcIntel.battle(world, attackerOwner, defenderVillage, outcome == BattleOutcome.ATTACKER_WIN, wallAfter, (int) Math.round(lootWood + lootClay + lootIron));
            }
        }
        // an NPC remembers who attacked it
        if (!scoutsOnly && previousOwner != null && previousOwner.isNpc() && attackerOwner != null && !previousOwner.getId().equals(attackerOwner.getId())) {
            npcIntel.hit(world, previousOwner, attackerOwner, conquered ? 3 : outcome == BattleOutcome.ATTACKER_WIN ? 1.5 : 0.7);
        }
        achievements.onBattle(attack.getOriginVillage().getOwner(), previousOwner, defenderVillage.getWorld(),
                outcome == BattleOutcome.ATTACKER_WIN, defenderUnits.values().stream().mapToLong(Integer::longValue).sum(),
                defenderLosses.values().stream().mapToLong(Integer::longValue).sum(),
                Math.round(lootWood + lootClay + lootIron), outcome == BattleOutcome.ATTACKER_WIN ? defenderLosses.getOrDefault(UnitType.SNOB, 0) : 0);

        if (attackerOwner != null && previousOwner != null && attackerOwner.getId().equals(previousOwner.getId())) {
            achievements.onSelfAttackLoss(attackerOwner, world, attackerLossesSum);
        }

        wars.onBattle(defenderVillage.getWorld(), attackerOwner, previousOwner,
                defenderLosses.values().stream().mapToLong(Integer::longValue).sum(),
                attackerLosses.values().stream().mapToLong(Integer::longValue).sum());

        return new Result(report, survivors, lootWood, lootClay, lootIron);
    }

    // For the host of the village, or a tribe-mate who had troops there.
    private CombatReport defenderCopy(CombatReport report, Account account) {
        CombatReport mine = new CombatReport();
        mine.setWorldId(report.getWorldId());
        mine.setAccountId(account.getId());
        mine.setDefenderView(true);
        mine.setAttackerVillageName(report.getAttackerVillageName());
        mine.setDefenderVillageName(report.getDefenderVillageName());
        mine.setAttackerPlayer(report.getAttackerPlayer());
        mine.setDefenderPlayer(report.getDefenderPlayer());
        mine.setOutcome(report.getOutcome());
        mine.setAttackerLosses(new EnumMap<>(report.getAttackerLosses()));
        mine.setDefenderLosses(new EnumMap<>(report.getDefenderLosses()));
        mine.setLootWood(report.getLootWood());
        mine.setLootClay(report.getLootClay());
        mine.setLootIron(report.getLootIron());
        mine.setOccurredAt(report.getOccurredAt());
        mine.setLoyaltyFrom(report.getLoyaltyFrom());
        mine.setLoyaltyTo(report.getLoyaltyTo());
        mine.setConquered(report.isConquered());
        mine.setDetail(new HashMap<>(report.getDetail()));
        return mine;
    }

    // A battle destroyed some levels of this building.
    private void damage(Village village, BuildingType type, int newLevel) {
        buildingRepository.findByVillageAndType(village, type).ifPresent(b -> {
            b.setLevel(Math.max(0, newLevel));
            buildingRepository.save(b);
        });
    }

    // What scouts see, by how many made it through (see BattleCalculator.spyLevel).
    private void spy(Village village, int level, Map<UnitType, Integer> defenderBefore, Map<String, Integer> detail) {
        detail.put("spy_level", level);
        detail.put("spy_wood", (int) village.getWood());
        detail.put("spy_clay", (int) village.getClay());
        detail.put("spy_iron", (int) village.getIron());
        if (level >= 2) {
            for (Building b : buildingRepository.findByVillage(village)) {
                if (b.getLevel() > 0) detail.put("spy_building:" + b.getType().name(), b.getLevel());
            }
        }
        if (level >= 3) {
            defenderBefore.forEach((t, n) -> detail.put("spy_home:" + t.name(), n));
        }
        if (level >= 4) {
            Map<UnitType, Integer> away = new EnumMap<>(UnitType.class);
            for (Movement m : movementRepository.findByOriginVillageOrTargetVillage(village, village)) {
                boolean out = m.getOriginVillage().getId().equals(village.getId()) && m.getType() != MovementType.RETURN;
                boolean back = m.getTargetVillage().getId().equals(village.getId()) && m.getType() == MovementType.RETURN;
                if (out || back) m.getUnits().forEach((t, n) -> away.merge(t, n, Integer::sum));
            }
            away.forEach((t, n) -> detail.put("spy_away:" + t.name(), n));
        }
    }

    // Sum over the buildings of all the player's villages in this world.
    private int pointsOf(Account account, World world) {
        int points = 0;
        for (Village v : villageRepository.findByWorldAndOwner(world, account)) {
            for (Building b : buildingRepository.findByVillage(v)) points += b.getType().points(b.getLevel());
        }
        return points;
    }
}
