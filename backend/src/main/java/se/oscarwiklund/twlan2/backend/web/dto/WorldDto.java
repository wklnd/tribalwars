package se.oscarwiklund.twlan2.backend.web.dto;

import java.time.Instant;

public record WorldDto(Long id, String name, double speed, Instant createdAt, boolean joined, String description, boolean closed) {}
