package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.MailContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MailContactRepository extends JpaRepository<MailContact, Long> {
    List<MailContact> findByAccountIdAndWorldIdAndBlocked(Long accountId, Long worldId, boolean blocked);
    Optional<MailContact> findByAccountIdAndWorldIdAndContactIdAndBlocked(Long accountId, Long worldId, Long contactId, boolean blocked);
    boolean existsByAccountIdAndWorldIdAndContactIdAndBlocked(Long accountId, Long worldId, Long contactId, boolean blocked);
    void deleteByAccountId(Long accountId);
    void deleteByContactId(Long contactId);
    void deleteByWorldId(Long worldId);
}
