package com.twlan.backend.web.dto;

import java.time.Instant;
import java.util.Map;

// Everything after conquered is null/empty on reports written before it existed.
public record ReportDto(
        Long id,
        String attackerVillageName,
        String defenderVillageName,
        String outcome,
        Map<String, Integer> attackerLosses,
        Map<String, Integer> defenderLosses,
        double lootWood,
        double lootClay,
        double lootIron,
        Instant occurredAt,
        Integer loyaltyFrom,
        Integer loyaltyTo,
        boolean conquered,
        Map<String, Integer> attackerUnits,
        Map<String, Integer> defenderUnits,
        Integer luck,
        Integer morale,
        Integer wallBefore,
        Integer wallAfter,
        Map<String, Integer> buildingDamage,
        Integer lootCapacity,
        SpyDto spy,
        boolean defenderView,
        String attackerPlayer,
        String defenderPlayer,
        Long attackerVillageId,
        Long defenderVillageId
) {
    // What the surviving scouts saw (level 1 resources, 2 + buildings, 3 + troops at home, 4 + troops away).
    public record SpyDto(int level, int wood, int clay, int iron, Map<String, Integer> buildings,
                         Map<String, Integer> unitsHome, Map<String, Integer> unitsAway) {}
}
