package com.twlan.backend.service.npc;

import com.twlan.backend.domain.UnitType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.twlan.backend.domain.UnitType.*;

// What army an NPC is aiming for and what to recruit next to get there. Pure functions (no Spring, no database):
// the mix is a set of target shares of the army's population, choose() picks the unit type furthest below its
// share among what the village can train, batch() sizes the order.
public final class NpcArmy {

    private NpcArmy() {}

    private static final Map<NpcArchetype, Map<UnitType, Double>> MIX = new EnumMap<>(NpcArchetype.class);

    static {
        MIX.put(NpcArchetype.FARMER, shares(LIGHT, 0.45, AXE, 0.15, SPEAR, 0.15, SWORD, 0.1, SCOUT, 0.1, MARCHER, 0.05));
        MIX.put(NpcArchetype.RAIDER, shares(AXE, 0.35, LIGHT, 0.25, RAM, 0.1, MARCHER, 0.07, SPEAR, 0.08, SWORD, 0.05, SCOUT, 0.08, CATAPULT, 0.02));
        MIX.put(NpcArchetype.TURTLE, shares(SPEAR, 0.33, SWORD, 0.3, ARCHER, 0.15, HEAVY, 0.1, SCOUT, 0.05, AXE, 0.07));
        MIX.put(NpcArchetype.CONQUEROR, shares(AXE, 0.28, LIGHT, 0.2, RAM, 0.1, CATAPULT, 0.05, SPEAR, 0.1, SWORD, 0.1, SCOUT, 0.05, HEAVY, 0.12));
        MIX.put(NpcArchetype.TRADER, shares(SPEAR, 0.3, SWORD, 0.25, AXE, 0.15, LIGHT, 0.1, SCOUT, 0.05, ARCHER, 0.15));
        MIX.put(NpcArchetype.BALANCED, shares(SPEAR, 0.2, SWORD, 0.15, AXE, 0.2, LIGHT, 0.15, ARCHER, 0.05, SCOUT, 0.05, RAM, 0.08, MARCHER, 0.05, HEAVY, 0.05, CATAPULT, 0.02));
    }

    private static Map<UnitType, Double> shares(Object... pairs) {
        Map<UnitType, Double> m = new EnumMap<>(UnitType.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((UnitType) pairs[i], (Double) pairs[i + 1]);
        return m;
    }

    // Target share of the army's population per unit type (the shares add up to 1).
    public static Map<UnitType, Double> mix(NpcArchetype archetype) {
        return MIX.get(archetype);
    }

    // The share of the farm's capacity the standing army should take up.
    public static double armyShare(NpcArchetype archetype, double skill) {
        double base = switch (archetype) {
            case RAIDER -> 0.5;
            case TURTLE -> 0.45;
            case CONQUEROR -> 0.4;
            case BALANCED -> 0.35;
            case FARMER -> 0.3;
            case TRADER -> 0.25;
        };
        return base * (0.7 + 0.3 * Math.max(0, Math.min(1, skill)));
    }

    // Scouts an NPC keeps around: they are what it scouts with, and its defence against enemy scouts.
    public static int scoutFloor(NpcArchetype archetype) {
        return switch (archetype) {
            case FARMER, RAIDER, CONQUEROR -> 12;
            default -> 6;
        };
    }

    // The unit to train next: the scouts while there are too few of them, else the type furthest below its own target among
    // `available`: each type has a target of `share x targetPop` population (the shares of the archetype's mix,
    // renormalised over what can be trained). `popByType` counts units at home, out and queued. A village whose garrison
    // is already full of one type still trains the others. Null if nothing is missing.
    public static UnitType choose(NpcArchetype archetype, Set<UnitType> available, Map<UnitType, Integer> popByType, int scoutsHave,
                                  boolean wantScouts, double targetPop) {
        if (available.isEmpty()) return null;
        if (wantScouts && available.contains(SCOUT) && scoutsHave < scoutFloor(archetype)) return SCOUT;
        Map<UnitType, Double> target = mix(archetype);
        double weightSum = 0;
        for (UnitType t : available) weightSum += target.getOrDefault(t, 0.02);
        UnitType best = null;
        double bestGap = 0;
        for (UnitType t : available) {
            double want = target.getOrDefault(t, 0.02) / weightSum * targetPop;
            double gap = want - popByType.getOrDefault(t, 0); // population this type is short of
            if (gap >= t.popCost && gap > bestGap) { bestGap = gap; best = t; }
        }
        return best;
    }

    // How many of `type` to order: what the budget and the free population allow, but not more than about
    // `maxQueueSeconds` of work, and at least one when a single unit is affordable. 0 if not even one is.
    public static int batch(UnitType type, double wood, double clay, double iron, int freePop, long perUnitSeconds, long maxQueueSeconds) {
        int byResources = (int) Math.min(wood / type.woodCost, Math.min(clay / type.clayCost, iron / type.ironCost));
        int byPop = freePop / type.popCost;
        int byTime = (int) Math.max(1, maxQueueSeconds / Math.max(1, perUnitSeconds));
        return Math.max(0, Math.min(byResources, Math.min(byPop, byTime)));
    }
}
