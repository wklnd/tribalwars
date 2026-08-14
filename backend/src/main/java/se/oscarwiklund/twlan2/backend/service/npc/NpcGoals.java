package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.BuildingType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static se.oscarwiklund.twlan2.backend.domain.BuildingType.*;

// The build plan of an NPC: per archetype an ordered list of (building, level) goals, worked through top to bottom.
// Pure (no Spring, no database) so the choice can be unit-tested. next() returns the building to raise now:
// the first unmet goal, or, when its requirements are missing, the requirement that has to be raised first.
public final class NpcGoals {

    private NpcGoals() {}

    public record Goal(BuildingType type, int level) {}

    // One thing to build: `type` to `level`, because of `goal` (the same, or a goal that needed it).
    public record Step(BuildingType type, int level, Goal goal) {}

    private static final List<Goal> FARMER = plan(
            HEADQUARTERS, 3, TIMBER_CAMP, 4, CLAY_PIT, 4, IRON_MINE, 3, WAREHOUSE, 3, FARM, 3,
            HEADQUARTERS, 5, MARKET, 2, TIMBER_CAMP, 7, CLAY_PIT, 7, IRON_MINE, 6, FARM, 6, WAREHOUSE, 6,
            BARRACKS, 3, HIDING_PLACE, 3, HEADQUARTERS, 8, TIMBER_CAMP, 10, CLAY_PIT, 10, IRON_MINE, 9,
            WAREHOUSE, 9, FARM, 9, SMITHY, 5, BARRACKS, 5, STABLE, 3, MARKET, 5, HEADQUARTERS, 10,
            TIMBER_CAMP, 14, CLAY_PIT, 14, IRON_MINE, 13, WAREHOUSE, 13, FARM, 13, STABLE, 5, HIDING_PLACE, 6,
            HEADQUARTERS, 14, TIMBER_CAMP, 18, CLAY_PIT, 18, IRON_MINE, 17, WAREHOUSE, 17, FARM, 17, STABLE, 8,
            TIMBER_CAMP, 22, CLAY_PIT, 22, IRON_MINE, 21, WAREHOUSE, 21, FARM, 21, HEADQUARTERS, 18, MARKET, 10);

    private static final List<Goal> RAIDER = plan(
            HEADQUARTERS, 3, TIMBER_CAMP, 3, CLAY_PIT, 3, IRON_MINE, 3, BARRACKS, 3, FARM, 3, WAREHOUSE, 3,
            HEADQUARTERS, 5, MARKET, 2, SMITHY, 3, BARRACKS, 5, TIMBER_CAMP, 6, CLAY_PIT, 6, IRON_MINE, 6, FARM, 6, WAREHOUSE, 6,
            WALL, 3, SMITHY, 5, HEADQUARTERS, 8, STABLE, 3, BARRACKS, 8, TIMBER_CAMP, 9, CLAY_PIT, 9, IRON_MINE, 9,
            FARM, 10, WAREHOUSE, 9, HEADQUARTERS, 10, STABLE, 5, WORKSHOP, 1, WALL, 5, SMITHY, 10, BARRACKS, 12,
            TIMBER_CAMP, 13, CLAY_PIT, 13, IRON_MINE, 13, FARM, 14, WAREHOUSE, 13, STABLE, 8, WORKSHOP, 3, MARKET, 5,
            HEADQUARTERS, 14, BARRACKS, 16, WALL, 8, TIMBER_CAMP, 17, CLAY_PIT, 17, IRON_MINE, 17, FARM, 18, WAREHOUSE, 17,
            STABLE, 10, SMITHY, 15, HEADQUARTERS, 18, BARRACKS, 20, TIMBER_CAMP, 21, CLAY_PIT, 21, IRON_MINE, 21, FARM, 22, WAREHOUSE, 21);

    private static final List<Goal> TURTLE = plan(
            HEADQUARTERS, 3, TIMBER_CAMP, 4, CLAY_PIT, 4, IRON_MINE, 3, BARRACKS, 3, FARM, 4, WAREHOUSE, 4,
            WALL, 5, HEADQUARTERS, 5, MARKET, 2, TIMBER_CAMP, 7, CLAY_PIT, 7, IRON_MINE, 6, BARRACKS, 5, SMITHY, 3, FARM, 7, WAREHOUSE, 7,
            WALL, 10, HIDING_PLACE, 4, TIMBER_CAMP, 10, CLAY_PIT, 10, IRON_MINE, 9, BARRACKS, 8, HEADQUARTERS, 8, FARM, 10, WAREHOUSE, 10,
            SMITHY, 6, WALL, 14, STABLE, 3, TIMBER_CAMP, 13, CLAY_PIT, 13, IRON_MINE, 13, BARRACKS, 12, FARM, 14, WAREHOUSE, 13,
            HEADQUARTERS, 12, WALL, 18, HIDING_PLACE, 7, SMITHY, 10, MARKET, 3, TIMBER_CAMP, 17, CLAY_PIT, 17, IRON_MINE, 17,
            BARRACKS, 16, FARM, 18, WAREHOUSE, 17, WALL, 20, HEADQUARTERS, 16, TIMBER_CAMP, 21, CLAY_PIT, 21, IRON_MINE, 21, FARM, 22, WAREHOUSE, 21);

    private static final List<Goal> CONQUEROR = plan(
            HEADQUARTERS, 3, TIMBER_CAMP, 4, CLAY_PIT, 4, IRON_MINE, 4, WAREHOUSE, 4, FARM, 4,
            HEADQUARTERS, 8, TIMBER_CAMP, 8, CLAY_PIT, 8, IRON_MINE, 8, BARRACKS, 5, FARM, 8, WAREHOUSE, 8,
            SMITHY, 10, HEADQUARTERS, 12, MARKET, 5, STABLE, 3, TIMBER_CAMP, 12, CLAY_PIT, 12, IRON_MINE, 12, FARM, 12, WAREHOUSE, 12,
            HEADQUARTERS, 20, SMITHY, 20, MARKET, 10, ACADEMY, 1, TIMBER_CAMP, 16, CLAY_PIT, 16, IRON_MINE, 16, WAREHOUSE, 16, FARM, 16,
            STABLE, 5, WORKSHOP, 1, WALL, 5, TIMBER_CAMP, 20, CLAY_PIT, 20, IRON_MINE, 20, WAREHOUSE, 20, FARM, 20,
            STABLE, 10, WORKSHOP, 3, WALL, 10, TIMBER_CAMP, 24, CLAY_PIT, 24, IRON_MINE, 24, WAREHOUSE, 24, FARM, 24);

    private static final List<Goal> TRADER = plan(
            HEADQUARTERS, 3, TIMBER_CAMP, 4, CLAY_PIT, 4, IRON_MINE, 3, WAREHOUSE, 4, FARM, 3, MARKET, 3,
            HEADQUARTERS, 5, TIMBER_CAMP, 7, CLAY_PIT, 7, IRON_MINE, 6, WAREHOUSE, 7, MARKET, 6, FARM, 6, BARRACKS, 3,
            TIMBER_CAMP, 10, CLAY_PIT, 10, IRON_MINE, 9, WAREHOUSE, 10, MARKET, 10, HEADQUARTERS, 8, FARM, 9, BARRACKS, 5, SMITHY, 5,
            TIMBER_CAMP, 14, CLAY_PIT, 14, IRON_MINE, 13, WAREHOUSE, 14, MARKET, 15, HEADQUARTERS, 11, FARM, 13, STABLE, 3, WALL, 5,
            TIMBER_CAMP, 18, CLAY_PIT, 18, IRON_MINE, 17, WAREHOUSE, 18, MARKET, 20, HEADQUARTERS, 15, FARM, 17, HIDING_PLACE, 5,
            TIMBER_CAMP, 22, CLAY_PIT, 22, IRON_MINE, 21, WAREHOUSE, 22, FARM, 21, STABLE, 5, SMITHY, 10);

    private static final List<Goal> BALANCED = plan(
            HEADQUARTERS, 3, TIMBER_CAMP, 3, CLAY_PIT, 3, IRON_MINE, 3, WAREHOUSE, 3, FARM, 3,
            HEADQUARTERS, 5, MARKET, 2, BARRACKS, 3, TIMBER_CAMP, 6, CLAY_PIT, 6, IRON_MINE, 6, FARM, 6, WAREHOUSE, 6, WALL, 3,
            SMITHY, 3, BARRACKS, 5, HEADQUARTERS, 8, TIMBER_CAMP, 9, CLAY_PIT, 9, IRON_MINE, 9, FARM, 9, WAREHOUSE, 9, STABLE, 3, MARKET, 3,
            WALL, 6, HEADQUARTERS, 10, SMITHY, 8, BARRACKS, 8, TIMBER_CAMP, 13, CLAY_PIT, 13, IRON_MINE, 13, FARM, 13, WAREHOUSE, 13,
            STABLE, 5, WORKSHOP, 1, HIDING_PLACE, 4, WALL, 10, HEADQUARTERS, 14, TIMBER_CAMP, 17, CLAY_PIT, 17, IRON_MINE, 17, FARM, 17, WAREHOUSE, 17,
            BARRACKS, 12, SMITHY, 12, MARKET, 8, STATUE, 1, TIMBER_CAMP, 21, CLAY_PIT, 21, IRON_MINE, 21, FARM, 21, WAREHOUSE, 21);

    // Alternating (building, level) pairs.
    private static List<Goal> plan(Object... pairs) {
        List<Goal> goals = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            BuildingType type = (BuildingType) pairs[i];
            int level = (Integer) pairs[i + 1];
            // wood is what everything costs most of (buildings and troops alike): the camps run two levels ahead, the mines one behind
            if (type == TIMBER_CAMP) level = Math.min(type.maxLevel, level + 2);
            else if (type == IRON_MINE) level = Math.max(1, level - 1);
            goals.add(new Goal(type, level));
        }
        return List.copyOf(goals);
    }

    public static List<Goal> goals(NpcArchetype archetype) {
        return switch (archetype) {
            case FARMER -> FARMER;
            case RAIDER -> RAIDER;
            case TURTLE -> TURTLE;
            case CONQUEROR -> CONQUEROR;
            case TRADER -> TRADER;
            case BALANCED -> BALANCED;
        };
    }

    // The next steps of the plan, in order, each one being a building that can be built right now (its requirements are met
    // at the level counted in `levelOf`, queued orders included). At most `limit` steps: the first is what the
    // NPC should build; the following ones are the cheaper alternatives to look at when it cannot afford the first.
    public static List<Step> next(NpcArchetype archetype, ToIntFunction<BuildingType> levelOf, int limit) {
        List<Step> steps = new ArrayList<>();
        for (Goal goal : goals(archetype)) {
            if (steps.size() >= limit) break;
            if (levelOf.applyAsInt(goal.type()) >= goal.level()) continue;
            Step step = resolve(goal.type(), goal, levelOf, 0);
            if (step != null && steps.stream().noneMatch(s -> s.type() == step.type())) steps.add(step);
        }
        return steps;
    }

    // The building that has to be raised for `type` to be buildable: itself, or the first missing requirement (recursively).
    private static Step resolve(BuildingType type, Goal goal, ToIntFunction<BuildingType> levelOf, int depth) {
        int level = levelOf.applyAsInt(type);
        if (level >= type.maxLevel || depth > 6) return null;
        for (var req : type.requirements().entrySet()) {
            if (levelOf.applyAsInt(req.getKey()) < req.getValue()) return resolve(req.getKey(), goal, levelOf, depth + 1);
        }
        return new Step(type, level + 1, goal);
    }

    // A farm is needed when less than 15 % of the population capacity is left free (counting what is already ordered).
    public static boolean needsFarm(int populationUsedIncludingOrders, int populationCapacity) {
        return populationCapacity - populationUsedIncludingOrders < populationCapacity * 0.15;
    }

    // A warehouse is needed when a resource is nearly full, or the next thing costs more than the warehouse can hold.
    public static boolean needsWarehouse(double wood, double clay, double iron, int capacity, int nextCostPeak) {
        return Math.max(wood, Math.max(clay, iron)) >= capacity * 0.9 || nextCostPeak > capacity;
    }

    // The largest single cost of a step, which the warehouse has to be able to hold.
    public static int peakCost(BuildingType type, int level) {
        return Math.max(type.woodCost(level), Math.max(type.clayCost(level), type.ironCost(level)));
    }
}
