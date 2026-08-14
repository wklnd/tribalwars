package se.oscarwiklund.twlan2.backend.service.npc;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NpcProfilesTest {

    @Test
    void anAccountAlwaysGetsTheSameDefaults() {
        for (long id = 1; id <= 50; id++) {
            assertEquals(NpcProfiles.defaultArchetype(id), NpcProfiles.defaultArchetype(id));
            assertEquals(NpcProfiles.defaultSkill(id), NpcProfiles.defaultSkill(id), 0);
        }
    }

    @Test
    void everyArchetypeShowsUpAndSkillStaysInRange() {
        Map<NpcArchetype, Integer> seen = new EnumMap<>(NpcArchetype.class);
        for (long id = 1; id <= 600; id++) {
            seen.merge(NpcProfiles.defaultArchetype(id), 1, Integer::sum);
            double s = NpcProfiles.defaultSkill(id);
            assertTrue(s >= 0.35 && s <= 1.0, "skill " + s);
        }
        for (NpcArchetype a : NpcArchetype.values()) assertTrue(seen.getOrDefault(a, 0) > 30, a + " is too rare: " + seen);
    }

    @Test
    void anAverageNpcPlaysAtThePresetsLevel() {
        assertEquals(0.6, NpcProfiles.effectiveSkill(NpcDifficulty.NORMAL, 0.5), 1e-9);
        assertTrue(NpcProfiles.effectiveSkill(NpcDifficulty.NORMAL, 1.0) > NpcProfiles.effectiveSkill(NpcDifficulty.NORMAL, 0.0));
        assertEquals(1.0, NpcProfiles.effectiveSkill(NpcDifficulty.BRUTAL, 1.0), 1e-9);
    }

    @Test
    void archetypeNamesParseLeniently() {
        assertEquals(NpcArchetype.RAIDER, NpcArchetype.parse(" raider "));
        assertEquals(NpcArchetype.BALANCED, NpcArchetype.parse("nonsense"));
        assertTrue(NpcArchetype.isValid("Turtle"));
        assertFalse(NpcArchetype.isValid("random"));
    }
}
