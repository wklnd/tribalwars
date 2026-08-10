package com.twlan.backend.service.npc;

import com.twlan.backend.domain.World;
import com.twlan.backend.service.WorldSettings;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// How well NPC players play in a world: one engine, the numbers per preset (world setting `npcDifficulty`). Presets
// change skill (noise on intel, mistakes, how fast they notice and react), never hand the NPCs free resources.
// The old setting `npcAggression` is still honoured for worlds that stored it (off/low/normal).
public record NpcDifficulty(
        String name,
        // multiplier of the chance that a village acts on an NPC tick
        double actionRate,
        // 0..1: how often the NPC takes the best option instead of a random one
        double skill,
        // keeps every build/recruit queue busy and saves for its goals
        boolean keepBusy,
        // sends scouts before attacking players and NPCs
        boolean scouts,
        // relative error on what it thinks a target has (0.2 = +-20 %)
        double intelNoise,
        // base chance per village step to send an attack (0 = never attacks)
        double raidChance,
        // how much it likes real players as targets (barbarians 3, NPCs 1)
        double humanWeight,
        // chance to skip a real player when picking a target
        double humanSkip,
        boolean farming,
        boolean retaliation,
        // several villages hit together (reserved: not used yet, NPCs mostly have one village)
        boolean coordinated,
        // chance per tick to notice an incoming attack
        double notice,
        boolean dodge,
        // chance to send or ask for support when a tribe-mate is threatened
        double supportChance,
        Expansion expansion,
        // largest share of the offensive troops it sends out at once
        double commit) {

    // What the NPC may conquer with noblemen.
    public enum Expansion {
        NONE, BARBARIANS, NPCS, HUMANS, HUMANS_LAST;

        public boolean allows(boolean barbarian, boolean human, boolean lastVillageOfHuman) {
            if (barbarian) return this != NONE;
            if (!human) return ordinal() >= NPCS.ordinal();
            return lastVillageOfHuman ? this == HUMANS_LAST : ordinal() >= HUMANS.ordinal();
        }
    }

    public static final NpcDifficulty OFF = new NpcDifficulty("off", 0.6, 0.35, false, false, 0.40, 0, 0, 0, false, false, false, 0, false, 0, Expansion.NONE, 1.0);
    public static final NpcDifficulty PASSIVE = new NpcDifficulty("passive", 0.6, 0.35, false, false, 0.40, 0.004, 0.6, 0.5, false, false, false, 0, false, 0, Expansion.NONE, 1.0);
    public static final NpcDifficulty NORMAL = new NpcDifficulty("normal", 1.0, 0.60, false, true, 0.20, 0.012, 1.5, 0, false, false, false, 0.4, true, 0.15, Expansion.BARBARIANS, 0.9);
    public static final NpcDifficulty HARD = new NpcDifficulty("hard", 1.6, 0.85, true, true, 0.10, 0.020, 1.5, 0, true, true, false, 0.8, true, 0.6, Expansion.HUMANS, 0.75);
    public static final NpcDifficulty BRUTAL = new NpcDifficulty("brutal", 2.5, 1.00, true, true, 0.03, 0.035, 2.0, 0, true, true, true, 1.0, true, 1.0, Expansion.HUMANS_LAST, 0.6);

    private static final Map<String, NpcDifficulty> BY_NAME = Map.of("off", OFF, "passive", PASSIVE, "normal", NORMAL, "hard", HARD, "brutal", BRUTAL);

    public static List<String> names() { return List.of("off", "passive", "normal", "hard", "brutal"); }

    public static NpcDifficulty named(String name) {
        return BY_NAME.getOrDefault(name == null ? "" : name.trim().toLowerCase(Locale.ROOT), NORMAL);
    }

    // The old `npcAggression` values mapped onto presets.
    public static String legacy(String npcAggression) {
        return switch (npcAggression == null ? "" : npcAggression) {
            case "off" -> "off";
            case "low" -> "passive";
            default -> "normal";
        };
    }

    // The difficulty of a world: its own `npcDifficulty`, else its stored old `npcAggression`, else the default.
    public static NpcDifficulty of(World world) {
        if (world == null) return NORMAL;
        String own = world.getSettings().get("npcDifficulty");
        if (own == null && world.getSettings().get("npcAggression") != null) own = legacy(world.getSettings().get("npcAggression"));
        if (own == null) own = WorldSettings.get(world, "npcDifficulty");
        return named(own);
    }

    public boolean attacks() { return raidChance > 0; }

    // More actions per tick on fast worlds (their orders finish sooner), but never more than double.
    public static double speedFactor(World world) {
        double speed = world == null || world.getSpeed() <= 0 ? 1 : world.getSpeed();
        return Math.min(2, Math.max(1, Math.pow(speed, 0.2)));
    }

    // What this world lets the NPCs conquer: the preset, overridden by the world setting `npcConquest`.
    public Expansion expansionFor(World world) {
        if (!attacks()) return Expansion.NONE;
        String rule = world == null ? "auto" : WorldSettings.get(world, "npcConquest");
        return resolveExpansion(expansion, rule);
    }

    static Expansion resolveExpansion(Expansion preset, String rule) {
        return switch (rule == null ? "auto" : rule) {
            case "never" -> preset.ordinal() > Expansion.NPCS.ordinal() ? Expansion.NPCS : preset;
            case "not-last" -> Expansion.HUMANS;
            case "anything" -> Expansion.HUMANS_LAST;
            default -> preset;
        };
    }
}
