package com.twlan.backend.repo;

import com.twlan.backend.domain.MailFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailFolderRepository extends JpaRepository<MailFolder, Long> {
    List<MailFolder> findByAccountIdAndWorldIdOrderByIdAsc(Long accountId, Long worldId);
    void deleteByAccountId(Long accountId);
    void deleteByWorldId(Long worldId);
}
