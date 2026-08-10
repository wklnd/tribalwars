package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// "I give sellAmount sellResource for buyAmount buyResource", up to remaining times. The goods on offer are
// taken from the village when the offer is made. Offers of NPC villages expire.
@Entity
@Table(name = "market_offer")
public class MarketOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long villageId;
    @Enumerated(EnumType.STRING)
    private Resource sellResource;
    private int sellAmount;
    @Enumerated(EnumType.STRING)
    private Resource buyResource;
    private int buyAmount;
    private int remaining;
    // Farthest distance (fields) an accepting village may have; 0 = unlimited.
    private int maxDistance;
    private Instant createdAt = Instant.now();
    private Instant expiresAt;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getVillageId() { return villageId; }
    public void setVillageId(Long villageId) { this.villageId = villageId; }
    public Resource getSellResource() { return sellResource; }
    public void setSellResource(Resource sellResource) { this.sellResource = sellResource; }
    public int getSellAmount() { return sellAmount; }
    public void setSellAmount(int sellAmount) { this.sellAmount = sellAmount; }
    public Resource getBuyResource() { return buyResource; }
    public void setBuyResource(Resource buyResource) { this.buyResource = buyResource; }
    public int getBuyAmount() { return buyAmount; }
    public void setBuyAmount(int buyAmount) { this.buyAmount = buyAmount; }
    public int getRemaining() { return remaining; }
    public void setRemaining(int remaining) { this.remaining = remaining; }
    public int getMaxDistance() { return maxDistance; }
    public void setMaxDistance(int maxDistance) { this.maxDistance = maxDistance; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
