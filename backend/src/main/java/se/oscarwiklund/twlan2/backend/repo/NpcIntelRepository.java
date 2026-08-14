package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.NpcIntel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NpcIntelRepository extends JpaRepository<NpcIntel, Long> {
    List<NpcIntel> findByAccountId(Long accountId);
    Optional<NpcIntel> findByAccountIdAndTargetVillageId(Long accountId, Long targetVillageId);
    void deleteByTargetVillageId(Long targetVillageId);
    void deleteByAccountId(Long accountId);
    void deleteByWorldId(Long worldId);
}
