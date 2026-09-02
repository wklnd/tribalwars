package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.WorldVictory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorldVictoryRepository extends JpaRepository<WorldVictory, Long> {
    Optional<WorldVictory> findByWorldId(Long worldId);
}
