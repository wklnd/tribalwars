package se.oscarwiklund.twlan2.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import se.oscarwiklund.twlan2.backend.domain.BuildingType;
import se.oscarwiklund.twlan2.backend.domain.UnitType;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VillageGeneratorTest {

    @Test
    void randomVillagesAlwaysObeyTheGameRules() {
        Random rnd = new Random(42);
        boolean sawAcademy = false;
        for (int i = 0; i < 20_000; i++) {
            double dev = (i % 101) / 100.0;
            var l = VillageGenerator.generate(rnd, dev);
            var b = l.buildings();
            int pop = 0;
            for (BuildingType t : BuildingType.values()) {
                int level = b.get(t);
                assertTrue(level >= 0 && level <= t.maxLevel, t + " level " + level);
                pop += t.popCost(level);
                if (level > t.startLevel()) {
                    t.requirements().forEach((req, need) ->
                            assertTrue(b.get(req) >= need, t + " " + level + " needs " + req + " " + need + " but has " + b.get(req)));
                }
                if (level > 0) {
                    t.requirements().forEach((req, need) -> assertTrue(b.get(req) >= need, t + " built without " + req));
                }
            }
            sawAcademy |= b.get(BuildingType.ACADEMY) > 0;
            if (b.get(BuildingType.ACADEMY) > 0) {
                assertTrue(b.get(BuildingType.SMITHY) >= 20 && b.get(BuildingType.HEADQUARTERS) >= 20 && b.get(BuildingType.MARKET) >= 10);
            }
            // troops need their buildings, population fits the farm, resources fit the warehouse
            int troopPop = 0;
            for (var e : l.units().entrySet()) {
                troopPop += e.getKey().popCost * e.getValue();
                switch (e.getKey()) {
                    case SPEAR -> assertTrue(b.get(BuildingType.BARRACKS) >= 1);
                    case SWORD -> assertTrue(b.get(BuildingType.BARRACKS) >= 1 && b.get(BuildingType.SMITHY) >= 1);
                    case AXE -> assertTrue(b.get(BuildingType.SMITHY) >= 2);
                    case ARCHER -> assertTrue(b.get(BuildingType.SMITHY) >= 5 && b.get(BuildingType.BARRACKS) >= 5);
                    case SCOUT -> assertTrue(b.get(BuildingType.STABLE) >= 1);
                }
            }
            assertTrue(pop + troopPop <= BuildingType.farmCapacity(b.get(BuildingType.FARM)), "population " + (pop + troopPop));
            int cap = BuildingType.warehouseCapacity(b.get(BuildingType.WAREHOUSE));
            assertTrue(l.wood() <= cap && l.clay() <= cap && l.iron() <= cap);
        }
        assertTrue(sawAcademy, "high development should occasionally produce an Academy");
    }

    @Test
    void developmentScalesTheVillage() {
        Random rnd = new Random(7);
        long low = 0, high = 0;
        for (int i = 0; i < 500; i++) {
            low += VillageGenerator.generate(rnd, 0.1).points();
            high += VillageGenerator.generate(rnd, 0.9).points();
        }
        assertTrue(high > low * 3, "avg points low=" + low / 500 + " high=" + high / 500);
    }

    @Test
    void freshVillagesAreSmallAndUnarmed() {
        var l = VillageGenerator.generate(new Random(1), 0.0);
        assertEquals(1, l.buildings().get(BuildingType.HEADQUARTERS));
        assertTrue(l.units().values().stream().allMatch(n -> n == 0) || l.units().isEmpty());
        assertEquals(0, l.buildings().get(BuildingType.ACADEMY));
        assertEquals(UnitType.values().length, 5);
    }
}
