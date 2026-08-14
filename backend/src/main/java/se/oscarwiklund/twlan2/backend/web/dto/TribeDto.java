package se.oscarwiklund.twlan2.backend.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TribeDto {
    private TribeDto() {}

    // Reused for both a tribe-ranking row and the numbers on a tribe's profile.
    public record Row(Long id, String name, String tag, int rank, int points, int members, int pointsPerPlayer,
                      int villages, int pointsPerVillage, long kills) {}

    // Also used for info_ally, not just ally/profile.php.
    public record Profile(Row tribe, String description, String homepage, String irc, Instant createdAt, boolean own,
                          List<MemberRow> members) {}

    public record MemberRow(Long id, String name, int rank, int points, int globalRank, int villages, List<String> roles,
                            String title, boolean titleOutside, boolean founder, boolean leader, boolean npc, Instant joinedAt) {}

    public record EventRow(Long id, int type, Long fromId, String from, Long toId, String to, Long toTribeId, String toTribeTag,
                           String toTribeName, Instant at) {}

    public record SentInvitation(Long id, Long playerId, String player, Instant at) {}

    public record ReceivedInvitation(Long id, Long tribeId, String tribeName, String tribeTag, Instant at) {}

    public record Me(Long id, List<String> permissions, boolean founder, boolean leader) {}

    public record Own(Long id, String name, String tag, String description, String announcement, String homepage, String irc,
                      boolean allowApply, String applyTemplate, int members, int points, int rank, int villages,
                      int pointsPerPlayer, int pointsPerVillage, long kills, Instant createdAt) {}

    // tribe is null when the viewer belongs to none (then only myInvitations matters).
    // events is one page of the overview log (eventsTotal rows overall, 10 per page).
    public record State(Own tribe, Me me, List<MemberRow> members, List<EventRow> events, int eventsTotal,
                        List<SentInvitation> invitations, List<ReceivedInvitation> myInvitations, List<Relation> relations,
                        int memberLimit) {}

    // kind = PARTNER | NAP | ENEMY.
    public record Relation(Long tribeId, String tag, String name, String kind, Instant since) {}

    // relations maps tribe id to kind.
    public record MapInfo(Long tribeId, Map<Long, String> relations, List<Row> tribes) {}

    public record RightsEdit(List<String> roles, String title, Boolean titleOutside) {}
}
