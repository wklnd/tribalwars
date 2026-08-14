package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.MailFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailFolderRepository extends JpaRepository<MailFolder, Long> {
    List<MailFolder> findByAccountIdAndWorldIdOrderByIdAsc(Long accountId, Long worldId);
    void deleteByAccountId(Long accountId);
    void deleteByWorldId(Long worldId);
}
