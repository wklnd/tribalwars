package com.twlan.backend.service;

import com.twlan.backend.domain.Account;
import com.twlan.backend.domain.Village;
import com.twlan.backend.domain.World;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionTest {

    private static Village villageOf(Account owner, String protectionMinutes) {
        World w = new World();
        if (protectionMinutes != null) w.getSettings().put("beginnerProtection", protectionMinutes);
        Village v = new Village();
        v.setWorld(w);
        v.setOwner(owner);
        return v;
    }

    private static Account account(boolean npc, long ageMinutes) {
        Account a = new Account();
        a.setNpc(npc);
        a.setCreatedAt(Instant.now().minus(ageMinutes, ChronoUnit.MINUTES));
        return a;
    }

    @Test
    void newRealPlayersAreProtectedUntilTheirAccountIsOldEnough() {
        assertTrue(MovementService.underProtection(villageOf(account(false, 60), null)));       // 48 h default
        assertFalse(MovementService.underProtection(villageOf(account(false, 3000), null)));
        assertFalse(MovementService.underProtection(villageOf(account(false, 60), "0")));       // switched off
    }

    @Test
    void npcsAndBarbariansAreNeverProtected() {
        assertFalse(MovementService.underProtection(villageOf(account(true, 1), null)));
        assertFalse(MovementService.underProtection(villageOf(null, null)));
    }
}
