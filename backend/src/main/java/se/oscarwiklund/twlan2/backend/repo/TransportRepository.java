package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.Transport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TransportRepository extends JpaRepository<Transport, Long> {
    List<Transport> findByOriginVillageId(Long villageId);
    List<Transport> findByTargetVillageId(Long villageId);
    List<Transport> findByArrivesAtLessThanEqual(Instant now);
    void deleteByOriginVillageId(Long villageId);
}
