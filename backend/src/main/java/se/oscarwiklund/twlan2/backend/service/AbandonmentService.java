package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.OwnerType;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldRepository;
import se.oscarwiklund.twlan2.backend.service.npc.NpcRetirement;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// World "leftVillagesGrow": a real account that hasn't logged in for that many real-world minutes loses its
// villages to the barbarians (user's chosen design, not a passive NPC-style build-up loop, since the original's
// exact behaviour is compiled and not discoverable). 0/unset disables the sweep for that world.
// NPC accounts follow their own, unrelated clock: NpcRetirement decides which ones ever stop playing and when;
// once retired, this same sweep turns their villages barbarian too, regardless of leftVillagesGrow.
@Service
public class AbandonmentService {

    private final WorldRepository worlds;
    private final VillageRepository villages;

    public AbandonmentService(WorldRepository worlds, VillageRepository villages) {
        this.worlds = worlds;
        this.villages = villages;
    }

    @Scheduled(fixedDelayString = "${game.abandon-sweep-ms:60000}", initialDelay = 30000)
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        for (World world : worlds.findAll()) {
            double minutes = WorldSettings.number(world, "leftVillagesGrow");
            for (Village v : villages.findByWorldAndOwnerType(world, OwnerType.PLAYER)) {
                Account owner = v.getOwner();
                if (owner == null) continue;
                boolean abandoned = owner.isNpc()
                        ? NpcRetirement.isRetired(owner, world, now)
                        : minutes > 0 && owner.getLastLoginAt() != null && Duration.between(owner.getLastLoginAt(), now).toMinutes() >= minutes;
                if (!abandoned) continue;
                v.setOwner(null);
                v.setOwnerType(OwnerType.BARBARIAN);
                villages.save(v);
            }
        }
    }
}
