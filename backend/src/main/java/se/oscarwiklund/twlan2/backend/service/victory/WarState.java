package se.oscarwiklund.twlan2.backend.service.victory;

import java.time.Instant;
import java.util.List;

// The War evaluator's own working memory (stored as WorldVictory.stateJson), separate from the admin-set
// WarParams. PENDING -> roster picked at selectAfterDays -> PREP -> live at rosterLockedAt+prepDays -> LIVE
// -> exactly one roster tribe still holds a village -> DONE.
public record WarState(Phase phase, List<Long> rosterTribeIds, Instant rosterLockedAt) {
    public enum Phase { PENDING, PREP, LIVE, DONE }

    public static WarState initial() { return new WarState(Phase.PENDING, List.of(), null); }
}
