package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// speed multiplies production and divides construction/recruitment/travel times in this world.
@Entity
@Table(name = "world")
public class World {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private double speed = 1.0;
    private Instant createdAt = Instant.now();

    // key -> value overrides; missing keys use WorldSettings' defaults.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "world_setting", joinColumns = @JoinColumn(name = "world_id"))
    @MapKeyColumn(name = "setting_key")
    @Column(name = "setting_value")
    private java.util.Map<String, String> settings = new java.util.HashMap<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public java.util.Map<String, String> getSettings() { return settings; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
