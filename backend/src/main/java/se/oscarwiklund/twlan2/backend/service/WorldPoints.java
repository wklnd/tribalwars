package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.repo.BuildingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Points of every village of a world (sum of the building points), the input of the ranking, the map lists, the profile,
// the tribe ranking and the NPCs' view of the world. Every one of those used to load all Building entities of the world
// on its own; now they share one lean query whose result is kept for a moment. A point total is at most
// KEEP_MS old, which nobody can tell from the 2.5 s the browser polls at.
@Service
public class WorldPoints {

    private static final long KEEP_MS = 1500;

    private record Entry(long at, Map<Long, Integer> points) {}

    private final BuildingRepository buildings;
    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    public WorldPoints(BuildingRepository buildings) {
        this.buildings = buildings;
    }

    // Villages without buildings are missing from the map (= 0).
    @Transactional(readOnly = true)
    public Map<Long, Integer> byVillage(World world) {
        long now = System.currentTimeMillis();
        Entry e = cache.get(world.getId());
        if (e != null && now - e.at < KEEP_MS) return e.points;
        Map<Long, Integer> points = new HashMap<>();
        for (BuildingRepository.Level b : buildings.levelsInWorld(world.getId())) {
            points.merge(b.getVillageId(), b.getType().points(b.getLevel()), Integer::sum);
        }
        Map<Long, Integer> fresh = Collections.unmodifiableMap(points);
        cache.put(world.getId(), new Entry(now, fresh));
        return fresh;
    }
}
