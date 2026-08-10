package com.twlan.backend.repo;

import com.twlan.backend.domain.TribeMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TribeMemberRepository extends JpaRepository<TribeMember, Long> {
    List<TribeMember> findByTribeId(Long tribeId);
    List<TribeMember> findByWorldId(Long worldId);
    Optional<TribeMember> findByWorldIdAndAccountId(Long worldId, Long accountId);
    List<TribeMember> findByAccountId(Long accountId);
    void deleteByTribeId(Long tribeId);
    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
}
