package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "achievement_counter", uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "worldId", "counterKey"}))
public class AchievementCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long worldId;
    private String counterKey;
    @Column(name = "counter_value")
    private long value;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public String getCounterKey() { return counterKey; }
    public void setCounterKey(String counterKey) { this.counterKey = counterKey; }
    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }
}
