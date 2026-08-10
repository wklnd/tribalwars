package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "mail_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"threadId", "accountId"}))
public class MailParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long threadId;
    private Long accountId;
    // 0 = the default inbox, otherwise a MailFolder id.
    private long folderId;
    private long lastReadId;
    private boolean deleted;

    public Long getId() { return id; }
    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public long getFolderId() { return folderId; }
    public void setFolderId(long folderId) { this.folderId = folderId; }
    public long getLastReadId() { return lastReadId; }
    public void setLastReadId(long lastReadId) { this.lastReadId = lastReadId; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
