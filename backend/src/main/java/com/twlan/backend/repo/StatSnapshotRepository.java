package com.twlan.backend.repo;

import com.twlan.backend.domain.StatSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StatSnapshotRepository extends JpaRepository<StatSnapshot, Long> {
    List<StatSnapshot> findByAccountIdAndWorldIdAndTakenAtAfterOrderByTakenAtAsc(Long accountId, Long worldId, Instant after);
    Optional<StatSnapshot> findFirstByAccountIdAndWorldIdOrderByTakenAtDesc(Long accountId, Long worldId);
    Optional<StatSnapshot> findFirstByAccountIdAndWorldIdAndTakenAtLessThanEqualOrderByTakenAtDesc(Long accountId, Long worldId, Instant at);
    void deleteByTakenAtBefore(Instant before);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
