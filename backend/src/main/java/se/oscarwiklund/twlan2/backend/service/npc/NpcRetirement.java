package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.World;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

// Like a real account going inactive: about a quarter of NPCs (stable per account id) have a career length
// after which they stop acting for good - the normal abandonment sweep then picks up their villages, same
// as an inactive real player's. The rest keep playing indefinitely. Career length follows an exponential
// distribution (like real player drop-off curves): most who ever quit do so within the first couple of
// weeks, with a long thin tail of a few playing for months. Days are real-world days at world speed 1,
// scaled down on fast worlds the same way NpcRhythm scales session timing.
public final class NpcRetirement {
    private NpcRetirement() {}

    private static final double RETIRES_SHARE = 0.25;
    private static final double MEAN_CAREER_DAYS = 10;
    private static final double MAX_CAREER_DAYS = 365; // defensive cap on the exponential tail, not a distribution shape

    public static boolean everRetires(Account npc) {
        return new Random(npc.getId() * 6364136223846793005L + 1).nextDouble() < RETIRES_SHARE;
    }

    // null if this NPC never retires.
    public static Instant retiresAt(Account npc, World world) {
        if (!everRetires(npc)) return null;
        Random r = new Random(npc.getId() * 2246822519L + 5);
        double days = Math.min(MAX_CAREER_DAYS, -MEAN_CAREER_DAYS * Math.log(1 - r.nextDouble()));
        double speed = world == null || world.getSpeed() <= 0 ? 1 : world.getSpeed();
        double pace = Math.min(30, Math.max(1, Math.sqrt(speed))); // mirrors NpcRhythm.pace()
        return npc.getCreatedAt().plus(Duration.ofMillis((long) (days * 86_400_000 / pace)));
    }

    public static boolean isRetired(Account npc, World world, Instant now) {
        Instant at = retiresAt(npc, world);
        return at != null && !now.isBefore(at);
    }
}
