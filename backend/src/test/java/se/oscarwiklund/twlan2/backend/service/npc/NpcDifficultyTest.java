package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.service.npc.NpcDifficulty.Expansion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcDifficultyTest {

    private static World world(double speed, String... settings) {
        World w = new World();
        w.setSpeed(speed);
        for (int i = 0; i < settings.length; i += 2) w.getSettings().put(settings[i], settings[i + 1]);
        return w;
    }

    @Test
    void newWorldsPlayOnNormal() {
        assertEquals("normal", NpcDifficulty.of(world(1)).name());
    }

    @Test
    void ownSettingWins() {
        assertEquals("brutal", NpcDifficulty.of(world(1, "npcDifficulty", "brutal")).name());
        assertEquals("hard", NpcDifficulty.of(world(1, "npcDifficulty", "hard", "npcAggression", "off")).name());
    }

    @Test
    void theOldAggressionSettingStillCounts() {
        assertEquals("off", NpcDifficulty.of(world(1, "npcAggression", "off")).name());
        assertEquals("passive", NpcDifficulty.of(world(1, "npcAggression", "low")).name());
        assertEquals("normal", NpcDifficulty.of(world(1, "npcAggression", "normal")).name());
    }

    @Test
    void unknownNamesFallBackToNormal() {
        assertSame(NpcDifficulty.NORMAL, NpcDifficulty.named("nonsense"));
        assertSame(NpcDifficulty.NORMAL, NpcDifficulty.named(null));
    }

    @Test
    void presetsGetStrongerStepByStep() {
        var order = new NpcDifficulty[]{NpcDifficulty.PASSIVE, NpcDifficulty.NORMAL, NpcDifficulty.HARD, NpcDifficulty.BRUTAL};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i].skill() >= order[i - 1].skill());
            assertTrue(order[i].actionRate() > order[i - 1].actionRate());
            assertTrue(order[i].raidChance() > order[i - 1].raidChance());
            assertTrue(order[i].intelNoise() < order[i - 1].intelNoise());
            assertTrue(order[i].notice() >= order[i - 1].notice());
            assertTrue(order[i].expansion().ordinal() >= order[i - 1].expansion().ordinal());
            assertTrue(order[i].commit() <= order[i - 1].commit());
        }
    }

    @Test
    void offNeverAttacksOrConquers() {
        assertFalse(NpcDifficulty.OFF.attacks());
        assertEquals(Expansion.NONE, NpcDifficulty.OFF.expansionFor(world(1, "npcConquest", "anything")));
    }

    @Test
    void whatEachExpansionMayTake() {
        // barbarian, human, last village of a human
        assertFalse(Expansion.NONE.allows(true, false, false));
        assertTrue(Expansion.BARBARIANS.allows(true, false, false));
        assertFalse(Expansion.BARBARIANS.allows(false, false, false));
        assertTrue(Expansion.NPCS.allows(false, false, false));
        assertFalse(Expansion.NPCS.allows(false, true, false));
        assertTrue(Expansion.HUMANS.allows(false, true, false));
        assertFalse(Expansion.HUMANS.allows(false, true, true));
        assertTrue(Expansion.HUMANS_LAST.allows(false, true, true));
    }

    @Test
    void theConquestSettingOverridesThePreset() {
        assertEquals(Expansion.HUMANS_LAST, NpcDifficulty.BRUTAL.expansionFor(world(1)));
        assertEquals(Expansion.NPCS, NpcDifficulty.BRUTAL.expansionFor(world(1, "npcConquest", "never")));
        assertEquals(Expansion.BARBARIANS, NpcDifficulty.NORMAL.expansionFor(world(1, "npcConquest", "never")));
        assertEquals(Expansion.HUMANS, NpcDifficulty.BRUTAL.expansionFor(world(1, "npcConquest", "not-last")));
        assertEquals(Expansion.HUMANS_LAST, NpcDifficulty.NORMAL.expansionFor(world(1, "npcConquest", "anything")));
    }

    @Test
    void fastWorldsActMoreOftenButNotMoreThanDouble() {
        assertEquals(1.0, NpcDifficulty.speedFactor(world(1)), 1e-9);
        assertTrue(NpcDifficulty.speedFactor(world(2)) > 1.1);
        assertEquals(2.0, NpcDifficulty.speedFactor(world(1000)), 1e-9);
    }
}
