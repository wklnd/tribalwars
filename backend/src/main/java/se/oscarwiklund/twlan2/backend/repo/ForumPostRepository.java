package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.ForumPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    List<ForumPost> findByThreadIdOrderByIdAsc(Long threadId, Pageable page);
    List<ForumPost> findByThreadIdOrderByIdAsc(Long threadId);
    long countByThreadId(Long threadId);
    void deleteByThreadId(Long threadId);
}
