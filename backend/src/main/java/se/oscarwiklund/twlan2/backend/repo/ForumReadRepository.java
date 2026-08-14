package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.ForumRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForumReadRepository extends JpaRepository<ForumRead, Long> {
    Optional<ForumRead> findByAccountIdAndThreadId(Long accountId, Long threadId);
    List<ForumRead> findByAccountId(Long accountId);
    void deleteByThreadId(Long threadId);
    void deleteByAccountId(Long accountId);
}
