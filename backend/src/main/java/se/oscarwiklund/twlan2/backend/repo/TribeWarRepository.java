package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.TribeWar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TribeWarRepository extends JpaRepository<TribeWar, Long> {
    List<TribeWar> findByWorldId(Long worldId);
    Optional<TribeWar> findByKillerTribeIdAndVictimTribeId(Long killerTribeId, Long victimTribeId);
    void deleteByKillerTribeIdOrVictimTribeId(Long killerTribeId, Long victimTribeId);
}
