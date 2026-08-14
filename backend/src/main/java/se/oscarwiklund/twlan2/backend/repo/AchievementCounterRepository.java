package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.AchievementCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AchievementCounterRepository extends JpaRepository<AchievementCounter, Long> {
    List<AchievementCounter> findByAccountIdAndWorldId(Long accountId, Long worldId);
    Optional<AchievementCounter> findByAccountIdAndWorldIdAndCounterKey(Long accountId, Long worldId, String counterKey);
    long countByAccountIdAndWorldIdAndCounterKeyStartingWith(Long accountId, Long worldId, String prefix);
    List<AchievementCounter> findByWorldIdAndCounterKey(Long worldId, String counterKey);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
