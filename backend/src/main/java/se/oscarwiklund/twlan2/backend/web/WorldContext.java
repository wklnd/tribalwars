package se.oscarwiklund.twlan2.backend.web;

// The world the current request plays in, set from the X-World-Id header by WebConfig's interceptor.
public final class WorldContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private WorldContext() {}

    public static Long get() { return CURRENT.get(); }
    public static void set(Long worldId) { CURRENT.set(worldId); }
    public static void clear() { CURRENT.remove(); }
}
