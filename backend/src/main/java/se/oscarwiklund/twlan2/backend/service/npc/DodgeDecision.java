package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.UnitType;
import se.oscarwiklund.twlan2.backend.service.BattleCalculator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// The pure part of an NPC's reaction to an attack on its way: what the attack probably is (an NPC only knows who sends it and
// how big that village is), whether the village would hold, and whether reinforcements would change that. No Spring, no database.
public final class DodgeDecision {

    private DodgeDecision() {}

    // Units that defend: they stay home and are what a helper sends.
    public static final List<UnitType> DEFENDERS = List.of(UnitType.SPEAR, UnitType.SWORD, UnitType.ARCHER, UnitType.HEAVY);
    // Units that only attack: they dodge.
    public static final List<UnitType> OFFENCE = List.of(UnitType.AXE, UnitType.LIGHT, UnitType.MARCHER, UnitType.RAM, UnitType.CATAPULT, UnitType.SNOB);

    public record Verdict(boolean wouldLose, double attackPower, double defencePower) {
        // attack / defence: above 1 the attacker wins.
        public double ratio() { return defencePower <= 0 ? Double.MAX_VALUE : attackPower / defencePower; }
    }

    // A guess at the army behind an attack from a village of `attackerPoints`, with no idea how fast it moves: mostly axes, some light cavalry and rams.
    public static Map<UnitType, Integer> estimateAttack(int attackerPoints, double noise) {
        return estimateAttack(attackerPoints, noise, 30);
    }

    // The same guess, using what the travel time gives away: an army arriving at the pace of the scouts (about 9 minutes a field)
    // is a scouting party (nothing to defend against); light cavalry pace means cavalry only; axe pace means no rams; anything
    // slower may have rams with it.
    public static Map<UnitType, Integer> estimateAttack(int attackerPoints, double noise, double minutesPerField) {
        Map<UnitType, Integer> a = new EnumMap<>(UnitType.class);
        if (minutesPerField < 9.5) return a; // scouts
        double pop = Math.max(0, attackerPoints) * 0.25 * noise;
        double axe, light, ram;
        if (minutesPerField < 12) { axe = 0; light = 1; ram = 0; }
        else if (minutesPerField < 25) { axe = 0.75; light = 0.25; ram = 0; }
        else { axe = 0.6; light = 0.2; ram = 0.2; }
        int axes = (int) Math.round(pop * axe / UnitType.AXE.popCost);
        int lights = (int) Math.round(pop * light / UnitType.LIGHT.popCost);
        int rams = (int) Math.round(pop * ram / UnitType.RAM.popCost);
        if (axes > 0) a.put(UnitType.AXE, axes);
        if (lights > 0) a.put(UnitType.LIGHT, lights);
        if (rams > 0) a.put(UnitType.RAM, rams);
        return a;
    }

    // Would the village hold against `attack` on an average day? `caution` adds to the attacker's luck (0.1 = expects a bit of bad luck).
    public static Verdict assess(Map<UnitType, Integer> attack, Map<UnitType, Integer> defenders, int wall, double basicDefense, double night, double caution) {
        if (attack.isEmpty()) return new Verdict(false, 0, 0);
        var battle = BattleCalculator.fight(attack, defenders, wall, 1, caution, night, basicDefense);
        return new Verdict(battle.attackerWins(), battle.attackPower(), battle.defensePower());
    }

    public static Map<UnitType, Integer> plus(Map<UnitType, Integer> a, Map<UnitType, Integer> b) {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        a.forEach((t, n) -> out.merge(t, n, Integer::sum));
        b.forEach((t, n) -> out.merge(t, n, Integer::sum));
        return out;
    }

    // `share` of the defending units among `home`, rounded down: what a helper can send.
    public static Map<UnitType, Integer> spareDefenders(Map<UnitType, Integer> home, double share) {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        for (UnitType t : DEFENDERS) {
            int n = (int) Math.floor(home.getOrDefault(t, 0) * share);
            if (n > 0) out.put(t, n);
        }
        return out;
    }

    public static Map<UnitType, Integer> offenceToSave(Map<UnitType, Integer> home) {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        for (UnitType t : OFFENCE) {
            int n = home.getOrDefault(t, 0);
            if (n > 0) out.put(t, n);
        }
        return out;
    }
}
