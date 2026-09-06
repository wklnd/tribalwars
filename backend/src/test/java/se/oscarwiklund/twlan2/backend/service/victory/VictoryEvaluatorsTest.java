package se.oscarwiklund.twlan2.backend.service.victory;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VictoryEvaluatorsTest {

    @Test
    void dominationNeedsTheThresholdShareOfAllPlayerVillages() {
        assertTrue(VictoryEvaluators.dominationMet(65, 100, 65));
        assertFalse(VictoryEvaluators.dominationMet(64, 100, 65));
        assertTrue(VictoryEvaluators.dominationMet(13, 20, 65));
        assertFalse(VictoryEvaluators.dominationMet(0, 0, 65)); // no player villages at all: never met
    }

    @Test
    void runeNeedsTheTotalAndOptionallyEveryPopulatedContinent() {
        RuneParams withContinents = new RuneParams(23, true, 7, 0.01);
        RuneParams withoutContinents = new RuneParams(23, false, 7, 0.01);

        assertFalse(VictoryEvaluators.runeMet(22, Set.of(45, 46), Set.of(45, 46), withContinents));
        assertFalse(VictoryEvaluators.runeMet(23, Set.of(45), Set.of(45, 46), withContinents));
        assertTrue(VictoryEvaluators.runeMet(23, Set.of(45, 46), Set.of(45, 46), withContinents));
        // missing a continent doesn't matter when the requirement is off
        assertTrue(VictoryEvaluators.runeMet(23, Set.of(45), Set.of(45, 46), withoutContinents));
    }

    @Test
    void streakAdvancesOrResetsAndWinsAtTheHoldThreshold() {
        assertEquals(1, VictoryEvaluators.nextStreak(0, true));
        assertEquals(4, VictoryEvaluators.nextStreak(3, true));
        assertEquals(0, VictoryEvaluators.nextStreak(3, false));

        assertFalse(VictoryEvaluators.streakWins(4, 5));
        assertTrue(VictoryEvaluators.streakWins(5, 5));
        assertTrue(VictoryEvaluators.streakWins(6, 5));
    }

    @Test
    void lastStandingPicksTheSoleRosterTribeWithVillagesLeft() {
        assertEquals(1L, VictoryEvaluators.lastStanding(Map.of(1L, 3, 2L, 0, 3L, 0)));
        assertNull(VictoryEvaluators.lastStanding(Map.of(1L, 3, 2L, 2))); // war continues
        assertNull(VictoryEvaluators.lastStanding(Map.of(1L, 0, 2L, 0))); // simultaneous elimination
        assertNull(VictoryEvaluators.lastStanding(Map.of()));
    }

    @Test
    void ruleRollsOnlyWhileTheParamsAreActive() {
        var rnd = new java.util.Random(1);
        assertFalse(RuneVillage.roll(rnd, null));
        RuneParams alwaysRolls = new RuneParams(23, true, 7, 1.0);
        assertTrue(RuneVillage.roll(rnd, alwaysRolls));
        RuneParams neverRolls = new RuneParams(23, true, 7, 0.0);
        assertFalse(RuneVillage.roll(rnd, neverRolls));
    }

    @Test
    void runeVillageDevelopmentIsAlwaysDeliberatelyHigh() {
        var rnd = new java.util.Random(2);
        for (int i = 0; i < 50; i++) {
            double d = RuneVillage.development(rnd);
            assertTrue(d >= 0.9 && d < 1.0, "development must stay in [0.9, 1.0), was " + d);
        }
    }
}
