package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.UnitType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SupportSplitTest {

    private static Map<UnitType, Integer> units(Object... kv) {
        Map<UnitType, Integer> m = new EnumMap<>(UnitType.class);
        for (int i = 0; i < kv.length; i += 2) m.put((UnitType) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private static int total(List<Map<UnitType, Integer>> split, UnitType t) {
        return split.stream().mapToInt(m -> m.getOrDefault(t, 0)).sum();
    }

    @Test
    void sharesLossesProportionally() {
        var split = SupportSplit.split(units(UnitType.SPEAR, 50), List.of(units(UnitType.SPEAR, 100), units(UnitType.SPEAR, 100)));
        assertEquals(25, split.get(0).get(UnitType.SPEAR));
        assertEquals(25, split.get(1).get(UnitType.SPEAR));
    }

    @Test
    void wholeLossesAreAlwaysHandedOut() {
        var sources = List.of(units(UnitType.SPEAR, 7), units(UnitType.SPEAR, 5), units(UnitType.SPEAR, 3));
        for (int loss = 0; loss <= 15; loss++) {
            var split = SupportSplit.split(units(UnitType.SPEAR, loss), sources);
            assertEquals(loss, total(split, UnitType.SPEAR), "loss " + loss);
            for (int i = 0; i < sources.size(); i++) {
                assertTrue(split.get(i).getOrDefault(UnitType.SPEAR, 0) <= sources.get(i).get(UnitType.SPEAR), "source " + i + " loss " + loss);
            }
        }
    }

    @Test
    void nobodyLosesMoreThanTheyHadAndTheTotalIsCapped() {
        var split = SupportSplit.split(units(UnitType.AXE, 99), List.of(units(UnitType.AXE, 4), units(UnitType.AXE, 6)));
        assertEquals(4, split.get(0).get(UnitType.AXE));
        assertEquals(6, split.get(1).get(UnitType.AXE));
    }

    @Test
    void everyUnitTypeIsSplitOnItsOwn() {
        var split = SupportSplit.split(units(UnitType.SPEAR, 10, UnitType.SWORD, 3),
                List.of(units(UnitType.SPEAR, 30), units(UnitType.SWORD, 30, UnitType.SPEAR, 10)));
        assertEquals(10, total(split, UnitType.SPEAR));
        assertEquals(3, total(split, UnitType.SWORD));
        assertEquals(0, split.get(0).getOrDefault(UnitType.SWORD, 0)); // it had none
        assertEquals(3, split.get(1).get(UnitType.SWORD));
    }

    @Test
    void remainderGoesToTheBiggerHolder() {
        var split = SupportSplit.split(units(UnitType.SPEAR, 1), List.of(units(UnitType.SPEAR, 1), units(UnitType.SPEAR, 9)));
        assertEquals(0, split.get(0).getOrDefault(UnitType.SPEAR, 0));
        assertEquals(1, split.get(1).get(UnitType.SPEAR));
    }

    @Test
    void emptyLossesGiveNothing() {
        var split = SupportSplit.split(units(), List.of(units(UnitType.SPEAR, 5)));
        assertTrue(split.get(0).isEmpty());
    }
}
