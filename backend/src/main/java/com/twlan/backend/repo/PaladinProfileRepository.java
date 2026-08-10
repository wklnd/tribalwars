package com.twlan.backend.repo;

import com.twlan.backend.domain.PaladinProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaladinProfileRepository extends JpaRepository<PaladinProfile, Long> {}
