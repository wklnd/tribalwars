package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// eventType numbers are the original's (game.ally.events.N): 1 founded, 2 announcement changed, 3 allied, 4 NAP,
// 5 enemies, 6 invited, 7 attributes changed, 8 invitation withdrawn, 9 rights/title changed, 10 left,
// 11 dismissed, 12 description changed, 13 joined, 14 invitation rejected, 666 relation cancelled.
@Entity
@Table(name = "tribe_event")
public class TribeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tribeId;
    private int eventType;
    private Long fromAccount;
    private Long toAccount;
    private Long toTribe;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public int getEventType() { return eventType; }
    public void setEventType(int eventType) { this.eventType = eventType; }
    public Long getFromAccount() { return fromAccount; }
    public void setFromAccount(Long fromAccount) { this.fromAccount = fromAccount; }
    public Long getToAccount() { return toAccount; }
    public void setToAccount(Long toAccount) { this.toAccount = toAccount; }
    public Long getToTribe() { return toTribe; }
    public void setToTribe(Long toTribe) { this.toTribe = toTribe; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
