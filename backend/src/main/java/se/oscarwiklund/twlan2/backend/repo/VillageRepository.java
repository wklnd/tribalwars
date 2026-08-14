package se.oscarwiklund.twlan2.backend.repo;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.OwnerType;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.domain.World;
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
