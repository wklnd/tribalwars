package com.twlan.backend.repo;

import com.twlan.backend.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUsernameLower(String usernameLower);
    java.util.List<Account> findByNpc(Boolean npc);
}
