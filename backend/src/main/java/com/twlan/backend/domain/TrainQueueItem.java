package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "train_queue_item")
public class TrainQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "village_id")
    private Village village;

    @Enumerated(EnumType.STRING)
    private UnitType type;

    private int totalCount;
    private int producedCount;

    private Instant startedAt;
    private Instant completesAt;
    private long perUnitSeconds;

    private int position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }

    public UnitType getType() { return type; }
    public void setType(UnitType type) { this.type = type; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getProducedCount() { return producedCount; }
    public void setProducedCount(int producedCount) { this.producedCount = producedCount; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletesAt() { return completesAt; }
    public void setCompletesAt(Instant completesAt) { this.completesAt = completesAt; }

    public long getPerUnitSeconds() { return perUnitSeconds; }
    public void setPerUnitSeconds(long perUnitSeconds) { this.perUnitSeconds = perUnitSeconds; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
