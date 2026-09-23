package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Catalog of per-world settings (the original world.json options plus a few of our own). Values are
// stored per world as strings; missing keys fall back to the default here. `implemented` = the game
// actually uses the value today; the rest are stored so worlds can already be configured like the original's.
public final class WorldSettings {

    public record Setting(String key, String label, String group, String type, String defaultValue,
                          List<String> options, Double min, Double max, boolean implemented, String help) {}

    private static final List<Setting> CATALOG = new ArrayList<>();
    private static final Map<String, Setting> BY_KEY = new LinkedHashMap<>();

    private static void add(String key, String label, String group, String type, String def, boolean implemented, String help) {
        add(key, label, group, type, def, null, null, null, implemented, help);
    }

    private static void add(String key, String label, String group, String type, String def, List<String> options,
                            Double min, Double max, boolean implemented, String help) {
        Setting s = new Setting(key, label, group, type, def, options, min, max, implemented, help);
        CATALOG.add(s);
        BY_KEY.put(key, s);
    }

    static {
        add("description", "Description", "General", "text", "", true, "Shown on the world's join page.");
        add("register", "Registration open", "General", "boolean", "true", true, "When off, nobody can join this world any more.");
        add("startWood", "Start wood", "General", "number", "1000", null, 0.0, 1_000_000.0, true, "Wood of a new village.");
        add("startClay", "Start clay", "General", "number", "1000", null, 0.0, 1_000_000.0, true, "Clay of a new village.");
        add("startIron", "Start iron", "General", "number", "1000", null, 0.0, 1_000_000.0, true, "Iron of a new village.");
        add("unitsSpeed", "Unit speed", "Speed", "number", "1", null, 0.01, 100_000.0, true, "Multiplies how fast troops travel (on top of the world speed).");
        add("buildqueueStart", "Build queue free slots", "Buildings", "number", "5", null, 1.0, 100.0, true, "Orders that fit in the construction queue before the resource-cost surcharge below kicks in.");
        add("buildqueueMultiply", "Build queue cost factor", "Buildings", "number", "1.25", null, 1.0, 10.0, true, "Resource-cost multiplier for each order past the free slots (confirmed from the original's own language file: a resource surcharge, not a time factor as the label used to say).");
        add("buildMainFactor", "Headquarters build factor", "Buildings", "number", "1.05", null, 1.0, 2.0, true, "Each headquarters level divides construction time by this.");
        add("morale", "Morale", "Combat", "select", "points", List.of("off", "points", "time"), null, null, true, "How morale weakens attacks on much smaller players (\"points\"; \"time\" acts the same here).");
        add("basicDefense", "Basic defense", "Combat", "number", "20", null, 0.0, 1000.0, true, "Free defense every village has, on top of its troops.");
        add("nightBonus", "Night bonus", "Combat", "number", "2", null, 1.0, 10.0, true, "Defense factor during the night hours.");
        add("nightStart", "Night starts", "Combat", "text", "22:00", true, "hh:mm (server time)");
        add("nightEnd", "Night ends", "Combat", "text", "08:00", true, "hh:mm (server time)");
        add("beginnerProtection", "Beginner protection (min)", "Combat", "number", "2880", null, 0.0, 100_000.0, true, "Real players cannot be attacked until their account is this old.");
        add("npcDifficulty", "NPC difficulty", "NPC", "select", "normal", List.of("off", "passive", "normal", "hard", "brutal"), null, null, true, "How well NPC players play. off: they build but never attack. passive: a lively backdrop, raids on barbarians, rarely you. normal: scouts before attacking, notices and dodges attacks, conquers barbarian villages. hard: farms barbarians, retaliates, asks tribe-mates for help, conquers NPC and player villages (not your last). brutal: fast and precise, always notices, dodges and gets help in time, can take your last village.");
        add("npcConquest", "NPC conquest of players", "NPC", "select", "auto", List.of("auto", "never", "not-last", "anything"), null, null, true, "Whether NPC noblemen may take real players' villages. auto: by difficulty. never: only barbarian and NPC villages. not-last: any village except a player's last one. anything: also a player's last village (the account then has to join the world again).");
        add("npcRhythm", "NPC daily rhythm", "NPC", "boolean", "true", true, "NPCs keep a real player's hours: most sleep around the night, a few top players do not, and awake they log in for short sessions (when something they queued is done, or now and then) instead of acting every few seconds. Off: always on.");
        add("npcAchievements", "NPC achievements", "NPC", "select", "off", List.of("off", "levels", "full"), null, null, true, "Whether NPCs earn achievements too (from the same events real players do). off: NPCs never earn any (default). levels: NPCs unlock achievement levels and show up on the achievement ranking/profile pages, but never win a daily \"of the day\" award - those stay yours to win. full: NPCs also compete for the daily awards.");
        add("fakeLimit", "Fake attack limit", "Combat", "boolean", "true", true, "Caps how many small attacks (under 10 total population) may be outstanding against the same village within 24h. Exact rule not discoverable from the original (compiled game) - a classic-Tribal-Wars-style approximation.");
        add("attackSelf", "Attack own villages", "Combat", "boolean", "true", true, "Whether you may attack your own villages (a different village of the same account).");
        add("destroyBuildings", "Destroy buildings", "Combat", "boolean", "true", true, "Rams and catapults can destroy the wall and other buildings.");
        add("researchSystem", "Research system", "Units", "select", "simple", List.of("off", "simple", "advanced"), null, null, true, "off: every unit trainable without research. simple: a unit is either researched or not (implemented). advanced: behaves the same as simple here - no distinct mechanic is discoverable from the original (compiled game).");
        add("farmRule", "Farm rule", "Units", "boolean", "false", true, "Refuses an attack on a real player whose village has fewer than 1/5 of the attacker's own village points. Exact ratio not discoverable from the original (compiled game) - a classic-Tribal-Wars-style approximation; never applies to barbarian villages.");
        add("knightActive", "Paladin", "Units", "boolean", "true", true, "Whether the Paladin can be trained on this world.");
        add("noblemanSystem", "Noblemen system", "Nobles", "select", "coins", List.of("off", "coins", "packages"), null, null, true, "off disables noblemen entirely. Only the coin system exists here - packages behaves the same as coins (no evidence a distinct mechanic exists in this build).");
        add("noblemanRange", "Noblemen range", "Nobles", "number", "1000", null, 1.0, 10_000.0, true, "Maximum distance a nobleman can travel to conquer.");
        add("noblemanMinDecrease", "Loyalty lost per nobleman (min)", "Nobles", "number", "20", null, 0.0, 100.0, true, "A won attack with noblemen lowers the loyalty once by a random amount between min and max, however many noblemen were sent (they count as one).");
        add("noblemanMaxDecrease", "Loyalty lost per nobleman (max)", "Nobles", "number", "35", null, 0.0, 100.0, true, "");
        add("coinWood", "Gold coin: wood", "Nobles", "number", "28000", null, 0.0, 100_000_000.0, true, "Wood needed to mint one gold coin.");
        add("coinClay", "Gold coin: clay", "Nobles", "number", "30000", null, 0.0, 100_000_000.0, true, "Clay needed to mint one gold coin.");
        add("coinIron", "Gold coin: iron", "Nobles", "number", "25000", null, 0.0, 100_000_000.0, true, "Iron needed to mint one gold coin.");
        add("loyaltyIncrease", "Loyalty increase per hour", "Nobles", "number", "1", null, 0.0, 100.0, true, "Loyalty regrown per hour (multiplied by the world speed).");
        add("npcTribes", "NPC tribes", "Tribes", "boolean", "true", true, "NPC players found tribes, recruit each other, set relations to other tribes and now and then invite you.");
        add("npcMarket", "NPC market", "Market", "boolean", "true", true, "NPC villages with a market post trade offers you can accept, and now and then fill fair offers you made.");
        add("tribeMemberLimit", "Tribe member limit", "Tribes", "number", "15", null, 1.0, 1000.0, true, "How many players a tribe may have.");
        add("mapSize", "Map size", "Map", "number", "1000", null, 100.0, 5000.0, true, "Bounds village coordinates to 1..size-2 around the centre (500|500).");
        add("mapDensity", "Map density", "Map", "number", "2.5", null, 0.1, 100.0, true, "How tightly villages are packed around the centre, relative to the default (2.5 reproduces the original density; higher packs tighter). Exact original formula not discoverable (compiled game).");
        add("bonusVillages", "Bonus villages", "Map", "select", "better", List.of("off", "normal", "better"), null, null, true, "Some barbarian villages carry a bonus that stays with the village when it is conquered: +100% of one resource, +10% of all, +10% farm population, +50% warehouse capacity, faster recruiting in the barracks, stable or workshop. normal: about 1 in 12 new barbarian villages has one, better: about 1 in 7. off: no bonuses at all (also hides the existing ones). Only villages created afterwards are drawn; existing ones keep theirs.");
        add("leftVillagesGrow", "Abandoned villages turn barbarian (min)", "Map", "number", "5000", null, 0.0, 1_000_000.0, true, "A real account's villages turn barbarian this many real-world minutes after its last login. 0 disables the sweep. Not a passive NPC-style growth loop (the original's exact behaviour is compiled/not discoverable) - by design choice here, ownership just changes.");
        add("prodToSeconds", "Production per second from", "Display", "number", "600000", null, 1.0, 100_000_000.0, true, "Show production per second at or above this rate per hour.");
        add("prodToMinutes", "Production per minute from", "Display", "number", "10000", null, 1.0, 100_000_000.0, true, "Show production per minute at or above this rate per hour (below prodToSeconds).");
    }

    private WorldSettings() {}

    public static List<Setting> catalog() { return CATALOG; }

    public static Map<String, String> effective(World world) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Setting s : CATALOG) out.put(s.key(), s.defaultValue());
        if (world != null) world.getSettings().forEach((k, v) -> { if (BY_KEY.containsKey(k)) out.put(k, v); });
        return out;
    }

    public static String get(World world, String key) {
        Setting s = BY_KEY.get(key);
        String own = world == null ? null : world.getSettings().get(key);
        return own != null ? own : (s == null ? null : s.defaultValue());
    }

    public static double number(World world, String key) {
        try {
            return Double.parseDouble(get(world, key));
        } catch (RuntimeException e) {
            return Double.parseDouble(BY_KEY.get(key).defaultValue());
        }
    }

    // Wraps past midnight.
    public static boolean isNight(World world, java.time.LocalTime now) {
        try {
            java.time.LocalTime start = java.time.LocalTime.parse(get(world, "nightStart"));
            java.time.LocalTime end = java.time.LocalTime.parse(get(world, "nightEnd"));
            if (start.equals(end)) return false;
            return start.isBefore(end) ? !now.isBefore(start) && now.isBefore(end) : !now.isBefore(start) || now.isBefore(end);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean bool(World world, String key) {
        return "true".equalsIgnoreCase(get(world, key));
    }

    public static void apply(World world, Map<String, String> values) {
        if (values == null) return;
        for (var e : values.entrySet()) {
            Setting s = BY_KEY.get(e.getKey());
            if (s == null) throw new IllegalArgumentException("Unknown setting: " + e.getKey());
            String v = e.getValue() == null ? "" : e.getValue().trim();
            switch (s.type()) {
                case "number" -> {
                    double d;
                    try { d = Double.parseDouble(v); } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(s.label() + " must be a number");
                    }
                    if (s.min() != null && d < s.min() || s.max() != null && d > s.max()) {
                        throw new IllegalArgumentException(s.label() + " must be between " + s.min() + " and " + s.max());
                    }
                }
                case "boolean" -> {
                    if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException(s.label() + " must be true or false");
                }
                case "select" -> {
                    if (!s.options().contains(v)) throw new IllegalArgumentException(s.label() + " must be one of " + s.options());
                }
                default -> { }
            }
            world.getSettings().put(s.key(), v);
        }
    }
}
