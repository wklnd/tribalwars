package com.twlan.backend.web.dto;

// ownerTribeId/ownerTribeTag are null for barbarian villages and players without a tribe;
// ownerNpc is true when the village belongs to a computer player; bonus is the BonusType code of
// its bonus (null = none).
public record VillageSummaryDto(Long id, String name, int x, int y, String ownerType, String ownerName, int points,
                                Long ownerTribeId, String ownerTribeTag, boolean ownerNpc, Integer bonus) {}
