package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.repo.AccountRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldRepository;
import se.oscarwiklund.twlan2.backend.service.npc.NpcNames;
import se.oscarwiklund.twlan2.backend.service.npc.NpcProfiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

// New NPCs keep joining a world over time, like real players trickling into a fresh server: most within the
// first couple of weeks, tapering off with the world's age (same exponential-decay idea as NpcRetirement's
// career length, and the same speed-scaled "world-days" pacing as NpcRetirement/NpcTribeService.warsAllowed),
// until the world is old enough that it has effectively closed to newcomers and only its established tribes
// are left fighting over the map. Reuses the exact account+village creation AdminService.createNpcs does for
// an admin-triggered batch, just spread out over time and picking its own development/placement.
@Service
public class NpcJoinService {

    private static final double DECAY_DAYS = 10;
    private static final double BASE_CHANCE_PER_SWEEP = 0.6;
    private static final double CLOSES_AFTER_DAYS = 60;

    private final WorldRepository worlds;
    private final AccountRepository accounts;
    private final WorldService worldService;
    private final NpcProfiles npcProfiles;
    private final Random rnd = new Random();

    public NpcJoinService(WorldRepository worlds, AccountRepository accounts, WorldService worldService, NpcProfiles npcProfiles) {
        this.worlds = worlds;
        this.accounts = accounts;
        this.worldService = worldService;
        this.npcProfiles = npcProfiles;
    }

    @Scheduled(fixedDelayString = "${game.npc-join-sweep-ms:300000}", initialDelay = 60000)
    @Transactional
    public void sweep() {
        for (World world : worlds.findAll()) {
            double ageDays = ageDays(world);
            if (ageDays >= CLOSES_AFTER_DAYS) continue; // the world has closed to newcomers
            double chance = BASE_CHANCE_PER_SWEEP * Math.exp(-ageDays / DECAY_DAYS);
            if (rnd.nextDouble() >= chance) continue;
            try {
                join(world);
            } catch (IllegalArgumentException noRoom) {
                // the map is full near the centre; try again next sweep
            }
        }
    }

    private double ageDays(World world) {
        double speed = world.getSpeed() <= 0 ? 1 : world.getSpeed();
        double pace = Math.min(30, Math.max(1, Math.sqrt(speed))); // mirrors NpcRhythm.pace()
        return Duration.between(world.getCreatedAt(), Instant.now()).toMillis() * pace / 86_400_000.0;
    }

    private void join(World world) {
        String name = NpcNames.generate(rnd, lower -> accounts.findByUsernameLower(lower).isPresent());
        Account npc = new Account();
        npc.setUsername(name);
        npc.setNpc(true);
        accounts.save(npc);
        npcProfiles.profileOf(npc); // materialises its default archetype/skill right away
        double development = rnd.nextDouble() * 0.4; // fresh joiners start small, like AdminService.createNpcs' own default range
        worldService.createVillageFromLayout(world, npc, VillageGenerator.generate(rnd, development), rnd);
    }
}
