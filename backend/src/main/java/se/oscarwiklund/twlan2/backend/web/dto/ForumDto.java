package se.oscarwiklund.twlan2.backend.web.dto;

import java.time.Instant;
import java.util.List;

public final class ForumDto {
    private ForumDto() {}

    public record Board(Long id, String name, String kind, int threads, int unread, String lastAuthor, Instant lastAt) {}

    public record Forum(List<Board> boards, boolean canAdmin, boolean canModerate) {}

    public record ThreadRow(Long id, String title, String author, int replies, String lastAuthor, Instant lastAt, boolean closed,
                            boolean pinned, boolean poll, boolean unread) {}

    public record BoardPage(Board board, List<ThreadRow> threads, int page, int pages) {}

    public record Post(Long id, Long authorId, String author, String body, Instant at, String editedBy, Instant editedAt, boolean own,
                       boolean canEdit) {}

    public record PollOption(Long id, String text, int votes) {}

    // options[].votes is -1 while the viewer may not see results yet.
    public record Poll(String question, List<PollOption> options, Long myVote, boolean ended, boolean canVote, int total, Instant endsAt) {}

    public record ThreadView(Long id, Long boardId, String boardName, String title, boolean closed, boolean pinned, Poll poll,
                             List<Post> posts, int page, int pages, boolean canModerate, List<Board> boards) {}

    public record PollRequest(String question, List<String> options, Integer days, Boolean showResults) {}
    public record NewThread(String title, String body, PollRequest poll) {}
    public record BodyRequest(String body) {}
    public record BoardRequest(String name, String kind) {}
    public record MoveRequest(Long boardId) {}
    public record VoteRequest(Long optionId) {}
}
