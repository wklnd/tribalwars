package com.twlan.backend.service.npc;

import com.twlan.backend.domain.UnitType;
import com.twlan.backend.service.BattleCalculator;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static com.twlan.backend.domain.UnitType.*;
import static org.junit.jupiter.api.Assertions.*;

class AttackPlannerTest {

    private static Map<UnitType, Integer> units(Object... pairs) {
        Map<UnitType, Integer> m = new EnumMap<>(UnitType.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((UnitType) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    private static AttackPlanner.Plan plan(Map<UnitType, Integer> army, Map<UnitType, Integer> defence, int wall, double margin, double loot, double maxLoss, boolean rams) {
        return AttackPlanner.plan(army, defence, wall, 20, 1, 1, margin, loot, maxLoss, rams);
    }

    @Test
    void sendsOnlyWhatIsNeeded() {
        var p = plan(units(AXE, 500), units(SPEAR, 40), 0, 0.2, 0, 0.5, false);
        assertNotNull(p);
        assertTrue(p.send().get(AXE) < 250, "a small garrison does not need half the army: " + p.send());
        // and it wins even with the worst luck against a garrison 20 % bigger than seen
        assertTrue(BattleCalculator.fight(p.send(), units(SPEAR, 48), 0, 1, -BattleCalculator.MAX_LUCK, 1, 20).attackerWins());
    }

    @Test
    void doesNotSendWhenItWouldLose() {
        assertNull(plan(units(AXE, 20), units(SPEAR, 300, SWORD, 200), 5, 0.2, 0, 0.5, false));
    }

    @Test
    void largerMarginSendsMore() {
        var careful = plan(units(AXE, 800), units(SPEAR, 100), 0, 0.5, 0, 0.5, false);
        var bold = plan(units(AXE, 800), units(SPEAR, 100), 0, 0.0, 0, 0.5, false);
        assertTrue(careful.send().get(AXE) > bold.send().get(AXE));
    }

    @Test
    void wallRaisesTheForce() {
        var open = plan(units(AXE, 800), units(SPEAR, 60), 0, 0.2, 0, 0.5, false);
        var walled = plan(units(AXE, 800), units(SPEAR, 60), 15, 0.2, 0, 0.5, false);
        assertTrue(walled.send().get(AXE) > open.send().get(AXE));
    }

    @Test
    void bringsRamsForTheWallOnlyWhenAsked() {
        var army = units(AXE, 800, RAM, 30);
        assertFalse(plan(army, units(SPEAR, 30), 5, 0.2, 0, 0.5, false).send().containsKey(RAM));
        var p = plan(army, units(SPEAR, 30), 5, 0.2, 0, 0.5, true);
        assertTrue(p.send().containsKey(RAM));
        assertTrue(BattleCalculator.levelsDestroyed(p.send().get(RAM), 5) >= 5, "enough rams to flatten the wall");
        assertFalse(plan(army, units(SPEAR, 30), 0, 0.2, 0, 0.5, true).send().containsKey(RAM), "no wall, no rams");
    }

    @Test
    void addsCarriersForTheLoot() {
        var army = units(AXE, 100, LIGHT, 200);
        var noLoot = plan(army, units(SPEAR, 5), 0, 0.2, 0, 0.5, false);
        var loot = plan(army, units(SPEAR, 5), 0, 0.2, 9000, 0.5, false);
        assertTrue(loot.carry() > noLoot.carry());
        assertTrue(loot.carry() >= 9000 || loot.send().get(LIGHT) == 200 && loot.send().get(AXE) == 100);
    }

    @Test
    void refusesWhenLossesAreTooHigh() {
        // barely enough force: the win costs almost everything, so with a low loss cap it does not go
        var army = units(AXE, 130);
        assertNotNull(plan(army, units(SPEAR, 100), 0, 0.0, 0, 1.0, false));
        assertNull(plan(army, units(SPEAR, 100), 0, 0.0, 0, 0.1, false));
    }

    @Test
    void noFightersNoPlan() {
        assertNull(plan(units(RAM, 20, CATAPULT, 5), units(SPEAR, 1), 0, 0.2, 0, 0.5, true));
    }

    @Test
    void guessGrowsWithPoints() {
        assertTrue(AttackPlanner.pop(AttackPlanner.guessGarrison(1000, 1)) > AttackPlanner.pop(AttackPlanner.guessGarrison(100, 1)));
        assertTrue(AttackPlanner.guessGarrison(0, 1).isEmpty());
    }
}
