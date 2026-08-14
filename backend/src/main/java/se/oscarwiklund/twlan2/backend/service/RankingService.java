package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

// The Ranking screen: players and tribes ordered by points or by opponents defeated, optionally restricted to one
// continent (the K-number of the map: K<y/100><x/100>). Everything is worked out from the villages on every
// request, like the tribe ranking, so nothing can go stale.
@Service
public class RankingService {

    public record PlayerRow(int rank, Long id, String name, Long tribeId, String tribeTag, int points, int villages, long kills) {}
    public record TribeRow(int rank, Long id, String name, String tag, int points, int members, int pointsPerPlayer, int villages,
                           int pointsPerVillage, long kills) {}
    // continents = every continent with at least one player village (numbers as on the map, e.g. 45 = K45).
    public record Ranking<T>(List<Integer> continents, List<T> rows) {}

    private final VillageRepository villages;
    private final WorldPoints worldPoints;
    private final AchievementCounterRepository counters;
    private final TribeRepository tribes;
    private final TribeMemberRepository members;

    public RankingService(VillageRepository villages, WorldPoints worldPoints, AchievementCounterRepository counters,
                          TribeRepository tribes, TribeMemberRepository members) {
        this.villages = villages;
        this.worldPoints = worldPoints;
        this.counters = counters;
        this.tribes = tribes;
        this.members = members;
    }

    public static int continentOf(int x, int y) {
        return (y / 100) * 10 + x / 100;
    }

    private static class Tally {
        String name;
        int points;
        int villages;
    }

    private Map<Long, Tally> tallies(World world, Integer continent, Set<Integer> continents) {
        Map<Long, Integer> villagePoints = worldPoints.byVillage(world);
        Map<Long, Tally> out = new HashMap<>();
        for (Village v : villages.findByWorld(world)) {
            if (v.getOwner() == null || v.getOwnerType() != OwnerType.PLAYER) continue;
            int c = continentOf(v.getX(), v.getY());
            continents.add(c);
            if (continent != null && c != continent) continue;
            Tally t = out.computeIfAbsent(v.getOwner().getId(), id -> new Tally());
            t.name = v.getOwner().getUsername();
            t.points += villagePoints.getOrDefault(v.getId(), 0);
            t.villages++;
        }
        return out;
    }

    private Map<Long, Long> killsByAccount(World world) {
        Map<Long, Long> kills = new HashMap<>();
        for (AchievementCounter c : counters.findByWorldIdAndCounterKey(world.getId(), "kills")) kills.put(c.getAccountId(), c.getValue());
        return kills;
    }

    // byKills orders by opponents defeated instead of points; ties fall back to points, then name.
    @Transactional(readOnly = true)
    public Ranking<PlayerRow> players(World world, Integer continent, boolean byKills) {
        Set<Integer> continents = new TreeSet<>();
        Map<Long, Tally> tallies = tallies(world, continent, continents);
        Map<Long, Long> kills = killsByAccount(world);
        Map<Long, Tribe> tribeById = tribes.findByWorldId(world.getId()).stream().collect(Collectors.toMap(Tribe::getId, t -> t));
        Map<Long, Long> tribeOf = members.findByWorldId(world.getId()).stream().collect(Collectors.toMap(TribeMember::getAccountId, TribeMember::getTribeId, (a, b) -> a));

        List<PlayerRow> rows = new ArrayList<>();
        for (var e : tallies.entrySet()) {
            Long id = e.getKey();
            Tribe t = tribeById.get(tribeOf.get(id));
            rows.add(new PlayerRow(0, id, e.getValue().name, t == null ? null : t.getId(), t == null ? null : t.getTag(),
                    e.getValue().points, e.getValue().villages, kills.getOrDefault(id, 0L)));
        }
        Comparator<PlayerRow> order = byKills
                ? Comparator.comparingLong(PlayerRow::kills).reversed().thenComparing(Comparator.comparingInt(PlayerRow::points).reversed())
                : Comparator.comparingInt(PlayerRow::points).reversed();
        rows.sort(order.thenComparing(r -> r.name().toLowerCase()));
        List<PlayerRow> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PlayerRow r = rows.get(i);
            ranked.add(new PlayerRow(i + 1, r.id(), r.name(), r.tribeId(), r.tribeTag(), r.points(), r.villages(), r.kills()));
        }
        return new Ranking<>(List.copyOf(continents), ranked);
    }

    // A tribe counts the villages of its members on the continent; members = those with a village there.
    @Transactional(readOnly = true)
    public Ranking<TribeRow> tribes(World world, Integer continent, boolean byKills) {
        Set<Integer> continents = new TreeSet<>();
        Map<Long, Tally> byAccount = tallies(world, continent, continents);
        Map<Long, Long> kills = killsByAccount(world);
        Map<Long, List<TribeMember>> byTribe = members.findByWorldId(world.getId()).stream().collect(Collectors.groupingBy(TribeMember::getTribeId));

        List<TribeRow> rows = new ArrayList<>();
        for (Tribe t : tribes.findByWorldId(world.getId())) {
            int points = 0, vill = 0, active = 0;
            long k = 0;
            for (TribeMember m : byTribe.getOrDefault(t.getId(), List.of())) {
                Tally tally = byAccount.get(m.getAccountId());
                if (tally == null) continue;
                points += tally.points;
                vill += tally.villages;
                active++;
                k += kills.getOrDefault(m.getAccountId(), 0L);
            }
            if (active == 0) continue;
            rows.add(new TribeRow(0, t.getId(), t.getName(), t.getTag(), points, active, points / active, vill, vill == 0 ? 0 : points / vill, k));
        }
        Comparator<TribeRow> order = byKills
                ? Comparator.comparingLong(TribeRow::kills).reversed().thenComparing(Comparator.comparingInt(TribeRow::points).reversed())
                : Comparator.comparingInt(TribeRow::points).reversed();
        rows.sort(order.thenComparing(r -> r.tag().toLowerCase()));
        List<TribeRow> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            TribeRow r = rows.get(i);
            ranked.add(new TribeRow(i + 1, r.id(), r.name(), r.tag(), r.points(), r.members(), r.pointsPerPlayer(), r.villages(), r.pointsPerVillage(), r.kills()));
        }
        return new Ranking<>(List.copyOf(continents), ranked);
    }
}
