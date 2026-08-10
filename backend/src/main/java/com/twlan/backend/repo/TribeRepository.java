package com.twlan.backend.repo;

import com.twlan.backend.domain.Tribe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TribeRepository extends JpaRepository<Tribe, Long> {
    List<Tribe> findByWorldId(Long worldId);
    Optional<Tribe> findByWorldIdAndTagLower(Long worldId, String tagLower);
    Optional<Tribe> findByWorldIdAndNameLower(Long worldId, String nameLower);
    void deleteByWorldId(Long worldId);
}
