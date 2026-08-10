package com.twlan.backend.service;

import com.twlan.backend.domain.*;
import com.twlan.backend.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ProfileService {

    public record VillageRow(Long id, String name, int x, int y, int points) {}
    public record AchievementRow(String key, String name, String icon, int level, int maxLevel) {}
    public record Profile(String name, boolean npc, boolean own, boolean admin, int rank, int players, int points, List<VillageRow> villages,
                          long kills, Instant memberSince, String description, String location, String birthday,
                          List<AchievementRow> achievements, Long tribeId, String tribeName, String tribeTag, String tribeTitle) {}

    private final WorldPoints worldPoints;
    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final BuildingRepository buildings;
    private final PlayerProfileRepository profiles;
    private final AchievementCounterRepository counters;
    private final AchievementService achievements;
    private final TribeService tribes;
    private final TribeRepository tribeRepository;
    private final TribeMemberRepository tribeMembers;

    public ProfileService(WorldPoints worldPoints, TribeService tribes, TribeRepository tribeRepository, TribeMemberRepository tribeMembers, AccountRepository accounts, VillageRepository villages, BuildingRepository buildings,
                          PlayerProfileRepository profiles, AchievementCounterRepository counters, AchievementService achievements) {
        this.worldPoints = worldPoints;
        this.tribes = tribes;
        this.tribeRepository = tribeRepository;
        this.tribeMembers = tribeMembers;
        this.accounts = accounts;
        this.villages = villages;
        this.buildings = buildings;
        this.profiles = profiles;
        this.counters = counters;
        this.achievements = achievements;
    }

    @Transactional(readOnly = true)
    public Profile profile(String name, Account viewer, World world) {
        Account account = accounts.findByUsernameLower(name == null ? "" : name.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        Map<Long, Integer> villagePoints = worldPoints.byVillage(world);
        Map<Long, Integer> totals = new HashMap<>();
        List<VillageRow> mine = new ArrayList<>();
        for (Village v : villages.findByWorld(world)) {
            if (v.getOwner() == null || v.getOwnerType() != OwnerType.PLAYER) continue;
            int p = villagePoints.getOrDefault(v.getId(), 0);
            totals.merge(v.getOwner().getId(), p, Integer::sum);
            if (v.getOwner().getId().equals(account.getId())) mine.add(new VillageRow(v.getId(), v.getName(), v.getX(), v.getY(), p));
        }
        if (mine.isEmpty()) throw new IllegalArgumentException("Player not found");
        mine.sort(Comparator.comparing(VillageRow::name));
        int points = totals.get(account.getId());
        int rank = 1 + (int) totals.values().stream().filter(p -> p > points).count();

        PlayerProfile p = profiles.findById(account.getId()).orElse(null);
        long kills = counters.findByAccountIdAndWorldIdAndCounterKey(account.getId(), world.getId(), "kills").map(AchievementCounter::getValue).orElse(0L);
        List<AchievementRow> earned = achievements.list(account, world).stream().filter(e -> e.level() > 0)
                .map(e -> new AchievementRow(e.key(), e.name(), e.key().equals("years_of_service") ? "years_of_service_" + Math.min(20, e.level()) : e.icon(), e.level(), e.maxLevel()))
                .toList();
        // the tribe and the player's title in it (titles are only shown to outsiders when the player allows it)
        TribeMember tm = tribeMembers.findByWorldIdAndAccountId(world.getId(), account.getId()).orElse(null);
        Tribe tribe = tm == null ? null : tribeRepository.findById(tm.getTribeId()).orElse(null);
        boolean sameTribe = tribe != null && viewer != null && tribes.sameTribe(viewer, account, world);
        String title = tm != null && (sameTribe || tm.isTitleOutside()) ? tm.getTitle() : "";
        return new Profile(account.getUsername(), account.isNpc(), viewer != null && viewer.getId().equals(account.getId()), account.isAdmin(),
                rank, totals.size(), points, mine, kills, account.getCreatedAt(),
                p == null ? "" : p.getDescription(), p == null ? "" : p.getLocation(), p == null ? "" : p.getBirthday(), earned,
                tribe == null ? null : tribe.getId(), tribe == null ? null : tribe.getName(), tribe == null ? null : tribe.getTag(), title);
    }

    public record Edit(String description, String location, String birthday) {}

    @Transactional
    public void edit(Account account, Edit edit) {
        String description = edit.description() == null ? "" : edit.description().trim();
        String location = edit.location() == null ? "" : edit.location().trim();
        String birthday = edit.birthday() == null ? "" : edit.birthday().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("The description may have at most 4000 characters.");
        if (location.length() > 60) throw new IllegalArgumentException("The location may have at most 60 characters.");
        if (!birthday.isEmpty()) {
            try {
                if (LocalDate.parse(birthday).isAfter(LocalDate.now())) throw new IllegalArgumentException("The birthday cannot be in the future.");
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("The birthday must be a date like 1990-04-23.");
            }
        }
        PlayerProfile p = profiles.findById(account.getId()).orElseGet(() -> {
            PlayerProfile n = new PlayerProfile();
            n.setAccountId(account.getId());
            return n;
        });
        p.setDescription(description);
        p.setLocation(location);
        p.setBirthday(birthday);
        profiles.save(p);
    }
}
