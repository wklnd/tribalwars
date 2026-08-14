package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// One tribe's own view of another: non-binding, one direction only.
@Entity
@Table(name = "tribe_relation", uniqueConstraints = @UniqueConstraint(columnNames = {"tribeId", "otherTribeId"}))
public class TribeRelation {

    public enum Kind { PARTNER, NAP, ENEMY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tribeId;
    private Long otherTribeId;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public Long getOtherTribeId() { return otherTribeId; }
    public void setOtherTribeId(Long otherTribeId) { this.otherTribeId = otherTribeId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
