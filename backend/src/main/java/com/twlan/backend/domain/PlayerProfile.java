package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "player_profile")
public class PlayerProfile {

    @Id
    private Long accountId;

    @Column(length = 4000)
    private String description;

    @Column(length = 60)
    private String location;

    // yyyy-MM-dd or empty.
    @Column(length = 10)
    private String birthday;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getDescription() { return description == null ? "" : description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location == null ? "" : location; }
    public void setLocation(String location) { this.location = location; }
    public String getBirthday() { return birthday == null ? "" : birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
}
