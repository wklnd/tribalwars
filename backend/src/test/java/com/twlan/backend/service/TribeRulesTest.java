package com.twlan.backend.service;

import com.twlan.backend.domain.TribeMember;
import com.twlan.backend.domain.TribeRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TribeRulesTest {

    private static TribeMember member(int roles) {
        TribeMember m = new TribeMember();
        m.setRoles(roles);
        return m;
    }

    @Test
    void founderHasEveryRole() {
        TribeMember founder = member(TribeRole.FOUND.bit());
        for (TribeRole r : TribeRole.values()) assertTrue(founder.has(r), r.name());
        assertTrue(founder.isLeader());
    }

    @Test
    void leaderHasEveryRoleButFounder() {
        TribeMember leader = member(TribeRole.LEAD.bit());
        for (TribeRole r : TribeRole.values()) assertEquals(r != TribeRole.FOUND, leader.has(r), r.name());
        assertFalse(leader.isFounder());
        assertTrue(leader.isLeader());
    }

    @Test
    void plainMemberOnlyHasWhatWasGranted() {
        TribeMember m = member(TribeRole.INVITE.bit() | TribeRole.FORUM_MOD.bit());
        assertTrue(m.has(TribeRole.INVITE));
        assertTrue(m.has(TribeRole.FORUM_MOD));
        assertFalse(m.has(TribeRole.DIPLOMACY));
        assertFalse(m.has(TribeRole.LEAD));
        assertFalse(m.isLeader());
        assertFalse(member(0).has(TribeRole.TRUSTED_MEMBER));
    }

    @Test
    void roleKeysMatchTheOriginalsFieldNames() {
        assertEquals("mass_mail", TribeRole.MASS_MAIL.key());
        assertEquals(TribeRole.INTERNAL_FORUM, TribeRole.ofKey("internal_forum"));
        assertNull(TribeRole.ofKey("nope"));
        assertEquals(0b11111111, TribeRole.all());
        assertEquals(TribeRole.all() & ~1, TribeRole.allButFounder());
    }

    @Test
    void nameAndTagValidation() {
        TribeService.checkNameAndTag("The Wolves", "WLF");
        assertThrows(TribeService.TribeException.class, () -> TribeService.checkNameAndTag("", "WLF"));
        assertThrows(TribeService.TribeException.class, () -> TribeService.checkNameAndTag("Wolves", ""));
        assertThrows(TribeService.TribeException.class, () -> TribeService.checkNameAndTag("Wolves", "TOOLONG"));
        assertThrows(TribeService.TribeException.class, () -> TribeService.checkNameAndTag("<b>Wolves", "WLF"));
        assertThrows(TribeService.TribeException.class, () -> TribeService.checkNameAndTag("Wolves", "W>F"));
    }

    @Test
    void cleanTrimsAndCaps() {
        assertEquals("abc", TribeService.clean("  abc  ", 10));
        assertEquals("ab", TribeService.clean("abcdef", 2));
        assertEquals("", TribeService.clean(null, 5));
    }
}
