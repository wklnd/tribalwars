package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.MigrationMarkRepository;
import se.oscarwiklund.twlan2.backend.repo.ResearchQueueItemRepository;
import se.oscarwiklund.twlan2.backend.repo.UnitResearchRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// The smithy's research (world researchSystem "simple": every unit is either researched or not). Spear and sword are
// always known, the paladin and the nobleman have their own rules; axe, archer, scout, light cavalry, mounted archer,
// heavy cavalry, ram and catapult are researched per village with the costs and times of units.json.
// Barbarian and NPC villages count as having researched everything.
@Service
public class ResearchService implements CommandLineRunner {

    // Cost and base duration of researching one unit (units.json research["1"]).
    public record Tech(UnitType unit, int wood, int clay, int iron, int seconds) {}

    public static final Map<UnitType, Tech> TECHS = new EnumMap<>(UnitType.class);
    static {
        add(UnitType.AXE, 700, 840, 820, 6930);
        add(UnitType.ARCHER, 640, 560, 740, 7950);
        add(UnitType.SCOUT, 560, 480, 480, 3960);
        add(UnitType.LIGHT, 2200, 2400, 2000, 8910);
        add(UnitType.MARCHER, 3000, 2400, 2000, 9900);
        add(UnitType.HEAVY, 3000, 2400, 2000, 9900);
        add(UnitType.RAM, 1200, 1600, 800, 7910);
        add(UnitType.CATAPULT, 1600, 2000, 1200, 9900);
    }

    private static void add(UnitType unit, int wood, int clay, int iron, int seconds) {
        TECHS.put(unit, new Tech(unit, wood, clay, iron, seconds));
    }

    public static class ResearchException extends RuntimeException {
        public ResearchException(String message) { super(message); }
    }

    private final LiveUpdates live;
    private final UnitResearchRepository researched;
    private final ResearchQueueItemRepository queue;
    private final VillageRepository villages;
    private final VillageService villageService;
    private final GameSettings settings;
    private final MigrationMarkRepository marks;

    public ResearchService(LiveUpdates live, UnitResearchRepository researched, ResearchQueueItemRepository queue, VillageRepository villages,
                           VillageService villageService, GameSettings settings, MigrationMarkRepository marks) {
        this.live = live;
        this.researched = researched;
        this.queue = queue;
        this.villages = villages;
        this.villageService = villageService;
        this.settings = settings;
        this.marks = marks;
    }

    // Saves from before research existed: every village keeps what it could recruit, i.e. everything is researched.
    @Override
    @Transactional
    public void run(String... args) {
        if (marks.existsById("research-granted")) return;
        for (Village v : villages.findAll()) {
            for (UnitType t : TECHS.keySet()) grant(v, t);
        }
        marks.save(new MigrationMark("research-granted"));
    }

    private void grant(Village village, UnitType unit) {
        if (researched.existsByVillageAndUnit(village, unit)) return;
        UnitResearch r = new UnitResearch();
        r.setVillage(village);
        r.setUnit(unit);
        researched.save(r);
    }

    public boolean isResearched(Village village, UnitType unit) {
        if (!TECHS.containsKey(unit)) return true; // spear, sword, paladin, nobleman
        if ("off".equals(WorldSettings.get(village.getWorld(), "researchSystem"))) return true;
        Account owner = village.getOwner();
        if (owner == null || owner.isNpc()) return true;
        return researched.existsByVillageAndUnit(village, unit);
    }

    // The smithy shortens research time like the headquarters does construction time.
    public long secondsFor(Village village, UnitType unit) {
        int smithy = villageService.levelOf(village, BuildingType.SMITHY);
        return settings.scaleSeconds(village, TECHS.get(unit).seconds() / Math.pow(1.05, smithy));
    }

    @Transactional(noRollbackFor = ResearchException.class)
    public ResearchQueueItem start(Village village, UnitType unit) {
        Tech tech = TECHS.get(unit);
        if (tech == null) throw new ResearchException(unit.displayName() + " cannot be researched");
        if (villageService.levelOf(village, BuildingType.SMITHY) < 1) throw new ResearchException("Requires a Smithy");
        if (!unit.requirementsMet(t -> villageService.levelOf(village, t))) throw new ResearchException("Building requirements unmet");
        if (researched.existsByVillageAndUnit(village, unit)) throw new ResearchException("Technology fully researched");
        List<ResearchQueueItem> items = queue.findByVillageOrderByPositionAsc(village);
        if (items.stream().anyMatch(i -> i.getUnit() == unit)) throw new ResearchException(unit.displayName() + " is already being researched");

        villageService.settleResources(village);
        if (village.getWood() < tech.wood() || village.getClay() < tech.clay() || village.getIron() < tech.iron()) {
            throw new ResearchException("Not enough resources");
        }
        int capacity = villageService.warehouseCapacity(village);
        if (Math.max(tech.wood(), Math.max(tech.clay(), tech.iron())) > capacity) throw new ResearchException("Warehouse too small");
        village.setWood(village.getWood() - tech.wood());
        village.setClay(village.getClay() - tech.clay());
        village.setIron(village.getIron() - tech.iron());
        villages.save(village);

        ResearchQueueItem item = new ResearchQueueItem();
        item.setVillage(village);
        item.setUnit(unit);
        item.setDurationSeconds(secondsFor(village, unit));
        item.setPosition(items.size());
        if (items.isEmpty()) {
            Instant now = Instant.now();
            item.setStartedAt(now);
            item.setCompletesAt(now.plusSeconds(item.getDurationSeconds()));
        }
        return queue.save(item);
    }

    @Transactional
    public void cancel(Village village, Long itemId) {
        villageService.settleResources(village);
        ResearchQueueItem item = queue.findByVillageOrderByPositionAsc(village).stream()
                .filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResearchException("No such research order"));
        Tech tech = TECHS.get(item.getUnit());
        int capacity = villageService.warehouseCapacity(village);
        village.setWood(Math.max(village.getWood(), Math.min(capacity, village.getWood() + tech.wood())));
        village.setClay(Math.max(village.getClay(), Math.min(capacity, village.getClay() + tech.clay())));
        village.setIron(Math.max(village.getIron(), Math.min(capacity, village.getIron() + tech.iron())));
        villages.save(village);
        queue.delete(item);
        resequence(village, Instant.now());
    }

    // Called every tick.
    @Transactional
    public void process(Instant now) {
        for (ResearchQueueItem item : queue.findDue(now)) {
            Village village = item.getVillage();
            grant(village, item.getUnit());
            queue.delete(item);
            resequence(village, now);
            live.village(village);
        }
    }

    private void resequence(Village village, Instant now) {
        List<ResearchQueueItem> rest = queue.findByVillageOrderByPositionAsc(village);
        for (int i = 0; i < rest.size(); i++) {
            ResearchQueueItem q = rest.get(i);
            q.setPosition(i);
            if (i == 0 && q.getStartedAt() == null) {
                q.setStartedAt(now);
                q.setCompletesAt(now.plusSeconds(q.getDurationSeconds()));
            }
            queue.save(q);
        }
    }

    public List<ResearchQueueItem> queueOf(Village village) {
        return queue.findByVillageOrderByPositionAsc(village);
    }

    // Admin's "finish queues" action.
    @Transactional
    public void finishAll(Village village) {
        for (ResearchQueueItem item : queue.findByVillageOrderByPositionAsc(village)) {
            grant(village, item.getUnit());
            queue.delete(item);
        }
    }

    @Transactional
    public void deleteVillageData(Village village) {
        queue.deleteAll(queue.findByVillageOrderByPositionAsc(village));
        researched.deleteAll(researched.findByVillage(village));
    }
}
