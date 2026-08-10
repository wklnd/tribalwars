package com.twlan.backend.repo;

import com.twlan.backend.domain.VillageGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VillageGroupMemberRepository extends JpaRepository<VillageGroupMember, Long> {
    List<VillageGroupMember> findByGroupIdIn(Collection<Long> groupIds);
    List<VillageGroupMember> findByVillageId(Long villageId);
    void deleteByGroupId(Long groupId);
    void deleteByVillageId(Long villageId);
}
