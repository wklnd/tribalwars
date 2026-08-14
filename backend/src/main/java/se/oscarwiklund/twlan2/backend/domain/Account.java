package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// One account can hold a village in every world.
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(unique = true)
    private String usernameLower;

    private String passwordHash;

    // Nullable wrappers: rows from before these columns existed read as false.
    private Boolean admin;
    private Boolean npc;
    private Instant createdAt = Instant.now();
    // Set each time a session is created (login/register); null for accounts that predate this column.
    private Instant lastLoginAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; this.usernameLower = username == null ? null : username.toLowerCase(); }

    public String getUsernameLower() { return usernameLower; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public boolean isAdmin() { return Boolean.TRUE.equals(admin); }
    public void setAdmin(boolean admin) { this.admin = admin; }

    // An AI-controlled player: has villages but no password, so nobody can log in as it.
    public boolean isNpc() { return Boolean.TRUE.equals(npc); }
    public void setNpc(boolean npc) { this.npc = npc; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
