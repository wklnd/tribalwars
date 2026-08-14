package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.MovementRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.service.TribeService;
import se.oscarwiklund.twlan2.backend.service.WorldPoints;

import java.util.*;

// A read-only picture of one world for one NPC tick: every village, who is in which tribe and which troops are on the
// road. It is built once per world per tick and shared by all NPC decisions, so an NPC never scans the whole world
// by itself (that used to cost a full query per village per tick). Orders an NPC gives during the tick are added with
// added() so the next NPC sees them.
public final class WorldView {

    public final World world;
    public final List<Village> villages;
    public final Map<Long, Tribe> tribeByAccount;
    private final Map<Long, Village> byId = new HashMap<>();
    private final Map<Long, Integer> villagesOf = new HashMap<>();
    private final Map<Long, Integer> points = new HashMap<>();
    private final Map<Long, Integer> pointsOfOwner = new HashMap<>();
    private final Map<Long, List<Movement>> byTarget = new HashMap<>();
    private final Map<Long, List<Movement>> byOrigin = new HashMap<>();
    private Map<Long, Map<Long, TribeRelation.Kind>> relations = Map.of();
    // Villages by 10x10 field cell, so "who is within N fields" does not scan the whole world (worlds get tens of thousands of villages).
    private static final int CELL = 10;
    private final Map<Long, List<Village>> grid = new HashMap<>();

    WorldView(World world, List<Village> villages, Map<Long, Tribe> tribeByAccount) { // (package-private for the test)
        this.world = world;
        this.villages = villages;
        this.tribeByAccount = tribeByAccount;
        for (Village v : villages) {
            byId.put(v.getId(), v);
            if (v.getOwner() != null) villagesOf.merge(v.getOwner().getId(), 1, Integer::sum);
            grid.computeIfAbsent(cell(Math.floorDiv(v.getX(), CELL), Math.floorDiv(v.getY(), CELL)), k -> new ArrayList<>()).add(v);
        }
    }

    private static long cell(int cx, int cy) { return cx * 1_000_003L + cy; }

    // Every village within `range` fields of the point (a square's worth, the caller measures the exact distance if it cares).
    public List<Village> near(int x, int y, double range) {
        int r = (int) Math.ceil(range);
        List<Village> out = new ArrayList<>();
        for (int cx = Math.floorDiv(x - r, CELL); cx <= Math.floorDiv(x + r, CELL); cx++) {
            for (int cy = Math.floorDiv(y - r, CELL); cy <= Math.floorDiv(y + r, CELL); cy++) {
                List<Village> cellVillages = grid.get(cell(cx, cy));
                if (cellVillages != null) out.addAll(cellVillages);
            }
        }
        return out;
    }

    // How tribe `mine` sees tribe `other` (null: no relation set).
    public TribeRelation.Kind relation(Long mine, Long other) {
        Map<Long, TribeRelation.Kind> m = relations.get(mine);
        return m == null ? null : m.get(other);
    }

    public static WorldView build(World world, VillageRepository villages, MovementRepository movements, TribeService tribes, WorldPoints worldPoints) {
        WorldView view = new WorldView(world, villages.findByWorld(world), tribes.tribeByAccount(world));
        Set<Long> tribeIds = new HashSet<>();
        for (Tribe t : view.tribeByAccount.values()) tribeIds.add(t.getId());
        view.relations = tribes.relationsOf(tribeIds);
        view.points.putAll(worldPoints.byVillage(world));
        for (Village v : view.villages) {
            if (v.getOwner() != null) view.pointsOfOwner.merge(v.getOwner().getId(), view.points.getOrDefault(v.getId(), 0), Integer::sum);
        }
        for (Movement m : movements.findAll()) {
            if (view.byId.containsKey(m.getOriginVillage().getId()) || view.byId.containsKey(m.getTargetVillage().getId())) view.added(m);
        }
        return view;
    }

    // Registers a movement that was just ordered (or already existed).
    public void added(Movement m) {
        m.getUnits().size(); // load the troop list while the session is open: the snapshot is read after it closed
        byTarget.computeIfAbsent(m.getTargetVillage().getId(), k -> new ArrayList<>()).add(m);
        byOrigin.computeIfAbsent(m.getOriginVillage().getId(), k -> new ArrayList<>()).add(m);
    }

    public Village village(Long id) { return byId.get(id); }

    public List<Movement> targeting(Village v) { return byTarget.getOrDefault(v.getId(), List.of()); }

    public List<Movement> from(Village v) { return byOrigin.getOrDefault(v.getId(), List.of()); }

    public boolean underAttack(Village v) {
        for (Movement m : targeting(v)) if (m.getType() == MovementType.ATTACK) return true;
        return false;
    }

    // True while the village has troops out that are not just coming home (a wave is out).
    public boolean waveOut(Village v) {
        for (Movement m : from(v)) if (m.getType() != MovementType.RETURN) return true;
        return false;
    }

    // Points of a village (what the world map shows for everyone).
    public int points(Village v) { return points.getOrDefault(v.getId(), 0); }

    public int pointsOf(Account owner) { return owner == null ? 0 : pointsOfOwner.getOrDefault(owner.getId(), 0); }

    public int villageCount(Account owner) { return owner == null ? 0 : villagesOf.getOrDefault(owner.getId(), 0); }

    public Tribe tribeOf(Account owner) { return owner == null ? null : tribeByAccount.get(owner.getId()); }
}
