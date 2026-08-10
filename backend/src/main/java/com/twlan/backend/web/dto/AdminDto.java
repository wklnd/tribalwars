package com.twlan.backend.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminDto {
    private AdminDto() {}

    public record Backup(String name, long sizeBytes, Instant createdAt) {}

    public record VillageCounts(long player, long npc, long barbarian) {}

    public record Dashboard(long accounts, long npcs, long worlds, VillageCounts villages, long queuedBuilds,
                            long queuedTrainings, long movements, long uptimeSeconds, String javaVersion,
                            long dbSizeBytes, List<Backup> backups) {}

    public record BuildingInfo(String type, int maxLevel, int startLevel, Map<String, Integer> requirements) {}
    public record UnitInfo(String type) {}
    public record Catalog(List<BuildingInfo> buildings, List<UnitInfo> units, List<com.twlan.backend.service.WorldSettings.Setting> settings) {}

    public record WorldRow(Long id, String name, double speed, Instant createdAt, long players, long npcs, long villages,
                        long barbarians, Map<String, String> settings) {}
    public record WorldRequest(String name, Double speed, Map<String, String> settings) {}

    public record Player(Long accountId, String name, boolean npc, boolean admin, int villages, int points, List<Long> villageIds) {}
    public record RemovePlayerRequest(String villageHandling, String transferTo) {}
    public record RemovePlayerResult(int removedVillages) {}

    public record NpcRequest(int count, String namePrefix, Integer minDevelopment, Integer maxDevelopment, String archetype, Integer skill) {}
    public record CreatedNpc(Long accountId, String name, Long villageId) {}
    public record NpcResult(List<CreatedNpc> created) {}

    public record VillageRow(Long id, String name, int x, int y, String ownerType, String ownerName, boolean npc) {}

    public record BarbarianRequest(int amount, Map<String, Integer> buildings, Map<String, Integer> units,
                                   Map<String, Double> resources, Integer spread) {}
    public record BarbarianResult(List<Long> created) {}

    public record RandomBarbarianRequest(int amount, Integer minDevelopment, Integer maxDevelopment, Integer spread) {}
    public record RandomPreviewRequest(Integer development) {}
    public record RandomLayout(int development, Map<String, Integer> buildings, Map<String, Integer> units,
                               int wood, int clay, int iron, int points) {}
    public record RandomBarbarianResult(List<Long> created, int minPoints, int maxPoints, int avgPoints) {}

    public record QueuedBuild(String type, int targetLevel) {}
    public record QueuedTraining(String type, int count) {}
    public record VillageDetail(Long id, String name, int x, int y, Long worldId, String worldName, String ownerType,
                                String ownerName, double wood, double clay, double iron, Map<String, Integer> buildings,
                                Map<String, Integer> units, List<QueuedBuild> buildQueue, List<QueuedTraining> trainQueue, Integer bonus) {}
    // bonus is a BonusType code; 0 removes the bonus, null leaves it as it is.
    public record VillageUpdate(String name, Double wood, Double clay, Double iron, Map<String, Integer> buildings,
                                Map<String, Integer> units, Integer bonus) {}
    public record FinishResult(int finishedBuilds, int finishedTrainings) {}

    public record WorldMembership(Long worldId, String worldName, int villages) {}
    public record AccountRow(Long id, String username, boolean admin, boolean npc, Instant createdAt, List<WorldMembership> worlds) {}
    public record AdminFlag(boolean admin) {}
    public record PasswordRequest(String password) {}
}
