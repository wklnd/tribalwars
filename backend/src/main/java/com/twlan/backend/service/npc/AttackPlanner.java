package com.twlan.backend.service.npc;

import com.twlan.backend.domain.UnitType;
import com.twlan.backend.service.BattleCalculator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Decides what an NPC sends at a target it has intel on: the smallest force that still wins with a safety margin (the worst
// luck, a garrison larger than seen), rams when the wall is worth breaking, extra carriers to take the loot along. Pure
// (no Spring, no database): the intel and the army come in as numbers, the plan comes out, or null when it would not win
// or would cost too much.
public final class AttackPlanner {

    private AttackPlanner() {}

    // The units that fight in a raid; rams join for the wall, catapults never do (only conquest operations use them).
    public static final List<UnitType> FIGHTERS = List.of(UnitType.AXE, UnitType.LIGHT, UnitType.MARCHER);

    public record Plan(Map<UnitType, Integer> send, int carry, double lossShare, double attackPower, double defencePower) {}

    // army: the offensive troops at home the NPC may use (already limited by its commit share). defenders: the garrison as
    // seen (or guessed). wall: the wall level as seen. margin: how much bigger than seen the garrison may really be (0.2 =
    // 20 %). expectedLoot: what it hopes to carry home. maxLossShare: give up when the expected losses (population share of
    // the sent force) are larger. withRams: bring rams (if any) to break the wall.
    public static Plan plan(Map<UnitType, Integer> army, Map<UnitType, Integer> defenders, int wall, double basicDefense,
                            double night, double morale, double margin, double expectedLoot, double maxLossShare, boolean withRams) {
        Map<UnitType, Integer> fighters = new EnumMap<>(UnitType.class);
        for (UnitType t : FIGHTERS) {
            int n = army.getOrDefault(t, 0);
            if (n > 0) fighters.put(t, n);
        }
        if (fighters.isEmpty()) return null;

        Map<UnitType, Integer> worstDefence = scaled(defenders, 1 + Math.max(0, margin));
        // the smallest force that wins even with the worst luck against a bigger garrison than seen, and that on an average
        // day loses no more than maxLossShare of itself (a bare win at bad luck would cost most of the force on a normal one)
        if (!acceptable(fighters, defenders, worstDefence, wall, basicDefense, night, morale, maxLossShare)) return null;
        double lo = 0, hi = 1;
        for (int i = 0; i < 20; i++) {
            double mid = (lo + hi) / 2;
            if (acceptable(scaled(fighters, mid), defenders, worstDefence, wall, basicDefense, night, morale, maxLossShare)) hi = mid;
            else lo = mid;
        }
        Map<UnitType, Integer> send = scaled(fighters, hi);
        if (send.isEmpty()) return null;

        // carriers: add more of the army while the loot does not fit
        int carry = carry(send);
        while (carry < expectedLoot) {
            UnitType best = null;
            for (UnitType t : fighters.keySet()) {
                if (send.getOrDefault(t, 0) < fighters.get(t) && (best == null || t.carryCapacity > best.carryCapacity)) best = t;
            }
            if (best == null || best.carryCapacity <= 0) break;
            int spare = fighters.get(best) - send.getOrDefault(best, 0);
            int need = (int) Math.ceil((expectedLoot - carry) / best.carryCapacity);
            send.merge(best, Math.min(spare, Math.max(1, need)), Integer::sum);
            carry = carry(send);
        }

        int rams = army.getOrDefault(UnitType.RAM, 0);
        if (withRams && wall > 0 && rams > 0) {
            int use = rams;
            for (int r = 1; r <= rams; r++) {
                if (BattleCalculator.levelsDestroyed(r, wall) >= wall) { use = r; break; }
            }
            send.put(UnitType.RAM, use);
        }

        var expected = BattleCalculator.fight(send, defenders, wall, morale, 0, night, basicDefense);
        double sentPop = pop(send);
        double lossShare = sentPop <= 0 ? 0 : pop(expected.attackerLosses()) / sentPop;
        return new Plan(send, carry, lossShare, expected.attackPower(), expected.defensePower());
    }

    private static boolean acceptable(Map<UnitType, Integer> send, Map<UnitType, Integer> seen, Map<UnitType, Integer> worstDefence, int wall,
                                      double basicDefense, double night, double morale, double maxLossShare) {
        if (send.isEmpty()) return false;
        if (!BattleCalculator.fight(send, worstDefence, wall, morale, -BattleCalculator.MAX_LUCK, night, basicDefense).attackerWins()) return false;
        var average = BattleCalculator.fight(send, seen, wall, morale, 0, night, basicDefense);
        double sentPop = pop(send);
        return average.attackerWins() && (sentPop <= 0 || pop(average.attackerLosses()) / sentPop <= maxLossShare);
    }

    // What a garrison of a village of the given points is guessed to be (public information only): spears and swords.
    public static Map<UnitType, Integer> guessGarrison(int points, double noise) {
        Map<UnitType, Integer> g = new EnumMap<>(UnitType.class);
        double pop = Math.max(0, points) * 0.3 * noise;
        int spears = (int) Math.round(pop * 0.6), swords = (int) Math.round(pop * 0.4);
        if (spears > 0) g.put(UnitType.SPEAR, spears);
        if (swords > 0) g.put(UnitType.SWORD, swords);
        return g;
    }

    public static int carry(Map<UnitType, Integer> units) {
        int c = 0;
        for (var e : units.entrySet()) c += e.getKey().carryCapacity * e.getValue();
        return c;
    }

    public static double pop(Map<UnitType, Integer> units) {
        double p = 0;
        for (var e : units.entrySet()) p += (double) e.getKey().popCost * e.getValue();
        return p;
    }

    // `share` of every count, rounded up, dropping types that come to zero.
    static Map<UnitType, Integer> scaled(Map<UnitType, Integer> units, double share) {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        for (var e : units.entrySet()) {
            int n = (int) Math.ceil(e.getValue() * share - 1e-9);
            if (n > 0) out.put(e.getKey(), n);
        }
        return out;
    }
}
