package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.NpcProfile;
import se.oscarwiklund.twlan2.backend.repo.NpcProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

// The archetype and skill of every NPC. Created on first use from the account id (so an NPC always plays the same way,
// also NPCs that already existed before profiles did); an admin can change or choose them.
@Service
public class NpcProfiles {

    private final NpcProfileRepository profiles;

    public NpcProfiles(NpcProfileRepository profiles) {
        this.profiles = profiles;
    }

    // The default archetype of an account: a fixed spread over the archetypes.
    public static NpcArchetype defaultArchetype(long accountId) {
        double x = new Random(accountId * 40503L + 7).nextDouble();
        if (x < 0.18) return NpcArchetype.FARMER;
        if (x < 0.36) return NpcArchetype.RAIDER;
        if (x < 0.50) return NpcArchetype.TURTLE;
        if (x < 0.64) return NpcArchetype.CONQUEROR;
        if (x < 0.78) return NpcArchetype.TRADER;
        return NpcArchetype.BALANCED;
    }

    // The default skill of an account, 0.35 .. 1.
    public static double defaultSkill(long accountId) {
        Random r = new Random(accountId * 40503L + 7);
        r.nextDouble();
        return 0.35 + 0.65 * r.nextDouble();
    }

    // The skill the NPC plays with: the difficulty's skill scaled by the NPC's own (a 0.5 NPC plays exactly at the preset's level).
    public static double effectiveSkill(NpcDifficulty difficulty, double profileSkill) {
        return Math.max(0, Math.min(1, difficulty.skill() * (0.6 + 0.8 * profileSkill)));
    }

    @Transactional
    public NpcProfile profileOf(Account npc) {
        return profiles.findByAccountId(npc.getId()).orElseGet(() -> {
            NpcProfile p = new NpcProfile();
            p.setAccountId(npc.getId());
            p.setArchetype(defaultArchetype(npc.getId()).name());
            p.setSkill(defaultSkill(npc.getId()));
            return profiles.save(p);
        });
    }

    // Sets the archetype (null / "random" keeps or draws the default) and skill in percent (null keeps / draws the default).
    @Transactional
    public NpcProfile update(Account npc, String archetype, Integer skillPercent) {
        NpcProfile p = profileOf(npc);
        if (archetype != null && !archetype.isBlank() && !archetype.equalsIgnoreCase("random")) {
            if (!NpcArchetype.isValid(archetype)) throw new IllegalArgumentException("Unknown archetype: " + archetype);
            p.setArchetype(NpcArchetype.parse(archetype).name());
        }
        if (skillPercent != null) {
            if (skillPercent < 0 || skillPercent > 100) throw new IllegalArgumentException("Skill is 0 to 100");
            p.setSkill(skillPercent / 100.0);
        }
        return profiles.save(p);
    }

    @Transactional
    public void deleteAccountData(Long accountId) { profiles.deleteByAccountId(accountId); }
}
