package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.VillageGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VillageGroupRepository extends JpaRepository<VillageGroup, Long> {
    List<VillageGroup> findByWorldIdAndAccountIdOrderByIdAsc(Long worldId, Long accountId);
    List<VillageGroup> findByWorldId(Long worldId);
    List<VillageGroup> findByAccountId(Long accountId);
}
