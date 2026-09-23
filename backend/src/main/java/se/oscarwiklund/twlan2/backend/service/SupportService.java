package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// Support to a tribe-mate's village: the troops stay there (StationedTroops), defend it, and can be recalled by
// their owner or sent back by the host. Leaving a tribe, conquering the host village or deleting a village sends the
// troops home.
@Service
public class SupportService {

    private final StationedTroopsRepository stationed;
    private final MovementRepository movements;
    private final UnitStockRepository unitStock;
    private final GameSettings settings;
    private final VillageRepository villages;
    private final AccountRepository accounts;
    private final WorldRepository worlds;

    public SupportService(StationedTroopsRepository stationed, MovementRepository movements, UnitStockRepository unitStock,
                          GameSettings settings, VillageRepository villages, AccountRepository accounts, WorldRepository worlds) {
        this.stationed = stationed;
        this.movements = movements;
        this.unitStock = unitStock;
        this.settings = settings;
        this.villages = villages;
        this.accounts = accounts;
        this.worlds = worlds;
    }

    public List<StationedTroops> guestsAt(Village host) { return stationed.findByHostVillage(host); }

    public List<StationedTroops> stationedFrom(Village origin) { return stationed.findByOriginVillage(origin); }

    // Adds to the army of that origin already stationed there, or starts a new one.
    @Transactional
    public void station(Village origin, Village host, Map<UnitType, Integer> units) {
        StationedTroops row = stationed.findByHostVillageAndOriginVillage(host, origin).orElseGet(() -> {
            StationedTroops n = new StationedTroops();
            n.setHostVillage(host);
            n.setOriginVillage(origin);
            return n;
        });
        units.forEach((t, n) -> { if (n != null && n > 0) row.getUnits().merge(t, n, Integer::sum); });
        stationed.save(row);
    }

    // units == null takes all of them back home.
    @Transactional
    public Movement withdraw(Account actor, Long armyId, Map<UnitType, Integer> units) {
        StationedTroops row = stationed.findById(armyId).orElseThrow(() -> new MovementService.MovementException("These troops are no longer there"));
        if (!isOwner(actor, row.getOriginVillage())) throw new MovementService.MovementException("These are not your troops");
        return returnTroops(row, units);
    }

    @Transactional
    public Movement sendBack(Account actor, Long armyId) {
        StationedTroops row = stationed.findById(armyId).orElseThrow(() -> new MovementService.MovementException("These troops are no longer there"));
        if (!isOwner(actor, row.getHostVillage())) throw new MovementService.MovementException("These troops are not stationed in your village");
        return returnTroops(row, null);
    }

    private static boolean isOwner(Account account, Village village) {
        return account != null && village.getOwner() != null && village.getOwner().getId().equals(account.getId());
    }

    private Movement returnTroops(StationedTroops row, Map<UnitType, Integer> wanted) {
        Map<UnitType, Integer> going = new EnumMap<>(UnitType.class);
        if (wanted == null) {
            row.getUnits().forEach((t, n) -> { if (n != null && n > 0) going.put(t, n); });
        } else {
            for (Map.Entry<UnitType, Integer> e : wanted.entrySet()) {
                int n = e.getValue() == null ? 0 : e.getValue();
                if (n <= 0) continue;
                if (n > row.getUnits().getOrDefault(e.getKey(), 0)) {
                    throw new MovementService.MovementException("Not enough " + e.getKey().displayName() + " stationed there");
                }
                going.put(e.getKey(), n);
            }
        }
        if (going.isEmpty()) throw new MovementService.MovementException("Select at least one unit to withdraw");
        going.forEach((t, n) -> {
            int left = row.getUnits().getOrDefault(t, 0) - n;
            if (left > 0) row.getUnits().put(t, left);
            else row.getUnits().remove(t);
        });
        Village host = row.getHostVillage();
        Village origin = row.getOriginVillage();
        if (row.isEmpty()) stationed.delete(row);
        else stationed.save(row);

        Instant now = Instant.now();
        Movement back = new Movement();
        back.setOriginVillage(host);
        back.setTargetVillage(origin);
        back.setType(MovementType.RETURN);
        back.getUnits().putAll(going);
        back.setDepartedAt(now);
        back.setArrivesAt(now.plusSeconds(MovementService.travelSeconds(host, origin, going, settings.travelSpeedOf(origin))));
        return movements.save(back);
    }

    // The garrison and every guest army lose their share of losses (empty armies vanish). Returns how many units
    // each guest's origin-village owner personally lost here, for the "death_of_a_hero" achievement.
    @Transactional
    public Map<Long, Long> applyDefenderLosses(Village host, Map<UnitType, Integer> losses) {
        List<UnitStock> stock = unitStock.findByVillage(host);
        List<StationedTroops> guests = stationed.findByHostVillage(host);
        List<Map<UnitType, Integer>> sources = new ArrayList<>();
        Map<UnitType, Integer> garrison = new EnumMap<>(UnitType.class);
        for (UnitStock s : stock) garrison.put(s.getType(), s.getCount());
        sources.add(garrison);
        for (StationedTroops g : guests) sources.add(g.getUnits());
        List<Map<UnitType, Integer>> split = SupportSplit.split(losses, sources);

        for (UnitStock s : stock) {
            s.setCount(Math.max(0, s.getCount() - split.get(0).getOrDefault(s.getType(), 0)));
            unitStock.save(s);
        }
        Map<Long, Long> lostByOwner = new HashMap<>();
        for (int i = 0; i < guests.size(); i++) {
            StationedTroops g = guests.get(i);
            Account owner = g.getOriginVillage().getOwner();
            long lost = split.get(i + 1).values().stream().mapToLong(Integer::longValue).sum();
            if (owner != null && lost > 0) lostByOwner.merge(owner.getId(), lost, Long::sum);
            split.get(i + 1).forEach((t, n) -> {
                int left = g.getUnits().getOrDefault(t, 0) - n;
                if (left > 0) g.getUnits().put(t, left);
                else g.getUnits().remove(t);
            });
            if (g.isEmpty()) stationed.delete(g);
            else stationed.save(g);
        }
        return lostByOwner;
    }

    // Its own troops stationed elsewhere are lost with it, not returned.
    @Transactional
    public void villageConquered(Village village) {
        for (StationedTroops g : stationed.findByHostVillage(village)) returnTroops(g, null);
        stationed.deleteAll(stationed.findByOriginVillage(village));
    }

    @Transactional
    public void deleteVillageData(Village village) {
        for (StationedTroops g : stationed.findByHostVillage(village)) returnTroops(g, null);
        stationed.deleteAll(stationed.findByOriginVillage(village));
    }

    // Every army stationed between the departing account and their former tribe-mates goes home.
    @EventListener
    @Transactional
    public void onMembershipEnded(TribeService.MembershipEnded event) {
        Account account = accounts.findById(event.accountId()).orElse(null);
        World world = worlds.findById(event.worldId()).orElse(null);
        if (account == null || world == null) return;
        for (Village v : villages.findByWorldAndOwner(world, account)) {
            for (StationedTroops g : stationed.findByHostVillage(v)) {
                if (!isOwner(account, g.getOriginVillage())) returnTroops(g, null);
            }
            for (StationedTroops s : stationed.findByOriginVillage(v)) {
                if (!isOwner(account, s.getHostVillage())) returnTroops(s, null);
            }
        }
    }
}
