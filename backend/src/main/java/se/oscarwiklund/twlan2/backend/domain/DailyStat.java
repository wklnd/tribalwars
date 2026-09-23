package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

// Feeds the daily "of the day" achievements, which go to the best of the day.
@Entity
@Table(name = "daily_stat", uniqueConstraints = @UniqueConstraint(columnNames = {"worldId", "epochDay", "accountId"}))
public class DailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private long epochDay;
    private Long accountId;
    private long kills;
    private long loot;
    private long plunders;
    private Long conquests;
    // nullable like conquests (not a bare "long"): ddl-auto=update cannot ALTER TABLE ADD COLUMN NOT NULL on an
    // already-populated table (see the "Something went wrong loading the game" incident - TrainQueueItem.decommission
    // was added as a bare NOT NULL boolean and every query touching it 500'd until the column got a default).
    private Long defended;
    private Long supported;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public long getEpochDay() { return epochDay; }
    public void setEpochDay(long epochDay) { this.epochDay = epochDay; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public long getKills() { return kills; }
    public void setKills(long kills) { this.kills = kills; }
    public long getLoot() { return loot; }
    public void setLoot(long loot) { this.loot = loot; }
    public long getPlunders() { return plunders; }
    public void setPlunders(long plunders) { this.plunders = plunders; }

    public long getConquests() { return conquests == null ? 0 : conquests; }
    public void setConquests(long conquests) { this.conquests = conquests; }

    public long getDefended() { return defended == null ? 0 : defended; }
    public void setDefended(long defended) { this.defended = defended; }
    public long getSupported() { return supported == null ? 0 : supported; }
    public void setSupported(long supported) { this.supported = supported; }
}
