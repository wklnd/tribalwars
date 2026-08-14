package se.oscarwiklund.twlan2.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Expected values are read off the ORIGINAL game's rendered pages (reference bundle, world "Welt 1").
class BuildingTypeTest {

    @Test
    void warehouseAndFarmCapacityUseLevelMinusOneExponent() {
        assertEquals(1000, BuildingType.warehouseCapacity(1));
        assertEquals(2285, BuildingType.warehouseCapacity(5));
        assertEquals(2810, BuildingType.warehouseCapacity(6));
        assertEquals(240, BuildingType.farmCapacity(1));
        assertEquals(453, BuildingType.farmCapacity(5));
    }

    @Test
    void pitProductionMatchesOriginalPerHour() {
        // original shows 319,308 / 371,393 units per hour at speed 5000 for levels 6 / 7
        assertEquals(319308.0 / 5000, BuildingType.TIMBER_CAMP.productionPerHour(6), 0.05);
        assertEquals(371393.0 / 5000, BuildingType.TIMBER_CAMP.productionPerHour(7), 0.05);
        assertEquals(5, BuildingType.CLAY_PIT.productionPerHour(0), 0.0);
    }

    @Test
    void costsAndPopulationMatchOriginalBuildPage() {
        assertEquals(286, BuildingType.HEADQUARTERS.woodCost(6));
        assertEquals(270, BuildingType.HEADQUARTERS.clayCost(6));
        assertEquals(222, BuildingType.HEADQUARTERS.ironCost(6));
        assertEquals(2, BuildingType.HEADQUARTERS.popIncrease(6));
        assertEquals(7, BuildingType.BARRACKS.popIncrease(1));
        // whole village: HQ5 + place1 + wood6 + clay5 + iron5 + hide2 = 57 population in the original
        int used = BuildingType.HEADQUARTERS.popCost(5) + BuildingType.TIMBER_CAMP.popCost(6)
                + BuildingType.CLAY_PIT.popCost(5) + BuildingType.IRON_MINE.popCost(5) + 2;
        assertEquals(57, used);
    }

    @Test
    void headquartersShortensBuildTimeByFivePercentPerLevel() {
        long base = BuildingType.WALL.buildTimeSeconds(1, 0);
        long hq10 = BuildingType.WALL.buildTimeSeconds(1, 10);
        assertEquals(Math.round(base / Math.pow(1.05, 10)), hq10);
    }

    @Test
    void buildMainFactorOverloadReplacesTheDefaultFivePercent() {
        // world "buildMainFactor" in place of the default 1.05 (WorldSettings.java)
        long default1p05 = BuildingType.WALL.buildTimeSeconds(1, 10, 1.0);
        long explicit1p05 = BuildingType.WALL.buildTimeSeconds(1, 10, 1.0, 1.05);
        assertEquals(default1p05, explicit1p05);

        long base = BuildingType.WALL.buildTimeSeconds(1, 0, 1.0, 1.10);
        long hq10 = BuildingType.WALL.buildTimeSeconds(1, 10, 1.0, 1.10);
        assertEquals(Math.round(base / Math.pow(1.10, 10)), hq10);
    }

    @Test
    void requirementsFromOriginalConfig() {
        assertEquals(3, BuildingType.BARRACKS.requirements().get(BuildingType.HEADQUARTERS));
        assertEquals(1, BuildingType.WALL.requirements().get(BuildingType.BARRACKS));
    }
}
