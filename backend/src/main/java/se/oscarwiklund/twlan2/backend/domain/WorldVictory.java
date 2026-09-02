package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// One row per world: which victory condition it uses (if any), the admin-set params for it (typed JSON,
// shape depends on type - see service/victory), the evaluator's own working state (also typed JSON, e.g.
// War's roster/phase - opaque to the admin, unlike params), and the outcome once won. lastEvalDay is the
// epoch-day the daily Domination/Rune check last ran, so a sweep running every few minutes only advances
// streaks once per elapsed real calendar day. Raw ids, no FKs, like Tribe/Mail/Market.
@Entity
@Table(name = "world_victory", uniqueConstraints = @UniqueConstraint(columnNames = "worldId"))
public class WorldVictory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;

    @Enumerated(EnumType.STRING)
    private VictoryType type = VictoryType.NONE;

    @Column(length = 4000)
    private String paramsJson;

    @Column(length = 4000)
    private String stateJson;

    private Long lastEvalDay;

    private Long wonTribeId;
    private Instant wonAt;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public VictoryType getType() { return type; }
    public void setType(VictoryType type) { this.type = type; }
    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public Long getLastEvalDay() { return lastEvalDay; }
    public void setLastEvalDay(Long lastEvalDay) { this.lastEvalDay = lastEvalDay; }
    public Long getWonTribeId() { return wonTribeId; }
    public void setWonTribeId(Long wonTribeId) { this.wonTribeId = wonTribeId; }
    public Instant getWonAt() { return wonAt; }
    public void setWonAt(Instant wonAt) { this.wonAt = wonAt; }
}
