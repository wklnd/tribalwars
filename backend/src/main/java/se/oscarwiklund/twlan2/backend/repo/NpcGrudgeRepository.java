package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.NpcGrudge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NpcGrudgeRepository extends JpaRepository<NpcGrudge, Long> {
    List<NpcGrudge> findByAccountId(Long accountId);
    Optional<NpcGrudge> findByAccountIdAndEnemyAccountId(Long accountId, Long enemyAccountId);
    void deleteByAccountId(Long accountId);
    void deleteByEnemyAccountId(Long enemyAccountId);
    void deleteByWorldId(Long worldId);
}
