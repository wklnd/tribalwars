package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

// The implicit group "all" is not stored.
@Entity
@Table(name = "village_group")
public class VillageGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long accountId;
    private String name;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
