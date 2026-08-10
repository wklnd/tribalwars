package com.twlan.backend.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BonusTypeTest {

    private static Village village(Integer code, String setting) {
        World w = new World();
        if (setting != null) w.getSettings().put("bonusVillages", setting);
        Village v = new Village();
        v.setWorld(w);
        v.setBonusCode(code);
        return v;
    }

    @Test
    void codesAreTheOriginalsBonusIconNumbers() {
        // game.css bonus_icon_1..9 rendered: axe (wood), shovel (clay), pickaxe (iron), bread (farm), swords (barracks), horseshoe (stable),
        // gears (workshop), resource pile (all), wheel (storage)
        assertEquals(1, BonusType.WOOD.code);
        assertEquals(2, BonusType.CLAY.code);
        assertEquals(3, BonusType.IRON.code);
        assertEquals(4, BonusType.FARM.code);
        assertEquals(5, BonusType.BARRACKS.code);
        assertEquals(6, BonusType.STABLE.code);
        assertEquals(7, BonusType.GARAGE.code);
        assertEquals(8, BonusType.ALL.code);
        assertEquals(9, BonusType.STORAGE.code);
        assertEquals(BonusType.CLAY, BonusType.byCode(2));
        assertNull(BonusType.byCode(0));
        assertNull(BonusType.byCode(null));
    }

    @Test
    void resourceBonusesDoubleOnlyTheirOwnProducer() {
        assertEquals(2.0, BonusType.WOOD.production(BuildingType.TIMBER_CAMP));
        assertEquals(1.0, BonusType.WOOD.production(BuildingType.CLAY_PIT));
        assertEquals(2.0, BonusType.CLAY.production(BuildingType.CLAY_PIT));
        assertEquals(2.0, BonusType.IRON.production(BuildingType.IRON_MINE));
        assertEquals(1.1, BonusType.ALL.production(BuildingType.TIMBER_CAMP));
        assertEquals(1.1, BonusType.ALL.production(BuildingType.IRON_MINE));
        assertEquals(1.0, BonusType.STORAGE.production(BuildingType.TIMBER_CAMP));
    }

    @Test
    void capacityAndPopulationBonuses() {
        assertEquals(1.5, BonusType.STORAGE.capacity());
        assertEquals(1.0, BonusType.FARM.capacity());
        assertEquals(1.1, BonusType.FARM.population());
        assertEquals(1.0, BonusType.STORAGE.population());
    }

    @Test
    void recruitBonusesOnlyAffectTheirOwnBuilding() {
        assertEquals(1 / 1.5, BonusType.BARRACKS.recruitTime(BuildingType.BARRACKS), 1e-12);
        assertEquals(1.0, BonusType.BARRACKS.recruitTime(BuildingType.STABLE));
        assertEquals(1 / 1.5, BonusType.STABLE.recruitTime(BuildingType.STABLE), 1e-12);
        assertEquals(0.5, BonusType.GARAGE.recruitTime(BuildingType.WORKSHOP));
        assertEquals(1.0, BonusType.GARAGE.recruitTime(BuildingType.BARRACKS));
        assertEquals(1.0, BonusType.WOOD.recruitTime(BuildingType.BARRACKS));
    }

    @Test
    void aVillageWithoutBonusHasFactorOne() {
        Village v = village(null, null);
        assertNull(BonusType.of(v));
        assertEquals(1.0, BonusType.productionFactor(v, BuildingType.TIMBER_CAMP));
        assertEquals(1.0, BonusType.capacityFactor(v));
        assertEquals(1.0, BonusType.populationFactor(v));
        assertEquals(1.0, BonusType.recruitTimeFactor(v, BuildingType.BARRACKS));
    }

    @Test
    void aBonusVillageAppliesItsFactors() {
        Village v = village(BonusType.WOOD.code, null);
        assertEquals(BonusType.WOOD, BonusType.of(v));
        assertEquals(2.0, BonusType.productionFactor(v, BuildingType.TIMBER_CAMP));
        assertEquals(1.0, BonusType.productionFactor(v, BuildingType.CLAY_PIT));
    }

    @Test
    void offSwitchesEveryBonusOff() {
        Village v = village(BonusType.STORAGE.code, "off");
        assertNull(BonusType.of(v));
        assertEquals(1.0, BonusType.capacityFactor(v));
        assertEquals(0.0, BonusType.share(v.getWorld()));
    }

    @Test
    void sharesAndRolls() {
        World normal = new World();
        normal.getSettings().put("bonusVillages", "normal");
        assertEquals(0.08, BonusType.share(normal));
        assertEquals(0.15, BonusType.share(new World())); // default "better"
        World off = new World();
        off.getSettings().put("bonusVillages", "off");
        Random rnd = new Random(7);
        for (int i = 0; i < 200; i++) assertNull(BonusType.roll(rnd, off));

        int given = 0;
        for (int i = 0; i < 20_000; i++) if (BonusType.roll(rnd, new World()) != null) given++;
        assertEquals(0.15, given / 20_000.0, 0.015);
    }

    @Test
    void drawFavoursTheSingleResourceKinds() {
        Random rnd = new Random(11);
        Map<BonusType, Integer> n = new EnumMap<>(BonusType.class);
        for (int i = 0; i < 30_000; i++) n.merge(BonusType.draw(rnd), 1, Integer::sum);
        for (BonusType b : BonusType.values()) assertTrue(n.getOrDefault(b, 0) > 0, b + " is never drawn");
        // weights 3/3/3 and six times 1 (total 15)
        assertEquals(0.2, n.get(BonusType.WOOD) / 30_000.0, 0.02);
        assertEquals(1 / 15.0, n.get(BonusType.FARM) / 30_000.0, 0.015);
    }
}
