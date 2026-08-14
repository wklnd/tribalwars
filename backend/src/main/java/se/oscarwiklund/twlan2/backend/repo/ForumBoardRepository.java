package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.ForumBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumBoardRepository extends JpaRepository<ForumBoard, Long> {
    List<ForumBoard> findByTribeIdOrderByPositionAscIdAsc(Long tribeId);
    void deleteByTribeId(Long tribeId);
}
