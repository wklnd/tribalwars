package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "mail_folder")
public class MailFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long worldId;
    @Column(length = 40)
    private String name;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
