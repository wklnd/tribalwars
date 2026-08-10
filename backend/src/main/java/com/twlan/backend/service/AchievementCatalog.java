package com.twlan.backend.service;

import java.util.ArrayList;
import java.util.List;

// Every achievement of Tribal Wars (https://support.innogames.com/kb/TribalWars/en_DK/5345), grouped like the
// knowledge-base page. Each has up to four levels; thresholds[0] is the first level's target, the description
// shows the target of the level being worked on in place of {n}.
//
// Only achievements with a metric can be earned in this game — the others need features TWLAN2 does not have
// (conquering, tribes, premium, quests, events, ...) and are listed as unavailable.
public final class AchievementCatalog {

    public record Def(String key, String category, String group, String name, String description, String icon,
                      long[] thresholds, String metric, boolean lowerIsBetter) {
        public boolean tracked() { return metric != null; }
        public int maxLevel() { return thresholds.length; }
    }

    public static final String DAILY = "Daily";
    public static final String COMBAT = "Combat";
    public static final String SOCIAL = "Social";
    public static final String GROWTH = "Growth";
    public static final String OTHER = "Other";

    private static final List<Def> ALL = new ArrayList<>();

    private static void add(String category, String group, String key, String name, String description, String icon, String metric, boolean lowerIsBetter, long... thresholds) {
        ALL.add(new Def(key, category, group, name, description, icon, thresholds, metric, lowerIsBetter));
    }

    private static void a(String category, String key, String name, String description, String icon, String metric, long... thresholds) {
        add(category, null, key, name, description, icon, metric, false, thresholds);
    }

    private static void other(String group, String key, String name, String description, String icon, long... thresholds) {
        add(OTHER, group, key, name, description, icon, null, false, thresholds);
    }

    static {
        // ---- daily ------------------------------------------------------------------------------------------
        a(DAILY, "attacker_of_day", "Attacker of the day", "Defeat the most units in this world as the attacker", "award10", "won_attacker_day", 1);
        a(DAILY, "defender_of_day", "Defender of the day", "Defeat the most units in this world as the defender", "award11", null, 1);
        a(DAILY, "looter_of_day", "Looter of the day", "Plunder the most resources in this world", "farmer_of_the_day", "won_looter_day", 1);
        a(DAILY, "plunderer_of_day", "Plunderer of the day", "Plunder the most villages in this world", "award12", "won_plunderer_day", 1);
        a(DAILY, "great_power_of_day", "Great power of the day", "Conquer the most villages in this world", "award9", "won_conquer_day", 1);
        a(DAILY, "supporter_of_day", "Supporter of the day", "Defeat the most units in this world as a supporter", "award13", null, 1);
        a(DAILY, "gatherer_of_day", "Gatherer of the day", "Scavenge the most resources in this world", "farmer_of_the_day", null, 1);

        // ---- combat -----------------------------------------------------------------------------------------
        a(COMBAT, "unlucky_fellow", "Unlucky fellow", "Fail to conquer a village due to the loyalty only being reduced to +1", "award15", "unlucky", 1);
        a(COMBAT, "lucky_fellow", "Lucky fellow", "Conquer a village by getting the loyalty down to exactly 0", "award14", "lucky", 1);
        a(COMBAT, "victim", "Victim", "Be conquered within one week of your beginner protection expiring", "award19", null, 1);
        a(COMBAT, "self_conquest", "Self-conquest", "Conquer yourself, because that's the only way to show them who's boss", "award18", null, 1);
        a(COMBAT, "warlord", "The Warlord", "Attack {n} different players", "warmonger", "targets", 10, 25, 100, 250);
        a(COMBAT, "demolisher", "Demolisher", "Destroy {n} building levels using catapults", "demolisher", null, 25, 250, 2500, 10000);
        a(COMBAT, "death_of_a_hero", "Death of a hero", "Lose {n} of your units while supporting other villages", "award6", null, 1000, 7500, 20000, 100000);
        a(COMBAT, "plunderer", "Plunderer", "Plunder other villages {n} times", "award4", "plunders", 10, 100, 1000, 10000);
        a(COMBAT, "nobles_faith", "Nobles Faith", "Defeat {n} nobleman", "nobles_faith", "nobles_killed", 1, 25, 100, 500);
        a(COMBAT, "noble_claims", "Successful noble claims", "Noble {n} claimed villages", "award5", null, 5, 25, 50, 100);
        a(COMBAT, "robber", "Robber", "Loot {n} resources", "award2", "loot", 500, 10000, 1000000, 100000000);
        a(COMBAT, "scout_hunter", "Scout Hunter", "Fend off {n} scout attacks", "scout_hunter", null, 25, 50, 250, 500);
        a(COMBAT, "reliable_commander", "Reliable Commander", "Support another player in {n} battles", "reliable_commander", null, 50, 100, 500, 3000);
        a(COMBAT, "conquest", "Conquest", "Conquer {n} villages", "award3", "conquests", 5, 50, 500, 1000);
        a(COMBAT, "master_of_the_battlefield", "Master of the Battlefield", "Completely destroy {n} hostile armies", "master_of_the_battlefield", "armies", 25, 250, 1000, 2500);
        a(COMBAT, "wallbreaker", "Wallbreaker", "Destroy {n} Wall levels using your rams", "wallbreaker", null, 25, 250, 2500, 10000);
        a(COMBAT, "stronghold_crusher", "Stronghold crusher", "Reduce the level of a Stronghold {n} times", "award20", null, 10, 40, 70, 100);
        a(COMBAT, "leader", "Leader", "Defeat a total of {n} enemy units", "award16", "kills", 10000, 100000, 1000000, 20000000);
        a(COMBAT, "self_attack", "Self-attack", "Attack yourself and lose more than {n} units in one battle", "award17", null, 10, 100, 1000, 10000);

        // ---- social -----------------------------------------------------------------------------------------
        a(SOCIAL, "educated", "Educated", "Graduate from an apprenticeship", "dummy", null, 1);
        a(SOCIAL, "philanthropist", "Philanthropist", "Gift a Premium subscription to {n} player", "dummy", null, 1, 5, 15, 30);
        a(SOCIAL, "successful_recruitment", "Successful recruitment", "Invite {n} friend", "dummy", null, 1, 5, 10, 25);
        a(SOCIAL, "mentor", "The mentor", "As a mentor, graduate {n} apprentice", "dummy", null, 1, 3, 5, 8);
        a(SOCIAL, "brothers_in_arms", "Brothers in Arms", "Be a member of the same tribe for {n} consecutive days", "brothers_in_arms", null, 30, 60, 180, 360);
        a(SOCIAL, "beloved_friend", "Beloved Friend", "Make a total of {n} friendships", "beloved_friends", null, 5, 15, 50, 100);

        // ---- growth -----------------------------------------------------------------------------------------
        a(GROWTH, "wealth_in_gold", "Wealth comes in gold", "Mint {n} gold coins", "wealth_comes_in_gold", null, 50, 500, 5000, 50000);
        a(GROWTH, "market_guru", "Market Guru", "Trade using your market {n} times", "market_guru", null, 10, 100, 500, 1000);
        a(GROWTH, "master_of_quests", "Master of Quests", "Complete {n} quests", "dummy", null, 40);
        a(GROWTH, "out_of_time", "Out of time", "Use the instant complete option {n} times", "award7", "instant", 15, 100, 2000, 8000);
        a(GROWTH, "architect", "Architect", "Build a total of {n} building levels", "award13", "levels", 10, 150, 5000, 100000);
        a(GROWTH, "recruitment_drive", "Recruitment Drive", "Recruit a total of {n} units", "recruit", "recruited", 20, 5000, 50000, 1000000);
        a(GROWTH, "librarian", "Librarian", "Discover {n} unique Skill Books", "dummy", null, 3, 6, 9, 12);
        a(GROWTH, "gatherer", "Gatherer", "Scavenge {n} resources", "award2", null, 1000, 10000, 10000000, 100000000);
        a(GROWTH, "score_champion", "Score champion", "Climb the rankings tables and reach {n} points", "award1", "points", 100, 5000, 100000, 10000000);
        add(GROWTH, null, "top_scorer", "Top scorer", "Make it into the top {n} in the world", "award8", "rank_world", true, 1000, 100, 20, 1);
        add(GROWTH, null, "continent_scorer", "Continent scorer", "Make it into the top {n} of a continent", "award7", "rank_continent", true, 100, 30, 5, 1);
        a(GROWTH, "band_of_brothers", "Band of Brothers", "Have {n} paladins", "dummy", null, 2, 4, 8, 10);
        a(GROWTH, "paladins_level", "Paladin's level", "Level up your paladin to level {n}", "dummy", null, 5, 10, 20, 30);
        a(GROWTH, "accomplished_student", "Accomplished student", "Improve your paladins' skills to a combined total of {n} skill levels", "dummy", null, 15, 50, 100, 300);
        a(GROWTH, "treasury", "It Belongs in a Treasury", "Acquire {n} relics", "royalty_tinkerer", null, 10, 40, 70, 130);
        a(GROWTH, "archeologist", "The Archeologist", "Discover {n} unique relics", "royalty_monster_hunter", null, 2, 6, 10, 15);
        a(GROWTH, "fortune_and_glory", "Fortune and Glory", "Have {n} relics attached", "royalty_rescue_royalty", null, 2, 4, 8, 10);
        a(GROWTH, "merger", "You're a Merger?", "Upgrade {n} relics", "royalty_tinkerer", null, 5, 10, 20, 40);

        // ---- other ------------------------------------------------------------------------------------------
        other("General", "resurrection", "Resurrection", "Start over {n} times in this world", "award9", 5);
        add(OTHER, "General", "years_of_service", "Years of Service", "Play Tribal Wars for {n} year(s)", "years_of_service_1", "years", false,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        other("General", "bigger_and_better", "Bigger and better", "Upgrade your tribe's stronghold to level {n}", "award20", 1, 4, 7, 10);
        other("General", "hat_experimenter", "Tribal Wars Wizardry: Hat experimenter", "Acquire one of the mysterious hats from the Wizard or other players", "wizard1", 1);
        other("General", "hat_connoisseur", "Tribal Wars Wizardry: Hat connoisseur", "Acquire ten of the mysterious hats from the Wizard or other players", "wizard2", 1);
        other("General", "repeat_customer", "Tribal Wars Wizardry: Repeat customer", "Visit the Wizard Emporium 5 times", "wizard1", 1);

        other("Castle Assault Event", "castle_siegelord", "Castle Assault: Siegelord", "Participate in the destruction of {n} castles", "fall2014_aggressor", 2, 4, 6, 8);
        other("Castle Assault Event", "castle_front_line", "Castle Assault: On the Front Line!", "Lead the Assault action gauge when it triggers", "fall2014_aggressor", 1);
        other("Castle Assault Event", "castle_boom", "Castle Assault: It Goes Boom!", "Lead the Bombardment action gauge when it triggers", "fall2014_aggressor", 1);
        other("Castle Assault Event", "castle_row", "Castle Assault: Row Faster!", "Lead the Naval Supply action gauge when it triggers", "fall2014_aggressor", 1);
        other("Castle Assault Event", "castle_join", "Castle Assault: Join the fun!", "Contribute {n} medals to help the tribe", "fall2014_aggressor", 3000, 5000, 8000, 15000);

        other("Noble's Fair Event", "fair_wheel", "Noble's Fair: The wheel keeps spinning", "Spin the wheel {n} times", "fall2014_spinner", 5, 10, 25, 50);
        other("Noble's Fair Event", "fair_enjoying", "Noble's Fair: Enjoying the Fair", "Finish {n} activity mini-games in the Noble's fair", "fall2014_spinner", 2, 5, 10, 13);
        other("Noble's Fair Event", "fair_master", "Noble's Fair: Master of the Fair", "Finish all the different activity mini-games of the Noble's Fair", "fall2014_spinner", 1);

        other("Battle of the Tower Event", "tower_conqueror", "Battle of the Tower: New World Conqueror", "Help your group to annex {n} sectors in the Battle of the Tower", "bday1", 3, 8, 15, 25);
        other("Battle of the Tower Event", "tower_general", "Battle of the Tower: Superior General!", "Lead the attack ranking at the end of a battle", "bday1", 1);
        other("Battle of the Tower Event", "tower_ice_wall", "Battle of the Tower: Ice Wall!", "Lead the defense ranking at the end of a battle", "bday1", 1);

        other("Beast Mountain Event", "beast_hunter", "Beast Mountain: Hunter", "Defeat {n} beast in the Beast of the Black Mountain event", "royalty_monster_hunter", 1, 2, 3, 5);
        other("Beast Mountain Event", "beast_equip", "Beast Mountain: Equip your hero", "Equip your hero with all pieces of equipment", "royalty_monster_hunter", 1);
        other("Beast Mountain Event", "beast_legendary", "Beast Mountain: Legendary equipment", "Equip your hero with at least one legendary equipment", "royalty_monster_hunter", 1);

        other("Barricade Battle Event", "barricade_detective", "Barricade Battle: Detective", "Clear {n}% of the stages", "bday2", 17, 25, 33);
        other("Barricade Battle Event", "barricade_predator", "Barricade Battle: Predator", "Defeat {n} enemy units in the Barricade Battle", "bday2", 10000, 50000, 100000, 180000);
        other("Barricade Battle Event", "barricade_find", "Barricade Battle: Did you find him yet?", "Lead the daily ranking at the end of the event day", "bday2", 1);

        other("Ancient Forge Event", "forge_recipes", "Ancient Forge: Collector of Recipes", "Find {n} unique recipes", "royalty_tinkerer", 10, 20, 30, 40);
        other("Ancient Forge Event", "forge_smith", "Ancient Forge: Master Smith", "Craft {n} items", "royalty_tinkerer", 10, 20, 30, 40);

        other("Seas of Fortune Event", "seas_explorer", "Seas of Fortune: Explorer", "Explore {n} regions", "bday3", 10, 20, 30, 40);
        other("Seas of Fortune Event", "seas_discovery", "Seas of Fortune: Great Discovery", "Explore {n} island", "bday3", 1, 2, 3, 4);

        other("Calendar & Card Game Events", "calendar_door", "20 days and 20 years", "Open {n} door in the Gift Calendar", "beloved_friends", 1, 5, 10, 20);
        other("Calendar & Card Game Events", "card_master", "Card Master", "Win {n} rounds", "beloved_friends", 20, 40, 60, 80);
        other("Calendar & Card Game Events", "strategist", "The Strategist", "Perform {n} double, triple or quadruple changes", "beloved_friends", 5, 10, 20, 30);

        add(OTHER, "Daily Achievement Repeats", "vanquisher", "The Vanquisher", "Be the first who defeats the most units in this world as the attacker on 2 different days", "award10", "won_attacker_day", false, 1);
        other("Daily Achievement Repeats", "protector", "The Protector", "Be the first who defeats the most units in this world as the defender on 2 different days", "award11", 1);
        other("Daily Achievement Repeats", "stalwart", "The Stalwart", "Be the first who defeats the most units in this world as the supporter on 2 different days", "award13", 1);
        add(OTHER, "Daily Achievement Repeats", "affluent", "The Affluent", "Be the first who loots the most resources in this world on 2 different days", "farmer_of_the_day", "won_looter_day", false, 1);
    }

    public static List<Def> all() { return ALL; }

    private AchievementCatalog() {}
}
