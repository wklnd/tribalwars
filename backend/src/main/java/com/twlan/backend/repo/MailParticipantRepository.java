package com.twlan.backend.repo;

import com.twlan.backend.domain.MailParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MailParticipantRepository extends JpaRepository<MailParticipant, Long> {
    List<MailParticipant> findByAccountIdAndDeletedFalse(Long accountId);
    List<MailParticipant> findByThreadId(Long threadId);
    Optional<MailParticipant> findByThreadIdAndAccountId(Long threadId, Long accountId);
    List<MailParticipant> findByAccountId(Long accountId);
    void deleteByThreadId(Long threadId);

    // How many conversations of the account (in the world) have a message it has not read.
    @Query("select count(p) from MailParticipant p, MailThread t where p.accountId = :accountId and p.deleted = false "
            + "and t.id = p.threadId and t.worldId = :worldId "
            + "and p.lastReadId < (select max(m.id) from MailMessage m where m.threadId = p.threadId)")
    long countUnread(@Param("accountId") Long accountId, @Param("worldId") Long worldId);
    void deleteByAccountId(Long accountId);
}
