package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.MailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MailMessageRepository extends JpaRepository<MailMessage, Long> {
    List<MailMessage> findByThreadIdOrderByIdAsc(Long threadId);
    void deleteByThreadId(Long threadId);

    // (threadId, id of the newest message) for each of the threads.
    @Query("select m.threadId, max(m.id) from MailMessage m where m.threadId in :ids group by m.threadId")
    List<Object[]> newestOf(@Param("ids") List<Long> ids);
}
