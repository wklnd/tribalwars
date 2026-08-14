package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    private String token;

    private Long accountId;
    private Instant createdAt = Instant.now();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
