package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.AccountRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class NpcAdminService {

    public record ArchetypeInfo(String name, String label) {}
    public record NpcRow(Long accountId, String name, String archetype, int skill, int villages, String lastKind, String lastMessage, Instant lastAt, String rhythm) {}
    public record NpcList(String difficulty, List<ArchetypeInfo> archetypes, List<NpcRow> npcs) {}
    public record LogRow(Long id, Instant at, Long accountId, String npc, Long villageId, String village, String kind, String message) {}
    public record LogPage(List<LogRow> entries, Map<String, Long> counts) {}

    private final WorldRepository worlds;
    private final VillageRepository villages;
    private final AccountRepository accounts;
    private final NpcProfiles profiles;
    private final NpcLogService log;
    private final NpcRhythm rhythm;

    public NpcAdminService(WorldRepository worlds, VillageRepository villages, AccountRepository accounts, NpcProfiles profiles, NpcLogService log, NpcRhythm rhythm) {
        this.rhythm = rhythm;
        this.worlds = worlds;
        this.villages = villages;
        this.accounts = accounts;
        this.profiles = profiles;
        this.log = log;
    }

    private World world(Long id) { return worlds.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown world " + id)); }

    private static List<ArchetypeInfo> archetypes() {
        return Arrays.stream(NpcArchetype.values()).map(a -> new ArchetypeInfo(a.name(), a.label)).toList();
    }

    private NpcRow row(Account npc, int villageCount, NpcLogEntry last, World world) {
        NpcProfile p = profiles.profileOf(npc);
        return new NpcRow(npc.getId(), npc.getUsername(), p.getArchetype(), (int) Math.round(p.getSkill() * 100), villageCount,
                last == null ? null : last.getKind(), last == null ? null : last.getMessage(), last == null ? null : last.getOccurredAt(),
                rhythm.describe(npc.getId(), world));
    }

    @Transactional
    public NpcList list(Long worldId) {
        World w = world(worldId);
        Map<Long, Integer> count = new LinkedHashMap<>();
        Map<Long, Account> npcs = new LinkedHashMap<>();
        for (Village v : villages.findByWorld(w)) {
            Account o = v.getOwner();
            if (o == null || !o.isNpc()) continue;
            npcs.put(o.getId(), o);
            count.merge(o.getId(), 1, Integer::sum);
        }
        Map<Long, NpcLogEntry> last = new HashMap<>();
        for (NpcLogEntry e : log.recent(worldId, null, null, 500)) last.putIfAbsent(e.getAccountId(), e);
        List<NpcRow> rows = new ArrayList<>();
        for (Account a : npcs.values()) rows.add(row(a, count.get(a.getId()), last.get(a.getId()), w));
        rows.sort(Comparator.comparing(NpcRow::name, String.CASE_INSENSITIVE_ORDER));
        return new NpcList(NpcDifficulty.of(w).name(), archetypes(), rows);
    }

    @Transactional
    public NpcRow update(Long accountId, String archetype, Integer skill) {
        Account npc = accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Unknown account " + accountId));
        if (!npc.isNpc()) throw new IllegalArgumentException("This is not an NPC");
        profiles.update(npc, archetype, skill);
        var mine = villages.findByOwner(npc);
        return row(npc, mine.size(), null, mine.isEmpty() ? null : mine.get(0).getWorld());
    }

    @Transactional(readOnly = true)
    public LogPage log(Long worldId, String kind, Long accountId, int limit) {
        world(worldId);
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> villageNames = new HashMap<>();
        List<LogRow> rows = new ArrayList<>();
        for (NpcLogEntry e : log.recent(worldId, kind, accountId, limit)) {
            String name = names.computeIfAbsent(e.getAccountId(), id -> accounts.findById(id).map(Account::getUsername).orElse("?"));
            String vname = e.getVillageId() == null ? null : villageNames.computeIfAbsent(e.getVillageId(), id -> villages.findById(id).map(Village::getName).orElse(null));
            rows.add(new LogRow(e.getId(), e.getOccurredAt(), e.getAccountId(), name, e.getVillageId(), vname, e.getKind(), e.getMessage()));
        }
        return new LogPage(rows, log.counts(worldId));
    }
}
