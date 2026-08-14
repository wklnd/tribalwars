package se.oscarwiklund.twlan2.backend.domain;

// In the column order of the original's member table (ally/members.php). Stored as a bit set in
// TribeMember.getRoles(). The founder holds every role, a leader every role but FOUND.
// MASS_MAIL lets a member write circular mails to the tribe (MailService.sendCircular).
public enum TribeRole {
    FOUND, LEAD, INVITE, DIPLOMACY, MASS_MAIL, FORUM_MOD, INTERNAL_FORUM, TRUSTED_MEMBER;

    public int bit() { return 1 << ordinal(); }

    // Matches the API and the original's form field names (`found`, `lead`, `mass_mail`, ...).
    public String key() { return name().toLowerCase(); }

    public static int all() { return (1 << values().length) - 1; }

    public static int allButFounder() { return all() & ~FOUND.bit(); }

    public static TribeRole ofKey(String key) {
        for (TribeRole r : values()) if (r.key().equals(key)) return r;
        return null;
    }
}
