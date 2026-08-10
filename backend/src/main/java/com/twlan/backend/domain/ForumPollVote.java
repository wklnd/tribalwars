package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "forum_poll_vote", uniqueConstraints = @UniqueConstraint(columnNames = {"threadId", "accountId"}))
public class ForumPollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long threadId;
    private Long optionId;
    private Long accountId;

    public Long getId() { return id; }
    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
}
