package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.TribeRepository;
import se.oscarwiklund.twlan2.backend.repo.TribeWarRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// Tribal wars: battles between players of two different tribes are tallied per pair of tribes (units destroyed in each
// direction). A pair shows up on the wars page once it has kills or one side has set the other as enemy.
// The original's own wars page was never implemented ("Not implemented yet"), so this has no ground truth.
@Service
public class WarService {

    public record TribeRef(Long id, String tag, String name) {}

    // a is the viewer's own tribe on the tribe page; on the world list the tribe with the lower id.
    public record War(TribeRef a, TribeRef b, long killsA, long killsB, String relationAB, String relationBA) {}

    private final TribeWarRepository wars;
    private final TribeRepository tribes;
    private final TribeService tribeService;

    public WarService(TribeWarRepository wars, TribeRepository tribes, TribeService tribeService) {
        this.wars = wars;
        this.tribes = tribes;
        this.tribeService = tribeService;
    }

    // defenderLosses are units the attacker's tribe destroyed, attackerLosses units the defender's tribe destroyed.
    // Nothing happens unless both sides are members of different tribes.
    @Transactional
    public void onBattle(World world, Account attacker, Account defender, long defenderLosses, long attackerLosses) {
        if (world == null || attacker == null || defender == null) return;
        Optional<Tribe> ta = tribeService.tribeOf(attacker, world);
        Optional<Tribe> td = tribeService.tribeOf(defender, world);
        if (ta.isEmpty() || td.isEmpty() || ta.get().getId().equals(td.get().getId())) return;
        add(world.getId(), ta.get().getId(), td.get().getId(), defenderLosses);
        add(world.getId(), td.get().getId(), ta.get().getId(), attackerLosses);
    }

    private void add(Long worldId, Long killer, Long victim, long kills) {
        if (kills <= 0) return;
        TribeWar w = wars.findByKillerTribeIdAndVictimTribeId(killer, victim).orElseGet(() -> {
            TribeWar n = new TribeWar();
            n.setWorldId(worldId);
            n.setKillerTribeId(killer);
            n.setVictimTribeId(victim);
            return n;
        });
        w.setKills(w.getKills() + kills);
        wars.save(w);
    }

    // Most units destroyed first; with tribeId only that tribe's wars, oriented so "a" is it.
    @Transactional(readOnly = true)
    public List<War> list(World world, Long tribeId) {
        Map<Long, Tribe> byId = new HashMap<>();
        for (Tribe t : tribes.findByWorldId(world.getId())) byId.put(t.getId(), t);
        Map<String, long[]> kills = new LinkedHashMap<>(); // "lo:hi" -> {kills of lo, kills of hi}
        for (TribeWar w : wars.findByWorldId(world.getId())) {
            if (!byId.containsKey(w.getKillerTribeId()) || !byId.containsKey(w.getVictimTribeId())) continue;
            long lo = Math.min(w.getKillerTribeId(), w.getVictimTribeId()), hi = Math.max(w.getKillerTribeId(), w.getVictimTribeId());
            kills.computeIfAbsent(lo + ":" + hi, k -> new long[2])[w.getKillerTribeId() == lo ? 0 : 1] += w.getKills();
        }
        // pairs that only declared an enemy
        for (Tribe t : byId.values()) {
            for (Tribe o : byId.values()) {
                if (t.getId() >= o.getId()) continue;
                if (tribeService.relationOf(t.getId(), o.getId()) == TribeRelation.Kind.ENEMY
                        || tribeService.relationOf(o.getId(), t.getId()) == TribeRelation.Kind.ENEMY) {
                    kills.computeIfAbsent(t.getId() + ":" + o.getId(), k -> new long[2]);
                }
            }
        }
        List<War> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : kills.entrySet()) {
            String[] ids = e.getKey().split(":");
            Tribe lo = byId.get(Long.parseLong(ids[0])), hi = byId.get(Long.parseLong(ids[1]));
            long[] k = e.getValue();
            War war = new War(ref(lo), ref(hi), k[0], k[1], rel(lo, hi), rel(hi, lo));
            if (tribeId == null) out.add(war);
            else if (lo.getId().equals(tribeId)) out.add(war);
            else if (hi.getId().equals(tribeId)) out.add(new War(war.b(), war.a(), war.killsB(), war.killsA(), war.relationBA(), war.relationAB()));
        }
        out.sort(Comparator.comparingLong((War w) -> w.killsA() + w.killsB()).reversed().thenComparing(w -> w.a().tag(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static TribeRef ref(Tribe t) { return new TribeRef(t.getId(), t.getTag(), t.getName()); }

    private String rel(Tribe from, Tribe to) {
        TribeRelation.Kind k = tribeService.relationOf(from.getId(), to.getId());
        return k == null ? null : k.name();
    }

    @EventListener
    @Transactional
    public void onTribeDisbanded(TribeService.TribeDisbanded event) {
        wars.deleteByKillerTribeIdOrVictimTribeId(event.tribeId(), event.tribeId());
    }
}
