package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tribe_member", uniqueConstraints = @UniqueConstraint(columnNames = {"worldId", "accountId"}))
public class TribeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tribeId;
    private Long worldId;
    private Long accountId;
    // Bit set of TribeRole.
    private int roles;
    @Column(length = 40)
    private String title;
    // "Tribal status visible to outsiders" in the original.
    private boolean titleOutside;
    private Instant joinedAt = Instant.now();

    public Long getId() { return id; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public int getRoles() { return roles; }
    public void setRoles(int roles) { this.roles = roles; }
    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isTitleOutside() { return titleOutside; }
    public void setTitleOutside(boolean titleOutside) { this.titleOutside = titleOutside; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public boolean isFounder() { return (roles & TribeRole.FOUND.bit()) != 0; }
    public boolean isLeader() { return isFounder() || (roles & TribeRole.LEAD.bit()) != 0; }

    // The founder has every role, a leader every one but FOUND.
    public boolean has(TribeRole role) {
        if (isFounder()) return true;
        if (isLeader()) return role != TribeRole.FOUND;
        return (roles & role.bit()) != 0;
    }
}
