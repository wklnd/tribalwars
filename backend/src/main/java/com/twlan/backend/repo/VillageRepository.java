package com.twlan.backend.repo;

import com.twlan.backend.domain.Account;
import com.twlan.backend.domain.OwnerType;
import com.twlan.backend.domain.Village;
import com.twlan.backend.domain.World;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VillageRepository extends JpaRepository<Village, Long> {
    List<Village> findByOwnerType(OwnerType ownerType);
    List<Village> findByWorld(World world);
    List<Village> findByWorldIsNull();
    List<Village> findByOwnerTypeAndOwnerIsNull(OwnerType ownerType);
    List<Village> findByWorldAndOwner(World world, Account owner);
    List<Village> findByOwner(Account owner);
    List<Village> findByWorldAndOwnerType(World world, OwnerType ownerType);
}
