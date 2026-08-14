package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.web.dto.VillageStateDto;
import se.oscarwiklund.twlan2.backend.web.dto.VillageSummaryDto;
import se.oscarwiklund.twlan2.backend.service.WorldPoints;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class VillageController {

    private final GameFacade gameFacade;
    private final VillageRepository villageRepository;
    private final WorldPoints worldPoints;
    private final se.oscarwiklund.twlan2.backend.service.TribeService tribes;

    public VillageController(GameFacade gameFacade, VillageRepository villageRepository, WorldPoints worldPoints,
                             se.oscarwiklund.twlan2.backend.service.TribeService tribes) {
        this.tribes = tribes;
        this.gameFacade = gameFacade;
        this.villageRepository = villageRepository;
        this.worldPoints = worldPoints;
    }

    @GetMapping("/api/village")
    public VillageStateDto village() {
        return gameFacade.toDto(gameFacade.playerHomeVillage());
    }

    public record RenameRequest(String name) {}

    @PutMapping("/api/village/name")
    @Transactional
    public VillageStateDto rename(@RequestBody RenameRequest request) {
        String name = request.name() == null ? "" : request.name().trim().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 32) {
            throw new IllegalArgumentException("The village name must be between 3 and 32 characters long.");
        }
        if (name.matches(".*[<>\"&].*")) {
            throw new IllegalArgumentException("The village name may not contain < > & or quotes.");
        }
        var village = gameFacade.playerHomeVillage();
        village.setName(name);
        villageRepository.save(village);
        return gameFacade.toDto(village);
    }

    // What the world's village list looked like a moment ago: the browser asks every 2.5 s, and
    // thousands of villages are not rebuilt for each ask.
    private record Cached(long at, List<VillageSummaryDto> list, String etag) {}

    private static final long KEEP_MS = 1000;
    private final java.util.concurrent.ConcurrentHashMap<Long, Cached> listCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Weak ETag (Tomcat does not gzip a response with a strong one) + "no-cache": the browser
    // revalidates every time and gets a body-less 304 while nothing on the map changed.
    @GetMapping("/api/villages")
    public ResponseEntity<List<VillageSummaryDto>> villages(WebRequest request) {
        World world = gameFacade.currentWorld();
        Cached c = listCache.get(world.getId());
        if (c == null || System.currentTimeMillis() - c.at() > KEEP_MS) {
            List<VillageSummaryDto> list = buildList(world);
            c = new Cached(System.currentTimeMillis(), list, "W/\"" + Integer.toHexString(list.hashCode()) + "\"");
            listCache.put(world.getId(), c);
        }
        if (request.checkNotModified(c.etag())) return null; // 304
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(c.etag()).body(c.list());
    }

    private List<VillageSummaryDto> buildList(World world) {
        Map<Long, Integer> points = worldPoints.byVillage(world);
        Map<Long, se.oscarwiklund.twlan2.backend.domain.Tribe> tribeOf = tribes.tribeByAccount(world);
        return villageRepository.findByWorld(world).stream()
                .map(v -> {
                    var tribe = v.getOwner() == null ? null : tribeOf.get(v.getOwner().getId());
                    return new VillageSummaryDto(v.getId(), v.getName(), v.getX(), v.getY(), v.getOwnerType().name(),
                            v.getOwner() == null ? null : v.getOwner().getUsername(), points.getOrDefault(v.getId(), 0),
                            tribe == null ? null : tribe.getId(), tribe == null ? null : tribe.getTag(),
                            v.getOwner() != null && v.getOwner().isNpc(), GameFacade.bonusCode(v));
                })
                .toList();
    }
}
