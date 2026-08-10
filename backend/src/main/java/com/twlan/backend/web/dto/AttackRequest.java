package com.twlan.backend.web.dto;

import java.util.Map;

public record AttackRequest(Long targetVillageId, Map<String, Integer> units) {}
