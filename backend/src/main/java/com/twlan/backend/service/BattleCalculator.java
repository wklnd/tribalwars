package com.twlan.backend.service;

import com.twlan.backend.domain.UnitType;

import java.util.EnumMap;
import java.util.Map;

// The battle maths, free of Spring/DB so it can be tested (and mirrored by the rally point simulator in
// frontend/src/screens/military/sim.js). Classic Tribal Wars: the attackers' strength is split by category
// (infantry / cavalry / archers) and each defending unit's defence is weighted by that split, then multiplied by the
// wall (WALL_BONUS^level) and the night bonus, then topped up with the world's basic defence. The
// attacker's strength is scaled by morale and luck. The loser is wiped out, the winner loses
// (loserPower / winnerPower)^1.5 of every unit.
// The original bundle only ships the numbers (units.json / world.json), its battle code is compiled, so the wall
// bonus, the 1.5 exponent, the morale curve and the ram/catapult tables below are the classic values, not read from it.
public final class BattleCalculator {

    private BattleCalculator() {}

    public static final double WALL_BONUS = 1.037;
    public static final double LOSS_EXPONENT = 1.5;
    // Luck is uniform in +-25 % of the attacker's strength.
    public static final double MAX_LUCK = 0.25;

    public record Battle(boolean attackerWins, double attackPower, double defensePower,
                         Map<UnitType, Integer> attackerLosses, Map<UnitType, Integer> defenderLosses) {}

    // morale: 0.3..1, factor on the attackers' strength. luck: -0.25..0.25, added to the attackers' strength factor.
    // defenseFactor: the night bonus (1 by day).
    public static Battle fight(Map<UnitType, Integer> attackers, Map<UnitType, Integer> defenders, int wallLevel,
                               double morale, double luck, double defenseFactor, double basicDefense) {
        double[] off = new double[UnitType.Category.values().length];
        double offRaw = 0;
        for (var e : attackers.entrySet()) {
            double p = (double) e.getKey().attack * e.getValue();
            off[e.getKey().category.ordinal()] += p;
            offRaw += p;
        }
        double attackPower = offRaw * morale * (1 + luck);

        // what the defenders have to withstand: the more cavalry attacks, the more the anti-cavalry values count
        double[] share = new double[off.length];
        if (offRaw > 0) for (int i = 0; i < off.length; i++) share[i] = off[i] / offRaw;
        else share[UnitType.Category.INFANTRY.ordinal()] = 1;
        double defRaw = 0;
        for (var e : defenders.entrySet()) {
            UnitType t = e.getKey();
            double per = t.defense * share[UnitType.Category.INFANTRY.ordinal()]
                    + t.defenseCavalry * share[UnitType.Category.CAVALRY.ordinal()]
                    + t.defenseArcher * share[UnitType.Category.ARCHER.ordinal()];
            defRaw += per * e.getValue();
        }
        // the rally point simulator words the night bonus as "100% wall bonus": it multiplies the troops' defence, the
        // basic defence comes on top
        double defensePower = defRaw * Math.pow(WALL_BONUS, Math.max(0, wallLevel)) * Math.max(1, defenseFactor)
                + Math.max(0, basicDefense);

        boolean attackerWins = attackPower > defensePower;
        Map<UnitType, Integer> attackerLosses = new EnumMap<>(UnitType.class);
        Map<UnitType, Integer> defenderLosses = new EnumMap<>(UnitType.class);
        if (attackerWins) {
            double fraction = Math.min(1, Math.pow(defensePower / attackPower, LOSS_EXPONENT));
            lose(attackers, fraction, attackerLosses);
            lose(defenders, 1, defenderLosses);
        } else {
            double fraction = defensePower <= 0 ? 0 : Math.min(1, Math.pow(attackPower / defensePower, LOSS_EXPONENT));
            lose(attackers, 1, attackerLosses);
            lose(defenders, fraction, defenderLosses);
        }
        return new Battle(attackerWins, attackPower, defensePower, attackerLosses, defenderLosses);
    }

    private static void lose(Map<UnitType, Integer> units, double fraction, Map<UnitType, Integer> out) {
        for (var e : units.entrySet()) {
            if (e.getValue() <= 0) continue;
            out.put(e.getKey(), (int) Math.min(e.getValue(), Math.round(e.getValue() * fraction)));
        }
    }

    // World setting "morale = points": 100 % when the defender is as big as the attacker or bigger, sinking towards
    // 30 % the smaller he is. Barbarian villages never weaken an attack.
    public static double morale(double attackerPoints, double defenderPoints) {
        if (attackerPoints <= 0 || defenderPoints >= attackerPoints) return 1;
        return Math.max(0.3, Math.min(1, 0.3 + 0.7 * defenderPoints / attackerPoints));
    }

    // Units of a building/wall level needed to knock one level off it: 1.2^level, at least 2.
    static int hitsNeeded(int level) {
        return Math.max(2, (int) Math.ceil(Math.pow(1.2, level)));
    }

    // Also used for catapults, despite the parameter name.
    public static int levelsDestroyed(int rams, int level) {
        int left = rams;
        int lost = 0;
        while (level - lost > 0 && left >= hitsNeeded(level - lost)) {
            left -= hitsNeeded(level - lost);
            lost++;
        }
        return lost;
    }

    // An attack made of scouts only: the larger side wins and loses (small/large)^1.5.
    public static Battle scoutDuel(int attackingScouts, int defendingScouts) {
        Map<UnitType, Integer> attLoss = new EnumMap<>(UnitType.class);
        Map<UnitType, Integer> defLoss = new EnumMap<>(UnitType.class);
        boolean attackerWins = attackingScouts > defendingScouts;
        if (attackerWins) {
            double fraction = defendingScouts == 0 ? 0 : Math.pow((double) defendingScouts / attackingScouts, LOSS_EXPONENT);
            attLoss.put(UnitType.SCOUT, (int) Math.round(attackingScouts * fraction));
            if (defendingScouts > 0) defLoss.put(UnitType.SCOUT, defendingScouts);
        } else {
            double fraction = Math.pow((double) attackingScouts / Math.max(1, defendingScouts), LOSS_EXPONENT);
            attLoss.put(UnitType.SCOUT, attackingScouts);
            defLoss.put(UnitType.SCOUT, (int) Math.round(defendingScouts * Math.min(1, fraction)));
        }
        return new Battle(attackerWins, attackingScouts, defendingScouts, attLoss, defLoss);
    }

    // What surviving scouts get to see: 1 resources, 2 + buildings, 3 + troops at home, 4 or more + troops away.
    public static int spyLevel(int survivingScouts) {
        return Math.min(4, Math.max(0, survivingScouts));
    }
}
