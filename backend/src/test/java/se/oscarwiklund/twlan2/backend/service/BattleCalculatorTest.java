package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.BuildingType;
import se.oscarwiklund.twlan2.backend.domain.UnitType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleCalculatorTest {

    private static Map<UnitType, Integer> army(Object... pairs) {
        Map<UnitType, Integer> m = new EnumMap<>(UnitType.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((UnitType) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    private static BattleCalculator.Battle fight(Map<UnitType, Integer> att, Map<UnitType, Integer> def, int wall, double night) {
        return BattleCalculator.fight(att, def, wall, 1, 0, night, 20);
    }

    @Test
    void winnerLosesLoserPowerRatioToThePowerOneAndAHalf() {
        // 1000 axes = 40000 attack; 1000 spears defend infantry with 15 each + 20 basic = 15020
        var b = fight(army(UnitType.AXE, 1000), army(UnitType.SPEAR, 1000), 0, 1);
        assertTrue(b.attackerWins());
        assertEquals(15020, b.defensePower(), 1e-6);
        assertEquals((int) Math.round(1000 * Math.pow(15020.0 / 40000, 1.5)), (int) b.attackerLosses().get(UnitType.AXE));
        assertEquals(1000, b.defenderLosses().get(UnitType.SPEAR));
    }

    @Test
    void theLoserIsWipedOut() {
        var b = fight(army(UnitType.AXE, 100), army(UnitType.SWORD, 1000), 0, 1);
        assertFalse(b.attackerWins());
        assertEquals(100, b.attackerLosses().get(UnitType.AXE));
        assertTrue(b.defenderLosses().get(UnitType.SWORD) < 1000);
    }

    @Test
    void cavalryFacesTheAntiCavalryDefence() {
        // spears defend 45 against cavalry but only 15 against infantry
        var cav = fight(army(UnitType.LIGHT, 100), army(UnitType.SPEAR, 100), 0, 1);
        var inf = fight(army(UnitType.AXE, 100), army(UnitType.SPEAR, 100), 0, 1);
        assertEquals(100 * 45 + 20, cav.defensePower(), 1e-6);
        assertEquals(100 * 15 + 20, inf.defensePower(), 1e-6);
    }

    @Test
    void mixedAttackWeighsTheDefenceByTheStrengthOfEachCategory() {
        // 1000 axes = 40000 infantry, 200 light cavalry = 26000 cavalry -> 60.6 % / 39.4 %
        var b = fight(army(UnitType.AXE, 1000, UnitType.LIGHT, 200), army(UnitType.SPEAR, 100), 0, 1);
        double infantryShare = 40000.0 / 66000;
        assertEquals(100 * (15 * infantryShare + 45 * (1 - infantryShare)) + 20, b.defensePower(), 1e-6);
    }

    @Test
    void wallAndNightMultiplyTheDefence() {
        var plain = fight(army(UnitType.AXE, 1000), army(UnitType.SPEAR, 1000), 0, 1);
        var walled = fight(army(UnitType.AXE, 1000), army(UnitType.SPEAR, 1000), 20, 1);
        var night = fight(army(UnitType.AXE, 1000), army(UnitType.SPEAR, 1000), 0, 2);
        assertEquals((15000 * Math.pow(1.037, 20) + 20), walled.defensePower(), 1e-6);
        assertEquals(15000 * 2 + 20, night.defensePower(), 1e-6);
    }

    @Test
    void moraleAndLuckScaleTheAttack() {
        var b = BattleCalculator.fight(army(UnitType.AXE, 1000), army(), 0, 0.5, 0.25, 1, 20);
        assertEquals(40000 * 0.5 * 1.25, b.attackPower(), 1e-6);
    }

    @Test
    void anAttackWithoutStrengthNeverBeatsTheBasicDefence() {
        var b = fight(army(UnitType.SCOUT, 50), army(), 0, 1);
        assertFalse(b.attackerWins());
    }

    @Test
    void moraleCurve() {
        assertEquals(1, BattleCalculator.morale(1000, 1000), 1e-9);
        assertEquals(1, BattleCalculator.morale(1000, 5000), 1e-9);
        assertEquals(0.65, BattleCalculator.morale(1000, 500), 1e-9);
        assertEquals(0.3, BattleCalculator.morale(1000, 0), 1e-9);
    }

    @Test
    void ramsAndCatapultsKnockLevelsOff() {
        assertEquals(0, BattleCalculator.levelsDestroyed(1, 5));
        assertEquals(1, BattleCalculator.levelsDestroyed(2, 1));
        assertEquals(0, BattleCalculator.levelsDestroyed(500, 0));
        int few = BattleCalculator.levelsDestroyed(20, 20);
        int many = BattleCalculator.levelsDestroyed(200, 20);
        assertTrue(many > few && many <= 20);
        assertEquals(20, BattleCalculator.levelsDestroyed(100_000, 20));
    }

    @Test
    void scoutDuel() {
        var win = BattleCalculator.scoutDuel(10, 0);
        assertTrue(win.attackerWins());
        assertEquals(0, win.attackerLosses().get(UnitType.SCOUT));

        var narrow = BattleCalculator.scoutDuel(10, 5);
        assertTrue(narrow.attackerWins());
        assertEquals((int) Math.round(10 * Math.pow(0.5, 1.5)), (int) narrow.attackerLosses().get(UnitType.SCOUT));
        assertEquals(5, narrow.defenderLosses().get(UnitType.SCOUT));

        var lost = BattleCalculator.scoutDuel(3, 8);
        assertFalse(lost.attackerWins());
        assertEquals(3, lost.attackerLosses().get(UnitType.SCOUT));

        assertEquals(0, BattleCalculator.spyLevel(0));
        assertEquals(2, BattleCalculator.spyLevel(2));
        assertEquals(4, BattleCalculator.spyLevel(50));
    }

    @Test
    void hidingPlaceCapacityFollowsTheOriginalCurve() {
        assertEquals(0, BuildingType.hidingCapacity(0));
        assertEquals(150, BuildingType.hidingCapacity(1));
        assertEquals(1996.0, BuildingType.hidingCapacity(10), 5);
    }

    @Test
    void everyUnitKnowsWhereItIsRecruitedAndWhatItNeeds() {
        assertEquals(BuildingType.STABLE, UnitType.LIGHT.recruitBuilding);
        assertEquals(BuildingType.WORKSHOP, UnitType.CATAPULT.recruitBuilding);
        assertEquals(12, UnitType.CATAPULT.requirements().get(BuildingType.SMITHY));
        assertFalse(UnitType.HEAVY.requirementsMet(t -> t == BuildingType.STABLE ? 10 : 0));
        assertTrue(UnitType.HEAVY.requirementsMet(t -> t == BuildingType.STABLE ? 10 : 15));
        assertTrue(UnitType.SPEAR.requirementsMet(t -> 0));
    }
}
