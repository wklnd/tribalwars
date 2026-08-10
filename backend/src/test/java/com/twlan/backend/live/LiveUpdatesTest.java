package com.twlan.backend.live;

import com.twlan.backend.domain.Account;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveUpdatesTest {

    private static class Fake implements LiveUpdates.Sink {
        final List<String> got = new ArrayList<>();
        boolean broken;

        @Override
        public void event(String topic) throws IOException {
            if (broken) throw new IOException("gone");
            got.add(topic);
        }

        @Override
        public void ping() throws IOException {
            if (broken) throw new IOException("gone");
            got.add("ping");
        }
    }

    private static Account account(long id, boolean npc) {
        Account a = new Account();
        a.setId(id);
        a.setNpc(npc);
        return a;
    }

    @Test
    void nothingIsQueuedWhileNobodyIsConnected() {
        LiveUpdates live = new LiveUpdates();
        live.toAccount(account(1, false), 6L, LiveUpdates.VILLAGE);
        live.toWorld(6L, LiveUpdates.MAP);
        assertEquals(0, live.pendingCount());
    }

    @Test
    void aBurstBecomesOneEventPerTopic() {
        LiveUpdates live = new LiveUpdates();
        Fake me = new Fake();
        live.add(new LiveUpdates.Client(1, 6, me));
        for (int i = 0; i < 100; i++) {
            live.toAccount(account(1, false), 6L, LiveUpdates.VILLAGE);
            live.toAccount(account(1, false), 6L, LiveUpdates.REPORTS);
        }
        live.flush(1000);
        assertEquals(List.of(LiveUpdates.VILLAGE, LiveUpdates.REPORTS), me.got.stream().sorted((a, b) -> b.compareTo(a)).toList());
        live.flush(1250);
        assertEquals(2, me.got.size(), "sent once, not again");
    }

    @Test
    void eventsReachOnlyTheRightAccountAndWorld() {
        LiveUpdates live = new LiveUpdates();
        Fake mine = new Fake(), other = new Fake(), otherWorld = new Fake();
        live.add(new LiveUpdates.Client(1, 6, mine));
        live.add(new LiveUpdates.Client(2, 6, other));
        live.add(new LiveUpdates.Client(1, 5, otherWorld));
        live.toAccount(account(1, false), 6L, LiveUpdates.VILLAGE);
        live.flush(1000);
        assertEquals(List.of(LiveUpdates.VILLAGE), mine.got);
        assertTrue(other.got.isEmpty(), "another player");
        assertTrue(otherWorld.got.isEmpty(), "the same player in another world");

        live.toWorld(6L, LiveUpdates.MARKET);
        live.flush(2000);
        assertEquals(List.of(LiveUpdates.VILLAGE, LiveUpdates.MARKET), mine.got);
        assertEquals(List.of(LiveUpdates.MARKET), other.got);
        assertTrue(otherWorld.got.isEmpty());
    }

    @Test
    void npcsAndUnknownAccountsAreIgnored() {
        LiveUpdates live = new LiveUpdates();
        live.add(new LiveUpdates.Client(1, 6, new Fake()));
        live.toAccount(account(9, true), 6L, LiveUpdates.VILLAGE);
        live.toAccount(null, 6L, LiveUpdates.VILLAGE);
        live.toAccount(account(1, false), null, LiveUpdates.VILLAGE);
        assertEquals(0, live.pendingCount());
    }

    @Test
    void theMapEventIsThrottledPerWorld() {
        LiveUpdates live = new LiveUpdates();
        Fake me = new Fake();
        live.add(new LiveUpdates.Client(1, 6, me));
        live.toWorld(6L, LiveUpdates.MAP);
        live.flush(10_000);
        assertEquals(List.of(LiveUpdates.MAP), me.got, "the first one goes out at once");

        live.toWorld(6L, LiveUpdates.MAP);
        live.flush(11_000);
        assertEquals(1, me.got.size(), "too soon");
        assertEquals(1, live.pendingCount(), "but it is not lost");
        live.flush(10_000 + LiveUpdates.MAP_EVERY_MS);
        assertEquals(2, me.got.size(), "sent when due");
        assertEquals(0, live.pendingCount());
    }

    @Test
    void aBrokenConnectionIsDroppedAndTheOthersStillGetTheEvent() {
        LiveUpdates live = new LiveUpdates();
        Fake dead = new Fake(), alive = new Fake();
        dead.broken = true;
        live.add(new LiveUpdates.Client(1, 6, dead));
        live.add(new LiveUpdates.Client(1, 6, alive));
        assertEquals(2, live.connections());
        live.toAccount(account(1, false), 6L, LiveUpdates.VILLAGE);
        live.flush(1000);
        assertEquals(1, live.connections());
        assertEquals(List.of(LiveUpdates.VILLAGE), alive.got);
    }

    @Test
    void heartbeatGoesOutRegularlyAndFindsDeadConnections() {
        LiveUpdates live = new LiveUpdates();
        Fake alive = new Fake(), dead = new Fake();
        live.add(new LiveUpdates.Client(1, 6, alive));
        live.add(new LiveUpdates.Client(2, 6, dead));
        long t0 = System.currentTimeMillis();
        live.flush(t0 + LiveUpdates.PING_MS + 1);
        assertEquals(List.of("ping"), alive.got);
        dead.broken = true;
        live.flush(t0 + 2 * LiveUpdates.PING_MS + 2);
        assertEquals(1, live.connections(), "the ping noticed the dead one");
        assertEquals(List.of("ping", "ping"), alive.got);
    }
}
