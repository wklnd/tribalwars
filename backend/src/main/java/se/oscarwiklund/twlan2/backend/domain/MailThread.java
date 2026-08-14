package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mail_thread")
public class MailThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;
    @Column(length = 120)
    private String subject;
    // A circular mail to the tribe: every recipient gets the thread, a reply goes to the sender only.
    private boolean mass;
    private Long tribeId;
    private Instant lastAt = Instant.now();

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public boolean isMass() { return mass; }
    public void setMass(boolean mass) { this.mass = mass; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public Instant getLastAt() { return lastAt; }
    public void setLastAt(Instant lastAt) { this.lastAt = lastAt; }
}
