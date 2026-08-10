package com.twlan.backend.service.npc;

import com.twlan.backend.domain.UnitType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static com.twlan.backend.domain.UnitType.*;
import static org.junit.jupiter.api.Assertions.*;

class DodgeDecisionTest {

    private static Map<UnitType, Integer> units(Object... pairs) {
        Map<UnitType, Integer> m = new EnumMap<>(UnitType.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((UnitType) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    @Test
    void estimateGrowsWithPointsAndNoise() {
        assertTrue(AttackPlanner.pop(DodgeDecision.estimateAttack(2000, 1)) > AttackPlanner.pop(DodgeDecision.estimateAttack(500, 1)));
        assertTrue(AttackPlanner.pop(DodgeDecision.estimateAttack(1000, 1.3)) > AttackPlanner.pop(DodgeDecision.estimateAttack(1000, 0.7)));
        assertTrue(DodgeDecision.estimateAttack(0, 1).isEmpty());
    }

    @Test
    void paceGivesTheArmyAway() {
        assertTrue(DodgeDecision.estimateAttack(1000, 1, 9).isEmpty(), "scout pace: a probe");
        var cav = DodgeDecision.estimateAttack(1000, 1, 10);
        assertTrue(cav.containsKey(LIGHT) && cav.size() == 1);
        var axes = DodgeDecision.estimateAttack(1000, 1, 18);
        assertTrue(axes.containsKey(AXE) && !axes.containsKey(RAM));
        assertTrue(DodgeDecision.estimateAttack(1000, 1, 30).containsKey(RAM));
    }

    @Test
    void weakGarrisonLoses() {
        var v = DodgeDecision.assess(units(AXE, 400, LIGHT, 100), units(SPEAR, 30), 3, 20, 1, 0);
        assertTrue(v.wouldLose());
        assertTrue(v.ratio() > 1);
    }

    @Test
    void strongGarrisonHolds() {
        var v = DodgeDecision.assess(units(AXE, 100), units(SPEAR, 500, SWORD, 300), 10, 20, 1, 0);
        assertFalse(v.wouldLose());
        assertTrue(v.ratio() < 1);
    }

    @Test
    void reinforcementsCanFlipTheVerdict() {
        var attack = units(AXE, 200);
        var home = units(SPEAR, 100);
        assertTrue(DodgeDecision.assess(attack, home, 0, 20, 1, 0).wouldLose());
        var help = DodgeDecision.spareDefenders(units(SPEAR, 400, SWORD, 200, AXE, 999), 0.5);
        assertEquals(200, help.get(SPEAR));
        assertEquals(100, help.get(SWORD));
        assertFalse(help.containsKey(AXE), "a helper never sends its axes as defence");
        assertFalse(DodgeDecision.assess(attack, DodgeDecision.plus(home, help), 0, 20, 1, 0).wouldLose());
    }

    @Test
    void cautionMakesItPessimistic() {
        var attack = units(AXE, 200);
        var home = units(SPEAR, 100, SWORD, 60);
        var plain = DodgeDecision.assess(attack, home, 0, 20, 1, 0);
        var cautious = DodgeDecision.assess(attack, home, 0, 20, 1, 0.25);
        assertTrue(cautious.ratio() > plain.ratio());
    }

    @Test
    void onlyOffenceDodges() {
        var save = DodgeDecision.offenceToSave(units(SPEAR, 100, AXE, 30, LIGHT, 5, SCOUT, 4, SNOB, 1));
        assertEquals(units(AXE, 30, LIGHT, 5, SNOB, 1), save);
    }
}
