package com.twlan.backend.repo;

import com.twlan.backend.domain.UnitResearch;
import com.twlan.backend.domain.UnitType;
import com.twlan.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitResearchRepository extends JpaRepository<UnitResearch, Long> {
    List<UnitResearch> findByVillage(Village village);
    boolean existsByVillageAndUnit(Village village, UnitType unit);
}
