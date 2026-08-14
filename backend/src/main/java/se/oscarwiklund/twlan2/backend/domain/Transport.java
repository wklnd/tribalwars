package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Merchants on the road. They leave originVillageId carrying out*, hand that over at the target and come back
// carrying back* (what was bought; zero for a plain shipment). The merchants count as away from the origin for
// the whole round trip; the row is deleted when they are home again. Raw village ids (no FKs), cleaned by
// MarketService.dropVillage.
@Entity
@Table(name = "transport")
public class Transport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long originVillageId;
    private Long targetVillageId;
    private int outWood;
    private int outClay;
    private int outIron;
    private int backWood;
    private int backClay;
    private int backIron;
    private int merchants;
    private boolean returning;
    private Instant departedAt;
    private Instant arrivesAt;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getOriginVillageId() { return originVillageId; }
    public void setOriginVillageId(Long originVillageId) { this.originVillageId = originVillageId; }
    public Long getTargetVillageId() { return targetVillageId; }
    public void setTargetVillageId(Long targetVillageId) { this.targetVillageId = targetVillageId; }
    public int getOutWood() { return outWood; }
    public void setOutWood(int outWood) { this.outWood = outWood; }
    public int getOutClay() { return outClay; }
    public void setOutClay(int outClay) { this.outClay = outClay; }
    public int getOutIron() { return outIron; }
    public void setOutIron(int outIron) { this.outIron = outIron; }
    public int getBackWood() { return backWood; }
    public void setBackWood(int backWood) { this.backWood = backWood; }
    public int getBackClay() { return backClay; }
    public void setBackClay(int backClay) { this.backClay = backClay; }
    public int getBackIron() { return backIron; }
    public void setBackIron(int backIron) { this.backIron = backIron; }
    public int getMerchants() { return merchants; }
    public void setMerchants(int merchants) { this.merchants = merchants; }
    public boolean isReturning() { return returning; }
    public void setReturning(boolean returning) { this.returning = returning; }
    public Instant getDepartedAt() { return departedAt; }
    public void setDepartedAt(Instant departedAt) { this.departedAt = departedAt; }
    public Instant getArrivesAt() { return arrivesAt; }
    public void setArrivesAt(Instant arrivesAt) { this.arrivesAt = arrivesAt; }
}
