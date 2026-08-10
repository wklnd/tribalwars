package com.twlan.backend.domain;

import jakarta.persistence.*;

// One row per direction: killer/victim are not symmetric.
@Entity
@Table(name = "tribe_war", uniqueConstraints = @UniqueConstraint(columnNames = {"killerTribeId", "victimTribeId"}))
public class TribeWar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long killerTribeId;
    private Long victimTribeId;
    private long kills;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getKillerTribeId() { return killerTribeId; }
    public void setKillerTribeId(Long killerTribeId) { this.killerTribeId = killerTribeId; }
    public Long getVictimTribeId() { return victimTribeId; }
    public void setVictimTribeId(Long victimTribeId) { this.victimTribeId = victimTribeId; }
    public long getKills() { return kills; }
    public void setKills(long kills) { this.kills = kills; }
}
