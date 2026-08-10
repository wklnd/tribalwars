package com.twlan.backend.repo;

import com.twlan.backend.domain.Movement;
import com.twlan.backend.domain.MovementType;
import com.twlan.backend.domain.Village;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MovementRepository extends JpaRepository<Movement, Long> {
    List<Movement> findByArrivesAtLessThanEqual(Instant now);
    List<Movement> findByOriginVillageOrTargetVillage(Village origin, Village target);
    List<Movement> findByOriginVillageAndTargetVillageAndTypeAndDepartedAtAfter(Village origin, Village target, MovementType type, Instant after);
}
