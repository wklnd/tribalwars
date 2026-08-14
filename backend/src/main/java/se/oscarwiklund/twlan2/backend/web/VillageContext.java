package se.oscarwiklund.twlan2.backend.web;

// Which of the player's villages the current request acts on (X-Village-Id header; empty = the first one).
public final class VillageContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private VillageContext() {}

    public static Long get() { return CURRENT.get(); }
    public static void set(Long id) { CURRENT.set(id); }
    public static void clear() { CURRENT.remove(); }
}
