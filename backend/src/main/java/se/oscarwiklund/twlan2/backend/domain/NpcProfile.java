package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

// archetype is a name of NpcArchetype, stored as text so archetypes can be added later; skill is 0..1.
@Entity
@Table(name = "npc_profile", uniqueConstraints = @UniqueConstraint(columnNames = "accountId"))
public class NpcProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private String archetype;
    private double skill;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getArchetype() { return archetype; }
    public void setArchetype(String archetype) { this.archetype = archetype; }
    public double getSkill() { return skill; }
    public void setSkill(double skill) { this.skill = skill; }
}
