package com.twlan.backend.repo;

import com.twlan.backend.domain.NpcLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NpcLogRepository extends JpaRepository<NpcLogEntry, Long> {

    @Query("select l from NpcLogEntry l where l.worldId = :world and (:kind is null or l.kind = :kind) and (:account is null or l.accountId = :account) order by l.id desc")
    List<NpcLogEntry> recent(@Param("world") Long world, @Param("kind") String kind, @Param("account") Long account, Pageable page);

    @Query("select l.kind, count(l) from NpcLogEntry l where l.worldId = :world group by l.kind")
    List<Object[]> countsByKind(@Param("world") Long world);

    long countByWorldId(Long worldId);

    @Query("select max(l.id) from NpcLogEntry l where l.worldId = :world")
    Long newestId(@Param("world") Long world);

    @Modifying
    @Query("delete from NpcLogEntry l where l.worldId = :world and l.id <= :upTo")
    int deleteUpTo(@Param("world") Long world, @Param("upTo") Long upTo);

    @Query("select l.id from NpcLogEntry l where l.worldId = :world order by l.id desc")
    List<Long> ids(@Param("world") Long world, Pageable page);

    void deleteByWorldId(Long worldId);
    void deleteByAccountId(Long accountId);
    void deleteByVillageId(Long villageId);
}
