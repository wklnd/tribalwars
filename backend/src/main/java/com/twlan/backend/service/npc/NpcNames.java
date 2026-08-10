package com.twlan.backend.service.npc;

import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

// Player names for NPCs that look like the ones on a real world: first names, first name + surname, nicknames such as
// "SilentRaven" or "iron_wolf", a title or an epithet, sometimes a number, in mixed capitalisation. Letters, digits and
// "_ . -" only (what an account name may contain), 4 to 24 characters.
public final class NpcNames {

    private NpcNames() {}

    static final String[] GIVEN = {
            "Adrian", "Agnes", "Alaric", "Alfred", "Alma", "Anders", "Anneli", "Arthur", "Astrid", "Axel", "Beatrix", "Bengt", "Birgit",
            "Bjorn", "Bodil", "Boris", "Brenna", "Caspar", "Cecilia", "Clara", "Conrad", "Dagmar", "Darius", "Declan", "Dorothea",
            "Edmund", "Eero", "Elias", "Elin", "Elsa", "Emeric", "Erik", "Esben", "Eva", "Felix", "Fergus", "Finn", "Freya", "Gideon",
            "Godwin", "Greta", "Gunnar", "Gustav", "Gwen", "Hana", "Hedda", "Henrik", "Hilda", "Hugo", "Ida", "Ilse", "Ingrid", "Isak",
            "Isolde", "Jasper", "Jonas", "Josef", "Julius", "Kaspar", "Katja", "Klara", "Knut", "Lars", "Leif", "Lena", "Leonard",
            "Linnea", "Lothar", "Lukas", "Maeve", "Magnus", "Malin", "Marek", "Marta", "Mathias", "Maud", "Mikael", "Mira", "Niamh",
            "Nils", "Nora", "Olav", "Olga", "Oskar", "Otto", "Pavel", "Percy", "Petra", "Rasmus", "Rikard", "Rolf", "Ronan", "Rowena",
            "Rurik", "Sanna", "Selma", "Sigrid", "Sten", "Stella", "Svea", "Tage", "Tilda", "Tobias", "Torsten", "Tristan", "Ulf",
            "Ulrika", "Vera", "Viggo", "Viktor", "Wendell", "Wilhelm", "Winifred", "Yrsa", "Zofia"};

    static final String[] SURNAME = {
            "Andersson", "Ashdown", "Baker", "Bergman", "Blackwood", "Carlsen", "Carver", "Cooper", "Dahl", "Draper", "Ekholm", "Falk",
            "Fisher", "Fletcher", "Granger", "Grahn", "Hale", "Holm", "Hunter", "Isaksen", "Jansen", "Kruger", "Lindqvist", "Marsh",
            "Moller", "Nyberg", "Olsen", "Petrov", "Quist", "Roth", "Smith", "Sundberg", "Thatcher", "Thorsen", "Ulrich", "Vogel",
            "Wainwright", "Weiss", "Yates", "Zeller"};

    static final String[] ADJECTIVE = {
            "Ashen", "Black", "Blue", "Bold", "Brave", "Crimson", "Cunning", "Dark", "Ember", "Frost", "Golden", "Green", "Grim",
            "Hollow", "Iron", "Lone", "Mad", "Merry", "Northern", "Old", "Red", "Rusty", "Silent", "Silver", "Sly", "Stone", "Storm",
            "Swift", "White", "Wild"};

    static final String[] NOUN = {
            "Anvil", "Arrow", "Badger", "Banner", "Boar", "Bridge", "Brook", "Crown", "Falcon", "Fjord", "Forge", "Fox", "Harbor",
            "Hammer", "Hawk", "Lance", "Lynx", "Oak", "Otter", "Owl", "Ranger", "Raven", "Reeve", "Ridge", "Shield", "Stag", "Thorn",
            "Tower", "Viper", "Warden", "Wolf"};

    static final String[] TITLE = {"Captain", "Old", "Master", "Jarl", "Doc"}; // (neutral ones: they go with any first name)

    static final String[] EPITHET = {"Bold", "Red", "Wise", "Fair", "Grey", "Quiet", "Lucky", "Lame", "Young", "Boar"};

    // A name that is not `taken` (compared in lower case by the caller's predicate). After a few unlucky draws it
    // tacks digits on, so it always ends.
    public static String generate(Random rnd, Predicate<String> taken) {
        for (int attempt = 0; attempt < 40; attempt++) {
            String name = draw(rnd, attempt >= 20);
            if (!taken.test(name.toLowerCase(Locale.ROOT))) return name;
        }
        while (true) {
            String name = draw(rnd, true) + (10 + rnd.nextInt(990));
            if (name.length() <= 24 && !taken.test(name.toLowerCase(Locale.ROOT))) return name;
        }
    }

    private static String pick(Random rnd, String[] from) { return from[rnd.nextInt(from.length)]; }

    private static String number(Random rnd) {
        return switch (rnd.nextInt(4)) {
            case 0 -> String.valueOf(rnd.nextInt(90) + 1);           // 1..90
            case 1 -> String.valueOf(70 + rnd.nextInt(35) % 100);    // a birth year: 70..99, 00..04
            case 2 -> String.valueOf(rnd.nextInt(900) + 100);
            default -> String.valueOf(rnd.nextInt(10));
        };
    }

    private static String draw(Random rnd, boolean withNumber) {
        String name = switch (weighted(rnd)) {
            case 0 -> pick(rnd, GIVEN);
            case 1 -> pick(rnd, GIVEN) + sep(rnd) + number(rnd);
            case 2 -> pick(rnd, ADJECTIVE) + pick(rnd, NOUN);
            case 3 -> pick(rnd, ADJECTIVE) + pick(rnd, NOUN) + number(rnd);
            case 4 -> pick(rnd, TITLE) + sep(rnd) + pick(rnd, GIVEN);
            case 5 -> pick(rnd, GIVEN) + (rnd.nextBoolean() ? "_" : ".") + pick(rnd, SURNAME);
            case 6 -> pick(rnd, GIVEN) + "_the_" + pick(rnd, EPITHET);
            case 7 -> (pick(rnd, ADJECTIVE) + "_" + pick(rnd, NOUN)).toLowerCase(Locale.ROOT);
            default -> pick(rnd, GIVEN).toLowerCase(Locale.ROOT) + (rnd.nextBoolean() ? "" : "_") + number(rnd);
        };
        if (withNumber && !Character.isDigit(name.charAt(name.length() - 1))) name += number(rnd);
        if (name.length() > 24) name = name.substring(0, 24);
        return name.length() < 4 ? name + number(rnd) : name;
    }

    private static String sep(Random rnd) { return rnd.nextBoolean() ? "" : "_"; }

    // Which style: plain first names and "First_Surname" are the most common, like on a real world.
    private static int weighted(Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 12) return 0;
        if (r < 24) return 1;
        if (r < 46) return 2;
        if (r < 52) return 3;
        if (r < 60) return 4;
        if (r < 80) return 5;
        if (r < 87) return 6;
        if (r < 94) return 7;
        return 8;
    }
}
