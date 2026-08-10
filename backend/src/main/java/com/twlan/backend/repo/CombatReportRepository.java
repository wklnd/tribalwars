package com.twlan.backend.repo;

import com.twlan.backend.domain.CombatReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CombatReportRepository extends JpaRepository<CombatReport, Long> {
    List<CombatReport> findAllByOrderByOccurredAtDesc();
    List<CombatReport> findByWorldIdOrderByOccurredAtDesc(Long worldId);
    List<CombatReport> findByWorldIdIsNull();
    List<CombatReport> findByWorldIdAndAccountIdOrderByOccurredAtDesc(Long worldId, Long accountId);
    List<CombatReport> findByAccountIdIsNull();
}
