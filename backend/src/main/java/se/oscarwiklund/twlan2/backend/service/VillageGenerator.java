package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.BuildingType;
import se.oscarwiklund.twlan2.backend.domain.UnitType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Generates a plausible random village for a "development" between 0 (freshly founded) and 1 (fully grown).
// The result always obeys the game's rules: every built building has its requirements met (an Academy needs
// Headquarters 20, Smithy 20 and Market 10, ...), levels stay within the maximum, troops need the buildings that
// train them, population fits the farm and resources fit the warehouse.
public final class VillageGenerator {

    public record Layout(Map<BuildingType, Integer> buildings, Map<UnitType, Integer> units, int wood, int clay, int iron) {
        public int points() {
            int sum = 0;
            for (var e : buildings.entrySet()) sum += e.getKey().points(e.getValue());
            return sum;
        }
    }

    private VillageGenerator() {}

    public static Layout generate(Random rnd, double development) {
        double d = Math.max(0, Math.min(1, development));
        Map<BuildingType, Integer> lv = new EnumMap<>(BuildingType.class);
        for (BuildingType t : BuildingType.values()) lv.put(t, t.startLevel());

        // core economy
        set(lv, BuildingType.HEADQUARTERS, 1 + scaled(rnd, d, 29, 0.75, 1.0));
        set(lv, BuildingType.TIMBER_CAMP, 1 + scaled(rnd, d, 29, 0.6, 1.0));
        set(lv, BuildingType.CLAY_PIT, 1 + scaled(rnd, d, 29, 0.6, 1.0));
        set(lv, BuildingType.IRON_MINE, 1 + scaled(rnd, d, 29, 0.6, 1.0));
        set(lv, BuildingType.FARM, 1 + scaled(rnd, d, 29, 0.6, 1.0));
        set(lv, BuildingType.WAREHOUSE, 1 + scaled(rnd, d, 29, 0.6, 1.0));
        set(lv, BuildingType.HIDING_PLACE, Math.max(1, scaled(rnd, d, 10, 0.4, 1.0)));

        // optional buildings: chosen by chance that grows with development; requirements are raised as needed
        maybe(rnd, lv, BuildingType.BARRACKS, Math.min(1, d * 2.2), 1 + scaled(rnd, d, 24, 0.5, 1.0));
        maybe(rnd, lv, BuildingType.WALL, 0.25 + 0.6 * d, scaled(rnd, d, 20, 0.5, 1.0));
        maybe(rnd, lv, BuildingType.SMITHY, d * 1.3, 1 + scaled(rnd, d, 19, 0.5, 1.0));
        maybe(rnd, lv, BuildingType.MARKET, d * 1.2, 1 + scaled(rnd, d, 24, 0.4, 1.0));
        maybe(rnd, lv, BuildingType.STATUE, d * 0.8, 1);
        if (d > 0.35) maybe(rnd, lv, BuildingType.STABLE, d * 0.9, 1 + scaled(rnd, d, 19, 0.5, 1.0));
        if (d > 0.45) maybe(rnd, lv, BuildingType.WORKSHOP, d * 0.7, 1 + scaled(rnd, d, 14, 0.5, 1.0));
        if (d > 0.8) maybe(rnd, lv, BuildingType.ACADEMY, (d - 0.75) * 3, 1);

        // farm must hold every building's population
        int buildingPop = 0;
        for (var e : lv.entrySet()) buildingPop += e.getKey().popCost(e.getValue());
        while (BuildingType.farmCapacity(lv.get(BuildingType.FARM)) < buildingPop + 20 && lv.get(BuildingType.FARM) < BuildingType.FARM.maxLevel) {
            lv.merge(BuildingType.FARM, 1, Integer::sum);
        }

        // resources fit the warehouse
        int cap = BuildingType.warehouseCapacity(lv.get(BuildingType.WAREHOUSE));
        int wood = (int) (cap * (0.1 + 0.8 * rnd.nextDouble()));
        int clay = (int) (cap * (0.1 + 0.8 * rnd.nextDouble()));
        int iron = (int) (cap * (0.1 + 0.8 * rnd.nextDouble()));

        return new Layout(lv, troops(rnd, d, lv, BuildingType.farmCapacity(lv.get(BuildingType.FARM)) - buildingPop), wood, clay, iron);
    }

    private static Map<UnitType, Integer> troops(Random rnd, double d, Map<BuildingType, Integer> lv, int freePop) {
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        List<UnitType> allowed = new java.util.ArrayList<>();
        for (UnitType t : UnitType.values()) {
            if (t.isRegularTroop() && lv.get(t.recruitBuilding) >= 1 && t.requirementsMet(lv::get)) allowed.add(t);
        }
        if (allowed.isEmpty() || freePop <= 0) return units;

        double budget = freePop * (0.03 + 0.5 * d * rnd.nextDouble());
        double[] weights = new double[allowed.size()];
        double sum = 0;
        for (int i = 0; i < weights.length; i++) { weights[i] = 0.2 + rnd.nextDouble(); sum += weights[i]; }
        for (int i = 0; i < weights.length; i++) {
            UnitType t = allowed.get(i);
            int n = (int) (budget * weights[i] / sum / t.popCost);
            if (n > 0) units.put(t, n);
        }
        return units;
    }

    // Random level in [0, max] biased by development: max * d * jitter(lo..hi).
    private static int scaled(Random rnd, double d, int max, double lo, double hi) {
        double factor = lo + (hi - lo) * rnd.nextDouble();
        return (int) Math.round(max * d * factor);
    }

    private static void set(Map<BuildingType, Integer> lv, BuildingType t, int level) {
        lv.put(t, Math.max(lv.get(t), Math.min(level, t.maxLevel)));
    }

    // With the given chance builds the building at (at most) the level, first bringing its requirements up to level.
    private static void maybe(Random rnd, Map<BuildingType, Integer> lv, BuildingType t, double chance, int level) {
        if (level < 1 || rnd.nextDouble() >= chance) return;
        ensure(lv, t, level);
    }

    private static void ensure(Map<BuildingType, Integer> lv, BuildingType t, int level) {
        for (var req : t.requirements().entrySet()) ensure(lv, req.getKey(), req.getValue());
        lv.put(t, Math.max(lv.get(t), Math.min(level, t.maxLevel)));
    }
}
