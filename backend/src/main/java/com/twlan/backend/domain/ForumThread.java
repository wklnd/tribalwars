package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "forum_thread")
public class ForumThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long boardId;
    @Column(length = 120)
    private String title;
    private Long authorId;
    private boolean closed;
    private boolean pinned;
    @Column(length = 200)
    private String pollQuestion;
    private Instant pollEndsAt;
    private boolean pollShowResults;
    private Instant createdAt = Instant.now();
    private Instant lastPostAt = Instant.now();
    // 0 for none yet; kept so unread checks need no join.
    private long lastPostId;

    public Long getId() { return id; }
    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public String getPollQuestion() { return pollQuestion; }
    public void setPollQuestion(String pollQuestion) { this.pollQuestion = pollQuestion; }
    public Instant getPollEndsAt() { return pollEndsAt; }
    public void setPollEndsAt(Instant pollEndsAt) { this.pollEndsAt = pollEndsAt; }
    public boolean isPollShowResults() { return pollShowResults; }
    public void setPollShowResults(boolean pollShowResults) { this.pollShowResults = pollShowResults; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastPostAt() { return lastPostAt; }
    public void setLastPostAt(Instant lastPostAt) { this.lastPostAt = lastPostAt; }
    public long getLastPostId() { return lastPostId; }
    public void setLastPostId(long lastPostId) { this.lastPostId = lastPostId; }
    public boolean isPoll() { return pollQuestion != null && !pollQuestion.isEmpty(); }
}
