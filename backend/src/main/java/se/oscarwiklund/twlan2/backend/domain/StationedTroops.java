package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.util.EnumMap;
import java.util.Map;

// Troops of one village stationed in another player's village (support between tribe-mates): the "army" of the
// original's units page. They defend the host village in battles and can be recalled by their owner or sent back
// by the host. Support between a player's own villages is NOT stored here: those troops simply join the target's
// own stock.
@Entity
@Table(name = "stationed_troops")
public class StationedTroops {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "host_village_id")
    private Village hostVillage;

    @ManyToOne(optional = false)
    @JoinColumn(name = "origin_village_id")
    private Village originVillage;

    @ElementCollection
    @CollectionTable(name = "stationed_units", joinColumns = @JoinColumn(name = "stationed_id"))
    @MapKeyColumn(name = "unit_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "count")
    private Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);

    public Long getId() { return id; }
    public Village getHostVillage() { return hostVillage; }
    public void setHostVillage(Village hostVillage) { this.hostVillage = hostVillage; }
    public Village getOriginVillage() { return originVillage; }
    public void setOriginVillage(Village originVillage) { this.originVillage = originVillage; }
    public Map<UnitType, Integer> getUnits() { return units; }
    public void setUnits(Map<UnitType, Integer> units) { this.units = units; }

    public boolean isEmpty() { return units.values().stream().noneMatch(n -> n != null && n > 0); }
}
