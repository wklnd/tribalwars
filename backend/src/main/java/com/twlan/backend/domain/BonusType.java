package com.twlan.backend.domain;

import java.util.Map;
import java.util.Random;

// code is the original's id: the browser draws the icon with CSS class bonus_icon_<code> (game.css,
// graphic/overview/bonus_icons.png) and picture graphic/bonus/<image>.png ("stone" is what the original calls clay).
// The original computes the effects in compiled code, so these are the classic Tribal Wars values: +100% production
// of one resource, +10% of all three, +10% farm population, +50% warehouse capacity, a third less recruiting time
// in the barracks/stable, half in the workshop. A village keeps its bonus when it changes hands; world setting
// bonusVillages = off switches every bonus off.
public enum BonusType {
    WOOD(1, "wood", "+100% wood production"),
    CLAY(2, "stone", "+100% clay production"),
    IRON(3, "iron", "+100% iron production"),
    FARM(4, "farm", "+10% population"),
    BARRACKS(5, "barracks", "Recruitment in the barracks is 33% faster"),
    STABLE(6, "stable", "Recruitment in the stable is 33% faster"),
    GARAGE(7, "garage", "Recruitment in the workshop is 50% faster"),
    ALL(8, "all", "+10% production of all resources"),
    STORAGE(9, "storage", "+50% storage capacity");

    public final int code;
    public final String image;
    public final String text;

    BonusType(int code, String image, String text) {
        this.code = code;
        this.image = image;
        this.text = text;
    }

    public static BonusType byCode(Integer code) {
        if (code == null) return null;
        for (BonusType b : values()) if (b.code == code) return b;
        return null;
    }

    // World setting bonusVillages defaults to "better".
    public static boolean enabled(World world) {
        return world == null || !"off".equals(world.getSettings().get("bonusVillages"));
    }

    public static BonusType of(Village village) {
        if (village == null || village.getBonusCode() == null || !enabled(village.getWorld())) return null;
        return byCode(village.getBonusCode());
    }

    // Fraction of barbarian villages that carry a bonus: "normal" 8%, "better" (the default) 15%, "off" none.
    public static double share(World world) {
        String mode = world == null ? "better" : world.getSettings().getOrDefault("bonusVillages", "better");
        return switch (mode) {
            case "off" -> 0;
            case "normal" -> 0.08;
            default -> 0.15;
        };
    }

    // The three single-resource bonuses are the common kind.
    private static final Map<BonusType, Integer> WEIGHT = Map.of(WOOD, 3, CLAY, 3, IRON, 3, ALL, 1, FARM, 1, STORAGE, 1, BARRACKS, 1, STABLE, 1, GARAGE, 1);

    public static BonusType draw(Random rnd) {
        int total = WEIGHT.values().stream().mapToInt(Integer::intValue).sum();
        int r = rnd.nextInt(total);
        for (BonusType b : values()) {
            r -= WEIGHT.get(b);
            if (r < 0) return b;
        }
        return ALL;
    }

    public static Integer roll(Random rnd, World world) {
        return rnd.nextDouble() < share(world) ? draw(rnd).code : null;
    }

    // ---- effects ---------------------------------------------------------------------------------------------------------

    public double production(BuildingType producer) {
        if (this == ALL) return 1.1;
        boolean mine = (this == WOOD && producer == BuildingType.TIMBER_CAMP) || (this == CLAY && producer == BuildingType.CLAY_PIT)
                || (this == IRON && producer == BuildingType.IRON_MINE);
        return mine ? 2.0 : 1.0;
    }

    public double capacity() { return this == STORAGE ? 1.5 : 1.0; }

    public double population() { return this == FARM ? 1.1 : 1.0; }

    public double recruitTime(BuildingType recruitBuilding) {
        if (this == BARRACKS && recruitBuilding == BuildingType.BARRACKS) return 1 / 1.5;
        if (this == STABLE && recruitBuilding == BuildingType.STABLE) return 1 / 1.5;
        if (this == GARAGE && recruitBuilding == BuildingType.WORKSHOP) return 0.5;
        return 1.0;
    }

    // ---- the same for a village (1 without a bonus) ----------------------------------------------------------------------------

    public static double productionFactor(Village v, BuildingType producer) {
        BonusType b = of(v);
        return b == null ? 1.0 : b.production(producer);
    }

    public static double capacityFactor(Village v) {
        BonusType b = of(v);
        return b == null ? 1.0 : b.capacity();
    }

    public static double populationFactor(Village v) {
        BonusType b = of(v);
        return b == null ? 1.0 : b.population();
    }

    public static double recruitTimeFactor(Village v, BuildingType recruitBuilding) {
        BonusType b = of(v);
        return b == null ? 1.0 : b.recruitTime(recruitBuilding);
    }
}
