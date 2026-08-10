package com.twlan.backend.service.npc;

import com.twlan.backend.domain.World;
import com.twlan.backend.service.WorldSettings;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

// When an NPC "is at the computer", so that NPCs keep a real player's hours instead of acting in every 5-second tick:
// - Sleep: most sleep 6-9 hours around the world's night (bedtime drifts a little from day to day). A few - more
//   often the dedicated top players - are night owls who sleep only 3-5 hours at odd times.
// - Sessions: awake, an NPC is offline most of the time. It logs in when something it queued is done (a building,
//   a recruit batch, troops home from a raid) after a reaction time, or just to look in every now and then, plays for a few
//   minutes and logs off. Only while online it builds, recruits, attacks, notices incoming attacks, answers tribe
//   invitations and trades.
// All habits are stable per account (drawn from the account id, the skill and how active the personality is). Waits shrink with
// the world's speed (its square root, at most 30x, because on a fast world everything finishes in seconds) and session lengths
// with the square root of that, so on a fast world NPCs are online a larger share of the time.
// The state lives in memory only: after a restart everybody is somewhere in their day again. World setting
// `npcRhythm` = off gives the old always-on behaviour.
@Component
public class NpcRhythm {

    public enum Mood { ASLEEP, OFFLINE, LOGIN, ONLINE }

    // Minutes are real minutes on a world of speed 1.
    public record Habits(boolean nightOwl, double bedtimeShiftH, double sleepHours, double gapMin, double sessionMin, double reactionMin) {}

    private static final class State {
        long onlineUntil;
        long nextVisit;
        boolean asleep;
        boolean sessionOpen;
        final Map<Long, Long> due = new HashMap<>(); // village id -> when something it queued is done (ms)
    }

    private final Map<Long, State> states = new ConcurrentHashMap<>();
    private final Map<Long, Habits> habits = new ConcurrentHashMap<>();
    private final Random rnd = new Random();

    // ---- pure parts (tested) ---------------------------------------------------------------------------------------------

    // `activity` and `skill` both 0..1: the more dedicated, the shorter the breaks and the likelier a night owl.
    static Habits habitsOf(long accountId, double activity, double skill) {
        Random r = new Random(accountId * 2654435761L + 97);
        double dedication = Math.max(0, Math.min(1, 0.5 * skill + 0.5 * activity));
        boolean owl = r.nextDouble() < 0.01 + 0.22 * dedication * dedication * dedication;
        double shift = owl ? r.nextDouble() * 24 : Math.max(-2, Math.min(3, 0.5 + r.nextGaussian()));
        double sleep = owl ? 3 + 2 * r.nextDouble() : 6 + 3 * r.nextDouble();
        double gap = (40 + 160 * (1 - dedication)) * (0.7 + 0.6 * r.nextDouble()) * (owl ? 0.6 : 1);
        double session = (3 + 9 * r.nextDouble()) * (0.8 + 0.6 * dedication);
        double reaction = 2 + 30 * (1 - dedication) * r.nextDouble();
        return new Habits(owl, shift, sleep, gap, session, reaction);
    }

    // Bedtime (hour of the day, 0..24) on the given day: the world's night start plus the habit, with up to half an hour of daily drift.
    static double bedtime(Habits h, long accountId, double nightStartHour, long epochDay) {
        double drift = (new Random(accountId * 31 + epochDay).nextDouble() - 0.5);
        double b = (nightStartHour + h.bedtimeShiftH() + drift) % 24;
        return b < 0 ? b + 24 : b;
    }

    static boolean asleep(Habits h, long accountId, double nightStartHour, LocalDateTime now) {
        double t = now.toLocalTime().toSecondOfDay() / 3600.0;
        long day = now.toLocalDate().toEpochDay();
        for (int d = -1; d <= 0; d++) { // tonight's sleep and the one that began yesterday evening and runs into this morning
            double start = d * 24 + bedtime(h, accountId, nightStartHour, day + d);
            if (t >= start && t < start + h.sleepHours()) return true;
        }
        return false;
    }

    // How much faster things go on this world (everything an NPC waits for is over sooner).
    static double pace(World world) {
        double speed = world == null || world.getSpeed() <= 0 ? 1 : world.getSpeed();
        return Math.min(30, Math.max(1, Math.sqrt(speed)));
    }

    private static double nightStartHour(World world) {
        try {
            LocalTime t = LocalTime.parse(WorldSettings.get(world, "nightStart"));
            return t.toSecondOfDay() / 3600.0;
        } catch (RuntimeException e) {
            return 22;
        }
    }

    private static boolean enabled(World world) {
        return world != null && WorldSettings.bool(world, "npcRhythm");
    }

    private static long ms(double minutes, double pace) { return (long) (minutes * 60_000 / pace); }

    // ---- state ----------------------------------------------------------------------------------------------------------------

    // Called once per NPC per NPC tick. LOGIN is the first tick of a session (the NPC does its round of all villages),
    // ONLINE the following ones; ASLEEP / OFFLINE mean it does nothing.
    public Mood check(long accountId, World world, double activity, DoubleSupplier skill, Collection<Long> villageIds) {
        return check(accountId, world, activity, skill, villageIds, System.currentTimeMillis(), LocalDateTime.now());
    }

    // the clock is a parameter for the tests
    Mood check(long accountId, World world, double activity, DoubleSupplier skill, Collection<Long> villageIds, long now, LocalDateTime clock) {
        if (!enabled(world)) return Mood.ONLINE;
        Habits h = habits.computeIfAbsent(accountId, id -> habitsOf(id, activity, skill.getAsDouble()));
        double pace = pace(world);
        State s = states.get(accountId);
        if (s == null) {
            s = new State();
            s.nextVisit = now + (long) (rnd.nextDouble() * ms(h.gapMin(), pace) * 0.5); // spread the first visits out
            states.put(accountId, s);
        }
        if (asleep(h, accountId, nightStartHour(world), clock)) {
            if (s.sessionOpen) { // fell asleep mid-session: log off, plan the next visit
                s.sessionOpen = false;
                s.nextVisit = planNext(h, s, villageIds, now, pace);
            }
            s.onlineUntil = 0;
            s.asleep = true;
            return Mood.ASLEEP;
        }
        if (s.asleep) { // just woke up: has a look soon
            s.asleep = false;
            s.nextVisit = now + reaction(h, pace);
        }
        if (now < s.onlineUntil) return Mood.ONLINE;
        if (s.sessionOpen) {
            s.sessionOpen = false;
            s.nextVisit = planNext(h, s, villageIds, now, pace);
        }
        if (now >= s.nextVisit) {
            s.onlineUntil = now + Math.max(12_000, ms(h.sessionMin() * (0.6 + 0.8 * rnd.nextDouble()), Math.sqrt(pace))); // (sessions shrink less than the breaks: on a fast world a player is online most of the time)
            s.sessionOpen = true;
            return Mood.LOGIN;
        }
        return Mood.OFFLINE;
    }

    private long reaction(Habits h, double pace) {
        return ms(h.reactionMin() * (0.2 + 0.8 * rnd.nextDouble()), pace);
    }

    // The next visit: when the first thing queued is done (plus a reaction time), or after a break if nothing is due before that.
    private long planNext(Habits h, State s, Collection<Long> villageIds, long now, double pace) {
        long due = Long.MAX_VALUE;
        for (Long id : villageIds) {
            Long d = s.due.get(id);
            if (d != null) due = Math.min(due, d);
        }
        long byBreak = now + (long) (ms(h.gapMin(), pace) * (0.6 + 0.8 * rnd.nextDouble()));
        long byDue = due == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(due, now) + reaction(h, pace);
        return Math.max(now + 10_000, Math.min(byBreak, byDue));
    }

    // A village step reports when the first order it has queued (or the troops it has out) will be done.
    public void noteDue(long accountId, long villageId, long dueMs) {
        State s = states.get(accountId);
        if (s == null) return;
        if (dueMs == Long.MAX_VALUE) s.due.remove(villageId);
        else s.due.put(villageId, dueMs);
    }

    // True while the NPC is in a session (only then it notices attacks, trades, ...). Everybody counts as online when the rhythm is off.
    public boolean isOnline(long accountId, World world) {
        if (!enabled(world)) return true;
        State s = states.get(accountId);
        return s != null && System.currentTimeMillis() < s.onlineUntil;
    }

    // False while the NPC sleeps (used for slow social things like tribe invitations). Unknown NPCs count as awake.
    public boolean isAwake(long accountId, World world) {
        if (!enabled(world)) return true;
        Habits h = habits.get(accountId);
        return h == null || !asleep(h, accountId, nightStartHour(world), LocalDateTime.now());
    }

    // For the admin panel, e.g. "sleeps 23:10-06:40, online".
    public String describe(long accountId, World world) {
        if (!enabled(world)) return "always on";
        Habits h = habits.get(accountId);
        if (h == null) return "not seen yet";
        double b = bedtime(h, accountId, nightStartHour(world), LocalDateTime.now().toLocalDate().toEpochDay());
        double e = (b + h.sleepHours()) % 24;
        String status = !isAwake(accountId, world) ? "asleep" : isOnline(accountId, world) ? "online" : "offline";
        return String.format("%s%s %02d:%02d-%02d:%02d, %s", h.nightOwl() ? "night owl, " : "", "sleeps", (int) b, (int) ((b % 1) * 60), (int) e, (int) ((e % 1) * 60), status);
    }
}
