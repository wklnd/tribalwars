package com.twlan.backend.service.npc;

import com.twlan.backend.domain.UnitType;

import java.util.EnumMap;
import java.util.Map;

// Every won attack that arrives with a nobleman takes a random 20-35 (world setting) loyalty off the village, and noblemen
// only die in the battle itself, so a conquest is a chain of separate attacks that land one after another. Pure (no Spring, no database).
public final class ConquestPlan {

    private ConquestPlan() {}

    // One nobleman-carrying attack, sent `count` times.
    public record Waves(int count, Map<UnitType, Integer> escortEach) {}

    // How many noblemen-attacks a village at `loyalty` needs on average (rounded up); `safe` plans for the smallest drops instead.
    public static int wavesNeeded(double loyalty, int minDecrease, int maxDecrease, boolean safe) {
        double per = safe ? Math.max(1, minDecrease) : Math.max(1, (minDecrease + Math.max(minDecrease, maxDecrease)) / 2.0);
        return Math.max(1, (int) Math.ceil(loyalty / per));
    }

    // Splits the army into `needed` escorts, each strong enough to win alone against `seen` (the noblemen die with a lost attack).
    // Null if the noblemen or the army do not suffice.
    public static Waves waves(int noblemen, int needed, Map<UnitType, Integer> army, Map<UnitType, Integer> seen, int wall,
                              double basicDefense, double night, double morale, double margin) {
        if (needed < 1 || noblemen < needed) return null;
        var plan = AttackPlanner.plan(army, seen, wall, basicDefense, night, morale, margin, 0, 0.5, false);
        if (plan == null) return null;
        Map<UnitType, Integer> each = new EnumMap<>(UnitType.class);
        for (var e : plan.send().entrySet()) {
            each.put(e.getKey(), e.getValue());
            if ((long) e.getValue() * needed > army.getOrDefault(e.getKey(), 0)) return null;
        }
        return new Waves(needed, each);
    }
}
