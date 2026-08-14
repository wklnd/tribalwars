package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.BuildingType;
import se.oscarwiklund.twlan2.backend.domain.UnitType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static se.oscarwiklund.twlan2.backend.domain.BuildingType.*;
import static org.junit.jupiter.api.Assertions.*;

class NpcEconomyGoalsTest {

    private static Map<BuildingType, Integer> village(Object... pairs) {
        Map<BuildingType, Integer> m = new EnumMap<>(BuildingType.class);
        for (BuildingType t : BuildingType.values()) m.put(t, t.startLevel());
        for (int i = 0; i < pairs.length; i += 2) m.put((BuildingType) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    private static List<NpcGoals.Step> next(NpcArchetype a, Map<BuildingType, Integer> levels, int limit) {
        return NpcGoals.next(a, t -> levels.getOrDefault(t, 0), limit);
    }

    @Test
    void everyStepIsBuildableNow() {
        for (NpcArchetype a : NpcArchetype.values()) {
            Map<BuildingType, Integer> levels = village();
            // walk the whole plan by "building" the first step each time: every step must have its requirements met
            for (int i = 0; i < 2000; i++) {
                var steps = next(a, levels, 1);
                if (steps.isEmpty()) break;
                var step = steps.get(0);
                for (var req : step.type().requirements().entrySet()) {
                    assertTrue(levels.get(req.getKey()) >= req.getValue(), a + ": " + step + " needs " + req);
                }
                assertEquals(levels.get(step.type()) + 1, step.level());
                assertTrue(step.level() <= step.type().maxLevel);
                levels.put(step.type(), step.level());
            }
            assertTrue(next(a, levels, 1).isEmpty(), a + " plan finishes");
        }
    }

    @Test
    void missingRequirementIsRaisedFirst() {
        // the Academy needs HQ 20, Smithy 20 and Market 10
        var steps = next(NpcArchetype.CONQUEROR, village(HEADQUARTERS, 20, SMITHY, 20, MARKET, 5, BARRACKS, 5, WAREHOUSE, 16, FARM, 16,
                TIMBER_CAMP, 16, CLAY_PIT, 16, IRON_MINE, 16, STABLE, 3), 1);
        assertEquals(MARKET, steps.get(0).type());
        assertEquals(6, steps.get(0).level());
    }

    @Test
    void goalWithMissingChainResolvesToTheDeepestRequirement() {
        // stable needs barracks 5 + smithy 5, smithy needs barracks 1: from scratch the first thing is HQ
        var steps = next(NpcArchetype.FARMER, village(HEADQUARTERS, 10, MARKET, 5, BARRACKS, 1, SMITHY, 0, TIMBER_CAMP, 30, CLAY_PIT, 30, IRON_MINE, 30,
                WAREHOUSE, 30, FARM, 30), 1);
        assertEquals(BARRACKS, steps.get(0).type()); // barracks 3 goal comes before smithy/stable
    }

    @Test
    void nextReturnsDistinctBuildingsInPlanOrder() {
        var steps = next(NpcArchetype.RAIDER, village(), 6);
        assertEquals(6, steps.size());
        assertEquals(6, steps.stream().map(NpcGoals.Step::type).distinct().count());
        assertEquals(HEADQUARTERS, steps.get(0).type());
    }

    @Test
    void differentArchetypesPlanDifferently() {
        var raider = next(NpcArchetype.RAIDER, village(HEADQUARTERS, 5, TIMBER_CAMP, 8, CLAY_PIT, 6, IRON_MINE, 6, FARM, 6, WAREHOUSE, 6, BARRACKS, 5, SMITHY, 3, MARKET, 3), 1);
        var trader = next(NpcArchetype.TRADER, village(HEADQUARTERS, 5, TIMBER_CAMP, 8, CLAY_PIT, 6, IRON_MINE, 6, FARM, 6, WAREHOUSE, 6, BARRACKS, 5, SMITHY, 3, MARKET, 3), 1);
        assertEquals(WALL, raider.get(0).type());
        assertEquals(TIMBER_CAMP, trader.get(0).type()); // the trader keeps growing its camps
    }

    @Test
    void farmRule() {
        assertTrue(NpcGoals.needsFarm(430, 500));
        assertFalse(NpcGoals.needsFarm(400, 500));
        assertTrue(NpcGoals.needsFarm(240, 240));
    }

    @Test
    void warehouseRule() {
        assertTrue(NpcGoals.needsWarehouse(950, 100, 100, 1000, 300)); // nearly full
        assertTrue(NpcGoals.needsWarehouse(100, 100, 100, 1000, 1200)); // next thing does not fit
        assertFalse(NpcGoals.needsWarehouse(800, 100, 100, 1000, 900));
    }

    @Test
    void armyChoosesTheTypeFurthestBelowItsShare() {
        Map<UnitType, Integer> pop = new EnumMap<>(UnitType.class);
        pop.put(UnitType.AXE, 100);
        var available = EnumSet.of(UnitType.AXE, UnitType.LIGHT, UnitType.SPEAR);
        // raider wants axes 35 %, light 25 %, spear 8 % of the target population: the axes are done -> light next
        assertEquals(UnitType.LIGHT, NpcArmy.choose(NpcArchetype.RAIDER, available, pop, 0, false, 300));
        // no scouts available: nothing changes with the flag
        assertEquals(UnitType.LIGHT, NpcArmy.choose(NpcArchetype.RAIDER, available, pop, 0, true, 300));
        // a garrison full of spears still trains axes: the target is per type
        Map<UnitType, Integer> spears = new EnumMap<>(UnitType.class);
        spears.put(UnitType.SPEAR, 500);
        assertNotEquals(UnitType.SPEAR, NpcArmy.choose(NpcArchetype.RAIDER, available, spears, 0, false, 300));
        // everything met: nothing to train
        Map<UnitType, Integer> full = new EnumMap<>(UnitType.class);
        full.put(UnitType.AXE, 500); full.put(UnitType.LIGHT, 500); full.put(UnitType.SPEAR, 500);
        assertNull(NpcArmy.choose(NpcArchetype.RAIDER, available, full, 0, false, 300));
    }

    @Test
    void armyKeepsScoutsWhenAsked() {
        var available = EnumSet.of(UnitType.AXE, UnitType.SCOUT);
        assertEquals(UnitType.SCOUT, NpcArmy.choose(NpcArchetype.RAIDER, available, new EnumMap<>(UnitType.class), 3, true, 300));
        assertEquals(UnitType.AXE, NpcArmy.choose(NpcArchetype.RAIDER, available, new EnumMap<>(UnitType.class), 3, false, 300));
        assertEquals(UnitType.AXE, NpcArmy.choose(NpcArchetype.RAIDER, available, new EnumMap<>(UnitType.class), 12, true, 300));
    }

    @Test
    void armyMixesAddUpToOne() {
        for (NpcArchetype a : NpcArchetype.values()) {
            double sum = NpcArmy.mix(a).values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 1e-9, a.name());
        }
    }

    @Test
    void batchRespectsResourcesPopulationAndTime() {
        // spear: 50 wood 30 clay 10 iron, 1 pop
        assertEquals(10, NpcArmy.batch(UnitType.SPEAR, 500, 5000, 5000, 1000, 10, 1000));  // wood
        assertEquals(7, NpcArmy.batch(UnitType.SPEAR, 5000, 5000, 5000, 7, 10, 1000));     // population
        assertEquals(100, NpcArmy.batch(UnitType.SPEAR, 5000, 5000, 5000, 1000, 10, 1000)); // time
        assertEquals(1, NpcArmy.batch(UnitType.SPEAR, 5000, 5000, 5000, 1000, 5000, 1000)); // always one if affordable
        assertEquals(0, NpcArmy.batch(UnitType.SPEAR, 10, 5000, 5000, 1000, 10, 1000));
    }

    @Test
    void armyShareGrowsWithSkill() {
        assertTrue(NpcArmy.armyShare(NpcArchetype.RAIDER, 1) > NpcArmy.armyShare(NpcArchetype.RAIDER, 0));
        assertTrue(NpcArmy.armyShare(NpcArchetype.RAIDER, 0.5) > NpcArmy.armyShare(NpcArchetype.TRADER, 0.5));
    }
}
