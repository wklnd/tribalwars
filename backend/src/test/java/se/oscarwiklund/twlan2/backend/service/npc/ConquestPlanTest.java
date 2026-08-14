package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.UnitType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static se.oscarwiklund.twlan2.backend.domain.UnitType.*;
import static org.junit.jupiter.api.Assertions.*;

class ConquestPlanTest {

    private static Map<UnitType, Integer> units(Object... pairs) {
        Map<UnitType, Integer> m = new EnumMap<>(UnitType.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((UnitType) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    @Test
    void wavesFollowLoyaltyAndDecrease() {
        assertEquals(4, ConquestPlan.wavesNeeded(100, 20, 35, false)); // 100 / 27.5
        assertEquals(5, ConquestPlan.wavesNeeded(100, 20, 35, true));  // 100 / 20, guaranteed
        assertEquals(1, ConquestPlan.wavesNeeded(10, 20, 35, false));
        assertEquals(2, ConquestPlan.wavesNeeded(40, 20, 20, false));
        assertEquals(50, ConquestPlan.wavesNeeded(50, 0, 0, false), "a broken setting never divides by zero");
    }

    @Test
    void needsEnoughNoblemenAndEscort() {
        var army = units(AXE, 200);
        assertNull(ConquestPlan.waves(3, 4, army, units(), 0, 20, 1, 1, 0.3), "one nobleman short");
        var w = ConquestPlan.waves(4, 4, army, units(), 0, 20, 1, 1, 0.3);
        assertNotNull(w);
        assertEquals(4, w.count());
        assertTrue(w.escortEach().get(AXE) * 4 <= 200);
    }

    @Test
    void escortMustBeAbleToWinAlone() {
        // a garrison that needs most of the army: four escorts cannot all be that big
        assertNull(ConquestPlan.waves(4, 4, units(AXE, 200), units(SPEAR, 300), 0, 20, 1, 1, 0.3));
        // an empty village needs only a few axes each
        var w = ConquestPlan.waves(5, 5, units(AXE, 60), units(), 3, 20, 1, 1, 0.3);
        assertNotNull(w);
    }
}
