package com.twlan.backend.service;

import com.twlan.backend.domain.*;
import com.twlan.backend.repo.*;
import com.twlan.backend.service.npc.NpcRhythm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// NPC players in tribes: the sociable ones found tribes with generated names, recruit unaffiliated NPCs, set relations to
// other tribes (allies, non-aggression pacts, enemies) and now and then invite a real player. Everything goes through
// TribeService, so the NPCs obey the same rules as players. Driven by NpcService.tick once per NPC tick;
// switched off per world with the npcTribes setting.
@Service
public class NpcTribeService {

    private static final String[] FIRST = {"Iron", "Black", "Red", "Silver", "Golden", "Grey", "Northern", "Wild", "Free", "Stone", "Storm",
            "Crimson", "Ancient", "Shadow", "Bold", "Frost", "Ember", "Thunder", "Royal", "Hollow"};
    private static final String[] SECOND = {"Wolves", "Ravens", "Legion", "Order", "Company", "Clan", "Guard", "Riders", "Hawks", "Bears",
            "Wardens", "Brotherhood", "Lions", "Vanguard", "Pact", "Hand", "Foxes", "Crown", "Pilgrims", "Banner"};

    private final TribeService tribes;
    private final TribeRepository tribeRepository;
    private final TribeMemberRepository members;
    private final TribeInvitationRepository invitations;
    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final WorldRepository worlds;
    private final NpcRhythm rhythm;
    private final Random rnd = new Random();

    public NpcTribeService(TribeService tribes, TribeRepository tribeRepository, TribeMemberRepository members,
                           TribeInvitationRepository invitations, AccountRepository accounts, VillageRepository villages,
                           WorldRepository worlds, NpcRhythm rhythm) {
        this.rhythm = rhythm;
        this.tribes = tribes;
        this.tribeRepository = tribeRepository;
        this.members = members;
        this.invitations = invitations;
        this.accounts = accounts;
        this.villages = villages;
        this.worlds = worlds;
    }

    // Stable per account (derived from the account id) and independent of the other personality traits.
    static double sociability(Account npc) {
        return new Random(npc.getId() * 15485863L + 11).nextDouble();
    }

    @Transactional
    public void tick() {
        for (World world : worlds.findAll()) {
            if (!WorldSettings.bool(world, "npcTribes")) continue;
            try {
                tick(world);
            } catch (TribeService.TribeException | IllegalArgumentException ignored) {
                // a rule refused what the NPC tried (full tribe, name taken, ...): it just tries something else next time
            }
        }
    }

    private void tick(World world) {
        // NPC accounts that live in this world
        // (one query for the world instead of one per NPC: with hundreds of NPCs that alone took seconds per tick)
        Set<Long> livesHere = new HashSet<>();
        for (Village v : villages.findByWorld(world)) if (v.getOwner() != null) livesHere.add(v.getOwner().getId());
        List<Account> npcs = new ArrayList<>();
        for (Account a : accounts.findByNpc(true)) {
            if (livesHere.contains(a.getId()) && rhythm.isAwake(a.getId(), world)) npcs.add(a); // (asleep: answers the invitation in the morning)
        }
        if (npcs.size() < 2) return;
        Map<Long, Tribe> tribeOf = tribes.tribeByAccount(world);

        answerInvitations(world, npcs, tribeOf);
        found(world, npcs, tribeOf);
        recruit(world, npcs, tribeOf);
        inviteRealPlayer(world, tribeOf);
        relations(world);
    }

    // Those without a tribe usually accept, the others turn them down.
    private void answerInvitations(World world, List<Account> npcs, Map<Long, Tribe> tribeOf) {
        Map<Long, List<TribeInvitation>> held = new HashMap<>();
        for (TribeInvitation i : invitations.findByWorldId(world.getId())) held.computeIfAbsent(i.getAccountId(), k -> new ArrayList<>()).add(i);
        for (Account npc : npcs) {
            List<TribeInvitation> mine = held.get(npc.getId());
            if (mine == null) continue;
            mine.sort(Comparator.comparing(TribeInvitation::getCreatedAt).reversed());
            for (TribeInvitation i : mine) {
                if (tribeOf.containsKey(npc.getId())) {
                    tribes.reject(npc, world, i.getId());
                } else if (rnd.nextDouble() < 0.25 + 0.5 * sociability(npc)) {
                    tribes.accept(npc, world, i.getId());
                    tribeOf.put(npc.getId(), tribeRepository.findById(i.getTribeId()).orElse(null));
                } else if (rnd.nextDouble() < 0.2) {
                    tribes.reject(npc, world, i.getId());
                }
            }
        }
    }

    // Sociable NPCs without a tribe found one while the world has fewer than about one tribe per eight NPCs.
    private void found(World world, List<Account> npcs, Map<Long, Tribe> tribeOf) {
        int wanted = Math.max(1, npcs.size() / 8);
        if (rnd.nextDouble() > 0.3) return; // (cheap check first: counting the live tribes costs a query per tribe)
        Set<Long> tribesWithMembers = new HashSet<>();
        for (TribeMember m : members.findByWorldId(world.getId())) tribesWithMembers.add(m.getTribeId());
        long existing = tribeRepository.findByWorldId(world.getId()).stream().filter(t -> tribesWithMembers.contains(t.getId())).count();
        if (existing >= wanted) return;
        List<Account> candidates = new ArrayList<>();
        for (Account a : npcs) if (!tribeOf.containsKey(a.getId()) && sociability(a) > 0.45) candidates.add(a);
        if (candidates.isEmpty()) return;
        Account founder = candidates.get(rnd.nextInt(candidates.size()));
        for (int attempt = 0; attempt < 12; attempt++) {
            String first = FIRST[rnd.nextInt(FIRST.length)], second = SECOND[rnd.nextInt(SECOND.length)];
            String tag = "" + first.charAt(0) + second.charAt(0) + (attempt < 6 ? "" : String.valueOf(rnd.nextInt(90) + 10));
            try {
                Tribe t = tribes.found(founder, world, first + " " + second, tag);
                tribeOf.put(founder.getId(), t);
                return;
            } catch (TribeService.TribeException nameTaken) {
                // try another name
            }
        }
    }

    // NPC tribes below their target size invite unaffiliated NPCs, who answer on a later tick.
    private void recruit(World world, List<Account> npcs, Map<Long, Tribe> tribeOf) {
        Map<Long, List<TribeMember>> byTribe = new HashMap<>();
        for (TribeMember m : members.findByWorldId(world.getId())) byTribe.computeIfAbsent(m.getTribeId(), k -> new ArrayList<>()).add(m);
        Map<Long, Account> byId = new HashMap<>();
        for (Account a : npcs) byId.put(a.getId(), a);
        for (Map.Entry<Long, List<TribeMember>> e : byTribe.entrySet()) {
            TribeMember founder = e.getValue().stream().filter(TribeMember::isFounder).findFirst().orElse(null);
            Account leader = founder == null ? null : byId.get(founder.getAccountId());
            if (leader == null) continue; // only NPC-led tribes recruit on their own
            int target = Math.min(tribes.memberLimit(world), 3 + (int) (sociability(leader) * 9));
            if (e.getValue().size() >= target || rnd.nextDouble() > 0.15) continue;
            List<Account> free = new ArrayList<>();
            for (Account a : npcs) if (!tribeOf.containsKey(a.getId())) free.add(a);
            if (free.isEmpty()) continue;
            Account pick = free.get(rnd.nextInt(free.size()));
            try {
                tribes.invite(leader, world, pick.getUsername());
            } catch (TribeService.TribeException alreadyInvited) {
                // it holds an invitation from this tribe already
            }
        }
    }

    // From time to time a well-grown NPC tribe invites a real player who has no tribe.
    private void inviteRealPlayer(World world, Map<Long, Tribe> tribeOf) {
        if (rnd.nextDouble() > 0.006) return;
        List<Account> players = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Village v : villages.findByWorld(world)) {
            Account o = v.getOwner();
            if (o != null && !o.isNpc() && v.getOwnerType() == OwnerType.PLAYER && seen.add(o.getId()) && !tribeOf.containsKey(o.getId())
                    && invitations.findByWorldIdAndAccountIdOrderByCreatedAtDesc(world.getId(), o.getId()).size() < 2) players.add(o);
        }
        if (players.isEmpty()) return;
        Account player = players.get(rnd.nextInt(players.size()));
        List<Tribe> options = new ArrayList<>();
        for (Tribe t : tribeRepository.findByWorldId(world.getId())) {
            List<TribeMember> ms = members.findByTribeId(t.getId());
            boolean npcLed = ms.stream().anyMatch(m -> m.isFounder() && accounts.findById(m.getAccountId()).map(Account::isNpc).orElse(false));
            if (npcLed && ms.size() >= 3 && ms.size() < tribes.memberLimit(world)
                    && invitations.findByTribeIdAndAccountId(t.getId(), player.getId()).isEmpty()) options.add(t);
        }
        if (options.isEmpty()) return;
        Tribe from = options.get(rnd.nextInt(options.size()));
        TribeMember founder = members.findByTribeId(from.getId()).stream().filter(TribeMember::isFounder).findFirst().orElse(null);
        if (founder == null) return;
        Account leader = accounts.findById(founder.getAccountId()).orElse(null);
        if (leader != null) tribes.invite(leader, world, player.getUsername());
    }

    // NPC-led tribes now and then set (or end) a relation to another tribe. Non-binding, but NPC attacks follow it.
    private void relations(World world) {
        List<Tribe> all = tribeRepository.findByWorldId(world.getId());
        if (all.size() < 2 || rnd.nextDouble() > 0.05) return;
        Tribe a = all.get(rnd.nextInt(all.size()));
        Tribe b = all.get(rnd.nextInt(all.size()));
        if (a.getId().equals(b.getId())) return;
        TribeMember founder = members.findByTribeId(a.getId()).stream().filter(TribeMember::isFounder).findFirst().orElse(null);
        Account leader = founder == null ? null : accounts.findById(founder.getAccountId()).orElse(null);
        if (leader == null || !leader.isNpc()) return;
        TribeRelation.Kind current = tribes.relationOf(a.getId(), b.getId());
        if (current != null) {
            if (rnd.nextDouble() < 0.1) tribes.endRelation(leader, world, b.getId());
            return;
        }
        double roll = rnd.nextDouble();
        tribes.addRelation(leader, world, b.getTag(), roll < 0.4 ? "NAP" : roll < 0.7 ? "PARTNER" : "ENEMY");
    }
}
