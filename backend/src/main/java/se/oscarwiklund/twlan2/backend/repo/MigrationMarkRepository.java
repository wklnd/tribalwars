package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.MigrationMark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationMarkRepository extends JpaRepository<MigrationMark, String> {
}
