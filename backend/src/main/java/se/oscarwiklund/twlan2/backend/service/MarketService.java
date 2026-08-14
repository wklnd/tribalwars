package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.live.LiveUpdates;
import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.MarketOfferRepository;
import se.oscarwiklund.twlan2.backend.repo.TransportRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.web.dto.MarketDto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// The market: merchants, shipments of resources between villages and trade offers.
// - Merchants = a function of the market level (merchantsForLevel), each carries CAPACITY; they are away from their
//   village for the whole round trip (Transport).
// - A shipment takes MINUTES_PER_FIELD minutes per field (÷ world speed) each way; whatever the target's warehouse
//   cannot hold is lost.
// - An offer takes the goods on offer out of the village at once. Whoever accepts pays the asked resource, their merchants
//   carry it to the offering village and come back with the goods (so the acceptor needs the merchants, the offerer none).
// The merchant table, capacity and speed are the classic Tribal Wars values, not readable from the original (it has no
// market pages). Rule breaks throw MarketException (shown to the player, HTTP 400).
@Service
public class MarketService {

    public static class MarketException extends RuntimeException {
        public MarketException(String message) { super(message); }
    }

    public static final int CAPACITY = 1000;
    public static final int MINUTES_PER_FIELD = 6;
    public static final int MAX_OFFERS_PER_VILLAGE = 10;
    public static final int MAX_TIMES = 100;
    private static final int[] MERCHANTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 19, 26, 35, 46, 59, 74, 91, 110, 131, 154, 179, 206, 235};

    public static int merchantsForLevel(int level) {
        return MERCHANTS[Math.max(0, Math.min(MERCHANTS.length - 1, level))];
    }

    public static int merchantsNeeded(int amount) {
        return (amount + CAPACITY - 1) / CAPACITY;
    }

    private final LiveUpdates live;
    private final TransportRepository transports;
    private final MarketOfferRepository offers;
    private final VillageRepository villages;
    private final VillageService villageService;
    private final GameSettings settings;

    public MarketService(LiveUpdates live, TransportRepository transports, MarketOfferRepository offers, VillageRepository villages,
                         VillageService villageService, GameSettings settings) {
        this.live = live;
        this.transports = transports;
        this.offers = offers;
        this.villages = villages;
        this.villageService = villageService;
        this.settings = settings;
    }

    // ---- merchants -------------------------------------------------------------------------------------------------

    public int totalMerchants(Village v) { return merchantsForLevel(villageService.levelOf(v, BuildingType.MARKET)); }

    @Transactional(readOnly = true)
    public int availableMerchants(Village v) {
        int away = 0;
        for (Transport t : transports.findByOriginVillageId(v.getId())) away += t.getMerchants();
        return Math.max(0, totalMerchants(v) - away);
    }

    public static double distance(Village a, Village b) {
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
    }

    private long travelSeconds(Village a, Village b) {
        return Math.max(1, Math.round(distance(a, b) * MINUTES_PER_FIELD * 60 / settings.speedOf(a)));
    }

    // ---- resources of a village ------------------------------------------------------------------------------------

    private static double get(Village v, Resource r) {
        return switch (r) { case WOOD -> v.getWood(); case CLAY -> v.getClay(); case IRON -> v.getIron(); };
    }

    private static void set(Village v, Resource r, double value) {
        switch (r) { case WOOD -> v.setWood(value); case CLAY -> v.setClay(value); case IRON -> v.setIron(value); }
    }

    void deliver(Village v, Resource r, int amount) {
        if (amount <= 0) return;
        villageService.settleResources(v);
        double now = get(v, r);
        set(v, r, Math.max(now, Math.min(villageService.warehouseCapacity(v), now + amount)));
        villages.save(v);
    }

    private void take(Village v, Resource r, int amount) {
        if (amount <= 0) return;
        villageService.settleResources(v);
        if (Math.floor(get(v, r)) < amount) throw new MarketException("Not enough resources");
        set(v, r, get(v, r) - amount);
        villages.save(v);
    }

    // ---- shipments -------------------------------------------------------------------------------------------------

    @Transactional(noRollbackFor = MarketException.class)
    public Transport send(Village origin, Village target, int wood, int clay, int iron) {
        if (target == null) throw new MarketException("This village does not exist");
        if (target.getId().equals(origin.getId())) throw new MarketException("You cannot send resources to the same village");
        if (target.getWorld() == null || origin.getWorld() == null || !target.getWorld().getId().equals(origin.getWorld().getId())) {
            throw new MarketException("This village does not exist");
        }
        if (target.getOwnerType() == OwnerType.BARBARIAN) throw new MarketException("Resources cannot be sent to barbarian villages");
        if (wood < 0 || clay < 0 || iron < 0) throw new MarketException("You have to enter the resources to send");
        int sum = wood + clay + iron;
        if (sum <= 0) throw new MarketException("You have to enter the resources to send");
        if (totalMerchants(origin) == 0) throw new MarketException("You need a market to send resources");
        int needed = merchantsNeeded(sum);
        int available = availableMerchants(origin);
        if (needed > available) throw new MarketException("Not enough merchants (" + needed + " needed, " + available + " available)");
        // check everything before taking anything: a refused order must not leave a partial withdrawal behind
        villageService.settleResources(origin);
        if (Math.floor(origin.getWood()) < wood || Math.floor(origin.getClay()) < clay || Math.floor(origin.getIron()) < iron) {
            throw new MarketException("Not enough resources");
        }
        take(origin, Resource.WOOD, wood);
        take(origin, Resource.CLAY, clay);
        take(origin, Resource.IRON, iron);

        Instant now = Instant.now();
        Transport t = new Transport();
        t.setWorldId(origin.getWorld().getId());
        t.setOriginVillageId(origin.getId());
        t.setTargetVillageId(target.getId());
        t.setOutWood(wood);
        t.setOutClay(clay);
        t.setOutIron(iron);
        t.setMerchants(needed);
        t.setDepartedAt(now);
        t.setArrivesAt(now.plusSeconds(travelSeconds(origin, target)));
        return transports.save(t);
    }

    // Called by the game tick.
    @Transactional
    public void process(Instant now) {
        for (Transport t : transports.findByArrivesAtLessThanEqual(now)) {
            Village origin = villages.findById(t.getOriginVillageId()).orElse(null);
            notifyMarket(origin);
            notifyMarket(villages.findById(t.getTargetVillageId()).orElse(null));
            if (origin == null) {
                transports.delete(t);
                continue;
            }
            if (!t.isReturning()) {
                Village target = villages.findById(t.getTargetVillageId()).orElse(null);
                if (target != null && target.getOwnerType() != OwnerType.BARBARIAN) {
                    deliver(target, Resource.WOOD, t.getOutWood());
                    deliver(target, Resource.CLAY, t.getOutClay());
                    deliver(target, Resource.IRON, t.getOutIron());
                }
                t.setReturning(true);
                t.setDepartedAt(now);
                t.setArrivesAt(now.plusSeconds(target == null ? 1 : travelSeconds(target, origin)));
                transports.save(t);
            } else {
                deliver(origin, Resource.WOOD, t.getBackWood());
                deliver(origin, Resource.CLAY, t.getBackClay());
                deliver(origin, Resource.IRON, t.getBackIron());
                transports.delete(t);
            }
        }
        expireOffers(now);
    }

    // Tells the owner's village and market screen to refetch (goods arrived, an offer was filled or ran out).
    public void notifyMarket(Village v) {
        if (v == null || v.getWorld() == null) return;
        live.village(v);
        live.toAccount(v.getOwner(), v.getWorld().getId(), LiveUpdates.MARKET);
    }

    // ---- offers ----------------------------------------------------------------------------------------------------

    @Transactional(noRollbackFor = MarketException.class)
    public MarketOffer createOffer(Village v, Resource sell, int sellAmount, Resource buy, int buyAmount, int times, int maxDistance) {
        if (totalMerchants(v) == 0) throw new MarketException("You need a market to make offers");
        if (sell == null || buy == null || sell == buy) throw new MarketException("Offer and request have to be different resources");
        if (sellAmount < 1 || buyAmount < 1 || sellAmount > 100_000 || buyAmount > 100_000) throw new MarketException("Enter the amounts of the offer");
        if (times < 1 || times > MAX_TIMES) throw new MarketException("The offer can be accepted 1 to " + MAX_TIMES + " times");
        if (maxDistance < 0 || maxDistance > 999) throw new MarketException("Invalid maximum distance");
        double ratio = (double) buyAmount / sellAmount;
        if (ratio < 0.1 || ratio > 10) throw new MarketException("The exchange ratio has to be between 1:10 and 10:1");
        if (offers.findByVillageId(v.getId()).size() >= MAX_OFFERS_PER_VILLAGE) {
            throw new MarketException("A village can have at most " + MAX_OFFERS_PER_VILLAGE + " offers");
        }
        take(v, sell, sellAmount * times);
        MarketOffer o = new MarketOffer();
        o.setWorldId(v.getWorld().getId());
        o.setVillageId(v.getId());
        o.setSellResource(sell);
        o.setSellAmount(sellAmount);
        o.setBuyResource(buy);
        o.setBuyAmount(buyAmount);
        o.setRemaining(times);
        o.setMaxDistance(maxDistance);
        return offers.save(o);
    }

    @Transactional(noRollbackFor = MarketException.class)
    public void cancelOffer(Account account, Long offerId) {
        MarketOffer o = offers.findById(offerId).orElseThrow(() -> new MarketException("This offer does not exist any more"));
        Village v = villages.findById(o.getVillageId()).orElse(null);
        if (v == null || v.getOwner() == null || !v.getOwner().getId().equals(account.getId())) throw new MarketException("This is not your offer");
        deliver(v, o.getSellResource(), o.getSellAmount() * o.getRemaining());
        offers.delete(o);
    }

    @Transactional(noRollbackFor = MarketException.class)
    public Transport accept(Village acceptor, Long offerId, int times) {
        MarketOffer o = offers.findById(offerId).orElseThrow(() -> new MarketException("This offer does not exist any more"));
        Village seller = villages.findById(o.getVillageId()).orElseThrow(() -> new MarketException("This offer does not exist any more"));
        if (!o.getWorldId().equals(acceptor.getWorld().getId())) throw new MarketException("This offer does not exist any more");
        if (seller.getOwner() != null && acceptor.getOwner() != null && seller.getOwner().getId().equals(acceptor.getOwner().getId())) {
            throw new MarketException("You cannot accept your own offer");
        }
        if (times < 1 || times > o.getRemaining()) throw new MarketException("The offer can only be accepted " + o.getRemaining() + " more time(s)");
        if (o.getMaxDistance() > 0 && distance(acceptor, seller) > o.getMaxDistance()) throw new MarketException("Your village is too far away for this offer");
        if (totalMerchants(acceptor) == 0) throw new MarketException("You need a market to trade");
        int pay = o.getBuyAmount() * times;
        int get = o.getSellAmount() * times;
        int needed = merchantsNeeded(Math.max(pay, get));
        int available = availableMerchants(acceptor);
        if (needed > available) throw new MarketException("Not enough merchants (" + needed + " needed, " + available + " available)");
        take(acceptor, o.getBuyResource(), pay);

        Instant now = Instant.now();
        Transport t = new Transport();
        t.setWorldId(o.getWorldId());
        t.setOriginVillageId(acceptor.getId());
        t.setTargetVillageId(seller.getId());
        addOut(t, o.getBuyResource(), pay);
        addBack(t, o.getSellResource(), get);
        t.setMerchants(needed);
        t.setDepartedAt(now);
        t.setArrivesAt(now.plusSeconds(travelSeconds(acceptor, seller)));
        transports.save(t);

        o.setRemaining(o.getRemaining() - times);
        if (o.getRemaining() <= 0) offers.delete(o);
        else offers.save(o);
        return t;
    }

    static void addOut(Transport t, Resource r, int n) {
        switch (r) { case WOOD -> t.setOutWood(n); case CLAY -> t.setOutClay(n); case IRON -> t.setOutIron(n); }
    }

    static void addBack(Transport t, Resource r, int n) {
        switch (r) { case WOOD -> t.setBackWood(n); case CLAY -> t.setBackClay(n); case IRON -> t.setBackIron(n); }
    }

    // NPC offers run out of time; the goods go back to their village.
    @Transactional
    public void expireOffers(Instant now) {
        for (MarketOffer o : offers.findByExpiresAtBefore(now)) {
            villages.findById(o.getVillageId()).ifPresent(v -> { deliver(v, o.getSellResource(), o.getSellAmount() * o.getRemaining()); notifyMarket(v); });
            offers.delete(o);
        }
    }

    // ---- what the viewer sees --------------------------------------------------------------------------------------

    private Offer offerDto(MarketOffer o, Village from, Account viewer) {
        Village v = villages.findById(o.getVillageId()).orElse(null);
        if (v == null) return null;
        boolean mine = v.getOwner() != null && viewer != null && v.getOwner().getId().equals(viewer.getId());
        double dist = distance(from, v);
        int needed = merchantsNeeded(Math.max(o.getBuyAmount(), o.getSellAmount()));
        return new Offer(o.getId(), v.getId(), v.getName(), v.getX(), v.getY(), v.getOwner() == null ? null : v.getOwner().getUsername(), mine,
                o.getSellResource().name(), o.getSellAmount(), o.getBuyResource().name(), o.getBuyAmount(), o.getRemaining(),
                o.getMaxDistance(), dist, (double) o.getSellAmount() / o.getBuyAmount(), needed, o.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public State state(Account viewer, World world, Village current) {
        List<Village> mine = villages.findByWorldAndOwner(world, viewer);
        Set<Long> mineIds = new HashSet<>();
        for (Village v : mine) mineIds.add(v.getId());

        List<Offer> others = new ArrayList<>();
        List<Offer> own = new ArrayList<>();
        for (MarketOffer o : offers.findByWorldId(world.getId())) {
            Offer dto = offerDto(o, current, viewer);
            if (dto == null) continue;
            (dto.mine() ? own : others).add(dto);
        }
        others.sort(Comparator.comparingDouble(Offer::distance).thenComparing(Offer::id));
        own.sort(Comparator.comparing(Offer::id));

        Map<Long, Village> cache = new HashMap<>();
        for (Village v : mine) cache.put(v.getId(), v);
        List<Shipment> shipments = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Village v : mine) {
            List<Transport> all = new ArrayList<>(transports.findByOriginVillageId(v.getId()));
            all.addAll(transports.findByTargetVillageId(v.getId()));
            for (Transport t : all) {
                if (!seen.add(t.getId())) continue;
                boolean outgoing = mineIds.contains(t.getOriginVillageId());
                if (!outgoing && t.isReturning()) continue; // somebody else's merchants going home again
                Village a = cache.computeIfAbsent(t.getOriginVillageId(), id -> villages.findById(id).orElse(null));
                Village b = cache.computeIfAbsent(t.getTargetVillageId(), id -> villages.findById(id).orElse(null));
                if (a == null || b == null) continue;
                shipments.add(new Shipment(t.getId(), outgoing, t.isReturning(), a.getId(), a.getName(), a.getX(), a.getY(),
                        b.getId(), b.getName(), b.getX(), b.getY(),
                        t.isReturning() ? t.getBackWood() : t.getOutWood(), t.isReturning() ? t.getBackClay() : t.getOutClay(),
                        t.isReturning() ? t.getBackIron() : t.getOutIron(), t.getMerchants(), t.getDepartedAt(), t.getArrivesAt()));
            }
        }
        shipments.sort(Comparator.comparing(Shipment::arrivesAt));

        List<VillageMerchants> perVillage = new ArrayList<>();
        for (Village v : mine) perVillage.add(new VillageMerchants(v.getId(), v.getName(), v.getX(), v.getY(), totalMerchants(v), availableMerchants(v)));
        perVillage.sort(Comparator.comparing(VillageMerchants::name).thenComparing(VillageMerchants::villageId));

        return new State(new Merchants(totalMerchants(current), availableMerchants(current), CAPACITY), others, own, shipments, perVillage);
    }

    // ---- cleanup hooks ---------------------------------------------------------------------------------------------

    // Called when a village was conquered or deleted.
    @Transactional
    public void dropVillage(Long villageId) {
        offers.deleteByVillageId(villageId);
        transports.deleteByOriginVillageId(villageId);
    }
}
