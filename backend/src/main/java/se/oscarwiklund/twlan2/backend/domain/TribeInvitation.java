package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tribe_invitation", uniqueConstraints = @UniqueConstraint(columnNames = {"tribeId", "accountId"}))
public class TribeInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tribeId;
    private Long worldId;
    // The invited player.
    private Long accountId;
    private Long invitedBy;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
