package com.twlan.backend.domain;

import jakarta.persistence.*;

// One table for both: blocked = false is an address-book entry, blocked = true is a blocked sender.
@Entity
@Table(name = "mail_contact", uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "worldId", "contactId", "blocked"}))
public class MailContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long worldId;
    private Long contactId;
    private boolean blocked;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
}
