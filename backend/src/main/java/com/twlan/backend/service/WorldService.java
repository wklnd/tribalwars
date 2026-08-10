package com.twlan.backend.service;

import com.twlan.backend.domain.*;
import com.twlan.backend.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// Keeps several worlds running side by side, like the original's world list (normal worlds and
// speed worlds). Seeds the default set on first start, adopts pre-world saves into "Welt 1",
// and gives every world its player village + a barbarian village.
@Component
public class WorldService implements CommandLineRunner {

    // {name, speed}; created when the database has no worlds yet (the first one adopts an existing save).
    private static final Object[][] DEFAULT_WORLDS = {
            {"Welt 1", 1.0},
            {"Welt 2", 2.0},
            {"Speed 1", 50.0},
            {"Speed 2", 250.0},
            {"Speed 3", 1000.0},
    };

    private final WorldRepository worldRepository;
    private final VillageRepository villageRepository;
    private final BuildingRepository buildingRepository;
    private final UnitStockRepository unitStockRepository;
    private final CombatReportRepository combatReportRepository;
    private final AccountRepository accountRepository;

    public WorldService(WorldRepository worldRepository, VillageRepository villageRepository,
                         BuildingRepository buildingRepository, UnitStockRepository unitStockRepository,
                         CombatReportRepository combatReportRepository, AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        this.worldRepository = worldRepository;
        this.villageRepository = villageRepository;
        this.buildingRepository = buildingRepository;
        this.unitStockRepository = unitStockRepository;
        this.combatReportRepository = combatReportRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (worldRepository.count() == 0) {
            for (Object[] w : DEFAULT_WORLDS) {
                newWorld((String) w[0], (double) w[1]);
            }
        }
        World first = worldRepository.findAllByOrderByIdAsc().get(0);

        // saves from before worlds existed belong to the first world
        for (Village v : villageRepository.findByWorldIsNull()) {
            v.setWorld(first);
            villageRepository.save(v);
        }
        for (CombatReport r : combatReportRepository.findByWorldIdIsNull()) {
            r.setWorldId(first.getId());
            combatReportRepository.save(r);
        }

        // accounts made before admin rights existed: the oldest real account runs the game
        List<Account> real = accountRepository.findAll().stream()
                .filter(a -> !a.isNpc() && !"devtool".equalsIgnoreCase(a.getUsername()))
                .sorted(java.util.Comparator.comparing(Account::getId)).toList();
        if (!real.isEmpty() && real.stream().noneMatch(Account::isAdmin)) {
            real.get(0).setAdmin(true);
            accountRepository.save(real.get(0));
        }

        // saves from before a building type existed: give every village its start-level buildings
        for (Village v : villageRepository.findAll()) {
            if (v.getOwnerType() == OwnerType.PLAYER) {
                ensureStartBuildings(v);
            }
        }
    }

    // The save that existed before accounts belongs to the first account: hand over its villages and reports.
    @Transactional
    public void claimLegacyVillages(Account account) {
        for (Village v : villageRepository.findByOwnerTypeAndOwnerIsNull(OwnerType.PLAYER)) {
            v.setOwner(account);
            villageRepository.save(v);
        }
        for (CombatReport r : combatReportRepository.findByAccountIdIsNull()) {
            r.setAccountId(account.getId());
            combatReportRepository.save(r);
        }
    }

    public boolean hasVillage(World world, Account account) {
        return !villageRepository.findByWorldAndOwner(world, account).isEmpty();
    }

    // Also creates a barbarian village nearby to raid.
    @Transactional
    public Village joinWorld(World world, Account account) {
        List<Village> existing = villageRepository.findByWorldAndOwner(world, account);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        if (!WorldSettings.bool(world, "register") && !account.isNpc() && !account.isAdmin()) {
            throw new IllegalArgumentException("Registration is closed in " + world.getName() + ".");
        }
        return createStartVillages(world, account);
    }

    @Transactional
    public World createWorld(String name, double speed) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A world needs a name");
        }
        if (!(speed >= 1 && speed <= 100_000)) {
            throw new IllegalArgumentException("Speed must be between 1 and 100000");
        }
        return newWorld(name.trim(), speed);
    }

    World newWorld(String name, double speed) {
        World world = new World();
        world.setName(name);
        world.setSpeed(speed);
        return worldRepository.save(world);
    }

    Village createStartVillages(World world, Account account) {
        return createStartVillages(world, account, true);
    }

    Set<String> occupiedSpots(World world) {
        Set<String> taken = new HashSet<>();
        for (Village v : villageRepository.findByWorld(world)) {
            taken.add(v.getX() + "|" + v.getY());
        }
        return taken;
    }

    // Share of the fields inside the disc that carry a village: about 29 % on the original's world map, which is a filled disc.
    private static final double DENSITY = 0.28;

    // Radius of the disc that holds `villages` villages at DENSITY (about 1.05 * sqrt(villages)).
    static int autoRadius(int villages) {
        return autoRadius(villages, DENSITY);
    }

    // As above, with the world's own "mapDensity" (per mille, e.g. default 2.5 -> 0.0025) in place of DENSITY.
    static int autoRadius(int villages, double density) {
        return Math.max(8, (int) Math.ceil(Math.sqrt((villages + 1) / (density * Math.PI))));
    }

    // A random free field inside a circle around the world centre (500|500). The circle grows with the number of
    // villages so the world stays a round, densely filled disc (villages may stand next to each other, like on the
    // original's map) instead of a lattice or a square. The chosen spot is added to `taken`.
    static int[] randomFreeSpot(Set<String> taken, Random rnd) {
        return randomFreeSpot(taken, rnd, autoRadius(taken.size()));
    }

    // As above, honouring the world's "mapDensity"/"mapSize" settings. "mapDensity" isn't a discoverable original
    // formula (compiled game); its catalog default (2.5) is defined here to reproduce today's DENSITY exactly, and
    // scales it proportionally either way.
    static int[] randomFreeSpot(Set<String> taken, Random rnd, World world) {
        double density = DENSITY * (WorldSettings.number(world, "mapDensity") / 2.5);
        int mapSize = (int) WorldSettings.number(world, "mapSize");
        return randomFreeSpot(taken, rnd, autoRadius(taken.size(), density), mapSize);
    }

    static int[] randomFreeSpot(Set<String> taken, Random rnd, int radius) {
        return randomFreeSpot(taken, rnd, radius, 1000);
    }

    static int[] randomFreeSpot(Set<String> taken, Random rnd, int radius, int mapSize) {
        double center = mapSize / 2.0; // 500 at the default mapSize=1000, matching the original fixed centre
        for (int attempt = 0; attempt < 4000; attempt++) {
            double r = radius + attempt / 200; // slowly widen when the disc is crowded
            double angle = rnd.nextDouble() * 2 * Math.PI;
            double dist = r * Math.sqrt(rnd.nextDouble()); // sqrt: uniform over the disc, not bunched in the middle
            int x = clampCoord((int) Math.round(center + dist * Math.cos(angle)), mapSize);
            int y = clampCoord((int) Math.round(center + dist * Math.sin(angle)), mapSize);
            if (taken.contains(x + "|" + y)) continue;
            taken.add(x + "|" + y);
            return new int[]{x, y};
        }
        throw new IllegalArgumentException("No free spot left near the centre of the world");
    }

    // A free field 2-5 fields from (x, y): where a barbarian village next to a fresh player goes.
    static int[] freeSpotNear(Set<String> taken, Random rnd, int x, int y) {
        for (int attempt = 0; attempt < 500; attempt++) {
            double angle = rnd.nextDouble() * 2 * Math.PI;
            double dist = 2 + rnd.nextDouble() * 3.5;
            int nx = clampCoord((int) Math.round(x + dist * Math.cos(angle)));
            int ny = clampCoord((int) Math.round(y + dist * Math.sin(angle)));
            if (Math.max(Math.abs(nx - x), Math.abs(ny - y)) < 2 || taken.contains(nx + "|" + ny)) continue;
            taken.add(nx + "|" + ny);
            return new int[]{nx, ny};
        }
        return randomFreeSpot(taken, rnd);
    }

    private static int clampCoord(int v) {
        return clampCoord(v, 1000);
    }

    private static int clampCoord(int v, int mapSize) {
        return Math.max(1, Math.min(mapSize - 2, v));
    }

    Village createVillageFromLayout(World world, Account owner, VillageGenerator.Layout layout, Random rnd) {
        int[] spot = randomFreeSpot(occupiedSpots(world), rnd, world);
        Village home = new Village();
        home.setName(owner.getUsername() + "'s village");
        home.setWorld(world);
        home.setOwner(owner);
        home.setOwnerType(OwnerType.PLAYER);
        home.setX(spot[0]);
        home.setY(spot[1]);
        home.setWood(layout.wood());
        home.setClay(layout.clay());
        home.setIron(layout.iron());
        home.setResourcesSettledAt(Instant.now());
        villageRepository.save(home);
        layout.buildings().forEach((t, level) -> addBuilding(home, t, level));
        layout.units().forEach((t, n) -> { if (n > 0) addUnits(home, t, n); });
        return home;
    }

    Village createStartVillages(World world, Account account, boolean withBarbarian) {
        Random rnd = new Random();
        Set<String> taken = occupiedSpots(world);
        int[] spot = randomFreeSpot(taken, rnd, world);
        int x = spot[0];
        int y = spot[1];

        Village home = new Village();
        home.setName(account.getUsername() + "'s village");
        home.setWorld(world);
        home.setOwner(account);
        home.setOwnerType(OwnerType.PLAYER);
        home.setX(x);
        home.setY(y);
        home.setWood(WorldSettings.number(world, "startWood"));
        home.setClay(WorldSettings.number(world, "startClay"));
        home.setIron(WorldSettings.number(world, "startIron"));
        home.setResourcesSettledAt(Instant.now());
        villageRepository.save(home);

        addBuilding(home, BuildingType.HEADQUARTERS, 1);
        addBuilding(home, BuildingType.TIMBER_CAMP, 2);
        addBuilding(home, BuildingType.CLAY_PIT, 2);
        addBuilding(home, BuildingType.IRON_MINE, 2);
        addBuilding(home, BuildingType.FARM, 1);
        addBuilding(home, BuildingType.WAREHOUSE, 1);
        addBuilding(home, BuildingType.BARRACKS, 0);
        addBuilding(home, BuildingType.WALL, 0);
        ensureStartBuildings(home);

        if (!withBarbarian) {
            return home;
        }
        Village barbarian = new Village();
        barbarian.setName("Abandoned Camp");
        barbarian.setWorld(world);
        barbarian.setOwnerType(OwnerType.BARBARIAN);
        int[] near = freeSpotNear(taken, rnd, x, y);
        barbarian.setX(near[0]);
        barbarian.setY(near[1]);
        barbarian.setBonusCode(BonusType.roll(rnd, world));
        barbarian.setWood(400);
        barbarian.setClay(400);
        barbarian.setIron(400);
        barbarian.setResourcesSettledAt(Instant.now());
        villageRepository.save(barbarian);

        // A freshly generated barbarian village has no troops. Barbarians only ever hold troops
        // when they came from an abandoned player village (AbandonmentService/AdminService.removePlayer
        // convert a village in place and never touch its UnitStock).
        return home;
    }

    private void ensureStartBuildings(Village village) {
        for (BuildingType type : BuildingType.values()) {
            if (buildingRepository.findByVillageAndType(village, type).isEmpty()) {
                addBuilding(village, type, type.startLevel());
            }
        }
    }

    void addBuilding(Village village, BuildingType type, int level) {
        Building b = new Building();
        b.setVillage(village);
        b.setType(type);
        b.setLevel(level);
        buildingRepository.save(b);
    }

    void addUnitsTo(Village village, UnitType type, int count) {
        UnitStock s = unitStockRepository.findByVillageAndType(village, type).orElseGet(() -> {
            UnitStock n = new UnitStock();
            n.setVillage(village);
            n.setType(type);
            n.setCount(0);
            return n;
        });
        s.setCount(s.getCount() + count);
        unitStockRepository.save(s);
    }

    void addUnits(Village village, UnitType type, int count) {
        UnitStock s = new UnitStock();
        s.setVillage(village);
        s.setType(type);
        s.setCount(count);
        unitStockRepository.save(s);
    }
}
