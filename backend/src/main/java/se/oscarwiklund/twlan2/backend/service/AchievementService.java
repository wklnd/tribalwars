package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Tracks the achievements of AchievementCatalog: game events bump counters, dynamic values (points, ranks,
// years played) are refreshed every half minute, and every level reached is recorded once as an AchievementUnlock.
// Only real players earn achievements, never NPCs.
@Service
public class AchievementService {

    private final LiveUpdates live;
    private final WorldPoints worldPoints;
    private final AchievementCounterRepository counters;
    private final AchievementUnlockRepository unlocks;
    private final DailyStatRepository dailyStats;
    private final AccountRepository accounts;
    private final WorldRepository worlds;
    private final VillageRepository villages;
    private final BuildingRepository buildings;

    public AchievementService(LiveUpdates live, WorldPoints worldPoints, AchievementCounterRepository counters, AchievementUnlockRepository unlocks,
                              DailyStatRepository dailyStats, AccountRepository accounts, WorldRepository worlds,
                              VillageRepository villages, BuildingRepository buildings) {
        this.live = live;
        this.worldPoints = worldPoints;
        this.counters = counters;
        this.unlocks = unlocks;
        this.dailyStats = dailyStats;
        this.accounts = accounts;
        this.worlds = worlds;
        this.villages = villages;
        this.buildings = buildings;
    }

    // ---- recording -----------------------------------------------------------------------------------------

    private static boolean earns(Account a) {
        return a != null && !a.isNpc();
    }

    @Transactional
    public void count(Account account, World world, String key, long amount) {
        if (!earns(account) || world == null || amount == 0) return;
        bump(account, world, key, amount);
        evaluate(account, world);
    }

    private void bump(Account account, World world, String key, long amount) {
        AchievementCounter c = counters.findByAccountIdAndWorldIdAndCounterKey(account.getId(), world.getId(), key).orElseGet(() -> {
            AchievementCounter n = new AchievementCounter();
            n.setAccountId(account.getId());
            n.setWorldId(world.getId());
            n.setCounterKey(key);
            return n;
        });
        c.setValue(c.getValue() + amount);
        counters.save(c);
    }

    private void set(Account account, World world, String key, long value) {
        AchievementCounter c = counters.findByAccountIdAndWorldIdAndCounterKey(account.getId(), world.getId(), key).orElseGet(() -> {
            AchievementCounter n = new AchievementCounter();
            n.setAccountId(account.getId());
            n.setWorldId(world.getId());
            n.setCounterKey(key);
            return n;
        });
        c.setValue(value);
        counters.save(c);
    }

    // defenderOwner is null for barbarian villages; the losses are unit totals.
    @Transactional
    public void onBattle(Account attacker, Account defenderOwner, World world, boolean attackerWon, long defenderUnitsBefore,
                         long defenderLosses, long loot, long noblesKilled) {
        if (!earns(attacker) || world == null) return;
        if (defenderOwner != null && !defenderOwner.getId().equals(attacker.getId())) {
            markOnce(attacker, world, "target:" + defenderOwner.getId());
        }
        if (defenderLosses > 0) bump(attacker, world, "kills", defenderLosses);
        if (noblesKilled > 0) bump(attacker, world, "nobles_killed", noblesKilled);
        if (attackerWon && defenderUnitsBefore > 0) bump(attacker, world, "armies", 1);
        if (loot > 0) {
            bump(attacker, world, "loot", loot);
            bump(attacker, world, "plunders", 1);
        }
        DailyStat day = today(world, attacker);
        day.setKills(day.getKills() + defenderLosses);
        day.setLoot(day.getLoot() + loot);
        if (loot > 0) day.setPlunders(day.getPlunders() + 1);
        dailyStats.save(day);
        evaluate(attacker, world);
    }

    // loyaltyAfter is where the loyalty ended (<= 0 = conquered).
    @Transactional
    public void onNobles(Account attacker, World world, double loyaltyAfter, boolean conquered) {
        if (!earns(attacker) || world == null) return;
        if (conquered && loyaltyAfter == 0) bump(attacker, world, "lucky", 1); // brought down to exactly 0
        if (!conquered && loyaltyAfter > 0 && loyaltyAfter <= 1) bump(attacker, world, "unlucky", 1);
    }

    @Transactional
    public void onConquest(Account attacker, World world) {
        if (!earns(attacker) || world == null) return;
        bump(attacker, world, "conquests", 1);
        DailyStat day = today(world, attacker);
        day.setConquests(day.getConquests() + 1);
        dailyStats.save(day);
        evaluate(attacker, world);
    }

    private void markOnce(Account account, World world, String key) {
        if (counters.findByAccountIdAndWorldIdAndCounterKey(account.getId(), world.getId(), key).isEmpty()) {
            bump(account, world, key, 1);
        }
    }

    private DailyStat today(World world, Account account) {
        long day = LocalDate.now().toEpochDay();
        return dailyStats.findByWorldIdAndEpochDayAndAccountId(world.getId(), day, account.getId()).orElseGet(() -> {
            DailyStat s = new DailyStat();
            s.setWorldId(world.getId());
            s.setEpochDay(day);
            s.setAccountId(account.getId());
            return s;
        });
    }

    // ---- evaluation ----------------------------------------------------------------------------------------

    private Map<String, Long> values(Account account, World world) {
        Map<String, Long> v = new HashMap<>();
        for (AchievementCounter c : counters.findByAccountIdAndWorldId(account.getId(), world.getId())) v.put(c.getCounterKey(), c.getValue());
        v.put("targets", counters.countByAccountIdAndWorldIdAndCounterKeyStartingWith(account.getId(), world.getId(), "target:"));
        long years = account.getCreatedAt() == null ? 0 : ChronoUnit.YEARS.between(
                account.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(), LocalDate.now());
        v.put("years", Math.max(0, years));
        return v;
    }

    // For rank-like values (lowerIsBetter) a smaller number is better.
    static int levelFor(AchievementCatalog.Def d, Long value) {
        if (value == null) return 0;
        int level = 0;
        for (int i = 0; i < d.thresholds().length; i++) {
            boolean reached = d.lowerIsBetter() ? value > 0 && value <= d.thresholds()[i] : value >= d.thresholds()[i];
            if (reached) level = i + 1;
        }
        // two "of the day" repeats count days won: the plain ones need 1, the repeat ones 2
        return level;
    }

    // Backfills every level up to the one now reached, not just the top one.
    @Transactional
    public void evaluate(Account account, World world) {
        if (!earns(account) || world == null) return;
        Map<String, Long> v = values(account, world);
        Set<String> have = new HashSet<>();
        for (AchievementUnlock u : unlocks.findByAccountIdAndWorldId(account.getId(), world.getId())) have.add(u.getAchievementKey() + "#" + u.getUnlockLevel());
        for (AchievementCatalog.Def d : AchievementCatalog.all()) {
            long value = valueOf(d, v);
            if (!d.tracked()) continue;
            int level = levelFor(d, value);
            for (int l = 1; l <= level; l++) {
                if (have.add(d.key() + "#" + l)) {
                    AchievementUnlock u = new AchievementUnlock();
                    u.setAccountId(account.getId());
                    u.setWorldId(world.getId());
                    u.setAchievementKey(d.key());
                    u.setUnlockLevel(l);
                    unlocks.save(u);
                    live.toAccount(account, world.getId(), LiveUpdates.ACHIEVEMENTS);
                }
            }
        }
    }

    // The plain "of the day" achievement and its "repeat" counterpart share the same counter.
    static long valueOf(AchievementCatalog.Def d, Map<String, Long> v) {
        if (d.metric() == null) return 0;
        long value = v.getOrDefault(d.metric(), 0L);
        return d.key().equals("vanquisher") || d.key().equals("affluent") ? (value >= 2 ? 1 : 0) : value;
    }

    // ---- periodic: points, ranks, daily winners --------------------------------------------------------------

    @Scheduled(fixedDelayString = "${game.achievement-refresh-ms:30000}", initialDelay = 15000)
    @Transactional
    public void refresh() {
        for (World world : worlds.findAll()) {
            refreshRanks(world);
            awardDailyWinners(world);
        }
    }

    private void refreshRanks(World world) {
        Map<Long, Integer> points = new HashMap<>();
        Map<Long, Account> owners = new HashMap<>();
        Map<Long, Map<Integer, Integer>> continentPoints = new HashMap<>(); // account -> continent -> points
        Map<Long, Integer> villagePoints = worldPoints.byVillage(world);
        for (Village v : villages.findByWorld(world)) {
            if (v.getOwner() == null || v.getOwnerType() != OwnerType.PLAYER) continue;
            int p = villagePoints.getOrDefault(v.getId(), 0);
            points.merge(v.getOwner().getId(), p, Integer::sum);
            owners.put(v.getOwner().getId(), v.getOwner());
            int continent = (v.getY() / 100) * 10 + v.getX() / 100;
            continentPoints.computeIfAbsent(v.getOwner().getId(), k -> new HashMap<>()).merge(continent, p, Integer::sum);
        }
        for (Account a : owners.values()) {
            if (!earns(a)) continue;
            int mine = points.get(a.getId());
            long rank = 1 + points.values().stream().filter(p -> p > mine).count();
            // continent rank: among players with villages on the continent where the account has the most points
            var byContinent = continentPoints.get(a.getId());
            int continent = byContinent.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            int continentMine = byContinent.get(continent);
            long continentRank = 1 + continentPoints.values().stream()
                    .filter(m -> m.getOrDefault(continent, 0) > continentMine).count();
            set(a, world, "points", mine);
            set(a, world, "rank_world", rank);
            set(a, world, "rank_continent", continentRank);
            evaluate(a, world);
        }
    }

    // Gives yesterday's (and any earlier unprocessed day's) "of the day" achievements; nothing is awarded retroactively past that.
    private void awardDailyWinners(World world) {
        long today = LocalDate.now().toEpochDay();
        Optional<AchievementCounter> marker = counters.findByAccountIdAndWorldIdAndCounterKey(0L, world.getId(), "daily_processed");
        long done = marker.map(AchievementCounter::getValue).orElse(today - 1); // nothing is awarded retroactively
        for (long day = done + 1; day < today; day++) {
            List<DailyStat> stats = dailyStats.findByWorldIdAndEpochDay(world.getId(), day);
            award(world, stats, DailyStat::getKills, "won_attacker_day");
            award(world, stats, DailyStat::getLoot, "won_looter_day");
            award(world, stats, DailyStat::getPlunders, "won_plunderer_day");
            award(world, stats, DailyStat::getConquests, "won_conquer_day");
        }
        AchievementCounter c = marker.orElseGet(() -> {
            AchievementCounter n = new AchievementCounter();
            n.setAccountId(0L);
            n.setWorldId(world.getId());
            n.setCounterKey("daily_processed");
            return n;
        });
        c.setValue(today - 1);
        counters.save(c);
    }

    private void award(World world, List<DailyStat> stats, java.util.function.ToLongFunction<DailyStat> stat, String counter) {
        stats.stream().filter(s -> stat.applyAsLong(s) > 0).max(Comparator.comparingLong(stat)).ifPresent(best ->
                accounts.findById(best.getAccountId()).ifPresent(a -> count(a, world, counter, 1)));
    }

    // ---- reading -------------------------------------------------------------------------------------------

    public record Entry(String key, String category, String group, String name, String description, String icon, boolean available,
                        int level, int maxLevel, long value, long[] thresholds, boolean lowerIsBetter, Instant unlockedAt) {}

    @Transactional(readOnly = true)
    public List<Entry> list(Account account, World world) {
        Map<String, Long> v = values(account, world);
        Map<String, Instant> latest = new HashMap<>();
        for (AchievementUnlock u : unlocks.findByAccountIdAndWorldId(account.getId(), world.getId())) {
            latest.merge(u.getAchievementKey(), u.getUnlockedAt(), (x, y) -> x.isAfter(y) ? x : y);
        }
        List<Entry> out = new ArrayList<>();
        for (AchievementCatalog.Def d : AchievementCatalog.all()) {
            long value = valueOf(d, v);
            int level = d.tracked() ? levelFor(d, value) : 0;
            out.add(new Entry(d.key(), d.category(), d.group(), d.name(), d.description(), d.icon(), d.tracked(), level,
                    d.maxLevel(), value, d.thresholds(), d.lowerIsBetter(), latest.get(d.key())));
        }
        return out;
    }

    public record Unseen(Long id, String key, String name, String description, String icon, int level, long threshold) {}

    // Only the highest new level of each achievement is returned; marks them shown.
    @Transactional
    public List<Unseen> takeUnseen(Account account, World world) {
        Map<String, AchievementUnlock> best = new LinkedHashMap<>();
        for (AchievementUnlock u : unlocks.findByAccountIdAndWorldIdAndSeenFalseOrderByUnlockedAtAsc(account.getId(), world.getId())) {
            best.merge(u.getAchievementKey(), u, (x, y) -> x.getUnlockLevel() >= y.getUnlockLevel() ? x : y);
            u.setSeen(true);
            unlocks.save(u);
        }
        List<Unseen> out = new ArrayList<>();
        for (AchievementUnlock u : best.values()) {
            AchievementCatalog.all().stream().filter(d -> d.key().equals(u.getAchievementKey())).findFirst().ifPresent(d ->
                    out.add(new Unseen(u.getId(), d.key(), d.name(), d.description(), d.icon(), u.getUnlockLevel(),
                            d.thresholds()[Math.min(u.getUnlockLevel(), d.thresholds().length) - 1])));
        }
        return out;
    }

    public record Standing(String player, boolean npc, int levels, int achievements) {}

    @Transactional(readOnly = true)
    public List<Standing> ranking(World world) {
        Map<Long, int[]> perAccount = new HashMap<>(); // levels, distinct achievements
        Set<String> seen = new HashSet<>();
        for (AchievementUnlock u : unlocks.findByWorldId(world.getId())) {
            int[] s = perAccount.computeIfAbsent(u.getAccountId(), k -> new int[2]);
            s[0]++;
            if (seen.add(u.getAccountId() + "#" + u.getAchievementKey())) s[1]++;
        }
        List<Standing> out = new ArrayList<>();
        for (var e : perAccount.entrySet()) {
            accounts.findById(e.getKey()).ifPresent(a -> out.add(new Standing(a.getUsername(), a.isNpc(), e.getValue()[0], e.getValue()[1])));
        }
        out.sort(Comparator.comparingInt(Standing::levels).reversed().thenComparing(Standing::player));
        return out;
    }

    @Transactional
    public void deleteWorldData(Long worldId) {
        counters.deleteByWorldId(worldId);
        unlocks.deleteByWorldId(worldId);
        dailyStats.deleteByWorldId(worldId);
    }

    @Transactional
    public void deleteAccountData(Long accountId) {
        counters.deleteByAccountId(accountId);
        unlocks.deleteByAccountId(accountId);
        dailyStats.deleteByAccountId(accountId);
    }
}
