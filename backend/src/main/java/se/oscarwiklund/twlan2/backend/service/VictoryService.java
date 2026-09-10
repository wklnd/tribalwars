package se.oscarwiklund.twlan2.backend.service;

import tools.jackson.databind.ObjectMapper;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.TribeRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldVictoryRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldVictoryTribeRepository;
import se.oscarwiklund.twlan2.backend.service.victory.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Each world may pick a victory condition (Domination / War / Rune villages) at creation time, richly
// configurable per type. All day-based thresholds are real calendar days, deliberately never scaled by
// world speed - unlike NpcRetirement/NpcTribeService/NpcJoinService's speed-scaled pacing, which governs
// in-game NPC behaviour, a world's overall lifecycle/season length is meant to be speed-independent (a
// speed 10000 world takes exactly as long to close as a speed 1 world). Once a condition is met the world
// closes for good (WebConfig's interceptor blocks further mutating requests) but its data stays queryable.
@Service
public class VictoryService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(VictoryService.class);

    private final WorldRepository worlds;
    private final WorldVictoryRepository victories;
    private final WorldVictoryTribeRepository tribeProgress;
    private final VillageRepository villages;
    private final TribeRepository tribeRepository;
    private final TribeService tribeService;
    private final RankingService rankingService;
    private final MarketService marketService;
    private final ObjectMapper mapper;

    public VictoryService(WorldRepository worlds, WorldVictoryRepository victories, WorldVictoryTribeRepository tribeProgress,
                          VillageRepository villages, TribeRepository tribeRepository, TribeService tribeService,
                          RankingService rankingService, MarketService marketService, ObjectMapper mapper) {
        this.worlds = worlds;
        this.victories = victories;
        this.tribeProgress = tribeProgress;
        this.villages = villages;
        this.tribeRepository = tribeRepository;
        this.tribeService = tribeService;
        this.rankingService = rankingService;
        this.marketService = marketService;
        this.mapper = mapper;
    }

    // ---- read side, used by the web layer -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean isClosed(World world) {
        return world != null && isClosed(world.getId());
    }

    @Transactional(readOnly = true)
    public boolean isClosed(Long worldId) {
        if (worldId == null) return false;
        return victories.findByWorldId(worldId).map(v -> v.getWonAt() != null).orElse(false);
    }

    public record Outcome(VictoryType type, Long wonTribeId, String wonTribeName, Instant wonAt) {}

    @Transactional(readOnly = true)
    public Outcome outcomeOf(World world) {
        if (world == null) return null;
        WorldVictory wv = victories.findByWorldId(world.getId()).orElse(null);
        if (wv == null || wv.getWonAt() == null) return null;
        String name = tribeRepository.findById(wv.getWonTribeId()).map(Tribe::getName).orElse(null);
        return new Outcome(wv.getType(), wv.getWonTribeId(), name, wv.getWonAt());
    }

    // Only non-null while Rune is the world's active condition - callers roll a fresh barbarian as a rune
    // village with RuneVillage.roll(rnd, this).
    @Transactional(readOnly = true)
    public RuneParams runeParamsIfActive(World world) {
        if (world == null) return null;
        WorldVictory wv = victories.findByWorldId(world.getId()).orElse(null);
        if (wv == null || wv.getType() != VictoryType.RUNE) return null;
        return readJson(wv.getParamsJson(), RuneParams.class, RuneParams.defaults());
    }

    // ---- admin config -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WorldVictory getOrDefault(World world) {
        return victories.findByWorldId(world.getId()).orElseGet(() -> {
            WorldVictory n = new WorldVictory();
            n.setWorldId(world.getId());
            n.setType(VictoryType.NONE);
            return n;
        });
    }

    public Map<String, String> paramsAsMap(WorldVictory wv) {
        return switch (wv.getType()) {
            case NONE -> Map.of();
            case DOMINATION -> {
                DominationParams p = readJson(wv.getParamsJson(), DominationParams.class, DominationParams.defaults());
                yield Map.of("thresholdPercent", String.valueOf(p.thresholdPercent()),
                        "afterDays", String.valueOf(p.afterDays()), "holdDays", String.valueOf(p.holdDays()));
            }
            case WAR -> {
                WarParams p = readJson(wv.getParamsJson(), WarParams.class, WarParams.defaults());
                yield Map.of("rosterSize", String.valueOf(p.rosterSize()), "selectAfterDays", String.valueOf(p.selectAfterDays()),
                        "prepDays", String.valueOf(p.prepDays()), "bonusResource", p.bonusResource().name(),
                        "bonusAmount", String.valueOf(p.bonusAmount()));
            }
            case RUNE -> {
                RuneParams p = readJson(wv.getParamsJson(), RuneParams.class, RuneParams.defaults());
                yield Map.of("totalTarget", String.valueOf(p.totalTarget()),
                        "requireEveryPopulatedContinent", String.valueOf(p.requireEveryPopulatedContinent()),
                        "holdDays", String.valueOf(p.holdDays()), "spawnShare", String.valueOf(p.spawnShare()));
            }
        };
    }

    // rawParams: flat string key/value pairs from the admin form, same convention as WorldSettings.apply.
    @Transactional
    public void configure(World world, VictoryType type, Map<String, String> rawParams) {
        if (type == null) type = VictoryType.NONE;
        WorldVictory wv = victories.findByWorldId(world.getId()).orElseGet(() -> {
            WorldVictory n = new WorldVictory();
            n.setWorldId(world.getId());
            return n;
        });
        VictoryType previousType = wv.getType();
        wv.setType(type);
        try {
            wv.setParamsJson(switch (type) {
                case NONE -> null;
                case DOMINATION -> mapper.writeValueAsString(parseDomination(rawParams));
                case WAR -> mapper.writeValueAsString(parseWar(rawParams));
                case RUNE -> mapper.writeValueAsString(parseRune(rawParams));
            });
        } catch (IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid victory condition parameters: " + e.getMessage());
        }
        if (type != previousType) {
            // starting over: the evaluator's working state/outcome and every tribe's streak no longer apply
            wv.setStateJson(null);
            wv.setLastEvalDay(null);
            wv.setWonTribeId(null);
            wv.setWonAt(null);
            tribeProgress.deleteByWorldId(world.getId());
        }
        victories.save(wv);
    }

    private DominationParams parseDomination(Map<String, String> r) {
        DominationParams d = DominationParams.defaults();
        double threshold = parseDouble(r, "thresholdPercent", d.thresholdPercent(), 1, 100);
        int afterDays = (int) parseDouble(r, "afterDays", d.afterDays(), 0, 3650);
        int holdDays = (int) parseDouble(r, "holdDays", d.holdDays(), 1, 365);
        return new DominationParams(threshold, afterDays, holdDays);
    }

    private WarParams parseWar(Map<String, String> r) {
        WarParams d = WarParams.defaults();
        int rosterSize = (int) parseDouble(r, "rosterSize", d.rosterSize(), 2, 50);
        int selectAfterDays = (int) parseDouble(r, "selectAfterDays", d.selectAfterDays(), 0, 3650);
        int prepDays = (int) parseDouble(r, "prepDays", d.prepDays(), 0, 365);
        Resource bonusResource = parseResource(r == null ? null : r.getOrDefault("bonusResource", d.bonusResource().name()));
        double bonusAmount = parseDouble(r, "bonusAmount", d.bonusAmount(), 0, 10_000_000);
        return new WarParams(rosterSize, selectAfterDays, prepDays, bonusResource, bonusAmount);
    }

    private RuneParams parseRune(Map<String, String> r) {
        RuneParams d = RuneParams.defaults();
        int totalTarget = (int) parseDouble(r, "totalTarget", d.totalTarget(), 1, 1000);
        boolean requireEvery = Boolean.parseBoolean((r == null ? null : r.get("requireEveryPopulatedContinent")) != null
                ? r.get("requireEveryPopulatedContinent") : String.valueOf(d.requireEveryPopulatedContinent()));
        int holdDays = (int) parseDouble(r, "holdDays", d.holdDays(), 1, 365);
        double spawnShare = parseDouble(r, "spawnShare", d.spawnShare(), 0, 1);
        return new RuneParams(totalTarget, requireEvery, holdDays, spawnShare);
    }

    private double parseDouble(Map<String, String> r, String key, double fallback, double min, double max) {
        String raw = r == null ? null : r.get(key);
        double v;
        try {
            v = (raw == null || raw.isBlank()) ? fallback : Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        if (v < min || v > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        return v;
    }

    private Resource parseResource(String raw) {
        try {
            return Resource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown resource: " + raw);
        }
    }

    private <T> T readJson(String json, Class<T> type, T fallback) {
        if (json == null) return fallback;
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            LOG.warn("Could not parse victory data ({}): {}", type.getSimpleName(), e.toString());
            return fallback;
        }
    }

    private void writeState(WorldVictory wv, Object state) {
        try {
            wv.setStateJson(mapper.writeValueAsString(state));
        } catch (Exception e) {
            LOG.warn("Could not serialise victory state for world {}: {}", wv.getWorldId(), e.toString());
        }
    }

    // ---- scheduled evaluation ---------------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${game.victory-sweep-ms:600000}", initialDelay = 90000)
    @Transactional
    public void sweep() {
        long today = todayEpochDay();
        for (World world : worlds.findAll()) {
            try {
                evaluate(world, today);
            } catch (RuntimeException e) {
                LOG.warn("Victory evaluation stumbled for world {}: {}", world.getId(), e.toString());
            }
        }
    }

    private static long todayEpochDay() { return Instant.now().getEpochSecond() / 86400; }

    private void evaluate(World world, long today) {
        WorldVictory wv = victories.findByWorldId(world.getId()).orElse(null);
        if (wv == null || wv.getType() == VictoryType.NONE || wv.getWonAt() != null) return;
        switch (wv.getType()) {
            case DOMINATION -> evaluateDomination(world, wv, today);
            case RUNE -> evaluateRune(world, wv, today);
            case WAR -> evaluateWar(world, wv);
            default -> { }
        }
    }

    private void evaluateDomination(World world, WorldVictory wv, long today) {
        DominationParams p = readJson(wv.getParamsJson(), DominationParams.class, DominationParams.defaults());
        long ageDays = Duration.between(world.getCreatedAt(), Instant.now()).toDays();
        if (ageDays < p.afterDays()) return;
        if (wv.getLastEvalDay() != null && wv.getLastEvalDay() == today) return; // already ran today
        wv.setLastEvalDay(today);

        int totalPlayerVillages = villages.findByWorldAndOwnerType(world, OwnerType.PLAYER).size();
        RankingService.Ranking<RankingService.TribeRow> ranking = rankingService.tribes(world, null, false);
        for (RankingService.TribeRow row : ranking.rows()) {
            boolean met = VictoryEvaluators.dominationMet(row.villages(), totalPlayerVillages, p.thresholdPercent());
            int streak = updateStreak(world, row.id(), met);
            if (VictoryEvaluators.streakWins(streak, p.holdDays())) {
                win(wv, row.id());
                break;
            }
        }
        victories.save(wv);
    }

    private void evaluateRune(World world, WorldVictory wv, long today) {
        RuneParams p = readJson(wv.getParamsJson(), RuneParams.class, RuneParams.defaults());
        if (wv.getLastEvalDay() != null && wv.getLastEvalDay() == today) return;
        wv.setLastEvalDay(today);

        RankingService.Ranking<RankingService.TribeRow> ranking = rankingService.tribes(world, null, false);
        java.util.Set<Integer> populated = new java.util.HashSet<>(ranking.continents());

        Map<Long, Tribe> tribeByAccount = tribeService.tribeByAccount(world);
        Map<Long, Integer> heldByTribe = new HashMap<>();
        Map<Long, java.util.Set<Integer>> continentsByTribe = new HashMap<>();
        for (Village v : villages.findByWorldAndOwnerType(world, OwnerType.PLAYER)) {
            if (!v.isRune() || v.getOwner() == null) continue;
            Tribe t = tribeByAccount.get(v.getOwner().getId());
            if (t == null) continue;
            heldByTribe.merge(t.getId(), 1, Integer::sum);
            continentsByTribe.computeIfAbsent(t.getId(), k -> new java.util.HashSet<>()).add(RankingService.continentOf(v.getX(), v.getY()));
        }
        for (RankingService.TribeRow row : ranking.rows()) {
            boolean met = VictoryEvaluators.runeMet(heldByTribe.getOrDefault(row.id(), 0),
                    continentsByTribe.getOrDefault(row.id(), java.util.Set.of()), populated, p);
            int streak = updateStreak(world, row.id(), met);
            if (VictoryEvaluators.streakWins(streak, p.holdDays())) {
                win(wv, row.id());
                break;
            }
        }
        victories.save(wv);
    }

    private void evaluateWar(World world, WorldVictory wv) {
        WarParams p = readJson(wv.getParamsJson(), WarParams.class, WarParams.defaults());
        WarState state = readJson(wv.getStateJson(), WarState.class, WarState.initial());
        long ageDays = Duration.between(world.getCreatedAt(), Instant.now()).toDays();

        if (state.phase() == WarState.Phase.PENDING) {
            if (ageDays < p.selectAfterDays()) return;
            RankingService.Ranking<RankingService.TribeRow> ranking = rankingService.tribes(world, null, false); // sorted by points desc
            List<Long> roster = ranking.rows().stream().limit(p.rosterSize()).map(RankingService.TribeRow::id).toList();
            // no tribe exists yet: keep waiting rather than locking in an empty roster the war could never resolve
            if (roster.isEmpty()) return;
            writeState(wv, new WarState(WarState.Phase.PREP, roster, Instant.now()));
            victories.save(wv);
            return;
        }
        if (state.phase() == WarState.Phase.PREP) {
            if (state.rosterLockedAt() == null || Duration.between(state.rosterLockedAt(), Instant.now()).toDays() < p.prepDays()) return;
            writeState(wv, new WarState(WarState.Phase.LIVE, state.rosterTribeIds(), state.rosterLockedAt()));
            victories.save(wv);
            return;
        }
        if (state.phase() == WarState.Phase.LIVE) {
            RankingService.Ranking<RankingService.TribeRow> ranking = rankingService.tribes(world, null, false);
            Map<Long, Integer> villagesById = new HashMap<>();
            for (RankingService.TribeRow row : ranking.rows()) villagesById.put(row.id(), row.villages());
            Map<Long, Integer> rosterCounts = new HashMap<>();
            for (Long tribeId : state.rosterTribeIds()) rosterCounts.put(tribeId, villagesById.getOrDefault(tribeId, 0));
            Long winner = VictoryEvaluators.lastStanding(rosterCounts);
            if (winner == null) return;
            win(wv, winner);
            writeState(wv, new WarState(WarState.Phase.DONE, state.rosterTribeIds(), state.rosterLockedAt()));
            victories.save(wv);
            payWarBonus(world, winner, p);
        }
    }

    private void payWarBonus(World world, Long winningTribeId, WarParams p) {
        Map<Long, Tribe> tribeByAccount = tribeService.tribeByAccount(world);
        Map<Long, Village> homeVillageOf = new HashMap<>();
        for (Village v : villages.findByWorldAndOwnerType(world, OwnerType.PLAYER)) {
            if (v.getOwner() == null) continue;
            Tribe t = tribeByAccount.get(v.getOwner().getId());
            if (t == null || !t.getId().equals(winningTribeId)) continue;
            homeVillageOf.putIfAbsent(v.getOwner().getId(), v);
        }
        for (Village v : homeVillageOf.values()) marketService.deliver(v, p.bonusResource(), (int) p.bonusAmount());
    }

    private int updateStreak(World world, Long tribeId, boolean met) {
        WorldVictoryTribe row = tribeProgress.findByWorldIdAndTribeId(world.getId(), tribeId).orElseGet(() -> {
            WorldVictoryTribe n = new WorldVictoryTribe();
            n.setWorldId(world.getId());
            n.setTribeId(tribeId);
            return n;
        });
        int next = VictoryEvaluators.nextStreak(row.getStreakDays(), met);
        row.setStreakDays(next);
        tribeProgress.save(row);
        return next;
    }

    private void win(WorldVictory wv, Long tribeId) {
        wv.setWonTribeId(tribeId);
        wv.setWonAt(Instant.now());
    }
}
