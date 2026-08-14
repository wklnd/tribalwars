package se.oscarwiklund.twlan2.backend.web.dto;

import java.util.Map;

public record AttackRequest(Long targetVillageId, Map<String, Integer> units) {}
