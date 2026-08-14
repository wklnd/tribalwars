package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.VillageGroupMemberRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageGroupRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// Village groups: a player's own named sets of villages (Overviews > Groups). Every player implicitly has the group
// "all" (id 0), which is not stored. Rule breaks throw IllegalArgumentException (shown to the player, HTTP 400).
@Service
public class GroupService {

    public static final int MAX_NAME = 30;
    public static final int MAX_GROUPS = 50;

    public record Group(Long id, String name, List<Long> villageIds) {}

    private final VillageGroupRepository groups;
    private final VillageGroupMemberRepository members;
    private final VillageRepository villages;

    public GroupService(VillageGroupRepository groups, VillageGroupMemberRepository members, VillageRepository villages) {
        this.groups = groups;
        this.members = members;
        this.villages = villages;
    }

    // Oldest first; each group lists only the villages the account still owns.
    @Transactional(readOnly = true)
    public List<Group> list(Account account, World world) {
        List<VillageGroup> mine = groups.findByWorldIdAndAccountIdOrderByIdAsc(world.getId(), account.getId());
        if (mine.isEmpty()) return List.of();
        Set<Long> owned = new HashSet<>();
        for (Village v : villages.findByWorldAndOwner(world, account)) owned.add(v.getId());
        Map<Long, List<Long>> byGroup = new HashMap<>();
        for (VillageGroupMember m : members.findByGroupIdIn(mine.stream().map(VillageGroup::getId).toList())) {
            if (owned.contains(m.getVillageId())) byGroup.computeIfAbsent(m.getGroupId(), k -> new ArrayList<>()).add(m.getVillageId());
        }
        List<Group> out = new ArrayList<>();
        for (VillageGroup g : mine) {
            List<Long> ids = byGroup.getOrDefault(g.getId(), new ArrayList<>());
            Collections.sort(ids);
            out.add(new Group(g.getId(), g.getName(), ids));
        }
        return out;
    }

    private String cleanName(String raw, Account account, World world, Long ownId) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Please enter a group name");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("The group name may have at most " + MAX_NAME + " characters");
        if (name.equalsIgnoreCase("all")) throw new IllegalArgumentException("A group with this name already exists");
        for (VillageGroup g : groups.findByWorldIdAndAccountIdOrderByIdAsc(world.getId(), account.getId())) {
            if (!g.getId().equals(ownId) && g.getName().equalsIgnoreCase(name)) throw new IllegalArgumentException("A group with this name already exists");
        }
        return name;
    }

    private VillageGroup own(Long id, Account account, World world) {
        return groups.findById(id)
                .filter(g -> g.getAccountId().equals(account.getId()) && g.getWorldId().equals(world.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
    }

    @Transactional
    public List<Group> create(Account account, World world, String name) {
        String clean = cleanName(name, account, world, null);
        if (groups.findByWorldIdAndAccountIdOrderByIdAsc(world.getId(), account.getId()).size() >= MAX_GROUPS) {
            throw new IllegalArgumentException("You cannot have more than " + MAX_GROUPS + " groups");
        }
        VillageGroup g = new VillageGroup();
        g.setWorldId(world.getId());
        g.setAccountId(account.getId());
        g.setName(clean);
        groups.save(g);
        return list(account, world);
    }

    @Transactional
    public List<Group> rename(Account account, World world, Long id, String name) {
        VillageGroup g = own(id, account, world);
        g.setName(cleanName(name, account, world, g.getId()));
        groups.save(g);
        return list(account, world);
    }

    @Transactional
    public List<Group> delete(Account account, World world, Long id) {
        VillageGroup g = own(id, account, world);
        members.deleteByGroupId(g.getId());
        groups.delete(g);
        return list(account, world);
    }

    // Backs the checkboxes of "» edit".
    @Transactional
    public List<Group> assign(Account account, World world, Long villageId, Collection<Long> groupIds) {
        Village v = villages.findById(villageId).orElseThrow(() -> new IllegalArgumentException("Village not found"));
        if (v.getOwner() == null || !v.getOwner().getId().equals(account.getId()) || v.getWorld() == null || !v.getWorld().getId().equals(world.getId())) {
            throw new IllegalArgumentException("This is not your village");
        }
        Set<Long> wanted = new HashSet<>(groupIds == null ? List.of() : groupIds);
        for (VillageGroup g : groups.findByWorldIdAndAccountIdOrderByIdAsc(world.getId(), account.getId())) {
            List<VillageGroupMember> here = members.findByVillageId(villageId).stream().filter(m -> m.getGroupId().equals(g.getId())).toList();
            boolean in = !here.isEmpty();
            if (wanted.contains(g.getId()) && !in) {
                VillageGroupMember m = new VillageGroupMember();
                m.setGroupId(g.getId());
                m.setVillageId(villageId);
                members.save(m);
            } else if (!wanted.contains(g.getId()) && in) {
                members.deleteAll(here);
            }
        }
        return list(account, world);
    }

    // ---- cleanup hooks ---------------------------------------------------------------------------------------------

    // Called when a village was conquered or deleted.
    @Transactional
    public void dropVillage(Long villageId) { members.deleteByVillageId(villageId); }

    @Transactional
    public void deleteAccountData(Long accountId) {
        for (VillageGroup g : groups.findByAccountId(accountId)) {
            members.deleteByGroupId(g.getId());
            groups.delete(g);
        }
    }

    @Transactional
    public void deleteWorldData(Long worldId) {
        for (VillageGroup g : groups.findByWorldId(worldId)) {
            members.deleteByGroupId(g.getId());
            groups.delete(g);
        }
    }
}
