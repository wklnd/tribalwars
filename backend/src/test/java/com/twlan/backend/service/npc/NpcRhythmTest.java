package com.twlan.backend.service.npc;

import com.twlan.backend.domain.World;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcRhythmTest {

    private static World world(double speed) {
        World w = new World();
        w.setSpeed(speed);
        return w;
    }

    @Test
    void fewNightOwlsAndMoreAmongTheDedicated() {
        int casual = 0, top = 0, all = 0;
        for (long id = 1; id <= 2000; id++) {
            if (NpcRhythm.habitsOf(id, 0.4, 0.3).nightOwl()) casual++;
            if (NpcRhythm.habitsOf(id, 0.9, 0.95).nightOwl()) top++;
            if (NpcRhythm.habitsOf(id, 0.25 + (id % 65) / 100.0, (id % 100) / 100.0).nightOwl()) all++;
        }
        assertTrue(casual < 2000 * 0.08, "casual players rarely play at night: " + casual);
        assertTrue(top > casual * 3, "top players do it much more often: " + top + " vs " + casual);
        assertTrue(all > 2000 * 0.02 && all < 2000 * 0.10, "a few overall: " + all);
    }

    @Test
    void mostSleepAtThreeAndAlmostNobodyAtTwo() {
        int asleepAt3 = 0, asleepAt14 = 0, n = 1000;
        for (long id = 1; id <= n; id++) {
            var h = NpcRhythm.habitsOf(id, 0.5, 0.5);
            if (NpcRhythm.asleep(h, id, 22, LocalDateTime.of(2026, 9, 21, 3, 0))) asleepAt3++;
            if (NpcRhythm.asleep(h, id, 22, LocalDateTime.of(2026, 9, 21, 14, 0))) asleepAt14++;
        }
        assertTrue(asleepAt3 > n * 0.85, "asleep at 03:00: " + asleepAt3);
        assertTrue(asleepAt14 < n * 0.06, "asleep at 14:00: " + asleepAt14);
    }

    @Test
    void sleepIsOneBlockPerDayAcrossMidnight() {
        for (long id = 1; id <= 200; id++) {
            var h = NpcRhythm.habitsOf(id, 0.5, 0.5);
            boolean last = NpcRhythm.asleep(h, id, 22, LocalDateTime.of(2026, 9, 20, 0, 0));
            int changes = 0;
            for (int m = 10; m <= 72 * 60; m += 10) {
                boolean now = NpcRhythm.asleep(h, id, 22, LocalDateTime.of(2026, 9, 20, 0, 0).plusMinutes(m));
                if (now != last) changes++;
                last = now;
            }
            assertTrue(changes <= 3 * 2 + 1, "account " + id + " flips " + changes + " times in 3 days");
        }
    }

    @Test
    void paceGrowsWithWorldSpeedButIsCapped() {
        assertEquals(1, NpcRhythm.pace(world(1)), 1e-9);
        assertEquals(Math.sqrt(500), NpcRhythm.pace(world(500)), 1e-9);
        assertEquals(30, NpcRhythm.pace(world(1000)), 1e-9);
        assertEquals(1, NpcRhythm.pace(null), 1e-9);
    }

    @Test
    void playsInSessionsAndNotWhileAsleep() {
        NpcRhythm rhythm = new NpcRhythm();
        World w = world(1);
        long id = 7;
        LocalDateTime clock = LocalDateTime.of(2026, 9, 21, 8, 0);
        long t0 = 1_000_000_000L;
        int logins = 0, onlineTicks = 0, ticks = 0, actionsWhileAsleep = 0;
        boolean wasActive = false;
        for (int tick = 0; tick < 3 * 24 * 720; tick++) { // 3 days of 5 s ticks
            long now = t0 + tick * 5000L;
            LocalDateTime c = clock.plusSeconds(tick * 5L);
            var mood = rhythm.check(id, w, 0.5, () -> 0.5, List.of(1L), now, c);
            ticks++;
            boolean active = mood == NpcRhythm.Mood.LOGIN || mood == NpcRhythm.Mood.ONLINE;
            if (mood == NpcRhythm.Mood.LOGIN) logins++;
            if (active) onlineTicks++;
            var h = NpcRhythm.habitsOf(id, 0.5, 0.5);
            if (active && NpcRhythm.asleep(h, id, 22, c)) actionsWhileAsleep++;
            if (mood == NpcRhythm.Mood.LOGIN) rhythm.noteDue(id, 1L, now + 40 * 60_000L); // it queued something that takes 40 minutes
            wasActive = active;
        }
        assertEquals(0, actionsWhileAsleep);
        assertTrue(logins >= 3 * 6 && logins <= 3 * 40, "sessions per day " + logins / 3.0);
        double online = (double) onlineTicks / ticks;
        assertTrue(online > 0.02 && online < 0.30, "share of the time online: " + online);
    }

    @Test
    void offlineNpcLogsInSoonAfterItsOrderIsDone() {
        NpcRhythm rhythm = new NpcRhythm();
        World w = world(1);
        LocalDateTime clock = LocalDateTime.of(2026, 9, 21, 13, 0);
        long id = 1;
        while (true) { // somebody who is awake all afternoon and evening
            var h = NpcRhythm.habitsOf(id, 0.9, 0.9);
            boolean awake = true;
            for (int m = 0; m <= 9 * 60; m += 15) awake &= !NpcRhythm.asleep(h, id, 22, clock.plusMinutes(m));
            if (awake) break;
            id++;
        }
        long t0 = 1_000_000_000L;
        long loginAt = -1, nextLoginAt = -1;
        for (int tick = 0; tick < 24 * 720 && nextLoginAt < 0; tick++) {
            long now = t0 + tick * 5000L;
            var mood = rhythm.check(id, w, 0.9, () -> 0.9, List.of(1L), now, clock.plusSeconds(tick * 5L));
            if (mood == NpcRhythm.Mood.LOGIN) {
                if (loginAt < 0) {
                    loginAt = now;
                    rhythm.noteDue(id, 1L, now + 30 * 60_000L); // a 30 minute building
                } else nextLoginAt = now;
            }
        }
        assertTrue(loginAt >= 0 && nextLoginAt > loginAt);
        long minutes = (nextLoginAt - loginAt) / 60_000;
        assertTrue(minutes >= 30 && minutes <= 30 + 35, "logged in again after " + minutes + " min (order done after 30)");
    }

    @Test
    void offSwitchKeepsThemOnline() {
        NpcRhythm rhythm = new NpcRhythm();
        World w = world(1);
        w.getSettings().put("npcRhythm", "false");
        assertEquals(NpcRhythm.Mood.ONLINE, rhythm.check(1, w, 0.5, () -> 0.5, List.of(1L), 0, LocalDateTime.of(2026, 9, 21, 3, 0)));
        assertTrue(rhythm.isOnline(1, w));
    }
}
