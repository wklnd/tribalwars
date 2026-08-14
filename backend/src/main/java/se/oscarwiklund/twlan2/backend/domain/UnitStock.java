package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "unit_stock", uniqueConstraints = @UniqueConstraint(columnNames = {"village_id", "type"}))
public class UnitStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "village_id")
    private Village village;

    @Enumerated(EnumType.STRING)
    private UnitType type;

    private int count;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }

    public UnitType getType() { return type; }
    public void setType(UnitType type) { this.type = type; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
