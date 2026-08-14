package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

// spear/sword/paladin/nobleman need no research, see ResearchService.
@Entity
@Table(name = "unit_research")
public class UnitResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "village_id")
    private Village village;

    @Enumerated(EnumType.STRING)
    private UnitType unit;

    public Long getId() { return id; }
    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }
    public UnitType getUnit() { return unit; }
    public void setUnit(UnitType unit) { this.unit = unit; }
}
