package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.repo.*;
import se.oscarwiklund.twlan2.backend.web.dto.MailDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

// The in-game mail. The original TWLan has no mail (every mail page answers "couldn't be found"), so this is built from its
// menu, its header icon (game_data.player.new_igm) and the game.css classes for messages. A conversation (MailThread) has
// a subject, its messages and one MailParticipant row per player (folder, read marker, deleted flag). A circular mail is a
// conversation whose only author is the sender; replying to one opens a new private conversation with the sender.
// Rule breaks throw IllegalArgumentException (shown to the player, HTTP 400).
@Service
public class MailService {

    public static final int MAX_SUBJECT = 120;
    public static final int MAX_BODY = 8000;
    public static final int MAX_RECIPIENTS = 20;
    public static final int MAX_FOLDERS = 20;
    public static final int MAX_FOLDER_NAME = 40;
    public static final int PER_PAGE = 20;

    private final MailThreadRepository threads;
    private final MailMessageRepository messages;
    private final MailParticipantRepository participants;
    private final MailFolderRepository folders;
    private final MailContactRepository contacts;
    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final TribeMemberRepository tribeMembers;
    private final TribeService tribes;
    private final LiveUpdates live;

    public MailService(MailThreadRepository threads, MailMessageRepository messages, MailParticipantRepository participants,
                       MailFolderRepository folders, MailContactRepository contacts, AccountRepository accounts, VillageRepository villages,
                       TribeMemberRepository tribeMembers, TribeService tribes, LiveUpdates live) {
        this.threads = threads;
        this.messages = messages;
        this.participants = participants;
        this.folders = folders;
        this.contacts = contacts;
        this.accounts = accounts;
        this.villages = villages;
        this.tribeMembers = tribeMembers;
        this.tribes = tribes;
        this.live = live;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static String clean(String s, int max, String what) {
        String t = s == null ? "" : s.strip();
        if (t.isEmpty()) throw new IllegalArgumentException("Please enter " + what + ".");
        if (t.length() > max) throw new IllegalArgumentException("The " + what.replaceFirst("^(a|an) ", "") + " is too long (at most " + max + " characters).");
        return t;
    }

    private static String subjectOf(String s) {
        return clean(s == null ? null : s.replaceAll("\\s+", " "), MAX_SUBJECT, "a subject");
    }

    private Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        for (Account a : accounts.findAllById(ids.stream().filter(Objects::nonNull).collect(Collectors.toSet()))) out.put(a.getId(), a.getUsername());
        return out;
    }

    private static String name(Map<Long, String> names, Long id) {
        return id == null ? "System" : names.getOrDefault(id, "(deleted player)");
    }

    private Account player(World world, String name) {
        Account a = accounts.findByUsernameLower(name == null ? "" : name.strip().toLowerCase()).orElse(null);
        if (a == null || villages.findByWorldAndOwner(world, a).isEmpty()) {
            throw new IllegalArgumentException("The player \"" + (name == null ? "" : name.strip()) + "\" does not exist.");
        }
        return a;
    }

    private MailParticipant participation(Long threadId, Account account) {
        return participants.findByThreadIdAndAccountId(threadId, account.getId()).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("This message does not exist."));
    }

    private MailThread thread(Long id, World world) {
        return threads.findById(id).filter(t -> t.getWorldId().equals(world.getId())).orElseThrow(() -> new IllegalArgumentException("This message does not exist."));
    }

    private MailMessage append(MailThread t, Account sender, String body) {
        MailMessage m = new MailMessage();
        m.setThreadId(t.getId());
        m.setSenderId(sender.getId());
        m.setBody(body);
        m = messages.save(m);
        t.setLastAt(m.getSentAt());
        threads.save(t);
        return m;
    }

    private void join(MailThread t, Account who, long lastRead) {
        MailParticipant p = participants.findByThreadIdAndAccountId(t.getId(), who.getId()).orElseGet(() -> {
            MailParticipant n = new MailParticipant();
            n.setThreadId(t.getId());
            n.setAccountId(who.getId());
            return n;
        });
        p.setDeleted(false);
        p.setLastReadId(Math.max(p.getLastReadId(), lastRead));
        participants.save(p);
    }

    private boolean blocks(Account owner, World world, Account sender) {
        return contacts.existsByAccountIdAndWorldIdAndContactIdAndBlocked(owner.getId(), world.getId(), sender.getId(), true);
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public long unreadCount(Account account, World world) {
        if (account == null || world == null) return 0;
        return participants.countUnread(account.getId(), world.getId());
    }

    private record Mine(MailParticipant p, MailThread t, MailMessage newest) {
        boolean unread() { return p.getLastReadId() < newest.getId(); }
    }

    // Newest first.
    private List<Mine> mine(Account account, World world) {
        Map<Long, MailParticipant> byThread = participants.findByAccountIdAndDeletedFalse(account.getId()).stream()
                .collect(Collectors.toMap(MailParticipant::getThreadId, p -> p, (a, b) -> a));
        if (byThread.isEmpty()) return List.of();
        List<MailThread> ts = threads.findAllById(byThread.keySet()).stream().filter(t -> t.getWorldId().equals(world.getId())).toList();
        if (ts.isEmpty()) return List.of();
        List<Long> ids = ts.stream().map(MailThread::getId).toList();
        Map<Long, Long> newestId = new HashMap<>();
        for (Object[] row : messages.newestOf(ids)) newestId.put((Long) row[0], (Long) row[1]);
        Map<Long, MailMessage> newest = messages.findAllById(newestId.values()).stream().collect(Collectors.toMap(MailMessage::getThreadId, m -> m));
        List<Mine> out = new ArrayList<>();
        for (MailThread t : ts) {
            MailMessage m = newest.get(t.getId());
            if (m != null) out.add(new Mine(byThread.get(t.getId()), t, m));
        }
        out.sort(Comparator.comparing((Mine x) -> x.newest().getId()).reversed());
        return out;
    }

    private List<Folder> folderList(Account account, World world, List<Mine> all) {
        List<Folder> out = new ArrayList<>();
        out.add(folderRow(0L, "Mail", all));
        for (MailFolder f : folders.findByAccountIdAndWorldIdOrderByIdAsc(account.getId(), world.getId())) out.add(folderRow(f.getId(), f.getName(), all));
        return out;
    }

    private static Folder folderRow(Long id, String name, List<Mine> all) {
        int n = 0, unread = 0;
        for (Mine m : all) {
            if (m.p().getFolderId() != id) continue;
            n++;
            if (m.unread()) unread++;
        }
        return new Folder(id, name, unread, n);
    }

    @Transactional(readOnly = true)
    public Inbox inbox(Account account, World world, long folder, int page) {
        List<Mine> all = mine(account, world);
        List<Mine> shown = all.stream().filter(m -> m.p().getFolderId() == folder).toList();
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        int at = Math.min(Math.max(page, 0), pages - 1);
        List<Mine> slice = shown.subList(at * PER_PAGE, Math.min(shown.size(), (at + 1) * PER_PAGE));
        Map<Long, String> names = names(slice.stream().map(m -> m.newest().getSenderId()).toList());
        Map<Long, Integer> counts = new HashMap<>();
        for (Mine m : slice) counts.put(m.t().getId(), messages.findByThreadIdOrderByIdAsc(m.t().getId()).size());
        List<Row> rows = slice.stream().map(m -> new Row(m.t().getId(), m.t().getSubject(), name(names, m.newest().getSenderId()), m.newest().getSentAt(),
                m.unread(), m.t().isMass(), m.p().getFolderId(), counts.getOrDefault(m.t().getId(), 1))).toList();
        return new Inbox(rows, folderList(account, world, all), folder, at, pages, (int) all.stream().filter(Mine::unread).count());
    }

    // Marks the conversation read as a side effect.
    @Transactional
    public ThreadView view(Account account, World world, Long id) {
        MailThread t = thread(id, world);
        MailParticipant me = participation(id, account);
        List<MailMessage> ms = messages.findByThreadIdOrderByIdAsc(id);
        if (!ms.isEmpty() && me.getLastReadId() < ms.get(ms.size() - 1).getId()) {
            me.setLastReadId(ms.get(ms.size() - 1).getId());
            participants.save(me);
        }
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(participants.findByThreadId(id).stream().map(MailParticipant::getAccountId).toList());
        ms.forEach(m -> ids.add(m.getSenderId()));
        Map<Long, String> names = names(ids);
        List<String> who = new ArrayList<>();
        // a circular mail lists the sender only, not the whole tribe
        if (t.isMass() && !ms.isEmpty()) who.add(name(names, ms.get(0).getSenderId()));
        else participants.findByThreadId(id).stream().filter(p -> !p.isDeleted() || p.getAccountId().equals(account.getId())).forEach(p -> who.add(name(names, p.getAccountId())));
        List<Message> out = ms.stream().map(m -> new Message(m.getId(), m.getSenderId(), name(names, m.getSenderId()), m.getBody(), m.getSentAt(), account.getId().equals(m.getSenderId()))).toList();
        List<Mine> all = mine(account, world);
        Long prev = null, next = null; // "newer" and "older" neighbours in the same folder (the w/s hotkeys of the original)
        List<Mine> sameFolder = all.stream().filter(m -> m.p().getFolderId() == me.getFolderId()).toList();
        for (int i = 0; i < sameFolder.size(); i++) {
            if (sameFolder.get(i).t().getId().equals(id)) {
                if (i > 0) prev = sameFolder.get(i - 1).t().getId();
                if (i + 1 < sameFolder.size()) next = sameFolder.get(i + 1).t().getId();
            }
        }
        return new ThreadView(id, t.getSubject(), t.isMass(), who, out, me.getFolderId(), folderList(account, world, all), prev, next);
    }

    // ---- writing ---------------------------------------------------------------------------------------------------

    @Transactional
    public Long send(Account sender, World world, List<String> to, String subject, String body) {
        String subj = subjectOf(subject);
        String text = clean(body, MAX_BODY, "a message");
        LinkedHashMap<Long, Account> recipients = new LinkedHashMap<>();
        for (String n : to == null ? List.<String>of() : to) {
            if (n == null || n.isBlank()) continue;
            Account a = player(world, n);
            if (a.getId().equals(sender.getId())) throw new IllegalArgumentException("You cannot write a message to yourself.");
            recipients.put(a.getId(), a);
        }
        if (recipients.isEmpty()) throw new IllegalArgumentException("Please enter a recipient.");
        if (recipients.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("A message may have at most " + MAX_RECIPIENTS + " recipients.");
        for (Account r : recipients.values()) {
            if (blocks(r, world, sender)) throw new IllegalArgumentException(r.getUsername() + " does not accept messages from you.");
        }
        MailThread t = new MailThread();
        t.setWorldId(world.getId());
        t.setSubject(subj);
        t = threads.save(t);
        MailMessage m = append(t, sender, text);
        join(t, sender, m.getId());
        for (Account r : recipients.values()) {
            join(t, r, 0);
            live.toAccount(r, world.getId(), LiveUpdates.MAIL);
        }
        return t.getId();
    }

    // Returns the id of the conversation the answer went to; a circular mail opens a new (private) one.
    @Transactional
    public Long reply(Account sender, World world, Long id, String body) {
        String text = clean(body, MAX_BODY, "a message");
        MailThread t = thread(id, world);
        participation(id, sender);
        if (t.isMass()) {
            MailMessage first = messages.findByThreadIdOrderByIdAsc(id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("This message does not exist."));
            Account author = accounts.findById(first.getSenderId()).orElseThrow(() -> new IllegalArgumentException("The sender of this circular mail no longer exists."));
            if (author.getId().equals(sender.getId())) throw new IllegalArgumentException("You cannot answer your own circular mail.");
            String subject = t.getSubject().startsWith("Re: ") ? t.getSubject() : "Re: " + t.getSubject();
            return send(sender, world, List.of(author.getUsername()), subject.length() > MAX_SUBJECT ? subject.substring(0, MAX_SUBJECT) : subject, text);
        }
        List<MailParticipant> others = participants.findByThreadId(id).stream().filter(p -> !p.getAccountId().equals(sender.getId())).toList();
        List<Account> recipients = accounts.findAllById(others.stream().map(MailParticipant::getAccountId).toList());
        for (Account r : recipients) {
            if (blocks(r, world, sender)) throw new IllegalArgumentException(r.getUsername() + " does not accept messages from you.");
        }
        MailMessage m = append(t, sender, text);
        join(t, sender, m.getId());
        for (Account r : recipients) {
            MailParticipant p = participants.findByThreadIdAndAccountId(id, r.getId()).orElseThrow();
            p.setDeleted(false); // a new answer brings a deleted conversation back
            participants.save(p);
            live.toAccount(r, world.getId(), LiveUpdates.MAIL);
        }
        return id;
    }

    @Transactional
    public void delete(Account account, World world, List<Long> ids) {
        for (Long id : ids == null ? List.<Long>of() : ids) {
            MailThread t = threads.findById(id).filter(x -> x.getWorldId().equals(world.getId())).orElse(null);
            if (t == null) continue;
            participants.findByThreadIdAndAccountId(id, account.getId()).ifPresent(p -> {
                p.setDeleted(true);
                participants.save(p);
            });
            dropIfAbandoned(t);
        }
    }

    private void dropIfAbandoned(MailThread t) {
        if (participants.findByThreadId(t.getId()).stream().allMatch(MailParticipant::isDeleted)) {
            messages.deleteByThreadId(t.getId());
            participants.deleteByThreadId(t.getId());
            threads.delete(t);
        }
    }

    @Transactional
    public void markRead(Account account, World world, List<Long> ids, boolean read) {
        for (Long id : ids == null ? List.<Long>of() : ids) {
            participants.findByThreadIdAndAccountId(id, account.getId()).filter(p -> !p.isDeleted()).ifPresent(p -> {
                List<MailMessage> ms = messages.findByThreadIdOrderByIdAsc(id);
                if (ms.isEmpty()) return;
                // "unread" rewinds the marker to just before the newest message
                p.setLastReadId(read ? ms.get(ms.size() - 1).getId() : (ms.size() > 1 ? ms.get(ms.size() - 2).getId() : 0));
                participants.save(p);
            });
        }
    }

    @Transactional
    public void move(Account account, World world, List<Long> ids, Long folderId) {
        long target = folderId == null ? 0 : folderId;
        if (target != 0 && folders.findById(target).filter(f -> f.getAccountId().equals(account.getId()) && f.getWorldId().equals(world.getId())).isEmpty()) {
            throw new IllegalArgumentException("This folder does not exist.");
        }
        for (Long id : ids == null ? List.<Long>of() : ids) {
            participants.findByThreadIdAndAccountId(id, account.getId()).filter(p -> !p.isDeleted()).ifPresent(p -> {
                p.setFolderId(target);
                participants.save(p);
            });
        }
    }

    // ---- folders ---------------------------------------------------------------------------------------------------

    @Transactional
    public void createFolder(Account account, World world, String name) {
        String n = clean(name, MAX_FOLDER_NAME, "a name");
        List<MailFolder> mine = folders.findByAccountIdAndWorldIdOrderByIdAsc(account.getId(), world.getId());
        if (mine.size() >= MAX_FOLDERS) throw new IllegalArgumentException("You can have at most " + MAX_FOLDERS + " folders.");
        if (mine.stream().anyMatch(f -> f.getName().equalsIgnoreCase(n)) || n.equalsIgnoreCase("Mail")) throw new IllegalArgumentException("You already have a folder with this name.");
        MailFolder f = new MailFolder();
        f.setAccountId(account.getId());
        f.setWorldId(world.getId());
        f.setName(n);
        folders.save(f);
    }

    @Transactional
    public void renameFolder(Account account, World world, Long id, String name) {
        String n = clean(name, MAX_FOLDER_NAME, "a name");
        MailFolder f = ownFolder(account, world, id);
        if (folders.findByAccountIdAndWorldIdOrderByIdAsc(account.getId(), world.getId()).stream().anyMatch(o -> !o.getId().equals(id) && o.getName().equalsIgnoreCase(n))) {
            throw new IllegalArgumentException("You already have a folder with this name.");
        }
        f.setName(n);
        folders.save(f);
    }

    // Conversations inside a deleted folder go back to the inbox, they are not deleted.
    @Transactional
    public void deleteFolder(Account account, World world, Long id) {
        MailFolder f = ownFolder(account, world, id);
        for (MailParticipant p : participants.findByAccountId(account.getId())) {
            if (p.getFolderId() == id) {
                p.setFolderId(0);
                participants.save(p);
            }
        }
        folders.delete(f);
    }

    private MailFolder ownFolder(Account account, World world, Long id) {
        return folders.findById(id).filter(f -> f.getAccountId().equals(account.getId()) && f.getWorldId().equals(world.getId()))
                .orElseThrow(() -> new IllegalArgumentException("This folder does not exist."));
    }

    // ---- address book and blocked senders --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Contact> contacts(Account account, World world, boolean blocked) {
        List<MailContact> rows = contacts.findByAccountIdAndWorldIdAndBlocked(account.getId(), world.getId(), blocked);
        Map<Long, String> names = names(rows.stream().map(MailContact::getContactId).toList());
        return rows.stream().map(c -> new Contact(c.getId(), c.getContactId(), name(names, c.getContactId())))
                .sorted(Comparator.comparing(c -> c.name().toLowerCase())).toList();
    }

    @Transactional
    public void addContact(Account account, World world, String name, boolean blocked) {
        Account other = player(world, name);
        if (other.getId().equals(account.getId())) throw new IllegalArgumentException(blocked ? "You cannot block yourself." : "You cannot add yourself to the address book.");
        if (contacts.existsByAccountIdAndWorldIdAndContactIdAndBlocked(account.getId(), world.getId(), other.getId(), blocked)) {
            throw new IllegalArgumentException(other.getUsername() + (blocked ? " is already blocked." : " is already in your address book."));
        }
        MailContact c = new MailContact();
        c.setAccountId(account.getId());
        c.setWorldId(world.getId());
        c.setContactId(other.getId());
        c.setBlocked(blocked);
        contacts.save(c);
    }

    @Transactional
    public void removeContact(Account account, World world, Long id) {
        contacts.findById(id).filter(c -> c.getAccountId().equals(account.getId()) && c.getWorldId().equals(world.getId())).ifPresent(contacts::delete);
    }

    // ---- circular mail ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CircularPage circulars(Account account, World world) {
        Optional<TribeMember> m = tribes.membership(account, world);
        if (m.isEmpty()) return new CircularPage(false, null, 0, List.of());
        String tribe = tribes.tribeOf(account, world).map(Tribe::getName).orElse(null);
        boolean allowed = m.get().has(TribeRole.MASS_MAIL);
        List<Circular> sent = new ArrayList<>();
        for (MailThread t : threads.findByWorldIdAndTribeIdAndMassTrueOrderByLastAtDesc(world.getId(), m.get().getTribeId())) {
            List<MailMessage> ms = messages.findByThreadIdOrderByIdAsc(t.getId());
            if (ms.isEmpty() || !account.getId().equals(ms.get(0).getSenderId())) continue;
            sent.add(new Circular(t.getId(), t.getSubject(), ms.get(0).getSentAt(), Math.max(0, participants.findByThreadId(t.getId()).size() - 1)));
        }
        return new CircularPage(allowed, tribe, tribeMembers.findByTribeId(m.get().getTribeId()).size(), sent);
    }

    // Goes to real members only, AI members never read mail. Returns the number of recipients.
    @Transactional
    public int sendCircular(Account sender, World world, String subject, String body) {
        TribeMember me = tribes.membership(sender, world).orElseThrow(() -> new IllegalArgumentException("You do not belong to a tribe."));
        if (!me.has(TribeRole.MASS_MAIL)) throw new IllegalArgumentException("You may not write circular mails.");
        String subj = subjectOf(subject);
        String text = clean(body, MAX_BODY, "a message");
        List<Account> recipients = accounts.findAllById(tribeMembers.findByTribeId(me.getTribeId()).stream().map(TribeMember::getAccountId).filter(id -> !id.equals(sender.getId())).toList())
                .stream().filter(a -> !a.isNpc() && !blocks(a, world, sender)).toList();
        if (recipients.isEmpty()) throw new IllegalArgumentException("There is nobody in your tribe who could receive this.");
        MailThread t = new MailThread();
        t.setWorldId(world.getId());
        t.setSubject(subj);
        t.setMass(true);
        t.setTribeId(me.getTribeId());
        t = threads.save(t);
        MailMessage m = append(t, sender, text);
        join(t, sender, m.getId());
        for (Account r : recipients) {
            join(t, r, 0);
            live.toAccount(r, world.getId(), LiveUpdates.MAIL);
        }
        return recipients.size();
    }

    // ---- cleanup ---------------------------------------------------------------------------------------------------

    @Transactional
    public void deleteAccountData(Long accountId) {
        List<Long> touched = participants.findByAccountId(accountId).stream().map(MailParticipant::getThreadId).toList();
        participants.deleteByAccountId(accountId);
        for (Long id : touched) {
            threads.findById(id).ifPresent(t -> {
                if (participants.findByThreadId(id).isEmpty()) {
                    messages.deleteByThreadId(id);
                    threads.delete(t);
                }
            });
        }
        folders.deleteByAccountId(accountId);
        contacts.deleteByAccountId(accountId);
        contacts.deleteByContactId(accountId);
    }

    @Transactional
    public void deleteWorldData(Long worldId) {
        for (MailThread t : threads.findByWorldId(worldId)) {
            messages.deleteByThreadId(t.getId());
            participants.deleteByThreadId(t.getId());
        }
        threads.deleteByWorldId(worldId);
        folders.deleteByWorldId(worldId);
        contacts.deleteByWorldId(worldId);
    }
}
