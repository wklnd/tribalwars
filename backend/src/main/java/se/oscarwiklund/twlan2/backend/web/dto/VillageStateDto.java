package se.oscarwiklund.twlan2.backend.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VillageStateDto(
        Long id,
        String name,
        int x,
        int y,
        double wood,
        double clay,
        double iron,
        int warehouseCapacity,
        int populationUsed,
        int populationCapacity,
        double woodPerHour,
        double clayPerHour,
        double ironPerHour,
        List<BuildingDto> buildings,
        List<BuildQueueDto> buildQueue,
        Map<String, Integer> units,
        List<TrainQueueDto> trainQueue,
        List<UnitCostDto> unitCosts,
        List<MovementDto> outgoingMovements,
        List<MovementDto> incomingMovements,
        double worldSpeed,
        double prodToSeconds,
        double prodToMinutes,
        String worldName,
        Long worldId,
        String playerName,
        String paladinName,
        String paladinState,
        String paladinVillageName,
        boolean night,
        int loyalty,
        NobleDto nobles,
        List<OwnVillageDto> myVillages,
        List<ResearchDto> research,
        List<ResearchQueueDto> researchQueue,
        List<ArmyDto> stationed,
        List<ArmyDto> supporting,
        boolean newForumPost,
        MerchantsDto merchants,
        int newMails,
        Integer bonus
) {
    // Troops of a tribe-mate stationed here (stationed, village = where they come from) or of this
    // village stationed elsewhere (supporting, village = where they are).
    public record ArmyDto(Long id, Long villageId, String villageName, int x, int y, String player, Map<String, Integer> units) {}

    // researchable = its building requirements are met.
    public record ResearchDto(String type, boolean researched, boolean researchable, int wood, int clay, int iron, long seconds) {}

    public record ResearchQueueDto(Long id, String type, long durationSeconds, Instant startedAt, Instant completesAt) {}

    public record NobleDto(int coins, int limit, int existing, int inProduction, int conquered, int possible,
                           int coinsMissing, int coinsAlready, int costWood, int costClay, int costIron) {}

    public record OwnVillageDto(Long id, String name, int x, int y, int points, double wood, double clay, double iron,
                                int warehouseCapacity, int populationUsed, int populationCapacity, int loyalty,
                                int farmLevel, Map<String, Integer> units, Integer bonus) {}

    public record BuildingDto(String type, int level, int maxLevel, boolean maxedOrQueued,
                               int nextWood, int nextClay, int nextIron, long nextSeconds,
                               int nextPop, boolean requirementsMet) {}

    public record BuildQueueDto(String type, int targetLevel, Instant startedAt, Instant completesAt, Long id, long durationSeconds) {}

    public record TrainQueueDto(String type, int totalCount, int producedCount, Instant startedAt, Instant completesAt, Long id, long perUnitSeconds) {}

    public record UnitCostDto(String type, int wood, int clay, int iron, int pop, int attack, int defense,
                               int speedMinutesPerField, int carryCapacity, int defenseCavalry, int defenseArcher) {}

    // total from the market level, available = not away on a trip, capacity = load per merchant.
    public record MerchantsDto(int total, int available, int capacity) {}

    public record MovementDto(Long id, String type, String otherVillageName, Instant arrivesAt, Map<String, Integer> units,
                              Long originVillageId, Long targetVillageId, Instant departedAt, double wood, double clay, double iron) {}
}
