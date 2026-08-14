package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.TribeRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TribeRelationRepository extends JpaRepository<TribeRelation, Long> {
    List<TribeRelation> findByTribeId(Long tribeId);
    List<TribeRelation> findByOtherTribeId(Long otherTribeId);
    List<TribeRelation> findByTribeIdIn(java.util.Collection<Long> tribeIds);
    Optional<TribeRelation> findByTribeIdAndOtherTribeId(Long tribeId, Long otherTribeId);
    void deleteByTribeId(Long tribeId);
    void deleteByOtherTribeId(Long otherTribeId);
}
