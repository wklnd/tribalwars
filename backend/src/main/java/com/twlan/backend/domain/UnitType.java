package com.twlan.backend.domain;

import java.util.Map;
import java.util.function.ToIntFunction;

// Static combat/train config per unit, lifted from the original game's own world config
// (htdocs/config/worlds/world/units.json in the reference bundle): attack, the three defence values
// (against infantry/cavalry/archers), speed, haul, cost, recruit building and building requirements.
// The database stores these as ENUM columns: only ever APPEND constants (see SchemaMigration).
public enum UnitType {
    //                          atk  def:gen cav arch  min/fld haul pop wood clay iron  seconds  category            recruited in            requires
    SPEAR(                       10,  15,  45,  20,  18,  25, 1,  50,  30,  10, 1020, Category.INFANTRY, BuildingType.BARRACKS, Map.of()),
    SWORD(                       25,  50,  15,  40,  22,  15, 1,  30,  30,  70, 1500, Category.INFANTRY, BuildingType.BARRACKS, Map.of(BuildingType.SMITHY, 1)),
    AXE(                         40,  10,   5,  10,  18,  10, 1,  60,  30,  40, 1320, Category.INFANTRY, BuildingType.BARRACKS, Map.of(BuildingType.SMITHY, 2)),
    ARCHER(                      15,  50,  40,   5,  18,  10, 1, 100,  30,  60, 1800, Category.ARCHER,   BuildingType.BARRACKS, Map.of(BuildingType.SMITHY, 5, BuildingType.BARRACKS, 5)),
    SCOUT(                        0,   2,   1,   2,   9,   0, 2,  50,  50,  20,  900, Category.INFANTRY, BuildingType.STABLE,   Map.of(BuildingType.STABLE, 1)),
    // The paladin is trained at the Statue, not the Barracks, and each player may have only one.
    PALADIN(                    150, 250, 400, 150,  10, 100, 10,  20,  20,  20, 21600, Category.INFANTRY, BuildingType.STATUE,   Map.of(BuildingType.STATUE, 1)),
    // The nobleman is educated at the Academy (needs gold coins, see NobleService) and conquers villages.
    SNOB(                        30, 100,  50, 100,  35,   0, 100, 40000, 50000, 50000, 18000, Category.INFANTRY, BuildingType.ACADEMY, Map.of(BuildingType.ACADEMY, 1)),
    LIGHT(                      130,  30,  40,  30,  10,  80, 4, 125, 100, 250, 1800, Category.CAVALRY,  BuildingType.STABLE,   Map.of(BuildingType.STABLE, 3)),
    MARCHER(                    120,  40,  30,  50,  10,  50, 5, 250, 100, 150, 2700, Category.ARCHER,   BuildingType.STABLE,   Map.of(BuildingType.STABLE, 5)),
    HEAVY(                      150, 200,  80, 180,  11,  50, 6, 200, 150, 600, 3600, Category.CAVALRY,  BuildingType.STABLE,   Map.of(BuildingType.STABLE, 10, BuildingType.SMITHY, 15)),
    RAM(                          2,  20,  50,  20,  30,   0, 5, 300, 200, 200, 4800, Category.INFANTRY, BuildingType.WORKSHOP, Map.of(BuildingType.WORKSHOP, 1)),
    CATAPULT(                   100, 100,  50, 100,  30,   0, 8, 320, 400, 100, 7200, Category.INFANTRY, BuildingType.WORKSHOP, Map.of(BuildingType.WORKSHOP, 2, BuildingType.SMITHY, 12));

    // What an attacking unit counts as when the defence is worked out (units.json "type").
    public enum Category { INFANTRY, CAVALRY, ARCHER }

    public String displayName() {
        return switch (this) {
            case SPEAR -> "Spear fighter";
            case SWORD -> "Swordsman";
            case AXE -> "Axeman";
            case ARCHER -> "Archer";
            case SCOUT -> "Scout";
            case PALADIN -> "Paladin";
            case SNOB -> "Nobleman";
            case LIGHT -> "Light cavalry";
            case MARCHER -> "Mounted archer";
            case HEAVY -> "Heavy cavalry";
            case RAM -> "Ram";
            case CATAPULT -> "Catapult";
        };
    }

    public final int attack;
    // Defence against infantry attackers; see defenseCavalry/defenseArcher for the other two.
    public final int defense;
    public final int defenseCavalry;
    public final int defenseArcher;
    public final int speedMinutesPerField;
    public final int carryCapacity;
    public final int popCost;
    public final int woodCost;
    public final int clayCost;
    public final int ironCost;
    public final int buildTimeSeconds;
    public final Category category;
    // Each recruit building has its own queue.
    public final BuildingType recruitBuilding;
    private final Map<BuildingType, Integer> requirements;

    UnitType(int attack, int defense, int defenseCavalry, int defenseArcher, int speedMinutesPerField, int carryCapacity,
             int popCost, int woodCost, int clayCost, int ironCost, int buildTimeSeconds, Category category,
             BuildingType recruitBuilding, Map<BuildingType, Integer> requirements) {
        this.attack = attack;
        this.defense = defense;
        this.defenseCavalry = defenseCavalry;
        this.defenseArcher = defenseArcher;
        this.speedMinutesPerField = speedMinutesPerField;
        this.carryCapacity = carryCapacity;
        this.popCost = popCost;
        this.woodCost = woodCost;
        this.clayCost = clayCost;
        this.ironCost = ironCost;
        this.buildTimeSeconds = buildTimeSeconds;
        this.category = category;
        this.recruitBuilding = recruitBuilding;
        this.requirements = requirements;
    }

    public Map<BuildingType, Integer> requirements() { return requirements; }

    public boolean requirementsMet(ToIntFunction<BuildingType> levelOf) {
        for (var req : requirements.entrySet()) {
            if (levelOf.applyAsInt(req.getKey()) < req.getValue()) return false;
        }
        return true;
    }

    // The paladin and the nobleman have their own recruiting rules, not a regular queue.
    public boolean isRegularTroop() {
        return this != PALADIN && this != SNOB;
    }
}
