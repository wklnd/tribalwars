package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.NpcLogEntry;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.repo.NpcLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// What the NPCs did, one line each ("Raid: 40 units to Abandoned Camp (500|500)"). Kept as a ring: the newest
// KEEP lines per world, older ones are pruned. Kinds: RAID, BATTLE, NOBLE, CONQUEST, SCOUT, DODGE, SUPPORT, TRADE, BUILD.
@Service
public class NpcLogService {

    public static final int KEEP = 1500;

    private final NpcLogRepository log;
    private int sinceLastPrune = 0;

    public NpcLogService(NpcLogRepository log) {
        this.log = log;
    }

    @Transactional
    public void add(World world, Account npc, Village village, String kind, String message) {
        if (world == null || npc == null) return;
        NpcLogEntry e = new NpcLogEntry();
        e.setWorldId(world.getId());
        e.setAccountId(npc.getId());
        e.setVillageId(village == null ? null : village.getId());
        e.setKind(kind);
        e.setMessage(message == null ? "" : message.length() > 390 ? message.substring(0, 390) : message);
        log.save(e);
        if (++sinceLastPrune >= 200) {
            sinceLastPrune = 0;
            prune(world.getId());
        }
    }

    @Transactional
    public void prune(Long worldId) {
        List<Long> old = log.ids(worldId, PageRequest.of(KEEP, 1));
        if (!old.isEmpty()) log.deleteUpTo(worldId, old.get(0));
    }

    @Transactional(readOnly = true)
    public List<NpcLogEntry> recent(Long worldId, String kind, Long accountId, int limit) {
        return log.recent(worldId, kind == null || kind.isBlank() ? null : kind, accountId, PageRequest.of(0, Math.max(1, Math.min(500, limit))));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts(Long worldId) {
        Map<String, Long> out = new TreeMap<>();
        for (Object[] row : log.countsByKind(worldId)) out.put((String) row[0], ((Number) row[1]).longValue());
        return out;
    }

    @Transactional
    public void deleteWorldData(Long worldId) { log.deleteByWorldId(worldId); }

    @Transactional
    public void deleteAccountData(Long accountId) { log.deleteByAccountId(accountId); }
}
