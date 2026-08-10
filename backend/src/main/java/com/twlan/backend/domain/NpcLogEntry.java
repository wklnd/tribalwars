package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// A bounded ring per world, see NpcLogService.
@Entity
@Table(name = "npc_log", indexes = @Index(columnList = "worldId,id"))
public class NpcLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long accountId;
    private Long villageId;
    private Instant occurredAt = Instant.now();
    private String kind;
    @Column(length = 400)
    private String message;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getVillageId() { return villageId; }
    public void setVillageId(Long villageId) { this.villageId = villageId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
