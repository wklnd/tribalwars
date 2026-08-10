package com.twlan.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketRulesTest {

    @Test
    void merchantsGrowWithTheMarketLevel() {
        assertEquals(0, MarketService.merchantsForLevel(0));
        assertEquals(1, MarketService.merchantsForLevel(1));
        assertEquals(10, MarketService.merchantsForLevel(10));
        assertEquals(14, MarketService.merchantsForLevel(12));
        assertEquals(235, MarketService.merchantsForLevel(25));
    }

    @Test
    void levelsOutsideTheRangeAreClamped() {
        assertEquals(0, MarketService.merchantsForLevel(-3));
        assertEquals(235, MarketService.merchantsForLevel(99));
    }

    @Test
    void everyStartedThousandNeedsAMerchant() {
        assertEquals(1, MarketService.merchantsNeeded(1));
        assertEquals(1, MarketService.merchantsNeeded(1000));
        assertEquals(2, MarketService.merchantsNeeded(1001));
        assertEquals(5, MarketService.merchantsNeeded(4500));
    }
}
