package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "forum_read", uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "threadId"}))
public class ForumRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long threadId;
    private long lastPostId;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public long getLastPostId() { return lastPostId; }
    public void setLastPostId(long lastPostId) { this.lastPostId = lastPostId; }
}
