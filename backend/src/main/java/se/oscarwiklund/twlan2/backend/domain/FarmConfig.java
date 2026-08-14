package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// The farm assistant setup (templates A/B/C, filters, hidden villages) as the JSON text the frontend wrote;
// the server only keeps it so the setup follows the player to another browser.
@Entity
@Table(name = "farm_config", uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "worldId"}))
public class FarmConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Long worldId;

    @Column(name = "config", length = 20000)
    private String config;

    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
