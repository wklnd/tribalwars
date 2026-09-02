package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.WorldVictoryTribe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldVictoryTribeRepository extends JpaRepository<WorldVictoryTribe, Long> {
    List<WorldVictoryTribe> findByWorldId(Long worldId);
    Optional<WorldVictoryTribe> findByWorldIdAndTribeId(Long worldId, Long tribeId);
    void deleteByWorldId(Long worldId);
}
