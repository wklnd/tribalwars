package com.twlan.backend.repo;

import com.twlan.backend.domain.ForumThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumThreadRepository extends JpaRepository<ForumThread, Long> {
    List<ForumThread> findByBoardId(Long boardId);
    long countByBoardId(Long boardId);
}
