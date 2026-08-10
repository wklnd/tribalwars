package com.twlan.backend.repo;

import com.twlan.backend.domain.Building;
import com.twlan.backend.domain.BuildingType;
import com.twlan.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByVillage(Village village);

    // Just the numbers points are made of (no entities: a world has thousands of buildings).
    interface Level {
        Long getVillageId();
        BuildingType getType();
        int getLevel();
    }

    @org.springframework.data.jpa.repository.Query("select b.village.id as villageId, b.type as type, b.level as level from Building b where b.village.world.id = :worldId")
    List<Level> levelsInWorld(@org.springframework.data.repository.query.Param("worldId") Long worldId);

    List<Building> findByVillageWorld(com.twlan.backend.domain.World world);
    void deleteByVillage(Village village);
    Optional<Building> findByVillageAndType(Village village, BuildingType type);
}
