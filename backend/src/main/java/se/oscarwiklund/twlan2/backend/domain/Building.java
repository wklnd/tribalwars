package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "building", uniqueConstraints = @UniqueConstraint(columnNames = {"village_id", "type"}))
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "village_id")
    private Village village;

    @Enumerated(EnumType.STRING)
    private BuildingType type;

    private int level;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }

    public BuildingType getType() { return type; }
    public void setType(BuildingType type) { this.type = type; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
