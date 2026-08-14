package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "build_queue_item")
public class BuildQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "village_id")
    private Village village;

    @Enumerated(EnumType.STRING)
    private BuildingType type;

    private int targetLevel;

    private Instant startedAt;
    private Instant completesAt;

    private int position;

    // Resource-cost surcharge (world "buildqueueMultiply") applied at enqueue time, null = none (1.0);
    // kept so cancel() refunds what was actually paid regardless of later queue changes.
    private Double costMultiplier;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }

    public BuildingType getType() { return type; }
    public void setType(BuildingType type) { this.type = type; }

    public int getTargetLevel() { return targetLevel; }
    public void setTargetLevel(int targetLevel) { this.targetLevel = targetLevel; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletesAt() { return completesAt; }
    public void setCompletesAt(Instant completesAt) { this.completesAt = completesAt; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public Double getCostMultiplier() { return costMultiplier; }
    public void setCostMultiplier(Double costMultiplier) { this.costMultiplier = costMultiplier; }
}
