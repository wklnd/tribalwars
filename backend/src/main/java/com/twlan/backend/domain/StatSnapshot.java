package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Written only when points/rank/villages/kills actually changed.
@Entity
@Table(name = "stat_snapshot", indexes = @Index(columnList = "accountId, worldId, takenAt"))
public class StatSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long worldId;
    private Instant takenAt = Instant.now();
    private int points;
    private int rankPos;
    private int villageCount;
    private long kills;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Instant getTakenAt() { return takenAt; }
    public void setTakenAt(Instant takenAt) { this.takenAt = takenAt; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getRankPos() { return rankPos; }
    public void setRankPos(int rankPos) { this.rankPos = rankPos; }
    public int getVillageCount() { return villageCount; }
    public void setVillageCount(int villageCount) { this.villageCount = villageCount; }
    public long getKills() { return kills; }
    public void setKills(long kills) { this.kills = kills; }
}
