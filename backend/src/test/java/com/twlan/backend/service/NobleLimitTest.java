package com.twlan.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The coin system: the n-th nobleman needs n gold coins more than the one before (1, 3, 6, 10, ...).
class NobleLimitTest {

    @Test
    void limitGrowsTriangularWithCoins() {
        assertEquals(0, NobleService.limitFor(0));
        assertEquals(1, NobleService.limitFor(1));
        assertEquals(1, NobleService.limitFor(2));
        assertEquals(2, NobleService.limitFor(3));
        assertEquals(3, NobleService.limitFor(6));
        assertEquals(4, NobleService.limitFor(10));
        assertEquals(4, NobleService.limitFor(14));
        assertEquals(5, NobleService.limitFor(15));
    }

    @Test
    void coinsForLimitIsTheInverse() {
        for (int n = 0; n < 40; n++) {
            assertEquals(n, NobleService.limitFor(NobleService.coinsForLimit(n)));
            assertEquals(Math.max(0, n - 1), NobleService.limitFor(Math.max(0, NobleService.coinsForLimit(n) - 1)));
        }
    }
}
