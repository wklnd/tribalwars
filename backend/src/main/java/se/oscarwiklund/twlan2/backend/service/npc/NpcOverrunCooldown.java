package se.oscarwiklund.twlan2.backend.service.npc;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// A village whose entire home garrison was just wiped out gets a short, real-world grace period (never scaled by
// world speed) before NPC logic will recruit there again. Without this, an NPC already mid-session - or on a fast
// world, whose NpcRhythm reaction time can shrink to a few real seconds - starts rebuilding on literally the very
// next tick (every game.npc-tick-ms), leaving a real attacker no realistic chance to plan and land a follow-up
// "snipe" while the village is still weak. A human defender in real Tribal Wars doesn't need this rule spelled out:
// their own reaction time already provides the gap; an NPC that re-evaluates the whole world every few seconds does.
// In-memory only, like NpcRhythm's state - reset on a restart is fine, this is just a short-lived grace window.
@Component
public class NpcOverrunCooldown {

    static final long GRACE_MS = 5 * 60_000; // 5 real minutes, at every world speed

    private final Map<Long, Long> recoverUntil = new ConcurrentHashMap<>();

    public void markOverrun(long villageId) {
        recoverUntil.put(villageId, System.currentTimeMillis() + GRACE_MS);
    }

    public boolean isRecovering(long villageId) {
        Long until = recoverUntil.get(villageId);
        return until != null && System.currentTimeMillis() < until;
    }
}
