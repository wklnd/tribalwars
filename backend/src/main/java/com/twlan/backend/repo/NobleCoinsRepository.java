package com.twlan.backend.repo;

import com.twlan.backend.domain.NobleCoins;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NobleCoinsRepository extends JpaRepository<NobleCoins, Long> {
    Optional<NobleCoins> findByAccountIdAndWorldId(Long accountId, Long worldId);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
