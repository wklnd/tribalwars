package com.twlan.backend.service.npc;

import com.twlan.backend.domain.*;
import com.twlan.backend.repo.NpcGrudgeRepository;
import com.twlan.backend.repo.NpcIntelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

// What NPCs know and remember: intel about villages they scouted or fought at (table `npc_intel`) and grudges against
// players who attacked them (`npc_grudge`). An NPC only learns what a player could: what its scouts report, what
// its battles show. The rest is guessed from public information (points).
@Service
public class NpcIntelService {

    // A picture of a garrison is trusted for this many *world* seconds (real time divided by the world speed).
    public static final double FRESH_WORLD_SECONDS = 5400;
    public static final double MAX_GRUDGE = 5;

    private final NpcIntelRepository intel;
    private final NpcGrudgeRepository grudges;

    public NpcIntelService(NpcIntelRepository intel, NpcGrudgeRepository grudges) {
        this.intel = intel;
        this.grudges = grudges;
    }

    // ---- pure helpers ----------------------------------------------------------------------------------------------

    public static String format(Map<UnitType, Integer> troops) {
        StringBuilder sb = new StringBuilder();
        for (var e : new TreeMap<>(troops).entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public static Map<UnitType, Integer> parse(String troops) {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        if (troops == null || troops.isBlank()) return out;
        for (String part : troops.split(",")) {
            String[] kv = part.split(":");
            try {
                out.put(UnitType.valueOf(kv[0]), Integer.parseInt(kv[1]));
            } catch (RuntimeException ignored) {
                // a value we do not know: skip it
            }
        }
        return out;
    }

    // True while the garrison picture is recent enough to attack on.
    public static boolean fresh(NpcIntel i, double worldSpeed, Instant now) {
        if (i == null || i.getSeenAt() == null || i.getTroops() == null) return false;
        double worldSeconds = Duration.between(i.getSeenAt(), now).toMillis() / 1000.0 * Math.max(1, worldSpeed);
        return worldSeconds <= FRESH_WORLD_SECONDS;
    }

    // A grudge fades with a half-life of 12 world hours (real time / speed, speed capped at 50).
    public static double decayed(double score, Instant updatedAt, double worldSpeed, Instant now) {
        double hours = Duration.between(updatedAt, now).toMillis() / 3_600_000.0 * Math.min(50, Math.max(1, worldSpeed));
        return score * Math.pow(0.5, hours / 12);
    }

    // ---- intel -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<Long, NpcIntel> of(Long accountId) {
        Map<Long, NpcIntel> map = new HashMap<>();
        for (NpcIntel i : intel.findByAccountId(accountId)) map.put(i.getTargetVillageId(), i);
        return map;
    }

    private NpcIntel row(World world, Account npc, Village target) {
        return intel.findByAccountIdAndTargetVillageId(npc.getId(), target.getId()).orElseGet(() -> {
            NpcIntel i = new NpcIntel();
            i.setWorldId(world.getId());
            i.setAccountId(npc.getId());
            i.setTargetVillageId(target.getId());
            return i;
        });
    }

    // Scouts made it through: resources (spy level 1), buildings (2: the wall) and the garrison at home (3).
    @Transactional
    public void spy(World world, Account npc, Village target, int level, Map<UnitType, Integer> homeTroops, Integer wall,
                    double wood, double clay, double iron) {
        NpcIntel i = row(world, npc, target);
        i.setSource("SCOUT");
        i.setSeenAt(Instant.now());
        if (level >= 1) { i.setWood((int) wood); i.setClay((int) clay); i.setIron((int) iron); }
        if (level >= 2 && wall != null) i.setWall(wall);
        if (level >= 3) i.setTroops(format(homeTroops));
        else if (i.getTroops() != null) i.setTroops(null); // no garrison seen: not enough to attack on
        intel.save(i);
    }

    // A battle at the target: after a win its garrison is gone; after a loss the NPC knows less than before.
    @Transactional
    public void battle(World world, Account npc, Village target, boolean won, int wallAfter, int loot) {
        NpcIntel i = row(world, npc, target);
        Instant now = Instant.now();
        i.setLastAttackAt(now);
        i.setRaids(i.getRaids() + 1);
        if (won) {
            i.setSource("BATTLE");
            i.setSeenAt(now);
            i.setTroops("");
            i.setWall(wallAfter);
            i.setLastResult("WON");
            i.setLastLoot(loot);
            // the resources it saw are stale now (it took some): forget them, a raid estimate uses the loot
            i.setWood(null); i.setClay(null); i.setIron(null);
        } else {
            i.setLastResult("LOST");
            i.setLosses(i.getLosses() + 1);
            i.setTroops(null); // the old picture was wrong
        }
        intel.save(i);
    }

    @Transactional
    public void dropVillage(Long villageId) { intel.deleteByTargetVillageId(villageId); }

    // ---- grudges ---------------------------------------------------------------------------------------------------

    // `enemy` attacked `victim` (an NPC): the victim remembers.
    @Transactional
    public void hit(World world, Account victim, Account enemy, double weight) {
        if (world == null || victim == null || enemy == null || victim.getId().equals(enemy.getId())) return;
        Instant now = Instant.now();
        NpcGrudge g = grudges.findByAccountIdAndEnemyAccountId(victim.getId(), enemy.getId()).orElseGet(() -> {
            NpcGrudge n = new NpcGrudge();
            n.setWorldId(world.getId());
            n.setAccountId(victim.getId());
            n.setEnemyAccountId(enemy.getId());
            return n;
        });
        g.setScore(Math.min(MAX_GRUDGE, g.getScore() + weight));
        g.setUpdatedAt(now);
        grudges.save(g);
    }

    @Transactional(readOnly = true)
    public Map<Long, Double> grudgesOf(Long accountId, double worldSpeed) {
        Map<Long, Double> map = new HashMap<>();
        Instant now = Instant.now();
        for (NpcGrudge g : grudges.findByAccountId(accountId)) {
            double s = decayed(g.getScore(), g.getUpdatedAt(), worldSpeed, now);
            if (s >= 0.05) map.put(g.getEnemyAccountId(), s);
        }
        return map;
    }

    // ---- cleanup ---------------------------------------------------------------------------------------------------

    @Transactional
    public void deleteAccountData(Long accountId) {
        intel.deleteByAccountId(accountId);
        grudges.deleteByAccountId(accountId);
        grudges.deleteByEnemyAccountId(accountId);
    }

    @Transactional
    public void deleteWorldData(Long worldId) {
        intel.deleteByWorldId(worldId);
        grudges.deleteByWorldId(worldId);
    }
}
