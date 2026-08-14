package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.UnitStockRepository;
import se.oscarwiklund.twlan2.backend.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// How an NPC village picks a fight: it looks at the villages in range, works out what it knows about each (a scouting report,
// the result of its last battle there, or a guess from the village's points), lets AttackPlanner decide what to send
// and sends it; when it does not know enough it sends scouts first. Farming NPCs return to targets that paid off and drop
// those that did not, and a grudge (see NpcIntelService) pulls an NPC toward whoever hit it. All orders go through
// MovementService, so the rules are the players' rules.
@Service
public class NpcMilitary {

    private static final List<UnitType> OFFENCE = List.of(UnitType.AXE, UnitType.LIGHT, UnitType.MARCHER, UnitType.RAM, UnitType.CATAPULT);
    // Scouts a village needs at home before it will scout (fewer survive too rarely to see the garrison).
    private static final int MIN_SCOUTS = 4;

    private final UnitStockRepository unitStock;
    private final MovementService movementService;
    private final NpcIntelService intel;
    private final NpcLogService npcLog;
    private final GameSettings settings;

    public NpcMilitary(UnitStockRepository unitStock, MovementService movementService,
                       NpcIntelService intel, NpcLogService npcLog, GameSettings settings) {
        this.unitStock = unitStock;
        this.movementService = movementService;
        this.intel = intel;
        this.npcLog = npcLog;
        this.settings = settings;
    }

    private record Candidate(Village target, double weight, boolean human, boolean barbarian, boolean lastOfHuman) {}

    private record Conquest(Candidate c, ConquestPlan.Waves waves, double score) {}

    private record Strike(Candidate c, AttackPlanner.Plan plan, String basis, double score) {}

    // How the NPC's tribe sees the tribe of the village's owner (null: no tribe or no relation).
    private TribeRelation.Kind relationTo(Tribe mine, Village target, WorldView view) {
        if (mine == null || target.getOwner() == null) return null;
        Tribe other = view.tribeByAccount.get(target.getOwner().getId());
        return other == null ? null : view.relation(mine.getId(), other.getId());
    }

    @Transactional(noRollbackFor = MovementService.MovementException.class)
    public void step(Village v, NpcArchetype archetype, double skill, NpcDifficulty d, WorldView view, Random rnd) {
        World world = v.getWorld();
        if (world == null || !d.attacks()) return;
        if (view.waveOut(v)) return; // a wave (or scouts) is out

        Account me = v.getOwner();
        double speed = settings.speedOf(v);
        Instant now = Instant.now();

        // what it can send: the offensive troops (a share of them, so a raid never empties the village)
        Map<UnitType, Integer> home = new EnumMap<>(UnitType.class);
        for (UnitStock s : unitStock.findByVillage(v)) if (s.getCount() > 0) home.put(s.getType(), s.getCount());
        Map<UnitType, Integer> army = new EnumMap<>(UnitType.class);
        double power = 0;
        for (UnitType t : OFFENCE) {
            int n = (int) Math.floor(home.getOrDefault(t, 0) * d.commit());
            if (n <= 0) continue;
            army.put(t, n);
            power += (double) t.attack * n;
        }
        int scouts = home.getOrDefault(UnitType.SCOUT, 0);
        int snobs = home.getOrDefault(UnitType.SNOB, 0);
        boolean scoutOnlyPossible = d.scouts() && scouts >= MIN_SCOUTS;

        // conquest: with enough noblemen at home for a whole chain of attacks the NPC does not wait for its raid mood
        NpcDifficulty.Expansion expansion = d.expansionFor(world);
        int need = ConquestPlan.wavesNeeded(100, (int) WorldSettings.number(world, "noblemanMinDecrease"),
                (int) WorldSettings.number(world, "noblemanMaxDecrease"), false);
        boolean conquestReady = expansion != NpcDifficulty.Expansion.NONE && snobs >= need;
        double chance = d.raidChance() * archetype.drive() * (0.5 + skill);
        boolean mood = rnd.nextDouble() <= chance;
        if (!conquestReady && !mood) return;
        boolean act = mood || rnd.nextDouble() < chance * 4; // a conquering NPC clears and scouts its target faster
        if (power < 400 && !(scoutOnlyPossible && power > 0) && !conquestReady) return;

        Tribe myTribe = view.tribeOf(me);
        Map<Long, NpcIntel> known = intel.of(me.getId());
        Map<Long, Double> grudges = d.retaliation() ? intel.grudgesOf(me.getId(), speed) : Map.of();
        double range = (12 + 20 * archetype.drive()) * (grudges.isEmpty() ? 1 : 1.4);

        List<Village> around = new ArrayList<>();
        for (Village t : view.near(v.getX(), v.getY(), range)) {
            if (Math.hypot(t.getX() - v.getX(), t.getY() - v.getY()) > range) continue;
            if (t.getId().equals(v.getId()) || MovementService.isSameOwner(v, t)) continue;
            TribeRelation.Kind kind = relationTo(myTribe, t, view);
            if (kind == TribeRelation.Kind.PARTNER || kind == TribeRelation.Kind.NAP) continue;
            Tribe theirs = t.getOwner() == null ? null : view.tribeByAccount.get(t.getOwner().getId());
            if (myTribe != null && theirs != null && theirs.getId().equals(myTribe.getId())) continue;
            if (MovementService.underProtection(t)) continue;
            around.add(t);
        }
        Collections.shuffle(around, rnd);

        double basicDefense = WorldSettings.number(world, "basicDefense");
        double night = WorldSettings.isNight(world, java.time.LocalTime.now()) ? WorldSettings.number(world, "nightBonus") : 1;
        boolean moraleOn = !"off".equals(WorldSettings.get(world, "morale"));
        List<Strike> strikes = new ArrayList<>();
        List<Candidate> toScout = new ArrayList<>();
        List<Conquest> conquests = new ArrayList<>();

        for (Village t : around.subList(0, Math.min(10, around.size()))) {
            boolean human = t.getOwner() != null && !t.getOwner().isNpc();
            boolean barbarian = t.getOwnerType() == OwnerType.BARBARIAN;
            double grudge = t.getOwner() == null ? 0 : grudges.getOrDefault(t.getOwner().getId(), 0.0);
            if (human && grudge == 0 && rnd.nextDouble() < d.humanSkip()) continue; // a passive NPC leaves players alone half the time
            if (view.underAttack(t)) continue;

            double w = barbarian ? 3.0 : human ? d.humanWeight() : 1.0;
            if (BonusType.of(t) != null) w *= 2;                                                        // a bonus village is public knowledge (map popup) and worth having
            if (relationTo(myTribe, t, view) == TribeRelation.Kind.ENEMY) w *= 3;
            if (grudge > 0) w *= 1 + 4 * grudge;
            NpcIntel i = known.get(t.getId());
            if (d.farming() && i != null) {
                if ("LOST".equals(i.getLastResult()) && i.getLosses() >= 2) w *= 0.2;                        // it hurt us here
                else if ("WON".equals(i.getLastResult()) && i.getRaids() > 0 && i.getLastLoot() < 40) w *= 0.15; // nothing left to take
                else if ("WON".equals(i.getLastResult()) && i.getLastLoot() >= 200) w *= 1.5;               // pays well: come back
            }
            Candidate c = new Candidate(t, w, human, barbarian, human && view.villageCount(t.getOwner()) <= 1);
            boolean conquerable = conquestReady && expansion.allows(barbarian, human, c.lastOfHuman());
            if (conquerable) c = new Candidate(t, w * 2.5, human, barbarian, c.lastOfHuman()); // clear it, then take it

            Map<UnitType, Integer> seen;
            int wall;
            double margin;
            String basis;
            if (NpcIntelService.fresh(i, speed, now)) {
                seen = NpcIntelService.parse(i.getTroops());
                wall = i.getWall() == null ? 0 : i.getWall();
                margin = d.intelNoise();
                basis = "scouted";
            } else if (scoutOnlyPossible) {
                toScout.add(c);
                continue;
            } else {
                // no scouts (or none wanted): a guess from the public points, and a wide safety margin for it
                double noise = 1 + d.intelNoise() * (2 * rnd.nextDouble() - 1) * 1.5;
                int points = view.points(t);
                seen = AttackPlanner.guessGarrison(points, noise);
                wall = Math.min(15, points / 150);
                margin = 0.3 + d.intelNoise();
                basis = "guess";
            }

            double loot = i != null && i.getWood() != null ? 0.9 * (i.getWood() + i.getClay() + i.getIron())
                    : i != null && i.getLastLoot() > 0 ? 0.8 * i.getLastLoot() : 300;
            double morale = 1;
            if (moraleOn && human) morale = BattleCalculator.morale(view.pointsOf(me), view.pointsOf(t.getOwner()));
            if (conquerable && basis.equals("scouted") && seen.isEmpty()) {
                // cleared and known: this is the moment for the noblemen
                var waves = ConquestPlan.waves(snobs, need, army, seen, wall, basicDefense, night, morale, margin);
                if (waves != null) {
                    conquests.add(new Conquest(c, waves, c.weight()));
                    continue;
                }
            }
            boolean rams = wall > 0 && (d.farming() || (i != null && i.getRaids() > 0));
            Map<UnitType, Integer> forThis = new EnumMap<>(army);
            var plan = AttackPlanner.plan(forThis, seen, wall, basicDefense, night, morale, margin, loot, d.farming() ? 0.3 : 0.5, rams);
            if (plan == null) continue;
            double travelHours = MovementService.travelSeconds(v, t, plan.send(), speed) / 3600.0;
            double score = w * (1 + Math.min(loot, plan.carry()) / 500.0) / (d.farming() ? 1 + travelHours : 1);
            strikes.add(new Strike(c, plan, basis, score));
        }

        if (!conquests.isEmpty()) {
            Conquest best = conquests.get(pickIndex(conquests.stream().map(Conquest::score).toList(), rnd));
            sendConquest(v, me, best, view);
        } else if (!act) {
            return;
        } else if (!strikes.isEmpty()) {
            Strike s = rnd.nextDouble() < skill ? Collections.max(strikes, Comparator.comparingDouble(Strike::score)) : weighted(strikes, rnd);
            sendStrike(v, me, s, view);
        } else if (!toScout.isEmpty()) {
            Candidate c = toScout.get(pickIndex(toScout.stream().map(Candidate::weight).toList(), rnd));
            // enough scouts to beat the ones it expects at home (what an old report showed, else a guess for players and NPCs)
            NpcIntel old = known.get(c.target().getId());
            int expected = old != null && old.getTroops() != null ? NpcIntelService.parse(old.getTroops()).getOrDefault(UnitType.SCOUT, 0)
                    : c.barbarian() ? 0 : 8;
            int n = Math.min(scouts, Math.max((int) Math.round(4 + 6 * skill), (int) Math.ceil(1.5 * expected) + 3));
            sendScouts(v, me, c.target(), n, view);
        }
    }

    // A chain of attacks, each with one nobleman and an escort that wins alone: they land one after the other and take the loyalty down.
    private void sendConquest(Village v, Account me, Conquest c, WorldView view) {
        Village t = c.c().target();
        int sent = 0;
        for (int k = 0; k < c.waves().count(); k++) {
            Map<UnitType, Integer> send = new EnumMap<>(c.waves().escortEach());
            send.put(UnitType.SNOB, 1);
            try {
                view.added(movementService.sendAttack(v, t, send));
                sent++;
            } catch (MovementService.MovementException e) {
                break; // out of noblemen range or protected: the chain is not worth starting half way
            }
        }
        if (sent > 0) {
            npcLog.add(v.getWorld(), me, v, "NOBLE", "Sent " + sent + " noblemen in " + sent + " waves from " + v.getName() + " to take " + t.getName()
                    + " (" + t.getX() + "|" + t.getY() + ")" + (t.getOwner() == null ? "" : " of " + t.getOwner().getUsername()));
        }
    }

    private void sendStrike(Village v, Account me, Strike s, WorldView view) {
        Candidate c = s.c();
        Map<UnitType, Integer> send = new EnumMap<>(s.plan().send());
        try {
            Movement m = movementService.sendAttack(v, c.target(), send);
            view.added(m);
            int units = send.values().stream().mapToInt(Integer::intValue).sum();
            Village t = c.target();
            npcLog.add(v.getWorld(), me, v, "RAID", "Sent " + units + " units" + (send.containsKey(UnitType.SNOB) ? " with noblemen" : "")
                    + " from " + v.getName() + " at " + t.getName() + " (" + t.getX() + "|" + t.getY() + ")"
                    + (t.getOwner() == null ? "" : " of " + t.getOwner().getUsername()) + " [" + s.basis()
                    + ", loss " + Math.round(s.plan().lossShare() * 100) + " %]");
        } catch (MovementService.MovementException ignored) {
            // e.g. the nobles are out of range of this target: try again next time
        }
    }

    private void sendScouts(Village v, Account me, Village target, int n, WorldView view) {
        Map<UnitType, Integer> send = new EnumMap<>(UnitType.class);
        send.put(UnitType.SCOUT, n);
        try {
            Movement m = movementService.sendAttack(v, target, send);
            view.added(m);
            npcLog.add(v.getWorld(), me, v, "SCOUT", "Sent " + n + " scouts from " + v.getName() + " to " + target.getName()
                    + " (" + target.getX() + "|" + target.getY() + ")" + (target.getOwner() == null ? "" : " of " + target.getOwner().getUsername()));
        } catch (MovementService.MovementException ignored) {
            // out of range or protected: next time
        }
    }

    private static Strike weighted(List<Strike> strikes, Random rnd) {
        return strikes.get(pickIndex(strikes.stream().map(Strike::score).toList(), rnd));
    }

    private static int pickIndex(List<Double> weights, Random rnd) {
        double sum = 0;
        for (double w : weights) sum += w;
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < weights.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return i;
        }
        return weights.size() - 1;
    }
}
