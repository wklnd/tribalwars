package com.twlan.backend.repo;

import com.twlan.backend.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    void deleteByAccountId(Long accountId);
}
