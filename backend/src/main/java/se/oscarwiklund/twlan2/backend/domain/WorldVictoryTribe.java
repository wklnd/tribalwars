package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;

// Per (world, tribe) running progress toward Domination or Rune - "has this tribe met the win condition
// every day for the last streakDays days?" War doesn't use this: it reads tribe village counts live instead.
@Entity
@Table(name = "world_victory_tribe", uniqueConstraints = @UniqueConstraint(columnNames = {"worldId", "tribeId"}))
public class WorldVictoryTribe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    private Long tribeId;
    private int streakDays;

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
}
