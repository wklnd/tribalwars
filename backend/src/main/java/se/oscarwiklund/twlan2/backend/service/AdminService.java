package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.service.npc.NpcLogService;
import se.oscarwiklund.twlan2.backend.service.npc.NpcNames;
import se.oscarwiklund.twlan2.backend.service.npc.NpcProfiles;
import se.oscarwiklund.twlan2.backend.repo.*;
import se.oscarwiklund.twlan2.backend.web.dto.AdminDto;
import se.oscarwiklund.twlan2.backend.web.dto.AdminDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Path BACKUP_DIR = Path.of("backups");
    private final WorldRepository worlds;
    private final VillageRepository villages;
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final BuildingRepository buildings;
    private final BuildQueueItemRepository buildQueue;
    private final TrainQueueItemRepository trainQueue;
    private final UnitStockRepository unitStock;
    private final MovementRepository movements;
    private final CombatReportRepository reports;
    private final WorldService worldService;
    private final VillageService villageService;
    private final JdbcTemplate jdbc;
    private final AchievementService achievements;
    private final StatsService stats;
    private final NobleService nobles;
    private final FarmService farm;
    private final TribeService tribes;
    private final SupportService support;
    private final GroupService groups;
    private final MarketService market;
    private final MailService mail;
    private final NpcProfiles npcProfiles;
    private final NpcLogService npcLog;
    private final se.oscarwiklund.twlan2.backend.service.npc.NpcIntelService npcIntel;
    private final ResearchService research;

    public AdminService(WorldRepository worlds, VillageRepository villages, AccountRepository accounts,
                        AuthSessionRepository sessions, BuildingRepository buildings, BuildQueueItemRepository buildQueue,
                        TrainQueueItemRepository trainQueue, UnitStockRepository unitStock, MovementRepository movements,
                        CombatReportRepository reports, WorldService worldService, VillageService villageService,
                        JdbcTemplate jdbc, AchievementService achievements, StatsService stats,
                        NobleService nobles, FarmService farm, ResearchService research, TribeService tribes, SupportService support, GroupService groups, MarketService market, MailService mail, NpcProfiles npcProfiles, NpcLogService npcLog,
                        se.oscarwiklund.twlan2.backend.service.npc.NpcIntelService npcIntel) {
        this.npcIntel = npcIntel;
        this.npcProfiles = npcProfiles;
        this.npcLog = npcLog;
        this.market = market;
        this.mail = mail;
        this.groups = groups;
        this.support = support;
        this.tribes = tribes;
        this.research = research;
        this.nobles = nobles;
        this.farm = farm;
        this.achievements = achievements;
        this.stats = stats;
        this.worlds = worlds;
        this.villages = villages;
        this.accounts = accounts;
        this.sessions = sessions;
        this.buildings = buildings;
        this.buildQueue = buildQueue;
        this.trainQueue = trainQueue;
        this.unitStock = unitStock;
        this.movements = movements;
        this.reports = reports;
        this.worldService = worldService;
        this.villageService = villageService;
        this.jdbc = jdbc;
    }

    // ---- dashboard / backups -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<Village> all = villages.findAll();
        long player = all.stream().filter(v -> v.getOwnerType() == OwnerType.PLAYER && !isNpc(v)).count();
        long npc = all.stream().filter(v -> v.getOwnerType() == OwnerType.PLAYER && isNpc(v)).count();
        long barb = all.stream().filter(v -> v.getOwnerType() == OwnerType.BARBARIAN).count();
        List<Account> accs = accounts.findAll();
        File db = new File("data/twlan2.mv.db");
        return new Dashboard(accs.stream().filter(a -> !a.isNpc()).count(), accs.stream().filter(Account::isNpc).count(),
                worlds.count(), new VillageCounts(player, npc, barb), buildQueue.count(), trainQueue.count(), movements.count(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000, System.getProperty("java.version"),
                db.exists() ? db.length() : 0, backups());
    }

    public List<Backup> backups() {
        if (!Files.isDirectory(BACKUP_DIR)) return List.of();
        try (var files = Files.list(BACKUP_DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .map(p -> {
                        try {
                            return new Backup(p.getFileName().toString(), Files.size(p), Files.getLastModifiedTime(p).toInstant());
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Backup::createdAt).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // H2 online backup of the whole save (all worlds, accounts) to backend/backups/.
    public Backup backup() {
        try {
            Files.createDirectories(BACKUP_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the backup folder", e);
        }
        String name = "twlan-" + LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
        jdbc.execute("BACKUP TO '" + BACKUP_DIR.resolve(name).toAbsolutePath().toString().replace("'", "''") + "'");
        try {
            Path p = BACKUP_DIR.resolve(name);
            return new Backup(name, Files.size(p), Instant.now());
        } catch (IOException e) {
            throw new IllegalStateException("Backup file missing", e);
        }
    }

    // ---- catalog -------------------------------------------------------------------------------------------

    public Catalog catalog() {
        return new Catalog(
                Arrays.stream(BuildingType.values()).map(t -> new BuildingInfo(t.name(), t.maxLevel, t.startLevel(),
                        t.requirements().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)))).toList(),
                Arrays.stream(UnitType.values()).map(t -> new UnitInfo(t.name())).toList(),
                WorldSettings.catalog());
    }

    // ---- worlds --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminDto.WorldRow> listWorlds() {
        return worlds.findAllByOrderByIdAsc().stream().map(this::worldDto).toList();
    }

    private AdminDto.WorldRow worldDto(World w) {
        List<Village> vs = villages.findByWorld(w);
        long players = vs.stream().filter(v -> v.getOwnerType() == OwnerType.PLAYER && v.getOwner() != null && !v.getOwner().isNpc())
                .map(v -> v.getOwner().getId()).distinct().count();
        long npcs = vs.stream().filter(v -> v.getOwnerType() == OwnerType.PLAYER && v.getOwner() != null && v.getOwner().isNpc())
                .map(v -> v.getOwner().getId()).distinct().count();
        long barb = vs.stream().filter(v -> v.getOwnerType() == OwnerType.BARBARIAN).count();
        return new AdminDto.WorldRow(w.getId(), w.getName(), w.getSpeed(), w.getCreatedAt(), players, npcs, vs.size(), barb,
                WorldSettings.effective(w));
    }

    @Transactional
    public AdminDto.WorldRow createWorld(WorldRequest req) {
        if (req.name() == null || req.name().isBlank()) throw new IllegalArgumentException("A world needs a name");
        double speed = req.speed() == null ? 1.0 : req.speed();
        World w = worldService.createWorld(req.name(), speed);
        WorldSettings.apply(w, req.settings());
        worlds.save(w);
        return worldDto(w);
    }

    @Transactional
    public AdminDto.WorldRow updateWorld(Long id, WorldRequest req) {
        World w = world(id);
        if (req.name() != null) {
            if (req.name().isBlank()) throw new IllegalArgumentException("A world needs a name");
            w.setName(req.name().trim());
        }
        if (req.speed() != null) {
            if (!(req.speed() >= 1 && req.speed() <= 100_000)) throw new IllegalArgumentException("Speed must be between 1 and 100000");
            w.setSpeed(req.speed());
        }
        WorldSettings.apply(w, req.settings());
        worlds.save(w);
        return worldDto(w);
    }

    @Transactional
    public void deleteWorld(Long id) {
        World w = world(id);
        tribes.deleteWorldData(w.getId());
        groups.deleteWorldData(w.getId());
        mail.deleteWorldData(w.getId());
        npcLog.deleteWorldData(w.getId());
        npcIntel.deleteWorldData(w.getId());
        for (Village v : villages.findByWorld(w)) deleteVillageData(v);
        reports.deleteAll(reports.findByWorldIdOrderByOccurredAtDesc(w.getId()));
        // NPC accounts that only lived in this world go with it
        for (Account npc : accounts.findByNpc(true)) {
            if (villages.findByOwner(npc).isEmpty()) { npcProfiles.deleteAccountData(npc.getId()); npcLog.deleteAccountData(npc.getId()); npcIntel.deleteAccountData(npc.getId()); accounts.delete(npc); }
        }
        achievements.deleteWorldData(w.getId());
        stats.deleteWorldData(w.getId());
        nobles.deleteWorldData(w.getId());
        farm.deleteWorldData(w.getId());
        worlds.delete(w);
    }

    public record CompactResult(int villages, int radius) {}

    // Re-places every village of the world (players, NPCs, barbarians) into one dense disc around 500|500, like the
    // original's world map: WorldService.autoRadius for the current number of villages, random spots. Only the
    // coordinates change; marches on their way keep their arrival times.
    @Transactional
    public CompactResult compactWorld(Long id) {
        World w = world(id);
        List<Village> all = new ArrayList<>(villages.findByWorld(w));
        Collections.shuffle(all, new Random());
        int radius = WorldService.autoRadius(all.size());
        Set<String> taken = new HashSet<>();
        Random rnd = new Random();
        for (Village v : all) {
            int[] spot = WorldService.randomFreeSpot(taken, rnd, radius);
            v.setX(spot[0]);
            v.setY(spot[1]);
        }
        villages.saveAll(all);
        return new CompactResult(all.size(), radius);
    }

    // ---- players -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Player> players(Long worldId) {
        World w = world(worldId);
        Map<Long, List<Village>> byOwner = villages.findByWorld(w).stream()
                .filter(v -> v.getOwner() != null && v.getOwnerType() == OwnerType.PLAYER)
                .collect(Collectors.groupingBy(v -> v.getOwner().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Player> out = new ArrayList<>();
        for (var e : byOwner.entrySet()) {
            Account a = e.getValue().get(0).getOwner();
            int points = e.getValue().stream().mapToInt(this::approxPoints).sum();
            out.add(new Player(a.getId(), a.getUsername(), a.isNpc(), a.isAdmin(), e.getValue().size(), points,
                    e.getValue().stream().map(Village::getId).toList()));
        }
        return out;
    }

    private int approxPoints(Village v) {
        int sum = 0;
        for (Building b : buildings.findByVillage(v)) sum += b.getType().points(b.getLevel());
        return sum;
    }

    // ---- tribes --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TribeService.AdminTribe> tribes(Long worldId) { return tribes.adminOverview(world(worldId)); }

    @Transactional
    public void disbandTribe(Long worldId, Long tribeId) { tribes.disbandTribe(tribeId, world(worldId)); }

    @Transactional
    public void addTribeMember(Long worldId, Long tribeId, Long accountId) { tribes.adminAddMember(world(worldId), tribeId, accountId); }

    @Transactional
    public void removeTribeMember(Long worldId, Long accountId) {
        tribes.leaveWorld(account(accountId), world(worldId));
    }

    @Transactional
    public RemovePlayerResult removePlayer(Long worldId, Long accountId, RemovePlayerRequest req) {
        World w = world(worldId);
        Account account = accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Player was not found!"));
        List<Village> owned = villages.findByWorldAndOwner(w, account);
        if (owned.isEmpty()) throw new IllegalArgumentException("Player was not found!");
        String handling = req.villageHandling() == null ? "delete" : req.villageHandling();
        switch (handling) {
            case "delete" -> owned.forEach(this::deleteVillageData);
            case "transfer" -> {
                String to = req.transferTo() == null ? "" : req.transferTo().trim().toLowerCase();
                Account target = accounts.findByUsernameLower(to)
                        .orElseThrow(() -> new IllegalArgumentException("Given player '" + req.transferTo() + "' not found!"));
                if (target.getId().equals(account.getId())) throw new IllegalArgumentException("Choose another player");
                owned.forEach(v -> { v.setOwner(target); villages.save(v); });
            }
            case "barbarian" -> owned.forEach(v -> {
                v.setOwner(null);
                v.setOwnerType(OwnerType.BARBARIAN);
                v.setName("Abandoned Camp");
                villages.save(v);
            });
            default -> throw new IllegalArgumentException("Unknown village handling: " + handling);
        }
        if (villages.findByWorldAndOwner(w, account).isEmpty()) tribes.leaveWorld(account, w);
        if (account.isNpc() && villages.findByOwner(account).isEmpty()) { npcProfiles.deleteAccountData(account.getId()); npcLog.deleteAccountData(account.getId()); npcIntel.deleteAccountData(account.getId()); accounts.delete(account); }
        return new RemovePlayerResult(owned.size());
    }

    @Transactional
    public NpcResult createNpcs(Long worldId, NpcRequest req) {
        World w = world(worldId);
        int count = req.count();
        if (count < 1 || count > 200) throw new IllegalArgumentException("Create 1 to 200 NPC players at a time");
        String prefix = req.namePrefix() == null ? "" : req.namePrefix().trim();
        if (!prefix.isEmpty() && !prefix.matches("[A-Za-z0-9_.-]{1,40}")) {
            throw new IllegalArgumentException("The name prefix may only contain letters, digits, '_', '-' and '.'");
        }
        int min = clampPct(req.minDevelopment() == null ? 0 : req.minDevelopment());
        int max = clampPct(req.maxDevelopment() == null ? 40 : req.maxDevelopment());
        if (min > max) throw new IllegalArgumentException("The minimum development cannot be above the maximum");
        Random rnd = new Random();
        List<CreatedNpc> created = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < count; i++) {
            String name;
            if (prefix.isEmpty()) {
                name = NpcNames.generate(rnd, lower -> accounts.findByUsernameLower(lower).isPresent());
            } else {
                do {
                    seq++;
                    name = prefix + "_" + seq;
                } while (accounts.findByUsernameLower(name.toLowerCase()).isPresent());
            }
            Account npc = new Account();
            npc.setUsername(name);
            npc.setNpc(true);
            accounts.save(npc);
            npcProfiles.update(npc, req.archetype(), req.skill());
            double development = (min + (max - min) * rnd.nextDouble()) / 100.0;
            Village home = worldService.createVillageFromLayout(w, npc, VillageGenerator.generate(rnd, development), rnd);
            created.add(new CreatedNpc(npc.getId(), name, home.getId()));
        }
        return new NpcResult(created);
    }

    // ---- villages ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VillageRow> villages(Long worldId) {
        return villages.findByWorld(world(worldId)).stream()
                .map(v -> new VillageRow(v.getId(), v.getName(), v.getX(), v.getY(), v.getOwnerType().name(),
                        v.getOwner() == null ? null : v.getOwner().getUsername(), isNpc(v)))
                .toList();
    }

    @Transactional
    public BarbarianResult createBarbarians(Long worldId, BarbarianRequest req) {
        World w = world(worldId);
        if (req.amount() < 1 || req.amount() > 500) throw new IllegalArgumentException("Create 1 to 500 villages at a time");
        Map<String, Integer> b = req.buildings() == null ? Map.of() : req.buildings();
        Map<String, Integer> u = req.units() == null ? Map.of() : req.units();
        for (var e : b.entrySet()) {
            BuildingType t = buildingType(e.getKey());
            if (e.getValue() < 0 || e.getValue() > t.maxLevel) throw new IllegalArgumentException(t + " level must be 0-" + t.maxLevel);
        }
        for (var e : u.entrySet()) {
            unitType(e.getKey());
            if (e.getValue() < 0 || e.getValue() > 1_000_000) throw new IllegalArgumentException("Unit amounts must be 0-1000000");
        }
        Map<String, Double> res = req.resources() == null ? Map.of() : req.resources();
        Integer spread = req.spread() == null ? null : Math.max(2, Math.min(req.spread(), 500));

        Set<String> taken = villages.findByWorld(w).stream().map(v -> v.getX() + "|" + v.getY()).collect(Collectors.toCollection(HashSet::new));
        Random rnd = new Random();
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < req.amount(); i++) {
            int[] spot = spread == null ? WorldService.randomFreeSpot(taken, rnd) : WorldService.randomFreeSpot(taken, rnd, spread);
            int x = spot[0], y = spot[1];

            Village v = new Village();
            v.setName("Abandoned Camp");
            v.setWorld(w);
            v.setOwnerType(OwnerType.BARBARIAN);
            v.setX(x);
            v.setY(y);
            v.setBonusCode(BonusType.roll(rnd, w));
            v.setWood(res.getOrDefault("wood", 400.0));
            v.setClay(res.getOrDefault("clay", 400.0));
            v.setIron(res.getOrDefault("iron", 400.0));
            v.setResourcesSettledAt(Instant.now());
            villages.save(v);
            for (BuildingType t : BuildingType.values()) {
                worldService.addBuilding(v, t, b.getOrDefault(t.name(), t.startLevel()));
            }
            u.forEach((k, n) -> { if (n > 0) worldService.addUnits(v, unitType(k), n); });
            created.add(v.getId());
        }
        return new BarbarianResult(created);
    }

    @Transactional
    public RandomBarbarianResult createRandomBarbarians(Long worldId, RandomBarbarianRequest req) {
        World w = world(worldId);
        if (req.amount() < 1 || req.amount() > 500) throw new IllegalArgumentException("Create 1 to 500 villages at a time");
        int min = clampPct(req.minDevelopment() == null ? 0 : req.minDevelopment());
        int max = clampPct(req.maxDevelopment() == null ? 100 : req.maxDevelopment());
        if (min > max) throw new IllegalArgumentException("The minimum development cannot be above the maximum");
        Integer spread = req.spread() == null ? null : Math.max(2, Math.min(req.spread(), 500));

        Set<String> taken = villages.findByWorld(w).stream().map(v -> v.getX() + "|" + v.getY()).collect(Collectors.toCollection(HashSet::new));
        Random rnd = new Random();
        List<Long> created = new ArrayList<>();
        int lo = Integer.MAX_VALUE, hi = 0;
        long total = 0;
        for (int i = 0; i < req.amount(); i++) {
            int[] spot = spread == null ? WorldService.randomFreeSpot(taken, rnd) : WorldService.randomFreeSpot(taken, rnd, spread);
            int x = spot[0], y = spot[1];

            double development = (min + (max - min) * rnd.nextDouble()) / 100.0;
            VillageGenerator.Layout layout = VillageGenerator.generate(rnd, development);
            Village v = new Village();
            v.setName("Abandoned Camp");
            v.setWorld(w);
            v.setOwnerType(OwnerType.BARBARIAN);
            v.setX(x);
            v.setY(y);
            v.setBonusCode(BonusType.roll(rnd, w));
            v.setWood(layout.wood());
            v.setClay(layout.clay());
            v.setIron(layout.iron());
            v.setResourcesSettledAt(Instant.now());
            villages.save(v);
            layout.buildings().forEach((t, level) -> worldService.addBuilding(v, t, level));
            // Freshly generated barbarians never hold troops (only ones converted from an abandoned
            // player village do) - skip layout.units() here, buildings/resources only.
            created.add(v.getId());
            int pts = layout.points();
            lo = Math.min(lo, pts);
            hi = Math.max(hi, pts);
            total += pts;
        }
        return new RandomBarbarianResult(created, lo, hi, (int) (total / req.amount()));
    }

    // Nothing is saved — used for the form's preview.
    public RandomLayout previewRandom(Integer development) {
        int pct = clampPct(development == null ? 50 : development);
        VillageGenerator.Layout l = VillageGenerator.generate(new Random(), pct / 100.0);
        Map<String, Integer> b = new LinkedHashMap<>();
        l.buildings().forEach((t, lvl) -> b.put(t.name(), lvl));
        Map<String, Integer> u = new LinkedHashMap<>();
        for (UnitType t : UnitType.values()) u.put(t.name(), l.units().getOrDefault(t, 0));
        return new RandomLayout(pct, b, u, l.wood(), l.clay(), l.iron(), l.points());
    }

    private static int clampPct(int v) { return Math.max(0, Math.min(100, v)); }

    @Transactional(readOnly = true)
    public VillageDetail village(Long id) {
        return detail(village0(id));
    }

    @Transactional
    public VillageDetail updateVillage(Long id, VillageUpdate req) {
        Village v = village0(id);
        if (req.name() != null) {
            if (req.name().isBlank() || req.name().length() > 60) throw new IllegalArgumentException("Village names need 1-60 characters");
            v.setName(req.name().trim());
        }
        if (req.wood() != null) v.setWood(nonNegative(req.wood()));
        if (req.clay() != null) v.setClay(nonNegative(req.clay()));
        if (req.iron() != null) v.setIron(nonNegative(req.iron()));
        if (req.bonus() != null) {
            if (req.bonus() != 0 && BonusType.byCode(req.bonus()) == null) throw new IllegalArgumentException("Unknown bonus " + req.bonus());
            v.setBonusCode(req.bonus() == 0 ? null : req.bonus());
        }
        villages.save(v);
        if (req.buildings() != null) {
            for (var e : req.buildings().entrySet()) {
                BuildingType t = buildingType(e.getKey());
                if (e.getValue() < 0 || e.getValue() > t.maxLevel) throw new IllegalArgumentException(t + " level must be 0-" + t.maxLevel);
                Building row = buildings.findByVillageAndType(v, t).orElseGet(() -> {
                    Building nb = new Building();
                    nb.setVillage(v);
                    nb.setType(t);
                    return nb;
                });
                row.setLevel(e.getValue());
                buildings.save(row);
            }
        }
        if (req.units() != null) {
            for (var e : req.units().entrySet()) {
                UnitType t = unitType(e.getKey());
                if (e.getValue() < 0 || e.getValue() > 1_000_000) throw new IllegalArgumentException("Unit amounts must be 0-1000000");
                UnitStock row = unitStock.findByVillageAndType(v, t).orElseGet(() -> {
                    UnitStock ns = new UnitStock();
                    ns.setVillage(v);
                    ns.setType(t);
                    return ns;
                });
                row.setCount(e.getValue());
                unitStock.save(row);
            }
        }
        return detail(v);
    }

    @Transactional
    public FinishResult finishQueues(Long id) {
        Village v = village0(id);
        int builds = 0;
        for (BuildQueueItem q : buildQueue.findByVillageOrderByPositionAsc(v)) {
            Building row = buildings.findByVillageAndType(v, q.getType()).orElseGet(() -> {
                Building nb = new Building();
                nb.setVillage(v);
                nb.setType(q.getType());
                return nb;
            });
            row.setLevel(Math.max(row.getLevel(), q.getTargetLevel()));
            buildings.save(row);
            buildQueue.delete(q);
            builds++;
        }
        int trainings = 0;
        for (TrainQueueItem t : trainQueue.findByVillageOrderByPositionAsc(v)) {
            int remaining = t.getTotalCount() - t.getProducedCount();
            if (remaining > 0) worldService.addUnitsTo(v, t.getType(), remaining);
            trainQueue.delete(t);
            trainings++;
        }
        research.finishAll(v);
        return new FinishResult(builds, trainings);
    }

    @Transactional
    public void deleteVillage(Long id) {
        deleteVillageData(village0(id));
    }

    void deleteVillageData(Village v) {
        groups.dropVillage(v.getId());
        market.dropVillage(v.getId());
        npcIntel.dropVillage(v.getId());
        support.deleteVillageData(v);
        movements.deleteAll(movements.findByOriginVillageOrTargetVillage(v, v));
        buildQueue.deleteAll(buildQueue.findByVillageOrderByPositionAsc(v));
        trainQueue.deleteAll(trainQueue.findByVillageOrderByPositionAsc(v));
        research.deleteVillageData(v);
        unitStock.deleteAll(unitStock.findByVillage(v));
        buildings.deleteAll(buildings.findByVillage(v));
        villages.delete(v);
    }

    private VillageDetail detail(Village v) {
        Map<String, Integer> b = new LinkedHashMap<>();
        for (BuildingType t : BuildingType.values()) b.put(t.name(), villageService.levelOf(v, t));
        Map<String, Integer> u = new LinkedHashMap<>();
        for (UnitType t : UnitType.values()) u.put(t.name(), unitStock.findByVillageAndType(v, t).map(UnitStock::getCount).orElse(0));
        return new VillageDetail(v.getId(), v.getName(), v.getX(), v.getY(),
                v.getWorld() == null ? null : v.getWorld().getId(), v.getWorld() == null ? null : v.getWorld().getName(),
                v.getOwnerType().name(), v.getOwner() == null ? null : v.getOwner().getUsername(),
                v.getWood(), v.getClay(), v.getIron(), b, u,
                buildQueue.findByVillageOrderByPositionAsc(v).stream().map(q -> new QueuedBuild(q.getType().name(), q.getTargetLevel())).toList(),
                trainQueue.findByVillageOrderByPositionAsc(v).stream()
                        .map(t -> new QueuedTraining(t.getType().name(), t.getTotalCount() - t.getProducedCount())).toList(),
                v.getBonusCode());
    }

    // ---- accounts ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AccountRow> accounts() {
        List<Village> all = villages.findAll();
        return accounts.findAll().stream().map(a -> {
            Map<Long, Long> perWorld = all.stream().filter(v -> v.getOwner() != null && v.getOwner().getId().equals(a.getId()) && v.getWorld() != null)
                    .collect(Collectors.groupingBy(v -> v.getWorld().getId(), LinkedHashMap::new, Collectors.counting()));
            List<WorldMembership> ms = perWorld.entrySet().stream()
                    .map(e -> new WorldMembership(e.getKey(), worlds.findById(e.getKey()).map(World::getName).orElse("?"), e.getValue().intValue()))
                    .toList();
            return new AccountRow(a.getId(), a.getUsername(), a.isAdmin(), a.isNpc(), a.getCreatedAt(), ms);
        }).toList();
    }

    @Transactional
    public AccountRow setAdmin(Long id, boolean admin, Account actor) {
        Account a = account(id);
        if (a.isNpc()) throw new IllegalArgumentException("NPC players cannot be admins");
        if (!admin && a.getId().equals(actor.getId())) throw new IllegalArgumentException("You cannot remove your own admin rights");
        a.setAdmin(admin);
        accounts.save(a);
        return accounts().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void setPassword(Long id, String password) {
        Account a = account(id);
        if (a.isNpc()) throw new IllegalArgumentException("NPC players have no password");
        if (password == null || password.length() < 4 || password.length() > 50) throw new IllegalArgumentException("The password must be 4 to 50 characters long.");
        a.setPasswordHash(PasswordHasher.hash(password));
        accounts.save(a);
        sessions.deleteByAccountId(a.getId());
    }

    @Transactional
    public void deleteAccount(Long id, Account actor) {
        Account a = account(id);
        if (a.getId().equals(actor.getId())) throw new IllegalArgumentException("You cannot delete your own account");
        tribes.deleteAccountData(a);
        groups.deleteAccountData(a.getId());
        mail.deleteAccountData(a.getId());
        villages.findByOwner(a).forEach(this::deleteVillageData);
        sessions.deleteByAccountId(a.getId());
        achievements.deleteAccountData(a.getId());
        stats.deleteAccountData(a.getId());
        nobles.deleteAccountData(a.getId());
        farm.deleteAccountData(a.getId());
        npcProfiles.deleteAccountData(a.getId());
        npcLog.deleteAccountData(a.getId());
        npcIntel.deleteAccountData(a.getId());
        accounts.delete(a);
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private boolean isNpc(Village v) { return v.getOwner() != null && v.getOwner().isNpc(); }
    private double nonNegative(double d) { if (d < 0 || d > 1e9) throw new IllegalArgumentException("Resources must be 0-1000000000"); return d; }
    private World world(Long id) { return worlds.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown world " + id)); }
    private Village village0(Long id) { return villages.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown village " + id)); }
    private Account account(Long id) { return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown account " + id)); }

    private BuildingType buildingType(String s) {
        try { return BuildingType.valueOf(s); } catch (RuntimeException e) { throw new IllegalArgumentException("Unknown building type: " + s); }
    }

    private UnitType unitType(String s) {
        try { return UnitType.valueOf(s); } catch (RuntimeException e) { throw new IllegalArgumentException("Unknown unit type: " + s); }
    }
}
