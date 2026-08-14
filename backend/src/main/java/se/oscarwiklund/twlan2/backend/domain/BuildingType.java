package se.oscarwiklund.twlan2.backend.domain;

// Per-building-type balance config, lifted from the original game's own world config
// (htdocs/config/worlds/world/buildings.json in the reference bundle), not reconstructed from scratch.
// Cost/time at a level = base * factor^(level-1).
public enum BuildingType {
    //              wood          clay/stone     iron            pop            time         maxLevel
    HEADQUARTERS(90, 1.26, 80, 1.275, 70, 1.26, 5, 1.17, 900, 30),
    TIMBER_CAMP(50, 1.25, 60, 1.275, 40, 1.245, 5, 1.155, 900, 30),
    CLAY_PIT(65, 1.27, 50, 1.265, 40, 1.24, 10, 1.14, 900, 30),
    IRON_MINE(75, 1.252, 65, 1.275, 70, 1.24, 10, 1.17, 1080, 30),
    FARM(45, 1.3, 40, 1.32, 30, 1.29, 0, 1.0, 1200, 30),
    WAREHOUSE(60, 1.265, 50, 1.27, 40, 1.245, 0, 1.15, 1020, 30),
    BARRACKS(200, 1.26, 170, 1.28, 90, 1.26, 7, 1.17, 1800, 25),
    WALL(50, 1.26, 100, 1.275, 20, 1.26, 5, 1.17, 3600, 20),
    // Appended (never reorder/insert above): the database stores these as an ENUM, see SchemaMigration.
    STABLE(270, 1.26, 240, 1.28, 260, 1.26, 8, 1.17, 6000, 20),
    WORKSHOP(300, 1.26, 240, 1.28, 260, 1.26, 8, 1.17, 6000, 15),
    ACADEMY(15000, 2.0, 25000, 2.0, 10000, 2.0, 80, 1.17, 586800, 1),
    SMITHY(220, 1.26, 180, 1.275, 240, 1.26, 20, 1.17, 6000, 20),
    RALLY_POINT(10, 1.26, 40, 1.275, 30, 1.26, 0, 1.17, 10860, 1),
    STATUE(220, 1.26, 220, 1.275, 220, 1.26, 10, 1.17, 1500, 1),
    MARKET(100, 1.26, 100, 1.275, 100, 1.26, 20, 1.17, 2700, 25),
    HIDING_PLACE(50, 1.25, 60, 1.25, 50, 1.25, 2, 1.17, 1800, 10);

    private static final double BUILD_TIME_FACTOR = 1.2;
    // World "buildMainFactor": each Headquarters level divides construction time by this.
    private static final double HQ_TIME_FACTOR = 1.05;

    public final double baseWood;
    public final double woodFactor;
    public final double baseClay;
    public final double clayFactor;
    public final double baseIron;
    public final double ironFactor;
    public final int basePopCost;
    public final double popFactor;
    public final double baseBuildTimeSeconds;
    public final int maxLevel;

    BuildingType(double baseWood, double woodFactor, double baseClay, double clayFactor,
                 double baseIron, double ironFactor, int basePopCost, double popFactor,
                 double baseBuildTimeSeconds, int maxLevel) {
        this.baseWood = baseWood;
        this.woodFactor = woodFactor;
        this.baseClay = baseClay;
        this.clayFactor = clayFactor;
        this.baseIron = baseIron;
        this.ironFactor = ironFactor;
        this.basePopCost = basePopCost;
        this.popFactor = popFactor;
        this.baseBuildTimeSeconds = baseBuildTimeSeconds;
        this.maxLevel = maxLevel;
    }

    // Cost to go from (targetLevel - 1) to targetLevel, not the cumulative cost.
    public int woodCost(int targetLevel) {
        return (int) Math.round(baseWood * Math.pow(woodFactor, targetLevel - 1));
    }

    public int clayCost(int targetLevel) {
        return (int) Math.round(baseClay * Math.pow(clayFactor, targetLevel - 1));
    }

    public int ironCost(int targetLevel) {
        return (int) Math.round(baseIron * Math.pow(ironFactor, targetLevel - 1));
    }

    public int popCost(int level) {
        if (basePopCost <= 0 || level <= 0) {
            return 0;
        }
        return (int) Math.round(basePopCost * Math.pow(popFactor, level - 1));
    }

    public int popIncrease(int targetLevel) {
        return popCost(targetLevel) - popCost(targetLevel - 1);
    }

    // From the original world config.
    public java.util.Map<BuildingType, Integer> requirements() {
        return switch (this) {
            case BARRACKS -> java.util.Map.of(HEADQUARTERS, 3);
            case WALL -> java.util.Map.of(BARRACKS, 1);
            case STABLE -> java.util.Map.of(HEADQUARTERS, 10, BARRACKS, 5, SMITHY, 5);
            case WORKSHOP -> java.util.Map.of(HEADQUARTERS, 10, SMITHY, 10);
            case ACADEMY -> java.util.Map.of(HEADQUARTERS, 20, SMITHY, 20, MARKET, 10);
            case SMITHY -> java.util.Map.of(HEADQUARTERS, 5, BARRACKS, 1);
            case MARKET -> java.util.Map.of(HEADQUARTERS, 3, WAREHOUSE, 2);
            default -> java.util.Map.of();
        };
    }

    public long buildTimeSeconds(int targetLevel, int headquartersLevel) {
        return buildTimeSeconds(targetLevel, headquartersLevel, 1.0);
    }

    // Divided by the world speed; never below 1 second.
    public long buildTimeSeconds(int targetLevel, int headquartersLevel, double worldSpeed) {
        return buildTimeSeconds(targetLevel, headquartersLevel, worldSpeed, HQ_TIME_FACTOR);
    }

    // hqTimeFactor lets a world override the default 1.05 with its own "buildMainFactor".
    public long buildTimeSeconds(int targetLevel, int headquartersLevel, double worldSpeed, double hqTimeFactor) {
        double raw = baseBuildTimeSeconds * Math.pow(BUILD_TIME_FACTOR, targetLevel - 1);
        // Each Headquarters level shortens construction by hqTimeFactor (compounding), like the original world config.
        return Math.max(worldSpeed > 1 ? 1 : 5, Math.round(raw / Math.pow(hqTimeFactor, headquartersLevel) / worldSpeed));
    }

    public double productionPerHour(int level) {
        if (this != TIMBER_CAMP && this != CLAY_PIT && this != IRON_MINE) {
            return 0;
        }
        if (level <= 0) {
            return 5;
        }
        return 30 * Math.pow(1.1631180425543, level - 1);
    }

    // From the original's English language file.
    public String displayName() {
        return switch (this) {
            case HEADQUARTERS -> "Headquarters";
            case TIMBER_CAMP -> "Timber camp";
            case CLAY_PIT -> "Clay pit";
            case IRON_MINE -> "Iron mine";
            case FARM -> "Farm";
            case WAREHOUSE -> "Warehouse";
            case BARRACKS -> "Barracks";
            case WALL -> "Wall";
            case STABLE -> "Stable";
            case WORKSHOP -> "Workshop";
            case ACADEMY -> "Academy";
            case SMITHY -> "Smithy";
            case RALLY_POINT -> "Rally point";
            case STATUE -> "Statue";
            case MARKET -> "Market";
            case HIDING_PLACE -> "Hiding place";
        };
    }

    // buildings.json hide: 150 * 1.3335^(level-1).
    public static int hidingCapacity(int level) {
        return level <= 0 ? 0 : (int) Math.round(150 * Math.pow(1.333500530983, level - 1));
    }

    // Original world config "startLevel" for these.
    public int startLevel() {
        return (this == RALLY_POINT || this == HIDING_PLACE) ? 1 : 0;
    }

    // base * factor^(level-1), the original world config's "points" curve (buildings.json).
    // A village's points are the sum over its buildings.
    public int points(int level) {
        if (level <= 0) return 0;
        double base;
        double factor;
        switch (this) {
            case HEADQUARTERS, MARKET -> { base = 10; factor = 1.1999971560929; }
            case BARRACKS -> { base = 16; factor = 1.2000019829319; }
            case STABLE -> { base = 20; factor = 1.2000039538005; }
            case WORKSHOP -> { base = 24; factor = 1.1999609284227; }
            case ACADEMY -> { base = 512; factor = 1.1997721137783; }
            case SMITHY -> { base = 19; factor = 1.1999987515464; }
            case RALLY_POINT -> { return 0; }
            case STATUE -> { base = 24; factor = 1.25; }
            case TIMBER_CAMP, CLAY_PIT, IRON_MINE, WAREHOUSE -> { base = 6; factor = 1.2000041287667; }
            case FARM -> { base = 5; factor = 1.1999971560929; }
            case HIDING_PLACE -> { base = 5; factor = 1.201035728641; }
            case WALL -> { base = 8; factor = 1.2001027195781; }
            default -> { base = 0; factor = 1; }
        }
        return (int) Math.round(base * Math.pow(factor, level - 1));
    }

    public static int warehouseCapacity(int level) {
        return (int) Math.round(1000 * Math.pow(1.2294934136946, Math.max(level, 1) - 1));
    }

    public static int farmCapacity(int level) {
        return (int) Math.round(240 * Math.pow(1.1721022975335, Math.max(level, 1) - 1));
    }
}
