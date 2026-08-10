package com.twlan.backend.web.dto;

public record AccountDto(Long id, String username, String token, boolean admin) {}
