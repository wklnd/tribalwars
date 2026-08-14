package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.BuildingRepository;
import se.oscarwiklund.twlan2.backend.repo.UnitStockRepository;
import se.oscarwiklund.twlan2.backend.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// How NPCs react to an attack that is on its way. They only know what a player would: which village sends it (and how big
// that village is). Each incoming attack is noticed with the difficulty's chance per tick; the NPC then estimates the force
// (DodgeDecision), and if the village would fall it asks its own other villages and tribe-mates' NPC villages for
// support that can arrive in time, and moves its offensive troops out of harm's way (to another village of its own).
// Everything goes through MovementService, so distances, travel times and tribe rules are the players' rules.
@Service
public class NpcDefence {

    private final UnitStockRepository unitStock;
    private final BuildingRepository buildings;
    private final MovementService movementService;
    private final NpcProfiles profiles;
    private final NpcLogService npcLog;
    private final GameSettings settings;
    private final NpcRhythm rhythm;
    private final Random rnd = new Random();
    // attacks already reacted to (an NPC reacts once per attack); forgotten when the attack has landed
    private final Set<Long> handled = new HashSet<>();

    public NpcDefence(UnitStockRepository unitStock, BuildingRepository buildings, MovementService movementService,
                      NpcProfiles profiles, NpcLogService npcLog, GameSettings settings, NpcRhythm rhythm) {
        this.rhythm = rhythm;
        this.unitStock = unitStock;
        this.buildings = buildings;
        this.movementService = movementService;
        this.profiles = profiles;
        this.npcLog = npcLog;
        this.settings = settings;
    }

    @Transactional(noRollbackFor = MovementService.MovementException.class)
    public void react(Collection<WorldView> views) {
        Set<Long> live = new HashSet<>();
        Instant now = Instant.now();
        for (WorldView view : views) {
            NpcDifficulty d = NpcDifficulty.of(view.world);
            for (Village v : view.villages) {
                Account owner = v.getOwner();
                if (owner == null || !owner.isNpc()) continue;
                boolean online = rhythm.isOnline(owner.getId(), view.world); // asleep or away from the game, an NPC does not see the attack coming
                for (Movement m : new ArrayList<>(view.targeting(v))) { // a copy: reacting adds movements
                    if (m.getType() != MovementType.ATTACK) continue;
                    live.add(m.getId());
                    if (!online || d.notice() <= 0 || handled.contains(m.getId()) || rnd.nextDouble() > d.notice()) continue;
                    long left = java.time.Duration.between(now, m.getArrivesAt()).getSeconds();
                    if (left < 20) continue; // too late to do anything
                    handled.add(m.getId());
                    try {
                        respond(v, m, left, d, view);
                    } catch (MovementService.MovementException ignored) {
                        // e.g. the helper is out of range now: the NPC just takes the hit
                    }
                }
            }
        }
        handled.retainAll(live);
    }

    private void respond(Village v, Movement attack, long secondsLeft, NpcDifficulty d, WorldView view) {
        Account me = v.getOwner();
        double skill = NpcProfiles.effectiveSkill(d, profiles.profileOf(me).getSkill());
        Village from = attack.getOriginVillage();

        Map<UnitType, Integer> home = new EnumMap<>(UnitType.class);
        for (UnitStock s : unitStock.findByVillage(v)) if (s.getCount() > 0) home.put(s.getType(), s.getCount());
        int wall = buildings.findByVillageAndType(v, BuildingType.WALL).map(Building::getLevel).orElse(0);
        World world = v.getWorld();
        double basic = WorldSettings.number(world, "basicDefense");
        double night = WorldSettings.isNight(world, java.time.LocalTime.now()) ? WorldSettings.number(world, "nightBonus") : 1;

        double noise = 1 + d.intelNoise() * (2 * rnd.nextDouble() - 1) * 1.5;
        // the pace of the army gives away its slowest unit (scouts ~9 min/field, light cavalry 10, axes 18, rams 30)
        double distance = Math.max(0.5, Math.hypot(from.getX() - v.getX(), from.getY() - v.getY()));
        double seconds = attack.getDepartedAt() == null ? 0 : java.time.Duration.between(attack.getDepartedAt(), attack.getArrivesAt()).toMillis() / 1000.0;
        double minutesPerField = seconds <= 0 ? 30 : seconds * settings.speedOf(from) / 60 / distance;
        Map<UnitType, Integer> estimate = DodgeDecision.estimateAttack(view.points(from), noise, minutesPerField);
        if (estimate.isEmpty()) return; // a scouting party: nothing to defend against
        var verdict = DodgeDecision.assess(estimate, home, wall, basic, night, 0.1);
        String who = from.getOwner() == null ? from.getName() : from.getOwner().getUsername();
        if (!verdict.wouldLose()) {
            npcLog.add(world, me, v, "ALARM", v.getName() + " sees an attack from " + who + " coming and expects to hold");
            return;
        }
        npcLog.add(world, me, v, "ALARM", v.getName() + " expects to fall to an attack from " + who + " in " + secondsLeft + " s");

        if (rnd.nextDouble() < d.supportChance()) askForSupport(v, home, estimate, wall, basic, night, secondsLeft, view, me);
        if (d.dodge() && rnd.nextDouble() < 0.5 + 0.5 * skill) dodge(v, home, secondsLeft, view, me);
    }

    // Own other villages and tribe-mates' NPC villages send part of their defence, while it can still arrive.
    private void askForSupport(Village v, Map<UnitType, Integer> home, Map<UnitType, Integer> estimate, int wall, double basic,
                                                 double night, long secondsLeft, WorldView view, Account me) {
        Tribe tribe = view.tribeOf(me);
        List<Village> helpers = new ArrayList<>();
        for (Village h : view.villages) {
            if (h.getId().equals(v.getId()) || h.getOwner() == null || view.underAttack(h)) continue;
            boolean mine = h.getOwner().getId().equals(me.getId());
            boolean mate = tribe != null && h.getOwner().isNpc() && view.tribeByAccount.get(h.getOwner().getId()) != null
                    && view.tribeByAccount.get(h.getOwner().getId()).getId().equals(tribe.getId());
            if (mine || mate) helpers.add(h);
        }
        helpers.sort(Comparator.comparingDouble(h -> Math.hypot(h.getX() - v.getX(), h.getY() - v.getY())));
        Map<UnitType, Integer> garrison = home;
        int sent = 0;
        for (Village h : helpers.subList(0, Math.min(6, helpers.size()))) {
            if (sent >= 3) break;
            Map<UnitType, Integer> spare = new EnumMap<>(UnitType.class);
            for (UnitStock s : unitStock.findByVillage(h)) if (s.getCount() > 0) spare.put(s.getType(), s.getCount());
            Map<UnitType, Integer> send = DodgeDecision.spareDefenders(spare, 0.6);
            if (AttackPlanner.pop(send) < 20) continue;
            if (MovementService.travelSeconds(h, v, send, settings.speedOf(h)) > secondsLeft - 10) continue; // would be too late
            try {
                Movement m = movementService.sendSupport(h, v, send);
                view.added(m);
                garrison = DodgeDecision.plus(garrison, send);
                sent++;
                npcLog.add(v.getWorld(), h.getOwner(), h, "SUPPORT", h.getName() + " sends " + send.values().stream().mapToInt(Integer::intValue).sum()
                        + " defenders to " + v.getName() + " (" + v.getX() + "|" + v.getY() + ")");
                if (!DodgeDecision.assess(estimate, garrison, wall, basic, night, 0.1).wouldLose()) break; // enough
            } catch (MovementService.MovementException ignored) {
                // this helper cannot: next one
            }
        }
    }

    // The offensive troops leave for another village of the same owner that is not under attack, arriving before the hit.
    private void dodge(Village v, Map<UnitType, Integer> home, long secondsLeft, WorldView view, Account me) {
        Map<UnitType, Integer> save = DodgeDecision.offenceToSave(home);
        if (save.isEmpty()) return;
        Village best = null;
        double bestDist = Double.MAX_VALUE;
        for (Village h : view.villages) {
            if (h.getId().equals(v.getId()) || h.getOwner() == null || !h.getOwner().getId().equals(me.getId()) || view.underAttack(h)) continue;
            double dist = Math.hypot(h.getX() - v.getX(), h.getY() - v.getY());
            if (dist < bestDist && MovementService.travelSeconds(v, h, save, settings.speedOf(v)) < secondsLeft - 5) { best = h; bestDist = dist; }
        }
        if (best == null) return;
        Movement m = movementService.sendSupport(v, best, save);
        view.added(m);
        npcLog.add(v.getWorld(), me, v, "DODGE", v.getName() + " moves " + save.values().stream().mapToInt(Integer::intValue).sum()
                + " offensive units to " + best.getName() + " out of harm's way");
    }
}
