package com.twlan.backend.repo;

import com.twlan.backend.domain.TribeEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TribeEventRepository extends JpaRepository<TribeEvent, Long> {
    List<TribeEvent> findByTribeIdOrderByCreatedAtDescIdDesc(Long tribeId, Pageable page);
    long countByTribeId(Long tribeId);
    void deleteByTribeId(Long tribeId);
}
