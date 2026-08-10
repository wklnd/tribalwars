package com.twlan.backend.service;

import com.twlan.backend.domain.*;
import com.twlan.backend.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twlan.backend.service.npc.NpcRhythm;
import java.time.Instant;
import java.util.*;

// NPC traders. Villages of NPC players that have a market post offers from their surplus (which expire after a while) and
// now and then a fair offer of a real player is filled by an NPC village whose merchants bring the goods. Driven by
// NpcService.tick; switched off per world with the npcMarket setting. Everything goes through
// MarketService, so the NPCs obey the same rules as players.
@Service
public class NpcMarketService {

    // Threshold at which an offer is considered fair enough to fill: the offerer must get at least this much of what they give away.
    static final double FAIR_RATIO = 0.85;
    private static final Resource[] RESOURCES = Resource.values();

    private final MarketService market;
    private final MarketOfferRepository offers;
    private final TransportRepository transports;
    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final VillageService villageService;
    private final GameSettings settings;
    private final NpcRhythm rhythm;
    private final Random rnd = new Random();

    public NpcMarketService(MarketService market, MarketOfferRepository offers, TransportRepository transports, AccountRepository accounts,
                            VillageRepository villages, VillageService villageService, GameSettings settings, NpcRhythm rhythm) {
        this.rhythm = rhythm;
        this.market = market;
        this.offers = offers;
        this.transports = transports;
        this.accounts = accounts;
        this.villages = villages;
        this.villageService = villageService;
        this.settings = settings;
    }

    @Transactional
    public void tick() {
        List<Village> npcVillages = new ArrayList<>();
        for (Village v : villages.findByOwnerType(OwnerType.PLAYER)) { // one query (a findByOwner per NPC took seconds with hundreds of NPCs)
            if (v.getOwner() != null && v.getOwner().isNpc()) npcVillages.add(v);
        }
        for (Village v : npcVillages) {
            if (v.getWorld() == null || !WorldSettings.bool(v.getWorld(), "npcMarket")) continue;
            if (!rhythm.isOnline(v.getOwner().getId(), v.getWorld())) continue; // only while the NPC is logged in
            if (rnd.nextDouble() < 0.06 && market.totalMerchants(v) > 0) { // (cheap draw first: totalMerchants queries the market level)
                try {
                    post(v);
                } catch (MarketService.MarketException ignored) {
                    // nothing to trade right now
                }
            }
        }
        fillPlayerOffers(npcVillages);
    }

    private void post(Village v) {
        if (offers.findByVillageId(v.getId()).size() >= 2) return;
        villageService.settleResources(v);
        Resource sell = RESOURCES[0], buy = RESOURCES[0];
        double most = -1, least = Double.MAX_VALUE;
        for (Resource r : RESOURCES) {
            double have = have(v, r);
            if (have > most) { most = have; sell = r; }
            if (have < least) { least = have; buy = r; }
        }
        int cap = villageService.warehouseCapacity(v);
        if (sell == buy || most < Math.max(400, cap * 0.3)) return;
        int sellAmount = (int) Math.max(100, Math.round(Math.min(1000, most * 0.35) * (0.4 + rnd.nextDouble() * 0.6) / 10) * 10);
        int buyAmount = (int) Math.max(10, Math.round(sellAmount * (0.75 + rnd.nextDouble() * 0.5) / 10) * 10);
        int times = 1 + rnd.nextInt(3);
        while (times > 1 && sellAmount * times > most * 0.8) times--;
        if (sellAmount * times > most) return;
        MarketOffer o = market.createOffer(v, sell, sellAmount, buy, buyAmount, times, 0);
        o.setExpiresAt(Instant.now().plusSeconds(45 * 60 + rnd.nextInt(75 * 60)));
        offers.save(o);
    }

    // A village short of "need" (for a building it is saving for) asks the market: accepts an open offer that gives
    // that resource for one it has plenty of, or posts such an offer itself. Returns what it did, or null if nothing.
    @Transactional(noRollbackFor = MarketService.MarketException.class)
    public String tradeFor(Village v, Resource need, int missing) {
        if (v.getWorld() == null || !WorldSettings.bool(v.getWorld(), "npcMarket") || market.totalMerchants(v) == 0 || missing < 20) return null;
        int cap = villageService.warehouseCapacity(v);
        Resource give = null;
        double spare = 0;
        for (Resource r : RESOURCES) {
            if (r == need) continue;
            double s = have(v, r) - Math.max(cap * 0.35, missing);
            if (s > spare) { spare = s; give = r; }
        }
        if (give == null || spare < 100) return null;
        try {
            for (MarketOffer o : offers.findByWorldId(v.getWorld().getId())) {
                if (o.getSellResource() != need || o.getBuyResource() != give || o.getVillageId().equals(v.getId())) continue;
                if ((double) o.getSellAmount() / o.getBuyAmount() < FAIR_RATIO) continue;
                int times = Math.min(o.getRemaining(), Math.max(1, (int) Math.ceil((double) missing / o.getSellAmount())));
                while (times > 1 && (double) o.getBuyAmount() * times > spare) times--;
                if ((double) o.getBuyAmount() * times > spare) continue;
                market.accept(v, o.getId(), times);
                return "accepted an offer: " + o.getBuyAmount() * times + " " + give + " for " + o.getSellAmount() * times + " " + need;
            }
            if (offers.findByVillageId(v.getId()).size() >= 2) return null;
            int sellAmount = (int) Math.max(100, Math.min(1000, Math.round(Math.min(spare, missing * 1.1) / 10) * 10));
            if (sellAmount > spare) return null;
            int buyAmount = Math.max(10, (int) (Math.round(sellAmount * (0.85 + rnd.nextDouble() * 0.1) / 10) * 10));
            MarketOffer o = market.createOffer(v, give, sellAmount, need, buyAmount, 1, 0);
            o.setExpiresAt(Instant.now().plusSeconds(60 * 60 + rnd.nextInt(60 * 60)));
            offers.save(o);
            return "posted an offer: " + sellAmount + " " + give + " for " + buyAmount + " " + need;
        } catch (MarketService.MarketException e) {
            return null;
        }
    }

    private static double have(Village v, Resource r) {
        return switch (r) { case WOOD -> v.getWood(); case CLAY -> v.getClay(); case IRON -> v.getIron(); };
    }

    // A real player's offer that is at least fair gets filled after a short while by a merchant caravan of an NPC village.
    private void fillPlayerOffers(List<Village> npcVillages) {
        if (npcVillages.isEmpty()) return;
        Instant tooFresh = Instant.now().minusSeconds(20);
        for (MarketOffer o : offers.findAll()) {
            if (o.getCreatedAt().isAfter(tooFresh) || rnd.nextDouble() > 0.15) continue;
            if ((double) o.getBuyAmount() / o.getSellAmount() < FAIR_RATIO) continue;
            Village seller = villages.findById(o.getVillageId()).orElse(null);
            if (seller == null || seller.getOwner() == null || seller.getOwner().isNpc() || seller.getWorld() == null) continue;
            if (!WorldSettings.bool(seller.getWorld(), "npcMarket")) continue;
            List<Village> near = new ArrayList<>();
            for (Village n : npcVillages) {
                if (n.getWorld() != null && n.getWorld().getId().equals(seller.getWorld().getId()) && rhythm.isOnline(n.getOwner().getId(), n.getWorld())
                        && (o.getMaxDistance() == 0 || MarketService.distance(n, seller) <= o.getMaxDistance())) near.add(n);
            }
            if (near.isEmpty()) continue;
            Village from = near.get(rnd.nextInt(near.size()));
            market.notifyMarket(seller); // (its offer was taken)
            int pay = o.getBuyAmount() * o.getRemaining(); // what the offerer asked for, brought by the NPC's merchants
            Transport t = new Transport();
            t.setWorldId(o.getWorldId());
            t.setOriginVillageId(from.getId());
            t.setTargetVillageId(seller.getId());
            MarketService.addOut(t, o.getBuyResource(), pay);
            t.setMerchants(MarketService.merchantsNeeded(pay));
            Instant now = Instant.now();
            t.setDepartedAt(now);
            t.setArrivesAt(now.plusSeconds(Math.max(1, Math.round(MarketService.distance(from, seller) * MarketService.MINUTES_PER_FIELD * 60 / settings.speedOf(from)))));
            transports.save(t);
            offers.delete(o);
        }
    }
}
