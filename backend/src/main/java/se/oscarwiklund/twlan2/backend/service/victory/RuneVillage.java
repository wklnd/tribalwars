package se.oscarwiklund.twlan2.backend.service.victory;

import java.util.Random;

// Whether a freshly generated barbarian village is rolled as a rune village, and how strong to make it.
public final class RuneVillage {
    private RuneVillage() {}

    public static boolean roll(Random rnd, RuneParams params) {
        return params != null && rnd.nextDouble() < params.spawnShare();
    }

    // Deliberately near-maxed, like a "high level barbarian village" should be.
    public static double development(Random rnd) {
        return 0.9 + rnd.nextDouble() * 0.1;
    }
}
