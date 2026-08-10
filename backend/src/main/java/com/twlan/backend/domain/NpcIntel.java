package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// What an NPC knows about one village of someone else: what its scouts saw or what its last battle there showed,
// and how its raids there went. One row per (NPC account, target village); raw ids, no foreign keys (cleaned by
// NpcIntelService).
@Entity
@Table(name = "npc_intel", indexes = {@Index(columnList = "accountId"), @Index(columnList = "targetVillageId")},
        uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "targetVillageId"}))
public class NpcIntel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long accountId;
    private Long targetVillageId;
    // When the picture below was taken; null: nothing known about the garrison.
    private Instant seenAt;
    // SCOUT, BATTLE or GUESS
    private String source = "GUESS";
    // The garrison as "SPEAR:12,AXE:3"; null = unknown; "" = seen empty.
    @Column(length = 300)
    private String troops;
    private Integer wall;
    private Integer wood;
    private Integer clay;
    private Integer iron;
    private Instant lastAttackAt;
    // WON or LOST
    private String lastResult;
    private int lastLoot;
    private int raids;
    private int losses;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getTargetVillageId() { return targetVillageId; }
    public void setTargetVillageId(Long targetVillageId) { this.targetVillageId = targetVillageId; }
    public Instant getSeenAt() { return seenAt; }
    public void setSeenAt(Instant seenAt) { this.seenAt = seenAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTroops() { return troops; }
    public void setTroops(String troops) { this.troops = troops; }
    public Integer getWall() { return wall; }
    public void setWall(Integer wall) { this.wall = wall; }
    public Integer getWood() { return wood; }
    public void setWood(Integer wood) { this.wood = wood; }
    public Integer getClay() { return clay; }
    public void setClay(Integer clay) { this.clay = clay; }
    public Integer getIron() { return iron; }
    public void setIron(Integer iron) { this.iron = iron; }
    public Instant getLastAttackAt() { return lastAttackAt; }
    public void setLastAttackAt(Instant lastAttackAt) { this.lastAttackAt = lastAttackAt; }
    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }
    public int getLastLoot() { return lastLoot; }
    public void setLastLoot(int lastLoot) { this.lastLoot = lastLoot; }
    public int getRaids() { return raids; }
    public void setRaids(int raids) { this.raids = raids; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
}
