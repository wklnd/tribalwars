package se.oscarwiklund.twlan2.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Villages are placed like on the original's world map: a filled disc, about 29 % of its fields occupied.
class WorldPlacementTest {

    @Test
    void discRadiusMatchesTheOriginalsMap() {
        // the original: 19298 villages fill a disc of radius ~146
        assertEquals(146, WorldService.autoRadius(19298), 3);
        assertEquals(8, WorldService.autoRadius(0));
    }

    @Test
    void fiveHundredVillagesFormADenseDisc() {
        Set<String> taken = new HashSet<>();
        Random rnd = new Random(42);
        double farthest = 0;
        for (int i = 0; i < 500; i++) {
            int[] s = WorldService.randomFreeSpot(taken, rnd);
            farthest = Math.max(farthest, Math.hypot(s[0] - 500, s[1] - 500));
        }
        assertEquals(500, taken.size());
        assertTrue(farthest < 32, "500 villages must stay within ~30 fields of the centre, farthest was " + farthest);
    }

    @Test
    void mapDensityScalesTheRadiusProportionally() {
        // world "mapDensity" default 2.5 must reproduce today's DENSITY-only radius exactly
        assertEquals(WorldService.autoRadius(500), WorldService.autoRadius(500, 0.28));
        // doubling the density halves the radius^2, i.e. shrinks the disc
        assertTrue(WorldService.autoRadius(500, 0.56) < WorldService.autoRadius(500, 0.28));
    }

    @Test
    void spotsNearAPlayerStayCloseAndFree() {
        Set<String> taken = new HashSet<>(Set.of("500|500"));
        Random rnd = new Random(1);
        for (int i = 0; i < 50; i++) {
            int[] s = WorldService.freeSpotNear(taken, rnd, 500, 500);
            assertTrue(Math.max(Math.abs(s[0] - 500), Math.abs(s[1] - 500)) >= 2);
        }
        assertEquals(51, taken.size());
    }
}
