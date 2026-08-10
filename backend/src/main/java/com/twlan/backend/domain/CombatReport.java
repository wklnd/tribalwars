package com.twlan.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "combat_report")
public class CombatReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null only for pre-world saves until adopted.
    private Long worldId;

    // Account whose village attacked; the report belongs to this account.
    private Long accountId;

    private String attackerVillageName;
    private String defenderVillageName;

    @Enumerated(EnumType.STRING)
    private BattleOutcome outcome;

    @ElementCollection
    @CollectionTable(name = "report_attacker_losses", joinColumns = @JoinColumn(name = "report_id"))
    @MapKeyColumn(name = "unit_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "count")
    private Map<UnitType, Integer> attackerLosses = new EnumMap<>(UnitType.class);

    @ElementCollection
    @CollectionTable(name = "report_defender_losses", joinColumns = @JoinColumn(name = "report_id"))
    @MapKeyColumn(name = "unit_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "count")
    private Map<UnitType, Integer> defenderLosses = new EnumMap<>(UnitType.class);

    private double lootWood;
    private double lootClay;
    private double lootIron;

    private Instant occurredAt;

    // Loyalty of the defending village before/after; null when no nobleman took part.
    private Integer loyaltyFrom;
    private Integer loyaltyTo;
    private Boolean conquered;

    // True for the copy of a battle report the attacked player gets; null/false is the attacker's own report.
    private Boolean defenderView;
    // Null on old reports and for barbarian villages.
    private String attackerPlayer;
    private String defenderPlayer;

    // Everything the report shows beyond the losses, as named numbers (kept in its own table so the report
    // table never changes): att:SPEAR/def:SPEAR troops before the battle, luck and morale in percent,
    // wall_before/wall_after, dmg:<BUILDING> levels destroyed, capacity (carrying capacity), spy_level,
    // spy_wood|clay|iron, spy_building:<BUILDING>, spy_home:<UNIT>, spy_away:<UNIT>. Empty on old reports.
    @ElementCollection
    @CollectionTable(name = "report_detail", joinColumns = @JoinColumn(name = "report_id"))
    @MapKeyColumn(name = "detail_key")
    @Column(name = "amount")
    private Map<String, Integer> detail = new HashMap<>();

    public Map<String, Integer> getDetail() { return detail; }
    public void setDetail(Map<String, Integer> detail) { this.detail = detail; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAttackerVillageName() { return attackerVillageName; }
    public void setAttackerVillageName(String attackerVillageName) { this.attackerVillageName = attackerVillageName; }

    public String getDefenderVillageName() { return defenderVillageName; }
    public void setDefenderVillageName(String defenderVillageName) { this.defenderVillageName = defenderVillageName; }

    public BattleOutcome getOutcome() { return outcome; }
    public void setOutcome(BattleOutcome outcome) { this.outcome = outcome; }

    public Map<UnitType, Integer> getAttackerLosses() { return attackerLosses; }
    public void setAttackerLosses(Map<UnitType, Integer> attackerLosses) { this.attackerLosses = attackerLosses; }

    public Map<UnitType, Integer> getDefenderLosses() { return defenderLosses; }
    public void setDefenderLosses(Map<UnitType, Integer> defenderLosses) { this.defenderLosses = defenderLosses; }

    public double getLootWood() { return lootWood; }
    public void setLootWood(double lootWood) { this.lootWood = lootWood; }

    public double getLootClay() { return lootClay; }
    public void setLootClay(double lootClay) { this.lootClay = lootClay; }

    public double getLootIron() { return lootIron; }
    public void setLootIron(double lootIron) { this.lootIron = lootIron; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Integer getLoyaltyFrom() { return loyaltyFrom; }
    public void setLoyaltyFrom(Integer loyaltyFrom) { this.loyaltyFrom = loyaltyFrom; }
    public Integer getLoyaltyTo() { return loyaltyTo; }
    public void setLoyaltyTo(Integer loyaltyTo) { this.loyaltyTo = loyaltyTo; }
    public boolean isDefenderView() { return Boolean.TRUE.equals(defenderView); }
    public void setDefenderView(boolean defenderView) { this.defenderView = defenderView; }

    public String getAttackerPlayer() { return attackerPlayer; }
    public void setAttackerPlayer(String attackerPlayer) { this.attackerPlayer = attackerPlayer; }

    public String getDefenderPlayer() { return defenderPlayer; }
    public void setDefenderPlayer(String defenderPlayer) { this.defenderPlayer = defenderPlayer; }

    public boolean isConquered() { return Boolean.TRUE.equals(conquered); }
    public void setConquered(boolean conquered) { this.conquered = conquered; }
}
