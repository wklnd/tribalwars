package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.AchievementUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementUnlockRepository extends JpaRepository<AchievementUnlock, Long> {
    List<AchievementUnlock> findByAccountIdAndWorldId(Long accountId, Long worldId);
    List<AchievementUnlock> findByAccountIdAndWorldIdAndSeenFalseOrderByUnlockedAtAsc(Long accountId, Long worldId);
    List<AchievementUnlock> findByWorldId(Long worldId);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
