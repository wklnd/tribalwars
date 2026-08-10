package com.twlan.backend.repo;

import com.twlan.backend.domain.ForumPollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumPollOptionRepository extends JpaRepository<ForumPollOption, Long> {
    List<ForumPollOption> findByThreadIdOrderByPositionAsc(Long threadId);
    void deleteByThreadId(Long threadId);
}
