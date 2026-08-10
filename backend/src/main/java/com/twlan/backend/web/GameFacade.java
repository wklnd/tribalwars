package com.twlan.backend.web;

import com.twlan.backend.domain.*;
import com.twlan.backend.repo.*;
import com.twlan.backend.service.AuthService;
import com.twlan.backend.service.GameSettings;
import com.twlan.backend.service.MailService;
import com.twlan.backend.service.MarketService;
import com.twlan.backend.service.MovementService;
import com.twlan.backend.service.NobleService;
import com.twlan.backend.service.ResearchService;
import com.twlan.backend.service.ForumService;
import com.twlan.backend.service.SupportService;
import com.twlan.backend.service.TrainService;
import com.twlan.backend.service.WorldSettings;
import com.twlan.backend.service.VillageService;
import com.twlan.backend.web.dto.ReportDto;
import com.twlan.backend.web.dto.VillageStateDto;
import com.twlan.backend.web.dto.VillageStateDto.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GameFacade {

    private final VillageRepository villageRepository;
    private final BuildingRepository buildingRepository;
    private final BuildQueueItemRepository buildQueueItemRepository;
    private final UnitStockRepository unitStockRepository;
    private final TrainQueueItemRepository trainQueueItemRepository;
    private final MovementRepository movementRepository;
    private final CombatReportRepository combatReportRepository;
    private final VillageService villageService;
    private final GameSettings settings;
    private final WorldRepository worldRepository;
    private final TrainService trainService;
    private final NobleService nobleService;
    private final ResearchService researchService;
    private final PaladinProfileRepository paladinProfileRepository;
    private final SupportService supportService;
    private final ForumService forumService;
    private final MailService mailService;
    private final MarketService marketService;

    public GameFacade(VillageRepository villageRepository, BuildingRepository buildingRepository,
                       BuildQueueItemRepository buildQueueItemRepository, UnitStockRepository unitStockRepository,
                       TrainQueueItemRepository trainQueueItemRepository, MovementRepository movementRepository,
                       CombatReportRepository combatReportRepository, VillageService villageService,
                       GameSettings settings, WorldRepository worldRepository, TrainService trainService,
                       PaladinProfileRepository paladinProfileRepository, NobleService nobleService,
                       ResearchService researchService, SupportService supportService, ForumService forumService, MarketService marketService, MailService mailService) {
        this.marketService = marketService;
        this.forumService = forumService;
        this.mailService = mailService;
        this.supportService = supportService;
        this.researchService = researchService;
        this.nobleService = nobleService;
        this.paladinProfileRepository = paladinProfileRepository;
        this.trainService = trainService;
        this.settings = settings;
        this.worldRepository = worldRepository;
        this.villageRepository = villageRepository;
        this.buildingRepository = buildingRepository;
        this.buildQueueItemRepository = buildQueueItemRepository;
        this.unitStockRepository = unitStockRepository;
        this.trainQueueItemRepository = trainQueueItemRepository;
        this.movementRepository = movementRepository;
        this.combatReportRepository = combatReportRepository;
        this.villageService = villageService;
    }

    // X-World-Id header, else the first world.
    public World currentWorld() {
        Long id = WorldContext.get();
        if (id != null) {
            return worldRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown world " + id));
        }
        return worldRepository.findAllByOrderByIdAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No world exists"));
    }

    public static class NotJoinedException extends RuntimeException {
        public NotJoinedException(String message) { super(message); }
    }

    // Honours X-Village-Id (via VillageContext) when set, else falls back to the lowest-id village;
    // the account must have joined the world first.
    public Village playerHomeVillage() {
        World world = currentWorld();
        Account account = AccountContext.get();
        if (account == null) {
            throw new AuthService.AuthException("Please log in.");
        }
        List<Village> owned = villageRepository.findByWorldAndOwner(world, account);
        Long wanted = VillageContext.get();
        if (wanted != null) {
            for (Village v : owned) if (v.getId().equals(wanted)) return v;
        }
        return owned.stream().min(java.util.Comparator.comparing(Village::getId))
                .orElseThrow(() -> new NotJoinedException("You have not joined " + world.getName() + " yet."));
    }

    public Village ownVillage(Long id) {
        Account account = AccountContext.get();
        if (account == null) throw new AuthService.AuthException("Please log in.");
        return villageRepository.findByWorldAndOwner(currentWorld(), account).stream()
                .filter(v -> v.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That is not your village."));
    }

    @Transactional
    public VillageStateDto toDto(Village village) {
        villageService.settleResources(village);
        villageRepository.save(village);

        int hqLevel = villageService.levelOf(village, BuildingType.HEADQUARTERS);
        double hqTimeFactor = WorldSettings.number(village.getWorld(), "buildMainFactor");
        List<BuildQueueItem> queue = buildQueueItemRepository.findByVillageOrderByPositionAsc(village);

        List<BuildingDto> buildings = Arrays.stream(BuildingType.values()).map(type -> {
            int level = villageService.levelOf(village, type);
            long queuedOfType = queue.stream().filter(q -> q.getType() == type).count();
            int targetLevel = level + (int) queuedOfType + 1;
            boolean maxed = targetLevel > type.maxLevel;
            return new BuildingDto(type.name(), level, type.maxLevel, maxed,
                    maxed ? 0 : type.woodCost(targetLevel),
                    maxed ? 0 : type.clayCost(targetLevel),
                    maxed ? 0 : type.ironCost(targetLevel),
                    maxed ? 0 : type.buildTimeSeconds(targetLevel, hqLevel, settings.speedOf(village), hqTimeFactor),
                    maxed ? 0 : type.popIncrease(targetLevel),
                    type.requirements().entrySet().stream()
                            .allMatch(r -> villageService.levelOf(village, r.getKey()) >= r.getValue()));
        }).toList();

        List<BuildQueueDto> buildQueueDtos = queue.stream()
                .map(q -> new BuildQueueDto(q.getType().name(), q.getTargetLevel(), q.getStartedAt(), q.getCompletesAt(), q.getId(),
                        q.getType().buildTimeSeconds(q.getTargetLevel(), hqLevel, settings.speedOf(village), hqTimeFactor)))
                .toList();

        Map<String, Integer> units = new LinkedHashMap<>();
        for (UnitStock stock : unitStockRepository.findByVillage(village)) {
            units.put(stock.getType().name(), stock.getCount());
        }

        List<TrainQueueDto> trainQueueDtos = trainQueueItemRepository.findByVillageOrderByPositionAsc(village).stream()
                .map(t -> new TrainQueueDto(t.getType().name(), t.getTotalCount(), t.getProducedCount(), t.getStartedAt(), t.getCompletesAt(), t.getId(), t.getPerUnitSeconds()))
                .toList();

        List<UnitCostDto> unitCosts = Arrays.stream(UnitType.values())
                .map(t -> new UnitCostDto(t.name(), t.woodCost, t.clayCost, t.ironCost, t.popCost, t.attack, t.defense,
                        t.speedMinutesPerField, t.carryCapacity, t.defenseCavalry, t.defenseArcher))
                .toList();

        List<MovementDto> outgoing = movementRepository.findByOriginVillageOrTargetVillage(village, village).stream()
                .filter(m -> m.getType() != MovementType.RETURN && m.getOriginVillage().getId().equals(village.getId()))
                .map(this::toOutgoingDto)
                .toList();

        List<MovementDto> incoming = movementRepository.findByOriginVillageOrTargetVillage(village, village).stream()
                .filter(m -> m.getTargetVillage().getId().equals(village.getId())
                        && (m.getType() == MovementType.RETURN || m.getType() == MovementType.SUPPORT
                        || (m.getType() == MovementType.ATTACK && !MovementService.isSameOwner(m.getOriginVillage(), village))))
                .map(m -> m.getType() == MovementType.ATTACK
                        // what an enemy sends is not shown, only that it is coming
                        ? new MovementDto(m.getId(), "INCOMING_ATTACK", m.getOriginVillage().getName(), m.getArrivesAt(), Map.of(),
                                m.getOriginVillage().getId(), m.getTargetVillage().getId(), m.getDepartedAt(), 0, 0, 0)
                        : new MovementDto(m.getId(), m.getType() == MovementType.RETURN ? "RETURNING" : "SUPPORT_IN",
                                m.getOriginVillage().getName(), m.getArrivesAt(), stringifyUnits(m.getUnits()),
                                m.getOriginVillage().getId(), m.getTargetVillage().getId(), m.getDepartedAt(),
                                m.getCarriedWood(), m.getCarriedClay(), m.getCarriedIron()))
                .toList();

        TrainService.PaladinStatus paladin = trainService.paladinStatus(village);
        return new VillageStateDto(
                village.getId(), village.getName(), village.getX(), village.getY(),
                village.getWood(), village.getClay(), village.getIron(),
                villageService.warehouseCapacity(village),
                villageService.populationUsed(village), villageService.populationCapacity(village),
                BuildingType.TIMBER_CAMP.productionPerHour(villageService.levelOf(village, BuildingType.TIMBER_CAMP)) * BonusType.productionFactor(village, BuildingType.TIMBER_CAMP) * settings.speedOf(village),
                BuildingType.CLAY_PIT.productionPerHour(villageService.levelOf(village, BuildingType.CLAY_PIT)) * BonusType.productionFactor(village, BuildingType.CLAY_PIT) * settings.speedOf(village),
                BuildingType.IRON_MINE.productionPerHour(villageService.levelOf(village, BuildingType.IRON_MINE)) * BonusType.productionFactor(village, BuildingType.IRON_MINE) * settings.speedOf(village),
                buildings, buildQueueDtos, units, trainQueueDtos, unitCosts, outgoing, incoming,
                settings.speedOf(village),
                WorldSettings.number(village.getWorld(), "prodToSeconds"), WorldSettings.number(village.getWorld(), "prodToMinutes"),
                settings.worldNameOf(village),
                village.getWorld() == null ? null : village.getWorld().getId(),
                village.getOwner() == null ? "Player" : village.getOwner().getUsername(),
                village.getOwner() == null ? PaladinProfile.DEFAULT_NAME
                        : paladinProfileRepository.findById(village.getOwner().getId()).map(PaladinProfile::getName).orElse(PaladinProfile.DEFAULT_NAME),
                paladin == null ? null : paladin.state(),
                paladin == null ? null : paladin.at().getName(),
                WorldSettings.isNight(village.getWorld(), java.time.LocalTime.now()),
                (int) Math.ceil(villageService.loyaltyOf(village)),
                village.getOwner() == null ? null : toNobleDto(nobleService.info(village.getOwner(), village.getWorld())),
                ownVillages(village),
                researchList(village),
                researchService.queueOf(village).stream()
                        .map(q -> new ResearchQueueDto(q.getId(), q.getUnit().name(), q.getDurationSeconds(), q.getStartedAt(), q.getCompletesAt()))
                        .toList(),
                supportService.guestsAt(village).stream().map(g -> army(g, g.getOriginVillage())).toList(),
                supportService.stationedFrom(village).stream().map(g -> army(g, g.getHostVillage())).toList(),
                village.getOwner() != null && village.getWorld() != null && forumService.hasUnread(village.getOwner(), village.getWorld()),
                new MerchantsDto(marketService.totalMerchants(village), marketService.availableMerchants(village), MarketService.CAPACITY),
                (int) mailService.unreadCount(village.getOwner(), village.getWorld()),
                bonusCode(village)
        );
    }

    static Integer bonusCode(Village village) {
        BonusType b = BonusType.of(village);
        return b == null ? null : b.code;
    }

    private ArmyDto army(StationedTroops troops, Village other) {
        return new ArmyDto(troops.getId(), other.getId(), other.getName(), other.getX(), other.getY(),
                other.getOwner() == null ? null : other.getOwner().getUsername(), stringifyUnits(troops.getUnits()));
    }

    private List<ResearchDto> researchList(Village village) {
        return ResearchService.TECHS.values().stream()
                .map(t -> new ResearchDto(t.unit().name(), researchService.isResearched(village, t.unit()),
                        t.unit().requirementsMet(b -> villageService.levelOf(village, b)),
                        t.wood(), t.clay(), t.iron(), researchService.secondsFor(village, t.unit())))
                .toList();
    }

    private NobleDto toNobleDto(NobleService.Info i) {
        return new NobleDto(i.coins(), i.limit(), i.existing(), i.inProduction(), i.conquered(), i.possible(),
                i.coinsMissing(), i.coinsAlready(), i.costWood(), i.costClay(), i.costIron());
    }

    // Feeds the village switcher and the combined overview.
    private List<OwnVillageDto> ownVillages(Village current) {
        if (current.getOwner() == null || current.getWorld() == null) return List.of();
        List<OwnVillageDto> out = new java.util.ArrayList<>();
        for (Village v : villageRepository.findByWorldAndOwner(current.getWorld(), current.getOwner())) {
            int points = 0;
            for (Building b : buildingRepository.findByVillage(v)) points += b.getType().points(b.getLevel());
            if (v.getId().equals(current.getId())) {
                // the current village was settled just above; the others are shown as of their last settlement plus accrued production
            } else {
                villageService.settleResources(v);
                villageRepository.save(v);
            }
            Map<String, Integer> home = new LinkedHashMap<>();
            for (UnitStock stock : unitStockRepository.findByVillage(v)) home.put(stock.getType().name(), stock.getCount());
            out.add(new OwnVillageDto(v.getId(), v.getName(), v.getX(), v.getY(), points, v.getWood(), v.getClay(), v.getIron(),
                    villageService.warehouseCapacity(v), villageService.populationUsed(v), villageService.populationCapacity(v),
                    (int) Math.ceil(villageService.loyaltyOf(v)), villageService.levelOf(v, BuildingType.FARM), home, bonusCode(v)));
        }
        out.sort(java.util.Comparator.comparing(OwnVillageDto::name).thenComparing(OwnVillageDto::id));
        return out;
    }

    private MovementDto toOutgoingDto(Movement m) {
        return new MovementDto(m.getId(), m.getType() == MovementType.SUPPORT ? "SUPPORTING" : "ATTACKING", m.getTargetVillage().getName(), m.getArrivesAt(), stringifyUnits(m.getUnits()),
                m.getOriginVillage().getId(), m.getTargetVillage().getId(), m.getDepartedAt(), 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> listReports() {
        return combatReportRepository.findByWorldIdAndAccountIdOrderByOccurredAtDesc(currentWorld().getId(), AccountContext.get().getId()).stream()
                .map(this::toReportDto)
                .toList();
    }

    private ReportDto toReportDto(CombatReport r) {
        Map<String, Integer> d = r.getDetail();
        Map<String, Integer> attackerUnits = section(d, "att:");
        Map<String, Integer> defenderUnits = section(d, "def:");
        ReportDto.SpyDto spy = d.containsKey("spy_level")
                ? new ReportDto.SpyDto(d.get("spy_level"), d.getOrDefault("spy_wood", 0), d.getOrDefault("spy_clay", 0),
                        d.getOrDefault("spy_iron", 0), section(d, "spy_building:"), section(d, "spy_home:"), section(d, "spy_away:"))
                : null;
        return new ReportDto(
                r.getId(), r.getAttackerVillageName(), r.getDefenderVillageName(), r.getOutcome().name(),
                stringifyUnitCounts(r.getAttackerLosses()), stringifyUnitCounts(r.getDefenderLosses()),
                r.getLootWood(), r.getLootClay(), r.getLootIron(), r.getOccurredAt(),
                r.getLoyaltyFrom(), r.getLoyaltyTo(), r.isConquered(),
                d.isEmpty() ? null : attackerUnits, d.isEmpty() ? null : defenderUnits,
                d.get("luck"), d.get("morale"), d.get("wall_before"), d.get("wall_after"),
                section(d, "dmg:"), d.get("capacity"), spy,
                r.isDefenderView(), r.getAttackerPlayer(), r.getDefenderPlayer(),
                d.containsKey("att_village") ? d.get("att_village").longValue() : null,
                d.containsKey("def_village") ? d.get("def_village").longValue() : null);
    }

    private static Map<String, Integer> section(Map<String, Integer> detail, String prefix) {
        Map<String, Integer> out = new LinkedHashMap<>();
        detail.forEach((k, v) -> { if (k.startsWith(prefix)) out.put(k.substring(prefix.length()), v); });
        return out;
    }

    private Map<String, Integer> stringifyUnitCounts(Map<UnitType, Integer> units) {
        Map<String, Integer> out = new LinkedHashMap<>();
        units.forEach((k, v) -> out.put(k.name(), v));
        return out;
    }

    private Map<String, Integer> stringifyUnits(Map<UnitType, Integer> units) {
        Map<String, Integer> out = new LinkedHashMap<>();
        units.forEach((k, v) -> out.put(k.name(), v));
        return out;
    }
}
