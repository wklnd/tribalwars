package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.Village;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WorldViewTest {

    private static Village at(long id, int x, int y) {
        Village v = new Village();
        v.setId(id);
        v.setX(x);
        v.setY(y);
        return v;
    }

    // The grid must return everything within the range (it may return a little more: the square of cells around the point).
    @Test
    void nearFindsEveryVillageInRange() {
        Random rnd = new Random(7);
        List<Village> all = new ArrayList<>();
        for (int i = 0; i < 3000; i++) all.add(at(i, rnd.nextInt(400), rnd.nextInt(400)));
        all.add(at(9000, 0, 0));
        all.add(at(9001, 399, 399));
        WorldView view = new WorldView(null, all, Map.of());
        for (int[] p : new int[][] {{200, 200}, {0, 0}, {399, 399}, {5, 390}, {57, 13}}) {
            for (double range : new double[] {0, 3.5, 12, 32, 45}) {
                Set<Long> expected = all.stream().filter(v -> Math.hypot(v.getX() - p[0], v.getY() - p[1]) <= range)
                        .map(Village::getId).collect(Collectors.toSet());
                Set<Long> got = view.near(p[0], p[1], range).stream().map(Village::getId).collect(Collectors.toSet());
                assertTrue(got.containsAll(expected), "missed a village near " + p[0] + "|" + p[1] + " range " + range);
                assertTrue(got.size() <= expected.size() + 4 * range * range + 1000, "returns far more than the square around the point");
            }
        }
    }

    @Test
    void nearWorksAtTheEdgeOfTheMap() {
        WorldView view = new WorldView(null, List.of(at(1, 0, 0), at(2, 3, 3)), Map.of());
        assertEquals(2, view.near(0, 0, 10).size());
        assertTrue(view.near(0, 0, 0).stream().anyMatch(v -> v.getId() == 1), "the village on the spot itself");
    }
}
