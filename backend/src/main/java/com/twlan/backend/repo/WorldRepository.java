package com.twlan.backend.repo;

import com.twlan.backend.domain.World;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRepository extends JpaRepository<World, Long> {
    List<World> findAllByOrderByIdAsc();
}
