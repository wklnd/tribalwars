package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.DailyStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {
    Optional<DailyStat> findByWorldIdAndEpochDayAndAccountId(Long worldId, long epochDay, Long accountId);
    List<DailyStat> findByWorldIdAndEpochDay(Long worldId, long epochDay);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
