package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import se.oscarwiklund.twlan2.backend.web.dto.ForumDto.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

// The tribe forum. Neither the original's forum page (an iframe onto a route that does not exist) nor its "wars" page
// ship a markup to copy, so this is built from the original's strings and game.css classes. Boards: PUBLIC for every member,
// PRIVATE for the internal-forum privilege, HIDDEN for trusted members. Dukes administer boards, forum moderators edit
// and remove other people's posts and close, pin or move threads.
@Service
public class ForumService {

    public static final int THREADS_PER_PAGE = 20;
    public static final int POSTS_PER_PAGE = 10;

    private final TribeService tribes;
    private final TribeMemberRepository members;
    private final AccountRepository accounts;
    private final ForumBoardRepository boards;
    private final ForumThreadRepository threads;
    private final ForumPostRepository posts;
    private final ForumPollOptionRepository options;
    private final ForumPollVoteRepository votes;
    private final ForumReadRepository reads;

    public ForumService(TribeService tribes, TribeMemberRepository members, AccountRepository accounts, ForumBoardRepository boards,
                        ForumThreadRepository threads, ForumPostRepository posts, ForumPollOptionRepository options,
                        ForumPollVoteRepository votes, ForumReadRepository reads) {
        this.tribes = tribes;
        this.members = members;
        this.accounts = accounts;
        this.boards = boards;
        this.threads = threads;
        this.posts = posts;
        this.options = options;
        this.votes = votes;
        this.reads = reads;
    }

    // ---- access ------------------------------------------------------------------------------------------------

    private TribeMember member(Account account, World world) {
        return tribes.membership(account, world).orElseThrow(() -> new TribeService.TribeException("You do not belong to a tribe"));
    }

    private static boolean canSee(ForumBoard b, TribeMember m) {
        return switch (b.getKind()) {
            case PUBLIC -> true;
            case PRIVATE -> m.has(TribeRole.INTERNAL_FORUM);
            case HIDDEN -> m.has(TribeRole.TRUSTED_MEMBER);
        };
    }

    private ForumBoard board(Long id, TribeMember m) {
        ForumBoard b = boards.findById(id).filter(x -> x.getTribeId().equals(m.getTribeId())).orElseThrow(() -> new TribeService.TribeException("Forum not found"));
        if (!canSee(b, m)) throw new TribeService.TribeException("You may not enter this forum");
        return b;
    }

    private ForumThread thread(Long id, TribeMember m) {
        ForumThread t = threads.findById(id).orElseThrow(() -> new TribeService.TribeException("Thread not found"));
        board(t.getBoardId(), m);
        return t;
    }

    // Lazily creates the public/private boards on first access so a tribe's forum is usable at once.
    private List<ForumBoard> visibleBoards(TribeMember m) {
        List<ForumBoard> all = boards.findByTribeIdOrderByPositionAscIdAsc(m.getTribeId());
        if (all.isEmpty()) {
            all = new ArrayList<>();
            all.add(newBoard(m.getTribeId(), "General", ForumBoard.Kind.PUBLIC, 0));
            all.add(newBoard(m.getTribeId(), "Internal", ForumBoard.Kind.PRIVATE, 1));
        }
        return all.stream().filter(b -> canSee(b, m)).toList();
    }

    private ForumBoard newBoard(Long tribeId, String name, ForumBoard.Kind kind, int position) {
        ForumBoard b = new ForumBoard();
        b.setTribeId(tribeId);
        b.setName(name);
        b.setKind(kind);
        b.setPosition(position);
        return boards.save(b);
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    private String nameOf(Long accountId) {
        return accounts.findById(accountId).map(Account::getUsername).orElse("?");
    }

    private boolean unread(ForumThread t, Long accountId, Map<Long, Long> readMarks) {
        return t.getLastPostId() > readMarks.getOrDefault(t.getId(), 0L);
    }

    private Map<Long, Long> readMarks(Long accountId) {
        return reads.findByAccountId(accountId).stream().collect(Collectors.toMap(ForumRead::getThreadId, ForumRead::getLastPostId, Math::max));
    }

    private Board boardRow(ForumBoard b, Long accountId, Map<Long, Long> marks) {
        List<ForumThread> ts = threads.findByBoardId(b.getId());
        int unread = (int) ts.stream().filter(t -> unread(t, accountId, marks)).count();
        ForumThread last = ts.stream().max(Comparator.comparing(ForumThread::getLastPostAt)).orElse(null);
        String lastAuthor = null;
        if (last != null && last.getLastPostId() > 0) lastAuthor = posts.findById(last.getLastPostId()).map(p -> nameOf(p.getAuthorId())).orElse(null);
        return new Board(b.getId(), b.getName(), b.getKind().name(), ts.size(), unread, lastAuthor, last == null ? null : last.getLastPostAt());
    }

    @Transactional
    public Forum forum(Account account, World world) {
        TribeMember m = member(account, world);
        Map<Long, Long> marks = readMarks(account.getId());
        List<Board> rows = visibleBoards(m).stream().map(b -> boardRow(b, account.getId(), marks)).toList();
        return new Forum(rows, m.has(TribeRole.FOUND), m.has(TribeRole.FORUM_MOD));
    }

    // Drives the menu's "new post" mark.
    @Transactional(readOnly = true)
    public boolean hasUnread(Account account, World world) {
        TribeMember m = tribes.membership(account, world).orElse(null);
        if (m == null) return false;
        Map<Long, Long> marks = readMarks(account.getId());
        for (ForumBoard b : boards.findByTribeIdOrderByPositionAscIdAsc(m.getTribeId())) {
            if (!canSee(b, m)) continue;
            for (ForumThread t : threads.findByBoardId(b.getId())) {
                if (unread(t, account.getId(), marks)) {
                    // your own last post does not count as news
                    boolean mine = posts.findById(t.getLastPostId()).map(p -> p.getAuthorId().equals(account.getId())).orElse(false);
                    if (!mine) return true;
                }
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public BoardPage threads(Account account, World world, Long boardId, int page) {
        TribeMember m = member(account, world);
        ForumBoard b = board(boardId, m);
        Map<Long, Long> marks = readMarks(account.getId());
        List<ForumThread> all = threads.findByBoardId(b.getId());
        all.sort(Comparator.comparing(ForumThread::isPinned).reversed().thenComparing(ForumThread::getLastPostAt, Comparator.reverseOrder()));
        int pages = Math.max(1, (all.size() + THREADS_PER_PAGE - 1) / THREADS_PER_PAGE);
        int p = Math.min(Math.max(0, page), pages - 1);
        List<ThreadRow> rows = new ArrayList<>();
        for (ForumThread t : all.subList(p * THREADS_PER_PAGE, Math.min(all.size(), (p + 1) * THREADS_PER_PAGE))) {
            long count = posts.countByThreadId(t.getId());
            String lastAuthor = t.getLastPostId() > 0 ? posts.findById(t.getLastPostId()).map(x -> nameOf(x.getAuthorId())).orElse("?") : "?";
            rows.add(new ThreadRow(t.getId(), t.getTitle(), nameOf(t.getAuthorId()), (int) Math.max(0, count - 1), lastAuthor, t.getLastPostAt(),
                    t.isClosed(), t.isPinned(), t.isPoll(), unread(t, account.getId(), marks)));
        }
        return new BoardPage(boardRow(b, account.getId(), marks), rows, p, pages);
    }

    @Transactional
    public ThreadView view(Account account, World world, Long threadId, int page) {
        TribeMember m = member(account, world);
        ForumThread t = thread(threadId, m);
        ForumBoard b = boards.findById(t.getBoardId()).orElseThrow();
        long count = posts.countByThreadId(t.getId());
        int pages = (int) Math.max(1, (count + POSTS_PER_PAGE - 1) / POSTS_PER_PAGE);
        int p = Math.min(Math.max(0, page), pages - 1);
        boolean mod = m.has(TribeRole.FORUM_MOD);
        List<Post> rows = new ArrayList<>();
        for (ForumPost post : posts.findByThreadIdOrderByIdAsc(t.getId(), PageRequest.of(p, POSTS_PER_PAGE))) {
            boolean own = post.getAuthorId().equals(account.getId());
            rows.add(new Post(post.getId(), post.getAuthorId(), nameOf(post.getAuthorId()), post.getBody(), post.getCreatedAt(),
                    post.getEditedBy() == null ? null : nameOf(post.getEditedBy()), post.getEditedAt(), own, (own || mod) && !(t.isClosed() && !mod)));
        }
        // reading the last page of the thread marks it read
        if (p == pages - 1) markRead(account.getId(), t);
        Map<Long, Long> marks = readMarks(account.getId());
        List<Board> moveTargets = mod ? visibleBoards(m).stream().map(x -> boardRow(x, account.getId(), marks)).toList() : List.of();
        return new ThreadView(t.getId(), b.getId(), b.getName(), t.getTitle(), t.isClosed(), t.isPinned(), poll(t, account), rows, p, pages, mod, moveTargets);
    }

    private Poll poll(ForumThread t, Account viewer) {
        if (!t.isPoll()) return null;
        List<ForumPollOption> opts = options.findByThreadIdOrderByPositionAsc(t.getId());
        List<ForumPollVote> all = votes.findByThreadId(t.getId());
        Long mine = all.stream().filter(v -> v.getAccountId().equals(viewer.getId())).map(ForumPollVote::getOptionId).findFirst().orElse(null);
        boolean ended = t.isClosed() || (t.getPollEndsAt() != null && Instant.now().isAfter(t.getPollEndsAt()));
        boolean showResults = t.isPollShowResults() || mine != null || ended;
        Map<Long, Long> counts = all.stream().collect(Collectors.groupingBy(ForumPollVote::getOptionId, Collectors.counting()));
        List<PollOption> rows = opts.stream().map(o -> new PollOption(o.getId(), o.getText(), showResults ? counts.getOrDefault(o.getId(), 0L).intValue() : -1)).toList();
        return new Poll(t.getPollQuestion(), rows, mine, ended, mine == null && !ended, showResults ? all.size() : -1, t.getPollEndsAt());
    }

    private void markRead(Long accountId, ForumThread t) {
        ForumRead r = reads.findByAccountIdAndThreadId(accountId, t.getId()).orElseGet(() -> {
            ForumRead n = new ForumRead();
            n.setAccountId(accountId);
            n.setThreadId(t.getId());
            return n;
        });
        r.setLastPostId(t.getLastPostId());
        reads.save(r);
    }

    @Transactional
    public void markBoardRead(Account account, World world, Long boardId) {
        TribeMember m = member(account, world);
        List<ForumBoard> targets = boardId == null ? visibleBoards(m) : List.of(board(boardId, m));
        for (ForumBoard b : targets) for (ForumThread t : threads.findByBoardId(b.getId())) markRead(account.getId(), t);
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    private static String text(String s, int max, String what) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) throw new TribeService.TribeException("Please enter " + what);
        if (t.length() > max) throw new TribeService.TribeException("The " + what.replace("a ", "") + " may have at most " + max + " characters.");
        return t;
    }

    @Transactional
    public Long createThread(Account account, World world, Long boardId, NewThread req) {
        TribeMember m = member(account, world);
        ForumBoard b = board(boardId, m);
        ForumThread t = new ForumThread();
        t.setBoardId(b.getId());
        t.setTitle(text(req.title(), 120, "a title"));
        t.setAuthorId(account.getId());
        String body = text(req.body(), 8000, "a message");
        PollRequest poll = req.poll();
        List<String> opts = new ArrayList<>();
        if (poll != null && poll.question() != null && !poll.question().isBlank()) {
            t.setPollQuestion(text(poll.question(), 200, "a question"));
            for (String o : poll.options() == null ? List.<String>of() : poll.options()) if (o != null && !o.isBlank()) opts.add(text(o, 120, "an option"));
            if (opts.size() < 2 || opts.size() > 10) throw new TribeService.TribeException("A poll needs 2 to 10 options");
            if (poll.days() != null && poll.days() > 0) t.setPollEndsAt(Instant.now().plus(Duration.ofDays(Math.min(poll.days(), 365))));
            t.setPollShowResults(Boolean.TRUE.equals(poll.showResults()));
        }
        t = threads.save(t);
        for (int i = 0; i < opts.size(); i++) {
            ForumPollOption o = new ForumPollOption();
            o.setThreadId(t.getId());
            o.setText(opts.get(i));
            o.setPosition(i);
            options.save(o);
        }
        addPost(t, account, body);
        return t.getId();
    }

    private ForumPost addPost(ForumThread t, Account account, String body) {
        ForumPost p = new ForumPost();
        p.setThreadId(t.getId());
        p.setAuthorId(account.getId());
        p.setBody(body);
        p = posts.save(p);
        t.setLastPostId(p.getId());
        t.setLastPostAt(p.getCreatedAt());
        threads.save(t);
        markRead(account.getId(), t);
        return p;
    }

    @Transactional
    public void reply(Account account, World world, Long threadId, String body) {
        TribeMember m = member(account, world);
        ForumThread t = thread(threadId, m);
        if (t.isClosed() && !m.has(TribeRole.FORUM_MOD)) throw new TribeService.TribeException("This thread is closed");
        addPost(t, account, text(body, 8000, "a message"));
    }

    @Transactional
    public void editPost(Account account, World world, Long postId, String body) {
        TribeMember m = member(account, world);
        ForumPost p = posts.findById(postId).orElseThrow(() -> new TribeService.TribeException("Post not found"));
        ForumThread t = thread(p.getThreadId(), m);
        if (!p.getAuthorId().equals(account.getId()) && !m.has(TribeRole.FORUM_MOD)) throw new TribeService.TribeException("You may not edit this post");
        if (t.isClosed() && !m.has(TribeRole.FORUM_MOD)) throw new TribeService.TribeException("This thread is closed");
        p.setBody(text(body, 8000, "a message"));
        p.setEditedBy(account.getId());
        p.setEditedAt(Instant.now());
        posts.save(p);
    }

    // Removing the first post removes the whole thread; returns the thread id when it is gone.
    @Transactional
    public Long deletePost(Account account, World world, Long postId) {
        TribeMember m = member(account, world);
        ForumPost p = posts.findById(postId).orElseThrow(() -> new TribeService.TribeException("Post not found"));
        ForumThread t = thread(p.getThreadId(), m);
        if (!p.getAuthorId().equals(account.getId()) && !m.has(TribeRole.FORUM_MOD)) throw new TribeService.TribeException("You may not delete this post");
        List<ForumPost> all = posts.findByThreadIdOrderByIdAsc(t.getId());
        if (all.get(0).getId().equals(p.getId())) {
            removeThread(t);
            return t.getId();
        }
        posts.delete(p);
        ForumPost last = all.stream().filter(x -> !x.getId().equals(p.getId())).reduce((a, b) -> b).orElse(all.get(0));
        t.setLastPostId(last.getId());
        t.setLastPostAt(last.getCreatedAt());
        threads.save(t);
        return null;
    }

    private void removeThread(ForumThread t) {
        posts.deleteByThreadId(t.getId());
        options.deleteByThreadId(t.getId());
        votes.deleteByThreadId(t.getId());
        reads.deleteByThreadId(t.getId());
        threads.delete(t);
    }

    @Transactional
    public void deleteThread(Account account, World world, Long threadId) {
        TribeMember m = member(account, world);
        ForumThread t = thread(threadId, m);
        if (!m.has(TribeRole.FORUM_MOD) && !t.getAuthorId().equals(account.getId())) throw new TribeService.TribeException("You may not delete this thread");
        removeThread(t);
    }

    // what = close | open | pin | unpin.
    @Transactional
    public void moderate(Account account, World world, Long threadId, String what) {
        TribeMember m = member(account, world);
        ForumThread t = thread(threadId, m);
        if (!m.has(TribeRole.FORUM_MOD)) throw new TribeService.TribeException("You do not have the privilege to do that");
        switch (what) {
            case "close" -> t.setClosed(true);
            case "open" -> t.setClosed(false);
            case "pin" -> t.setPinned(true);
            case "unpin" -> t.setPinned(false);
            default -> throw new TribeService.TribeException("Invalid input");
        }
        threads.save(t);
    }

    @Transactional
    public void move(Account account, World world, Long threadId, Long boardId) {
        TribeMember m = member(account, world);
        ForumThread t = thread(threadId, m);
        if (!m.has(TribeRole.FORUM_MOD)) throw new TribeService.TribeException("You do not have the privilege to do that");
        t.setBoardId(board(boardId, m).getId());
        threads.save(t);
    }

    @Transactional
    public void vote(Account account, World world, Long threadId, Long optionId) {
        TribeMember m = member(account, world);
        ForumThread t = thread(threadId, m);
        Poll poll = poll(t, account);
        if (poll == null) throw new TribeService.TribeException("This thread has no poll");
        if (!poll.canVote()) throw new TribeService.TribeException(poll.ended() ? "This poll has ended" : "You have already voted");
        if (options.findByThreadIdOrderByPositionAsc(t.getId()).stream().noneMatch(o -> o.getId().equals(optionId))) throw new TribeService.TribeException("Invalid input");
        ForumPollVote v = new ForumPollVote();
        v.setThreadId(t.getId());
        v.setOptionId(optionId);
        v.setAccountId(account.getId());
        votes.save(v);
    }

    // ---- board administration (dukes) ----------------------------------------------------------------------------

    private TribeMember admin(Account account, World world) {
        TribeMember m = member(account, world);
        if (!m.has(TribeRole.FOUND)) throw new TribeService.TribeException("Only a duke can administer the forum");
        return m;
    }

    private static ForumBoard.Kind kind(String s) {
        try {
            return ForumBoard.Kind.valueOf(s == null ? "" : s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TribeService.TribeException("Invalid input");
        }
    }

    @Transactional
    public void createBoard(Account account, World world, BoardRequest req) {
        TribeMember m = admin(account, world);
        visibleBoards(m); // makes sure the defaults exist first
        newBoard(m.getTribeId(), text(req.name(), 60, "a name"), kind(req.kind()), boards.findByTribeIdOrderByPositionAscIdAsc(m.getTribeId()).size());
    }

    @Transactional
    public void editBoard(Account account, World world, Long boardId, BoardRequest req) {
        TribeMember m = admin(account, world);
        ForumBoard b = boards.findById(boardId).filter(x -> x.getTribeId().equals(m.getTribeId())).orElseThrow(() -> new TribeService.TribeException("Forum not found"));
        b.setName(text(req.name(), 60, "a name"));
        b.setKind(kind(req.kind()));
        boards.save(b);
    }

    @Transactional
    public void deleteBoard(Account account, World world, Long boardId) {
        TribeMember m = admin(account, world);
        ForumBoard b = boards.findById(boardId).filter(x -> x.getTribeId().equals(m.getTribeId())).orElseThrow(() -> new TribeService.TribeException("Forum not found"));
        for (ForumThread t : threads.findByBoardId(b.getId())) removeThread(t);
        boards.delete(b);
    }

    @EventListener
    @Transactional
    public void onTribeDisbanded(TribeService.TribeDisbanded event) {
        for (ForumBoard b : boards.findByTribeIdOrderByPositionAscIdAsc(event.tribeId())) {
            for (ForumThread t : threads.findByBoardId(b.getId())) removeThread(t);
        }
        boards.deleteByTribeId(event.tribeId());
    }

    // Deletes read marks for every tribe's forum, not just this one.
    @EventListener
    @Transactional
    public void onMembershipEnded(TribeService.MembershipEnded event) { reads.deleteByAccountId(event.accountId()); }
}
