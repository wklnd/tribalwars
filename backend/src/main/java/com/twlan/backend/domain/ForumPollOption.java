package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "forum_poll_option")
public class ForumPollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long threadId;
    @Column(length = 120)
    private String text;
    private int position;

    public Long getId() { return id; }
    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
