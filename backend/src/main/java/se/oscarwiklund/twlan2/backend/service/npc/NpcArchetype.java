package se.oscarwiklund.twlan2.backend.service.npc;

import java.util.Locale;

// The role an NPC plays. Stable per NPC (see NpcProfiles); the admin can change it. The archetype shapes what the
// NPC builds, recruits and attacks, how it reacts and whether it expands (later phases read the fields below).
public enum NpcArchetype {
    // Economy first, light cavalry, raids barbarians in a loop, avoids real fights.
    FARMER("Farmer"),
    // Offence early, targets weak players and enemy tribes, retaliates.
    RAIDER("Raider"),
    // Wall and defence, keeps a home reserve, sends and asks support.
    TURTLE("Turtle"),
    // Builds toward the Academy and conquers villages with noblemen.
    CONQUEROR("Conqueror"),
    // Market early, balances resources through offers, big storage.
    TRADER("Trader"),
    // The mix.
    BALANCED("Balanced");

    public final String label;

    // How eager the archetype is to send attacks (1 = average): a multiplier of the difficulty's raid chance.
    public double drive() {
        return switch (this) {
            case RAIDER -> 1.6;
            case FARMER -> 1.3;
            case CONQUEROR -> 1.1;
            case BALANCED -> 1.0;
            case TRADER -> 0.5;
            case TURTLE -> 0.35;
        };
    }

    NpcArchetype(String label) { this.label = label; }

    public static NpcArchetype parse(String name) {
        if (name == null) return BALANCED;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }

    public static boolean isValid(String name) {
        if (name == null) return false;
        try {
            valueOf(name.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
