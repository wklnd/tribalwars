package com.twlan.backend.service;

import com.twlan.backend.domain.Account;
import com.twlan.backend.domain.OwnerType;
import com.twlan.backend.domain.Village;
import com.twlan.backend.domain.World;
import com.twlan.backend.repo.VillageRepository;
import com.twlan.backend.repo.WorldRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// World "leftVillagesGrow": a real account that hasn't logged in for that many real-world minutes loses its
// villages to the barbarians (user's chosen design, not a passive NPC-style build-up loop, since the original's
// exact behaviour is compiled and not discoverable). 0/unset disables the sweep for that world.
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
            if (minutes <= 0) continue;
            for (Village v : villages.findByWorldAndOwnerType(world, OwnerType.PLAYER)) {
                Account owner = v.getOwner();
                if (owner == null || owner.isNpc() || owner.getLastLoginAt() == null) continue;
                if (Duration.between(owner.getLastLoginAt(), now).toMinutes() < minutes) continue;
                v.setOwner(null);
                v.setOwnerType(OwnerType.BARBARIAN);
                villages.save(v);
            }
        }
    }
}
