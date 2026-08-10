package com.twlan.backend.live;

import com.twlan.backend.domain.Account;
import com.twlan.backend.domain.Village;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Tells connected browsers "something changed" so they refetch instead of polling (GET /api/events, server-sent events).
// An event carries only a topic (VILLAGE, REPORTS, ...), never game data: the browser asks the normal
// endpoint, so a lost event costs at most one slow poll and every DTO stays defined in one place.
// - Calls are no-ops while nobody is connected (the browser closed), so publishing costs nothing then. NPCs have no connection.
// - Inside a transaction an event is queued only after it commits (never announce something that rolls back).
// - Events are collected and sent every FLUSH_MS ms, so a burst (an NPC tick) is one event per topic and client.
// - MAP (the world's village list: owners, points) goes out at most every MAP_EVERY_MS ms per world, TRIBE every TRIBE_EVERY_MS ms.
// - A comment line is sent every PING_MS ms: it notices dead connections and keeps proxies from closing an idle stream.
@Component
public class LiveUpdates {

    private static final Logger LOG = LoggerFactory.getLogger(LiveUpdates.class);

    // The player's own village: resources, queues, troops, movements, incoming attacks.
    public static final String VILLAGE = "village";
    public static final String REPORTS = "reports";
    // The village list of the world: owners and points.
    public static final String MAP = "map";
    public static final String MARKET = "market";
    public static final String TRIBE = "tribe";
    public static final String ACHIEVEMENTS = "achievements";
    // A new mail arrived (the menu's new-mail icon; an open mail screen refetches).
    public static final String MAIL = "mail";

    static final long FLUSH_MS = 250;
    static final long MAP_EVERY_MS = 8000;
    static final long TRIBE_EVERY_MS = 3000;
    static final long PING_MS = 20000;
    static final long EVERYONE = -1;

    // Where to send one event to one connection (an SseEmitter in production, a fake in the tests).
    interface Sink {
        void event(String topic) throws IOException;
        void ping() throws IOException;
    }

    record Client(long accountId, long worldId, Sink sink) {}

    // accountId EVERYONE: every connection in the world.
    record Target(long accountId, long worldId, String topic) {}

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final Set<Target> pending = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>(); // "world:topic" -> when a throttled event last went out
    private long lastPing = System.currentTimeMillis();
    private ScheduledExecutorService worker;

    @PostConstruct
    void start() {
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "live-updates");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(() -> {
            try {
                flush(System.currentTimeMillis());
            } catch (RuntimeException e) {
                LOG.warn("Live update flush failed: {}", e.toString()); // the loop must never die
            }
        }, FLUSH_MS, FLUSH_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (worker != null) worker.shutdownNow();
        for (Client c : clients) {
            if (c.sink() instanceof EmitterSink s) s.emitter.complete();
        }
        clients.clear();
    }

    // ---- connections -------------------------------------------------------------------------------------------------------

    // A new browser connection of the account, playing in the given world. It is told to refetch everything right away.
    public SseEmitter connect(long accountId, long worldId) {
        SseEmitter emitter = new SseEmitter(0L); // never times out: it lives as long as the page
        Client client = new Client(accountId, worldId, new EmitterSink(emitter));
        Runnable gone = () -> clients.remove(client);
        emitter.onCompletion(gone);
        emitter.onTimeout(gone);
        emitter.onError(e -> gone.run());
        try {
            client.sink().event("hello");
        } catch (IOException e) {
            return emitter;
        }
        clients.add(client);
        return emitter;
    }

    private record EmitterSink(SseEmitter emitter) implements Sink {
        @Override
        public void event(String topic) throws IOException {
            emitter.send(SseEmitter.event().name(topic).data("{}"));
        }

        @Override
        public void ping() throws IOException {
            emitter.send(SseEmitter.event().comment("ping"));
        }
    }

    public int connections() {
        return clients.size();
    }

    // ---- publishing --------------------------------------------------------------------------------------------------------

    // The village's owner (if a real player) should refetch their village.
    public void village(Village v) {
        if (v == null || v.getWorld() == null) return;
        toAccount(v.getOwner(), v.getWorld().getId(), VILLAGE);
    }

    public void toAccount(Account account, Long worldId, String topic) {
        if (account == null || account.isNpc() || worldId == null || clients.isEmpty()) return;
        queue(new Target(account.getId(), worldId, topic));
    }

    public void toWorld(Long worldId, String topic) {
        if (worldId == null || clients.isEmpty()) return;
        queue(new Target(EVERYONE, worldId, topic));
    }

    private void queue(Target target) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pending.add(target);
                }
            });
        } else {
            pending.add(target);
        }
    }

    // Topics that many things touch (NPCs build all the time) go out at most this often.
    private static long gapOf(String topic) {
        return MAP.equals(topic) ? MAP_EVERY_MS : TRIBE.equals(topic) ? TRIBE_EVERY_MS : 0;
    }

    // True while at least one browser is connected (lets callers skip work that only feeds an event).
    public boolean listening() {
        return !clients.isEmpty();
    }

    // ---- sending -----------------------------------------------------------------------------------------------------------

    // Sends what has been collected (and a heartbeat when due). Package-private with the clock as a parameter for the tests.
    void flush(long now) {
        if (clients.isEmpty()) {
            pending.clear();
            return;
        }
        for (Target t : pending) {
            long gap = gapOf(t.topic());
            String key = t.worldId() + ":" + t.topic();
            if (gap > 0 && now - lastSent.getOrDefault(key, 0L) < gap) continue; // stays queued until it is due
            if (!pending.remove(t)) continue;
            if (gap > 0) lastSent.put(key, now);
            for (Client c : clients) {
                if (c.worldId() != t.worldId() || (t.accountId() != EVERYONE && c.accountId() != t.accountId())) continue;
                try {
                    c.sink().event(t.topic());
                } catch (IOException | RuntimeException e) {
                    clients.remove(c); // the browser went away
                }
            }
        }
        if (now - lastPing >= PING_MS) {
            lastPing = now;
            for (Client c : clients) {
                try {
                    c.sink().ping();
                } catch (IOException | RuntimeException e) {
                    clients.remove(c);
                }
            }
        }
    }

    // ---- for the tests -----------------------------------------------------------------------------------------------------

    void add(Client client) {
        clients.add(client);
    }

    int pendingCount() {
        return pending.size();
    }
}
