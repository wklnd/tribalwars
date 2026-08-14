package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.PaladinProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaladinProfileRepository extends JpaRepository<PaladinProfile, Long> {}
