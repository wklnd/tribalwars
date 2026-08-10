package com.twlan.backend.domain;

import jakarta.persistence.*;

// A table of its own (rather than a column on Account) because adding a column to the existing account table
// made H2 misread an ENUM column in the first session after the change.
@Entity
@Table(name = "paladin_profile")
public class PaladinProfile {

    public static final String DEFAULT_NAME = "Paladin";

    @Id
    private Long accountId;

    private String name;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getName() { return name == null || name.isBlank() ? DEFAULT_NAME : name; }
    public void setName(String name) { this.name = name; }
}
