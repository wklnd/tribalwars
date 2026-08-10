package com.twlan.backend.repo;

import com.twlan.backend.domain.TribeInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TribeInvitationRepository extends JpaRepository<TribeInvitation, Long> {
    List<TribeInvitation> findByTribeIdOrderByCreatedAtDesc(Long tribeId);
    List<TribeInvitation> findByWorldId(Long worldId);
    List<TribeInvitation> findByWorldIdAndAccountIdOrderByCreatedAtDesc(Long worldId, Long accountId);
    Optional<TribeInvitation> findByTribeIdAndAccountId(Long tribeId, Long accountId);
    void deleteByTribeId(Long tribeId);
    void deleteByWorldIdAndAccountId(Long worldId, Long accountId);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
