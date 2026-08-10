package com.twlan.backend.repo;

import com.twlan.backend.domain.ForumPollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForumPollVoteRepository extends JpaRepository<ForumPollVote, Long> {
    List<ForumPollVote> findByThreadId(Long threadId);
    Optional<ForumPollVote> findByThreadIdAndAccountId(Long threadId, Long accountId);
    void deleteByThreadId(Long threadId);
}
