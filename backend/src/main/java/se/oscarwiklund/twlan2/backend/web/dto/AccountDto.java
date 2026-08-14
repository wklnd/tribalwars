package se.oscarwiklund.twlan2.backend.web.dto;

public record AccountDto(Long id, String username, String token, boolean admin) {}
