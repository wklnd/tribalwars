package com.twlan.backend.repo;

import com.twlan.backend.domain.MigrationMark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationMarkRepository extends JpaRepository<MigrationMark, String> {
}
