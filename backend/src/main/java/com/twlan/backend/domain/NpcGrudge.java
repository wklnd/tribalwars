package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// score grows with every attack from this enemy and fades with time.
@Entity
@Table(name = "npc_grudge", indexes = @Index(columnList = "accountId"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "enemyAccountId"}))
public class NpcGrudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long accountId;
    private Long enemyAccountId;
    private double score;
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getEnemyAccountId() { return enemyAccountId; }
    public void setEnemyAccountId(Long enemyAccountId) { this.enemyAccountId = enemyAccountId; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
