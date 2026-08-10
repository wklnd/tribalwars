package com.twlan.backend.web.dto;

import java.time.Instant;
import java.util.List;

public final class MailDto {
    private MailDto() {}

    // id 0 is the default inbox.
    public record Folder(Long id, String name, int unread, int threads) {}

    public record Row(Long id, String subject, String author, Instant at, boolean unread, boolean mass, Long folderId, int messages) {}

    public record Inbox(List<Row> threads, List<Folder> folders, Long folder, int page, int pages, int unread) {}

    public record Message(Long id, Long authorId, String author, String body, Instant at, boolean own) {}

    public record ThreadView(Long id, String subject, boolean mass, List<String> participants, List<Message> messages, Long folderId,
                             List<Folder> folders, Long previousId, Long nextId) {}

    public record Contact(Long id, Long accountId, String name) {}

    public record Circular(Long id, String subject, Instant at, int recipients) {}

    // allowed: the viewer may write circular mails (tribe privilege).
    public record CircularPage(boolean allowed, String tribe, int members, List<Circular> sent) {}

    public record SendRequest(List<String> to, String subject, String body) {}
    public record CircularRequest(String subject, String body) {}
    public record BodyRequest(String body) {}
    public record IdsRequest(List<Long> ids) {}
    public record MoveRequest(List<Long> ids, Long folderId) {}
    public record NameRequest(String name) {}
}
