package com.twlan.backend.domain;

import jakarta.persistence.*;

// coins and conquered together set how many noblemen this account may have (the triangular coin limit).
@Entity
@Table(name = "noble_coins", uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "worldId"}))
public class NobleCoins {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long worldId;
    private int coins;
    private int conquered;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = coins; }
    public int getConquered() { return conquered; }
    public void setConquered(int conquered) { this.conquered = conquered; }
}
