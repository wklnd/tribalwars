package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.NpcProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NpcProfileRepository extends JpaRepository<NpcProfile, Long> {
    Optional<NpcProfile> findByAccountId(Long accountId);
    void deleteByAccountId(Long accountId);
}
