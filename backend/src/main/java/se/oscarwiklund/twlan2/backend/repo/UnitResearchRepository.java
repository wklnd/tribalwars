package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.UnitResearch;
import se.oscarwiklund.twlan2.backend.domain.UnitType;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitResearchRepository extends JpaRepository<UnitResearch, Long> {
    List<UnitResearch> findByVillage(Village village);
    boolean existsByVillageAndUnit(Village village, UnitType unit);
}
