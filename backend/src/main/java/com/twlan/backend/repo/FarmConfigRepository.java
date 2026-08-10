package com.twlan.backend.repo;

import com.twlan.backend.domain.FarmConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmConfigRepository extends JpaRepository<FarmConfig, Long> {
    Optional<FarmConfig> findByAccountIdAndWorldId(Long accountId, Long worldId);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
