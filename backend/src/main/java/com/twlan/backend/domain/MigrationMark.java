package com.twlan.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Marks a one-time data migration that has already run on this save; name is the key.
@Entity
@Table(name = "migration_mark")
public class MigrationMark {

    @Id
    private String name;

    public MigrationMark() {}
    public MigrationMark(String name) { this.name = name; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
