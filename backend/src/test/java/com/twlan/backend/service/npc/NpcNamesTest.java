package com.twlan.backend.service.npc;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NpcNamesTest {

    @Test
    void namesAreUniqueAndAllowedForAnAccount() {
        Random rnd = new Random(3);
        Set<String> taken = new HashSet<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            String n = NpcNames.generate(rnd, taken::contains);
            assertTrue(taken.add(n.toLowerCase()), "duplicate " + n);
            assertTrue(n.matches("[A-Za-z0-9_.-]{4,24}"), "not a valid account name: " + n);
            names.add(n);
        }
        // the old scheme was "Name_<number>" for everybody
        long numbered = names.stream().filter(n -> n.matches(".*_\\d+$")).count();
        assertTrue(numbered < names.size() * 0.15, "too many Name_123 style names: " + numbered);
        long endWithDigit = names.stream().filter(n -> Character.isDigit(n.charAt(n.length() - 1))).count();
        assertTrue(endWithDigit < names.size() * 0.4, "too many numbers: " + endWithDigit);
        long firstLetters = names.stream().map(n -> Character.toLowerCase(n.charAt(0))).distinct().count();
        assertTrue(firstLetters >= 15, "names all start alike");
    }

    @Test
    void alwaysFindsAFreeNameEvenWhenMostAreTaken() {
        Random rnd = new Random(5);
        Set<String> taken = new HashSet<>();
        for (String g : NpcNames.GIVEN) taken.add(g.toLowerCase());
        String n = NpcNames.generate(rnd, taken::contains);
        assertFalse(taken.contains(n.toLowerCase()));
    }
}
