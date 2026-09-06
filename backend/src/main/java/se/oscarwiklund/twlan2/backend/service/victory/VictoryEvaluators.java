package se.oscarwiklund.twlan2.backend.service.victory;

import java.util.Map;
import java.util.Set;

// Pure win-condition checks, no Spring/JPA - the VictoryService orchestrator gathers the inputs and calls
// these once per elapsed real calendar day per world.
public final class VictoryEvaluators {
    private VictoryEvaluators() {}

    public static boolean dominationMet(int tribeVillages, int totalPlayerVillages, double thresholdPercent) {
        return totalPlayerVillages > 0 && 100.0 * tribeVillages / totalPlayerVillages >= thresholdPercent;
    }

    public static boolean runeMet(int runeVillagesHeld, Set<Integer> continentsHeldIn, Set<Integer> populatedContinents, RuneParams params) {
        if (runeVillagesHeld < params.totalTarget()) return false;
        return !params.requireEveryPopulatedContinent() || continentsHeldIn.containsAll(populatedContinents);
    }

    public static int nextStreak(int currentStreak, boolean metToday) {
        return metToday ? currentStreak + 1 : 0;
    }

    public static boolean streakWins(int streakDays, int holdDays) {
        return streakDays >= holdDays;
    }

    // Given exactly the roster tribes' current village counts, the tribe id still standing if only one is
    // left, else null (war continues, or - the rare simultaneous-elimination edge case - nobody is left).
    public static Long lastStanding(Map<Long, Integer> rosterVillageCounts) {
        Long only = null;
        for (var e : rosterVillageCounts.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (only != null) return null; // more than one still standing
            only = e.getKey();
        }
        return only;
    }
}
