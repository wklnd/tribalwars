package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.MailThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailThreadRepository extends JpaRepository<MailThread, Long> {
    List<MailThread> findByWorldId(Long worldId);
    List<MailThread> findByWorldIdAndTribeIdAndMassTrueOrderByLastAtDesc(Long worldId, Long tribeId);
    void deleteByWorldId(Long worldId);
}
