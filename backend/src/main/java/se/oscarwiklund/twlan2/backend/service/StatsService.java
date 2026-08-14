package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

// Statistics: every few minutes each player's points, rank, villages and kills are compared with the last record and
// a new StatSnapshot is stored when anything changed. The statistics page draws them as graphs.
@Service
public class StatsService {

    public record Point(long t, int points, int rank, int villages, long kills) {}
    public record Stats(String name, long from, long now, Point current, Point change, List<Point> series) {}

    private static final Duration KEEP = Duration.ofDays(120);

    private final WorldPoints worldPoints;
    private final StatSnapshotRepository snapshots;
    private final WorldRepository worlds;
    private final VillageRepository villages;
    private final BuildingRepository buildings;
    private final AchievementCounterRepository counters;
    private final AccountRepository accounts;

    public StatsService(WorldPoints worldPoints, StatSnapshotRepository snapshots, WorldRepository worlds, VillageRepository villages,
                        BuildingRepository buildings, AchievementCounterRepository counters, AccountRepository accounts) {
        this.worldPoints = worldPoints;
        this.snapshots = snapshots;
        this.worlds = worlds;
        this.villages = villages;
        this.buildings = buildings;
        this.counters = counters;
        this.accounts = accounts;
    }

    @Scheduled(fixedDelayString = "${game.stats-snapshot-ms:300000}", initialDelay = 20000)
    @Transactional
    public void snapshotAll() {
        for (World w : worlds.findAll()) snapshot(w);
        snapshots.deleteByTakenAtBefore(Instant.now().minus(KEEP));
    }

    void snapshot(World world) {
        Map<Long, Integer> villagePoints = worldPoints.byVillage(world);
        Map<Long, Integer> points = new HashMap<>();
        Map<Long, Integer> count = new HashMap<>();
        for (Village v : villages.findByWorld(world)) {
            if (v.getOwner() == null || v.getOwnerType() != OwnerType.PLAYER) continue;
            points.merge(v.getOwner().getId(), villagePoints.getOrDefault(v.getId(), 0), Integer::sum);
            count.merge(v.getOwner().getId(), 1, Integer::sum);
        }
        Map<Long, Long> kills = new HashMap<>();
        for (AchievementCounter c : counters.findByWorldIdAndCounterKey(world.getId(), "kills")) kills.put(c.getAccountId(), c.getValue());
        for (var e : points.entrySet()) {
            int mine = e.getValue();
            int rank = 1 + (int) points.values().stream().filter(p -> p > mine).count();
            StatSnapshot last = snapshots.findFirstByAccountIdAndWorldIdOrderByTakenAtDesc(e.getKey(), world.getId()).orElse(null);
            long k = kills.getOrDefault(e.getKey(), 0L);
            if (last != null && last.getPoints() == mine && last.getRankPos() == rank && last.getVillageCount() == count.get(e.getKey()) && last.getKills() == k) continue;
            StatSnapshot s = new StatSnapshot();
            s.setAccountId(e.getKey());
            s.setWorldId(world.getId());
            s.setPoints(mine);
            s.setRankPos(rank);
            s.setVillageCount(count.get(e.getKey()));
            s.setKills(k);
            snapshots.save(s);
        }
    }

    // The record in force at "from" leads the series.
    @Transactional(readOnly = true)
    public Stats stats(String name, World world, Instant from) {
        Account a = accounts.findByUsernameLower(name == null ? "" : name.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        Instant now = Instant.now();
        List<StatSnapshot> rows = new ArrayList<>();
        snapshots.findFirstByAccountIdAndWorldIdAndTakenAtLessThanEqualOrderByTakenAtDesc(a.getId(), world.getId(), from).ifPresent(rows::add);
        rows.addAll(snapshots.findByAccountIdAndWorldIdAndTakenAtAfterOrderByTakenAtAsc(a.getId(), world.getId(), from));
        if (rows.isEmpty()) throw new IllegalArgumentException("No statistics recorded for this player yet.");
        List<Point> series = new ArrayList<>();
        for (StatSnapshot s : rows) {
            long t = Math.max(from.toEpochMilli(), s.getTakenAt().toEpochMilli());
            series.add(new Point(t, s.getPoints(), s.getRankPos(), s.getVillageCount(), s.getKills()));
        }
        Point first = series.get(0);
        Point last = series.get(series.size() - 1);
        Point change = new Point(0, last.points() - first.points(), last.rank() - first.rank(), last.villages() - first.villages(), last.kills() - first.kills());
        return new Stats(a.getUsername(), from.toEpochMilli(), now.toEpochMilli(), last, change, series);
    }

    @Transactional
    public void deleteWorldData(Long worldId) { snapshots.deleteByWorldId(worldId); }

    @Transactional
    public void deleteAccountData(Long accountId) { snapshots.deleteByAccountId(accountId); }
}
