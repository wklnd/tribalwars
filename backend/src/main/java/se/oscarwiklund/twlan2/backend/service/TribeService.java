package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import se.oscarwiklund.twlan2.backend.web.dto.TribeDto;
import se.oscarwiklund.twlan2.backend.web.dto.TribeDto.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

// Tribes (the original's "ally"): founding, invitations, members and their privileges, the overview log, the tribe
// ranking. Points and ranks are worked out from the members' villages every time (nothing cached).
// Every rule that a player can trip over throws a TribeException whose text is shown to them.
@Service
public class TribeService {

    public static class TribeException extends RuntimeException {
        public TribeException(String message) { super(message); }
    }

    // Published when somebody stops being a member (left, kicked, tribe disbanded) so other services can clean up.
    public record MembershipEnded(Long worldId, Long tribeId, Long accountId) {}

    public record Standing(int points, int villages, int rank) {}

    public static final int EVENTS_PER_PAGE = 10;
    private static final int MAX_TEXT = 4000;

    private final LiveUpdates live;
    private final WorldPoints worldPoints;
    private final TribeRepository tribes;
    private final TribeMemberRepository members;
    private final TribeInvitationRepository invitations;
    private final TribeEventRepository events;
    private final TribeRelationRepository relations;
    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final BuildingRepository buildings;
    private final AchievementCounterRepository counters;
    private final ApplicationEventPublisher publisher;

    public TribeService(LiveUpdates live, WorldPoints worldPoints, TribeRepository tribes, TribeMemberRepository members, TribeInvitationRepository invitations,
                        TribeEventRepository events, TribeRelationRepository relations, AccountRepository accounts,
                        VillageRepository villages, BuildingRepository buildings, AchievementCounterRepository counters,
                        ApplicationEventPublisher publisher) {
        this.live = live;
        this.worldPoints = worldPoints;
        this.tribes = tribes;
        this.members = members;
        this.invitations = invitations;
        this.events = events;
        this.relations = relations;
        this.accounts = accounts;
        this.villages = villages;
        this.buildings = buildings;
        this.counters = counters;
        this.publisher = publisher;
    }

    // ---- lookups ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<TribeMember> membership(Account account, World world) {
        if (account == null) return Optional.empty();
        return members.findByWorldIdAndAccountId(world.getId(), account.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Tribe> tribeOf(Account account, World world) {
        return membership(account, world).flatMap(m -> tribes.findById(m.getTribeId()));
    }

    @Transactional(readOnly = true)
    public boolean sameTribe(Account a, Account b, World world) {
        if (a == null || b == null || world == null) return false;
        Optional<TribeMember> ma = members.findByWorldIdAndAccountId(world.getId(), a.getId());
        Optional<TribeMember> mb = members.findByWorldIdAndAccountId(world.getId(), b.getId());
        return ma.isPresent() && mb.isPresent() && ma.get().getTribeId().equals(mb.get().getTribeId());
    }

    // Used to label villages on the map and in lists.
    @Transactional(readOnly = true)
    public Map<Long, Tribe> tribeByAccount(World world) {
        Map<Long, Tribe> byId = tribes.findByWorldId(world.getId()).stream().collect(Collectors.toMap(Tribe::getId, t -> t));
        Map<Long, Tribe> out = new HashMap<>();
        for (TribeMember m : members.findByWorldId(world.getId())) {
            Tribe t = byId.get(m.getTribeId());
            if (t != null) out.put(m.getAccountId(), t);
        }
        return out;
    }

    // One query for all given tribes; an NPC tick reads it instead of asking per village.
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, TribeRelation.Kind>> relationsOf(Collection<Long> tribeIds) {
        Map<Long, Map<Long, TribeRelation.Kind>> out = new HashMap<>();
        if (tribeIds.isEmpty()) return out;
        for (TribeRelation r : relations.findByTribeIdIn(tribeIds)) out.computeIfAbsent(r.getTribeId(), k -> new HashMap<>()).put(r.getOtherTribeId(), r.getKind());
        return out;
    }

    // For the map colours.
    @Transactional(readOnly = true)
    public MapInfo mapInfo(Account viewer, World world) {
        Optional<TribeMember> m = membership(viewer, world);
        List<Row> rows = ranking(world);
        if (m.isEmpty()) return new MapInfo(null, Map.of(), rows);
        Map<Long, String> rel = new HashMap<>();
        for (TribeRelation r : relations.findByTribeId(m.get().getTribeId())) rel.put(r.getOtherTribeId(), r.getKind().name());
        return new MapInfo(m.get().getTribeId(), rel, rows);
    }

    // ---- points and ranks ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<Long, Standing> standings(World world) {
        Map<Long, Integer> villagePoints = worldPoints.byVillage(world);
        Map<Long, Integer> points = new HashMap<>();
        Map<Long, Integer> count = new HashMap<>();
        for (Village v : villages.findByWorld(world)) {
            if (v.getOwner() == null || v.getOwnerType() != OwnerType.PLAYER) continue;
            points.merge(v.getOwner().getId(), villagePoints.getOrDefault(v.getId(), 0), Integer::sum);
            count.merge(v.getOwner().getId(), 1, Integer::sum);
        }
        Map<Long, Standing> out = new HashMap<>();
        points.forEach((id, p) -> out.put(id, new Standing(p, count.get(id), 1 + (int) points.values().stream().filter(o -> o > p).count())));
        return out;
    }

    private long killsOf(Long accountId, World world) {
        return counters.findByAccountIdAndWorldIdAndCounterKey(accountId, world.getId(), "kills").map(AchievementCounter::getValue).orElse(0L);
    }

    // Ties are broken alphabetically by tag.
    @Transactional(readOnly = true)
    public List<Row> ranking(World world) {
        Map<Long, Standing> standings = standings(world);
        Map<Long, List<TribeMember>> byTribe = members.findByWorldId(world.getId()).stream().collect(Collectors.groupingBy(TribeMember::getTribeId));
        List<Row> rows = new ArrayList<>();
        for (Tribe t : tribes.findByWorldId(world.getId())) {
            List<TribeMember> ms = byTribe.getOrDefault(t.getId(), List.of());
            if (ms.isEmpty()) continue;
            int points = 0, vill = 0;
            long kills = 0;
            for (TribeMember m : ms) {
                Standing s = standings.get(m.getAccountId());
                if (s != null) { points += s.points(); vill += s.villages(); }
                kills += killsOf(m.getAccountId(), world);
            }
            rows.add(new Row(t.getId(), t.getName(), t.getTag(), 0, points, ms.size(), points / ms.size(), vill, vill == 0 ? 0 : points / vill, kills));
        }
        rows.sort(Comparator.comparingInt(Row::points).reversed().thenComparing(r -> r.tag().toLowerCase()));
        List<Row> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rank = i + 1;
            if (i > 0 && rows.get(i - 1).points() == r.points()) rank = ranked.get(i - 1).rank();
            ranked.add(new Row(r.id(), r.name(), r.tag(), rank, r.points(), r.members(), r.pointsPerPlayer(), r.villages(), r.pointsPerVillage(), r.kills()));
        }
        return ranked;
    }

    @Transactional(readOnly = true)
    public Row row(Long tribeId, World world) {
        return ranking(world).stream().filter(r -> r.id().equals(tribeId)).findFirst()
                .orElseThrow(() -> new TribeException("Tribe not found"));
    }

    // ---- views ---------------------------------------------------------------------------------------------------

    private List<String> permissionKeys(TribeMember m) {
        List<String> keys = new ArrayList<>();
        for (TribeRole r : TribeRole.values()) if (m.has(r)) keys.add(r.key());
        return keys;
    }

    private List<MemberRow> memberRows(Tribe tribe, World world) {
        Map<Long, Standing> standings = standings(world);
        List<TribeMember> ms = members.findByTribeId(tribe.getId());
        Map<Long, Account> byId = accounts.findAllById(ms.stream().map(TribeMember::getAccountId).toList()).stream()
                .collect(Collectors.toMap(Account::getId, a -> a));
        List<MemberRow> rows = new ArrayList<>();
        for (TribeMember m : ms) {
            Account a = byId.get(m.getAccountId());
            if (a == null) continue;
            Standing s = standings.getOrDefault(a.getId(), new Standing(0, 0, standings.size() + 1));
            rows.add(new MemberRow(a.getId(), a.getUsername(), 0, s.points(), s.rank(), s.villages(), permissionKeys(m),
                    m.getTitle(), m.isTitleOutside(), m.isFounder(), m.isLeader(), a.isNpc(), m.getJoinedAt()));
        }
        rows.sort(Comparator.comparingInt(MemberRow::points).reversed().thenComparing(MemberRow::name, String.CASE_INSENSITIVE_ORDER));
        List<MemberRow> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            MemberRow r = rows.get(i);
            int rank = i + 1;
            if (i > 0 && rows.get(i - 1).points() == r.points()) rank = ranked.get(i - 1).rank();
            ranked.add(new MemberRow(r.id(), r.name(), rank, r.points(), r.globalRank(), r.villages(), r.roles(), r.title(),
                    r.titleOutside(), r.founder(), r.leader(), r.npc(), r.joinedAt()));
        }
        return ranked;
    }

    private Own own(Tribe t, Row row) {
        return new Own(t.getId(), t.getName(), t.getTag(), t.getDescription(), t.getAnnouncement(), t.getHomepage(), t.getIrc(),
                t.isAllowApply(), t.getApplyTemplate(), row.members(), row.points(), row.rank(), row.villages(), row.pointsPerPlayer(),
                row.pointsPerVillage(), row.kills(), t.getCreatedAt());
    }

    public int memberLimit(World world) { return (int) WorldSettings.number(world, "tribeMemberLimit"); }

    @Transactional(readOnly = true)
    public State state(Account viewer, World world, int start) {
        List<ReceivedInvitation> mine = receivedInvitations(viewer, world);
        Optional<TribeMember> membership = membership(viewer, world);
        if (membership.isEmpty()) return new State(null, null, List.of(), List.of(), 0, List.of(), mine, List.of(), memberLimit(world));
        TribeMember me = membership.get();
        Tribe tribe = tribes.findById(me.getTribeId()).orElseThrow();
        Row row = row(tribe.getId(), world);
        List<TribeEvent> page = events.findByTribeIdOrderByCreatedAtDescIdDesc(tribe.getId(), PageRequest.of(Math.max(0, start) / EVENTS_PER_PAGE, EVENTS_PER_PAGE));
        List<SentInvitation> sent = new ArrayList<>();
        for (TribeInvitation i : invitations.findByTribeIdOrderByCreatedAtDesc(tribe.getId())) {
            accounts.findById(i.getAccountId()).ifPresent(a -> sent.add(new SentInvitation(i.getId(), a.getId(), a.getUsername(), i.getCreatedAt())));
        }
        return new State(own(tribe, row), new Me(viewer.getId(), permissionKeys(me), me.isFounder(), me.isLeader()),
                memberRows(tribe, world), eventRows(page), (int) events.countByTribeId(tribe.getId()), sent, mine,
                relationRows(tribe.getId()), memberLimit(world));
    }

    private List<ReceivedInvitation> receivedInvitations(Account viewer, World world) {
        List<ReceivedInvitation> out = new ArrayList<>();
        if (viewer == null) return out;
        for (TribeInvitation i : invitations.findByWorldIdAndAccountIdOrderByCreatedAtDesc(world.getId(), viewer.getId())) {
            tribes.findById(i.getTribeId()).ifPresent(t -> out.add(new ReceivedInvitation(i.getId(), t.getId(), t.getName(), t.getTag(), i.getCreatedAt())));
        }
        return out;
    }

    private List<EventRow> eventRows(List<TribeEvent> list) {
        Set<Long> accountIds = new HashSet<>();
        Set<Long> tribeIds = new HashSet<>();
        for (TribeEvent e : list) {
            if (e.getFromAccount() != null) accountIds.add(e.getFromAccount());
            if (e.getToAccount() != null) accountIds.add(e.getToAccount());
            if (e.getToTribe() != null) tribeIds.add(e.getToTribe());
        }
        Map<Long, Account> names = accounts.findAllById(accountIds).stream().collect(Collectors.toMap(Account::getId, a -> a));
        Map<Long, Tribe> other = tribes.findAllById(tribeIds).stream().collect(Collectors.toMap(Tribe::getId, t -> t));
        List<EventRow> rows = new ArrayList<>();
        for (TribeEvent e : list) {
            Account from = e.getFromAccount() == null ? null : names.get(e.getFromAccount());
            Account to = e.getToAccount() == null ? null : names.get(e.getToAccount());
            Tribe tt = e.getToTribe() == null ? null : other.get(e.getToTribe());
            rows.add(new EventRow(e.getId(), e.getEventType(), from == null ? null : from.getId(), from == null ? "?" : from.getUsername(),
                    to == null ? null : to.getId(), to == null ? "?" : to.getUsername(), tt == null ? null : tt.getId(),
                    tt == null ? "?" : tt.getTag(), tt == null ? "?" : tt.getName(), e.getCreatedAt()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Profile profile(Long tribeId, Account viewer, World world) {
        Tribe t = tribes.findById(tribeId).filter(x -> world.getId().equals(x.getWorldId())).orElseThrow(() -> new TribeException("Tribe not found"));
        boolean own = membership(viewer, world).map(m -> m.getTribeId().equals(tribeId)).orElse(false);
        return new Profile(row(tribeId, world), t.getDescription(), t.getHomepage(), t.getIrc(), t.getCreatedAt(), own, memberRows(t, world));
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    private void requirePlayer(Account account, World world) {
        if (account == null || villages.findByWorldAndOwner(world, account).isEmpty()) throw new TribeException("You have no village in this world");
    }

    private TribeMember require(Account actor, World world, TribeRole role) {
        TribeMember m = membership(actor, world).orElseThrow(() -> new TribeException("You do not belong to a tribe"));
        if (role != null && !m.has(role)) throw new TribeException("You do not have the privilege to do that");
        return m;
    }

    // The tribe's members and the accounts named in the event refetch their tribe pages.
    private void tellTribe(Long tribeId, Long from, Long to) {
        tribes.findById(tribeId).ifPresent(t -> {
            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (TribeMember m : members.findByTribeId(tribeId)) ids.add(m.getAccountId());
            if (from != null) ids.add(from);
            if (to != null) ids.add(to);
            for (Account a : accounts.findAllById(ids)) live.toAccount(a, t.getWorldId(), LiveUpdates.TRIBE); // (one query; NPCs are skipped)
        });
    }

    private TribeEvent log(Long tribeId, int type, Long from, Long to, Long toTribe) {
        TribeEvent e = new TribeEvent();
        e.setTribeId(tribeId);
        e.setEventType(type);
        e.setFromAccount(from);
        e.setToAccount(to);
        e.setToTribe(toTribe);
        if (live.listening()) tellTribe(tribeId, from, to);
        return events.save(e);
    }

    static String clean(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // Matches the founding form's own validation: 6 letters at most for the tag, no angle brackets.
    static void checkNameAndTag(String name, String tag) {
        if (name.isEmpty() || tag.isEmpty() || name.length() > 60 || tag.length() > 6 || name.matches(".*[<>].*") || tag.matches(".*[<>].*")) {
            throw new TribeException("Invalid input");
        }
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public Tribe found(Account founder, World world, String rawName, String rawTag) {
        requirePlayer(founder, world);
        if (membership(founder, world).isPresent()) throw new TribeException("You already belong to a tribe");
        String name = clean(rawName, 61), tag = clean(rawTag, 7);
        checkNameAndTag(name, tag);
        if (tribes.findByWorldIdAndTagLower(world.getId(), tag.toLowerCase()).isPresent()
                || tribes.findByWorldIdAndNameLower(world.getId(), name.toLowerCase()).isPresent()) {
            throw new TribeException("This ID is already being used by another tribe.");
        }
        Tribe t = new Tribe();
        t.setWorldId(world.getId());
        t.setName(name);
        t.setTag(tag);
        String owner = founder.getUsername();
        t.setDescription("If you have questions please contact [player=" + owner + "][/player].[br][br][i]This text can be changed by the tribal diplomats.[/i]");
        t.setAnnouncement("For questions please contact  [player=" + owner + "][/player][br][br][i]This text may be changed by the tribal aristocracy.[/i]");
        t = tribes.save(t);
        TribeMember m = new TribeMember();
        m.setTribeId(t.getId());
        m.setWorldId(world.getId());
        m.setAccountId(founder.getId());
        m.setRoles(TribeRole.FOUND.bit());
        members.save(m);
        invitations.deleteByWorldIdAndAccountId(world.getId(), founder.getId());
        log(t.getId(), 1, founder.getId(), null, null);
        return t;
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void invite(Account actor, World world, String playerName) {
        TribeMember me = require(actor, world, TribeRole.INVITE);
        Account target = accounts.findByUsernameLower(playerName == null ? "" : playerName.trim().toLowerCase()).orElse(null);
        if (target == null || villages.findByWorldAndOwner(world, target).isEmpty()) throw new TribeException("Player does not exist");
        if (target.getId().equals(actor.getId())) throw new TribeException("You cannot invite yourself.");
        if (membership(target, world).isPresent()) throw new TribeException("This player already belongs to a tribe.");
        if (invitations.findByTribeIdAndAccountId(me.getTribeId(), target.getId()).isPresent()) throw new TribeException("This player has already received an invitation.");
        TribeInvitation i = new TribeInvitation();
        i.setTribeId(me.getTribeId());
        i.setWorldId(world.getId());
        i.setAccountId(target.getId());
        i.setInvitedBy(actor.getId());
        invitations.save(i);
        log(me.getTribeId(), 6, actor.getId(), target.getId(), null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void withdrawInvitation(Account actor, World world, Long playerId) {
        TribeMember me = require(actor, world, TribeRole.INVITE);
        TribeInvitation i = invitations.findByTribeIdAndAccountId(me.getTribeId(), playerId).orElseThrow(() -> new TribeException("No such invitation"));
        invitations.delete(i);
        log(me.getTribeId(), 8, actor.getId(), playerId, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void accept(Account account, World world, Long invitationId) {
        TribeInvitation i = invitations.findById(invitationId).filter(x -> x.getAccountId().equals(account.getId()) && world.getId().equals(x.getWorldId()))
                .orElseThrow(() -> new TribeException("No such invitation"));
        if (membership(account, world).isPresent()) throw new TribeException("You already belong to a tribe");
        Tribe t = tribes.findById(i.getTribeId()).orElseThrow(() -> new TribeException("Tribe not found"));
        join(account, world, t);
    }

    // Also clears the account's other invitations.
    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void join(Account account, World world, Tribe tribe) {
        requirePlayer(account, world);
        if (members.findByTribeId(tribe.getId()).size() >= memberLimit(world)) throw new TribeException("This tribe is full");
        TribeMember m = new TribeMember();
        m.setTribeId(tribe.getId());
        m.setWorldId(world.getId());
        m.setAccountId(account.getId());
        members.save(m);
        invitations.deleteByWorldIdAndAccountId(world.getId(), account.getId());
        log(tribe.getId(), 13, account.getId(), null, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void reject(Account account, World world, Long invitationId) {
        TribeInvitation i = invitations.findById(invitationId).filter(x -> x.getAccountId().equals(account.getId()) && world.getId().equals(x.getWorldId()))
                .orElseThrow(() -> new TribeException("No such invitation"));
        invitations.delete(i);
        log(i.getTribeId(), 14, account.getId(), null, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void leave(Account account, World world) {
        TribeMember me = require(account, world, null);
        List<TribeMember> all = members.findByTribeId(me.getTribeId());
        if (all.size() == 1) { // the last one out closes the tribe
            disbandTribe(me.getTribeId(), world);
            return;
        }
        if (me.isFounder() && all.stream().filter(TribeMember::isFounder).count() == 1) {
            throw new TribeException("You are the only duke: name another duke before you leave, or disband the tribe");
        }
        members.delete(me);
        log(me.getTribeId(), 10, account.getId(), null, null);
        publisher.publishEvent(new MembershipEnded(world.getId(), me.getTribeId(), account.getId()));
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void kick(Account actor, World world, Long memberAccountId) {
        TribeMember me = require(actor, world, TribeRole.LEAD);
        TribeMember target = members.findByWorldIdAndAccountId(world.getId(), memberAccountId)
                .filter(m -> m.getTribeId().equals(me.getTribeId())).orElseThrow(() -> new TribeException("This player is not in your tribe"));
        if (target.getAccountId().equals(actor.getId())) throw new TribeException("Use 'Leave tribe' to leave");
        if (target.isLeader() && !me.isFounder()) throw new TribeException("Only a duke can dismiss a baron or a duke");
        members.delete(target);
        log(me.getTribeId(), 11, actor.getId(), memberAccountId, null);
        publisher.publishEvent(new MembershipEnded(world.getId(), me.getTribeId(), memberAccountId));
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void editRights(Account actor, World world, Long memberAccountId, RightsEdit edit) {
        TribeMember me = require(actor, world, TribeRole.LEAD);
        TribeMember target = members.findByWorldIdAndAccountId(world.getId(), memberAccountId)
                .filter(m -> m.getTribeId().equals(me.getTribeId())).orElseThrow(() -> new TribeException("This player is not in your tribe"));
        int wanted = 0;
        for (String key : edit.roles() == null ? List.<String>of() : edit.roles()) {
            TribeRole r = TribeRole.ofKey(key);
            if (r != null) wanted |= r.bit();
        }
        if ((wanted & TribeRole.FOUND.bit()) != 0) wanted = TribeRole.all();
        else if ((wanted & TribeRole.LEAD.bit()) != 0) wanted |= TribeRole.allButFounder();
        int current = target.getRoles();
        boolean touchesLeadership = ((wanted ^ current) & (TribeRole.FOUND.bit() | TribeRole.LEAD.bit())) != 0;
        if (!me.isFounder()) {
            if (touchesLeadership) throw new TribeException("Only a duke can name dukes and barons");
            if (target.isLeader()) throw new TribeException("Only a duke can change the privileges of a baron or a duke");
        }
        if (target.isFounder() && (wanted & TribeRole.FOUND.bit()) == 0
                && members.findByTribeId(me.getTribeId()).stream().filter(TribeMember::isFounder).count() <= 1) {
            throw new TribeException("The tribe needs at least one duke");
        }
        target.setRoles(wanted);
        target.setTitle(clean(edit.title(), 24));
        target.setTitleOutside(Boolean.TRUE.equals(edit.titleOutside()));
        members.save(target);
        log(me.getTribeId(), 9, actor.getId(), memberAccountId, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void editProperties(Account actor, World world, String rawName, String rawTag, String homepage, String irc) {
        TribeMember me = require(actor, world, TribeRole.FOUND);
        Tribe t = tribes.findById(me.getTribeId()).orElseThrow();
        String name = clean(rawName, 61), tag = clean(rawTag, 7);
        checkNameAndTag(name, tag);
        tribes.findByWorldIdAndTagLower(world.getId(), tag.toLowerCase()).filter(o -> !o.getId().equals(t.getId()))
                .ifPresent(o -> { throw new TribeException("This ID is already being used by another tribe."); });
        tribes.findByWorldIdAndNameLower(world.getId(), name.toLowerCase()).filter(o -> !o.getId().equals(t.getId()))
                .ifPresent(o -> { throw new TribeException("This ID is already being used by another tribe."); });
        t.setName(name);
        t.setTag(tag);
        t.setHomepage(clean(homepage, 128));
        t.setIrc(clean(irc, 128));
        tribes.save(t);
        log(t.getId(), 7, actor.getId(), null, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void editRecruitment(Account actor, World world, boolean allowApply, String template) {
        TribeMember me = require(actor, world, TribeRole.FOUND);
        Tribe t = tribes.findById(me.getTribeId()).orElseThrow();
        t.setAllowApply(allowApply);
        t.setApplyTemplate(clean(template, 2000));
        tribes.save(t);
        log(t.getId(), 7, actor.getId(), null, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void editDescription(Account actor, World world, String text) {
        TribeMember me = membership(actor, world).orElseThrow(() -> new TribeException("You do not belong to a tribe"));
        if (!me.has(TribeRole.FOUND) && !me.has(TribeRole.DIPLOMACY)) throw new TribeException("You do not have the privilege to do that");
        if (text != null && text.length() > MAX_TEXT) throw new TribeException("The text may have at most " + MAX_TEXT + " characters.");
        Tribe t = tribes.findById(me.getTribeId()).orElseThrow();
        t.setDescription(text == null ? "" : text.trim());
        tribes.save(t);
        log(t.getId(), 12, actor.getId(), null, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void editAnnouncement(Account actor, World world, String text) {
        TribeMember me = require(actor, world, TribeRole.LEAD);
        if (text != null && text.length() > MAX_TEXT) throw new TribeException("The text may have at most " + MAX_TEXT + " characters.");
        Tribe t = tribes.findById(me.getTribeId()).orElseThrow();
        t.setAnnouncement(text == null ? "" : text.trim());
        tribes.save(t);
        log(t.getId(), 2, actor.getId(), null, null);
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void disband(Account actor, World world) {
        TribeMember me = require(actor, world, TribeRole.FOUND);
        disbandTribe(me.getTribeId(), world);
    }

    // Every member gets a MembershipEnded event.
    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void disbandTribe(Long tribeId, World world) {
        for (TribeMember m : members.findByTribeId(tribeId)) publisher.publishEvent(new MembershipEnded(world.getId(), tribeId, m.getAccountId()));
        publisher.publishEvent(new TribeDisbanded(world.getId(), tribeId));
        members.deleteByTribeId(tribeId);
        invitations.deleteByTribeId(tribeId);
        events.deleteByTribeId(tribeId);
        relations.deleteByTribeId(tribeId);
        relations.deleteByOtherTribeId(tribeId);
        tribes.deleteById(tribeId);
    }

    // Published just before a tribe's rows are deleted (forum data, wars and the like hang on it).
    public record TribeDisbanded(Long worldId, Long tribeId) {}

    // ---- admin panel ---------------------------------------------------------------------------------------------

    public record AdminMember(Long id, String name, boolean npc, boolean founder) {}
    public record AdminTribe(Row tribe, List<AdminMember> members, List<Relation> relations) {}

    @Transactional(readOnly = true)
    public List<AdminTribe> adminOverview(World world) {
        List<AdminTribe> out = new ArrayList<>();
        for (Row row : ranking(world)) {
            List<AdminMember> ms = new ArrayList<>();
            for (TribeMember m : members.findByTribeId(row.id())) {
                accounts.findById(m.getAccountId()).ifPresent(a -> ms.add(new AdminMember(a.getId(), a.getUsername(), a.isNpc(), m.isFounder())));
            }
            out.add(new AdminTribe(row, ms, relationRows(row.id())));
        }
        return out;
    }

    // Bypasses the invitation flow; called by the admin panel.
    @Transactional(noRollbackFor = TribeException.class)
    public void adminAddMember(World world, Long tribeId, Long accountId) {
        Tribe tribe = tribes.findById(tribeId).filter(t -> world.getId().equals(t.getWorldId())).orElseThrow(() -> new TribeException("Tribe not found"));
        Account account = accounts.findById(accountId).orElseThrow(() -> new TribeException("Player does not exist"));
        if (membership(account, world).isPresent()) throw new TribeException("This player already belongs to a tribe.");
        join(account, world, tribe);
    }

    // ---- relations (diplomacy) -----------------------------------------------------------------------------------

    // The other tribe is looked up by tag; requires the diplomacy privilege.
    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void addRelation(Account actor, World world, String tag, String kindName) {
        TribeMember me = require(actor, world, TribeRole.DIPLOMACY);
        TribeRelation.Kind kind;
        try {
            kind = TribeRelation.Kind.valueOf(kindName == null ? "" : kindName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TribeException("Invalid input");
        }
        Tribe other = tribes.findByWorldIdAndTagLower(world.getId(), clean(tag, 30).toLowerCase()).orElseThrow(() -> new TribeException("Tribe not found"));
        if (other.getId().equals(me.getTribeId())) throw new TribeException("Invalid input");
        if (relations.findByTribeIdAndOtherTribeId(me.getTribeId(), other.getId()).isPresent()) {
            throw new TribeException("There is already a relationship to this tribe.");
        }
        TribeRelation r = new TribeRelation();
        r.setTribeId(me.getTribeId());
        r.setOtherTribeId(other.getId());
        r.setKind(kind);
        relations.save(r);
        log(me.getTribeId(), switch (kind) { case PARTNER -> 3; case NAP -> 4; case ENEMY -> 5; }, actor.getId(), null, other.getId());
    }

    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void endRelation(Account actor, World world, Long otherTribeId) {
        TribeMember me = require(actor, world, TribeRole.DIPLOMACY);
        TribeRelation r = relations.findByTribeIdAndOtherTribeId(me.getTribeId(), otherTribeId).orElseThrow(() -> new TribeException("No such relationship"));
        relations.delete(r);
        log(me.getTribeId(), 666, actor.getId(), null, otherTribeId);
    }

    // Returns null when no relation is set.
    @Transactional(readOnly = true)
    public TribeRelation.Kind relationOf(Long tribeId, Long otherTribeId) {
        return relations.findByTribeIdAndOtherTribeId(tribeId, otherTribeId).map(TribeRelation::getKind).orElse(null);
    }

    List<Relation> relationRows(Long tribeId) {
        List<Relation> out = new ArrayList<>();
        for (TribeRelation r : relations.findByTribeId(tribeId)) {
            tribes.findById(r.getOtherTribeId()).ifPresent(o -> out.add(new Relation(o.getId(), o.getTag(), o.getName(), r.getKind().name(), r.getCreatedAt())));
        }
        out.sort(Comparator.comparing(Relation::tag, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ---- cleanup hooks -------------------------------------------------------------------------------------------

    // Called when the world is being deleted.
    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void deleteWorldData(Long worldId) {
        for (Tribe t : tribes.findByWorldId(worldId)) {
            events.deleteByTribeId(t.getId());
            relations.deleteByTribeId(t.getId());
            relations.deleteByOtherTribeId(t.getId());
            publisher.publishEvent(new TribeDisbanded(worldId, t.getId()));
        }
        members.deleteByWorldId(worldId);
        invitations.deleteByWorldId(worldId);
        tribes.deleteByWorldId(worldId);
    }

    // Called when the account is being deleted.
    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void deleteAccountData(Account account) {
        for (TribeMember m : members.findByAccountId(account.getId())) dropMember(m, account);
        invitations.deleteByAccountId(account.getId());
    }

    // Called when the account lost its last village in the world; no-op if it has no tribe membership there.
    @Transactional(noRollbackFor = TribeException.class) // NPCs try things that may be refused
    public void leaveWorld(Account account, World world) {
        membership(account, world).ifPresent(m -> dropMember(m, account));
        invitations.deleteByWorldIdAndAccountId(world.getId(), account.getId());
    }

    // Takes a member out without their say: the last one closes the tribe, a lone duke hands the crown to the oldest member.
    private void dropMember(TribeMember m, Account account) {
        World w = new World();
        w.setId(m.getWorldId());
        List<TribeMember> all = members.findByTribeId(m.getTribeId());
        if (all.size() == 1) {
            disbandTribe(m.getTribeId(), w);
            return;
        }
        if (m.isFounder() && all.stream().filter(TribeMember::isFounder).count() == 1) {
            all.stream().filter(o -> !o.getId().equals(m.getId())).min(Comparator.comparing(TribeMember::getJoinedAt))
                    .ifPresent(o -> { o.setRoles(TribeRole.FOUND.bit()); members.save(o); });
        }
        members.delete(m);
        log(m.getTribeId(), 10, account.getId(), null, null);
        publisher.publishEvent(new MembershipEnded(m.getWorldId(), m.getTribeId(), account.getId()));
    }
}
