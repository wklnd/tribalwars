package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.StationedTroops;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StationedTroopsRepository extends JpaRepository<StationedTroops, Long> {
    List<StationedTroops> findByHostVillage(Village host);
    List<StationedTroops> findByOriginVillage(Village origin);
    Optional<StationedTroops> findByHostVillageAndOriginVillage(Village host, Village origin);
}
