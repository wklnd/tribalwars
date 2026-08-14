package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// @DynamicUpdate: only the changed columns are written. Several threads (game tick, NPC steps, requests, the
// admin's world compaction) load the same village; without this a stale copy written back later also reset
// columns it never touched (a village moved by the compaction jumped back to its old coordinates).
@Entity
@Table(name = "village")
@org.hibernate.annotations.DynamicUpdate
public class Village {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // Null for barbarian villages and pre-account saves until claimed.
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private Account owner;

    // Null only for saves from before worlds existed; WorldService adopts those into the first world.
    @ManyToOne
    @JoinColumn(name = "world_id")
    private World world;

    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;

    private int x;
    private int y;

    private double wood;
    private double clay;
    private double iron;

    private Instant resourcesSettledAt;

    // As of loyaltyUpdatedAt; null = 100. It regrows over time, see VillageService.loyaltyOf.
    private Double loyalty;
    private Instant loyaltyUpdatedAt;

    // BonusType.code of the bonus this village carries; null = none. It stays when the village changes hands.
    private Integer bonusCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Account getOwner() { return owner; }
    public void setOwner(Account owner) { this.owner = owner; }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OwnerType getOwnerType() { return ownerType; }
    public void setOwnerType(OwnerType ownerType) { this.ownerType = ownerType; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public double getWood() { return wood; }
    public void setWood(double wood) { this.wood = wood; }

    public double getClay() { return clay; }
    public void setClay(double clay) { this.clay = clay; }

    public double getIron() { return iron; }
    public void setIron(double iron) { this.iron = iron; }

    public Instant getResourcesSettledAt() { return resourcesSettledAt; }
    public void setResourcesSettledAt(Instant resourcesSettledAt) { this.resourcesSettledAt = resourcesSettledAt; }

    public Double getLoyalty() { return loyalty; }
    public void setLoyalty(Double loyalty) { this.loyalty = loyalty; }

    public Instant getLoyaltyUpdatedAt() { return loyaltyUpdatedAt; }
    public void setLoyaltyUpdatedAt(Instant loyaltyUpdatedAt) { this.loyaltyUpdatedAt = loyaltyUpdatedAt; }

    public Integer getBonusCode() { return bonusCode; }
    public void setBonusCode(Integer bonusCode) { this.bonusCode = bonusCode; }
}
