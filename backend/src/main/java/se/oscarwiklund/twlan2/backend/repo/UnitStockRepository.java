package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.UnitStock;
import se.oscarwiklund.twlan2.backend.domain.UnitType;
import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitStockRepository extends JpaRepository<UnitStock, Long> {
    List<UnitStock> findByVillage(Village village);
    List<UnitStock> findByVillageIn(java.util.Collection<Village> villages);
    void deleteByVillage(Village village);
    Optional<UnitStock> findByVillageAndType(Village village, UnitType type);
}
