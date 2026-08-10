package com.twlan.backend.service;

import com.twlan.backend.domain.BuildingType;
import com.twlan.backend.domain.World;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NightAndAchievementTest {

    private static World world(String start, String end) {
        World w = new World();
        w.getSettings().put("nightStart", start);
        w.getSettings().put("nightEnd", end);
        return w;
    }

    @Test
    void nightWrapsPastMidnight() {
        World w = world("22:00", "08:00");
        assertTrue(WorldSettings.isNight(w, LocalTime.of(23, 30)));
        assertTrue(WorldSettings.isNight(w, LocalTime.of(3, 0)));
        assertFalse(WorldSettings.isNight(w, LocalTime.of(8, 0)));
        assertFalse(WorldSettings.isNight(w, LocalTime.of(12, 0)));
    }

    @Test
    void buildingPointsFollowTheOriginalCurve() {
        assertEquals(10, BuildingType.HEADQUARTERS.points(1));
        assertEquals(0, BuildingType.HEADQUARTERS.points(0));
        assertEquals(0, BuildingType.RALLY_POINT.points(1));
        assertEquals(1975, BuildingType.HEADQUARTERS.points(30), 30);
    }

    @Test
    void achievementLevels() {
        var warlord = AchievementCatalog.all().stream().filter(d -> d.key().equals("warlord")).findFirst().orElseThrow();
        assertEquals(0, AchievementService.levelFor(warlord, 9L));
        assertEquals(1, AchievementService.levelFor(warlord, 10L));
        assertEquals(3, AchievementService.levelFor(warlord, 100L));
        var top = AchievementCatalog.all().stream().filter(d -> d.key().equals("top_scorer")).findFirst().orElseThrow();
        assertEquals(0, AchievementService.levelFor(top, 5000L));   // not in the top 1000
        assertEquals(3, AchievementService.levelFor(top, 7L));      // top 20
        assertEquals(4, AchievementService.levelFor(top, 1L));      // first
        var vanquisher = AchievementCatalog.all().stream().filter(d -> d.key().equals("vanquisher")).findFirst().orElseThrow();
        assertEquals(0, AchievementService.valueOf(vanquisher, Map.of("won_attacker_day", 1L)));
        assertEquals(1, AchievementService.valueOf(vanquisher, Map.of("won_attacker_day", 2L)));
    }
}
