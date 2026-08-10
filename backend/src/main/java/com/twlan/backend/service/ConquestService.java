package com.twlan.backend.service;

import com.twlan.backend.live.LiveUpdates;
import com.twlan.backend.domain.*;
import com.twlan.backend.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// Triggers when a village's loyalty is driven to zero.
@Service
public class ConquestService {

    public static final double LOYALTY_AFTER_CONQUEST = 25;

    private final LiveUpdates live;
    private final VillageRepository villages;
    private final BuildQueueItemRepository buildQueue;
    private final TrainQueueItemRepository trainQueue;
    private final UnitStockRepository unitStock;
    private final MovementRepository movements;
    private final VillageService villageService;
    private final BuildingRepository buildings;
    private final NobleService nobles;
    private final SupportService support;
    private final GroupService groups;
    private final MarketService market;
    private final com.twlan.backend.service.npc.NpcIntelService npcIntel;

    public ConquestService(LiveUpdates live, VillageRepository villages, BuildQueueItemRepository buildQueue, TrainQueueItemRepository trainQueue,
                           UnitStockRepository unitStock, MovementRepository movements, VillageService villageService, NobleService nobles,
                           BuildingRepository buildings, SupportService support, GroupService groups, MarketService market,
                           com.twlan.backend.service.npc.NpcIntelService npcIntel) {
        this.live = live;
        this.market = market;
        this.support = support;
        this.groups = groups;
        this.buildings = buildings;
        this.villages = villages;
        this.buildQueue = buildQueue;
        this.trainQueue = trainQueue;
        this.unitStock = unitStock;
        this.movements = movements;
        this.villageService = villageService;
        this.npcIntel = npcIntel;
        this.nobles = nobles;
    }

    // Given to a village with no buildings, so every conquered village is at least as developed as a freshly founded one.
    private static final Map<BuildingType, Integer> STARTER_BASE = Map.of(
            BuildingType.HEADQUARTERS, 1, BuildingType.TIMBER_CAMP, 2, BuildingType.CLAY_PIT, 2, BuildingType.IRON_MINE, 2,
            BuildingType.FARM, 1, BuildingType.WAREHOUSE, 1);

    // Buildings and stored resources stay, but the previous owner's orders, garrison and troops still out from it
    // are gone. It counts against the conqueror's noblemen limit.
    @Transactional
    public void conquer(Village village, Account conqueror) {
        villageService.settleResources(village);
        groups.dropVillage(village.getId()); // the old owner's groups lose it
        market.dropVillage(village.getId()); // its offers and merchants on the road are gone
        npcIntel.dropVillage(village.getId()); // what NPCs knew about it no longer holds
        support.villageConquered(village); // guests go home, the old owner's stationed armies are lost with it
        buildQueue.deleteAll(buildQueue.findByVillageOrderByPositionAsc(village));
        trainQueue.deleteAll(trainQueue.findByVillageOrderByPositionAsc(village));
        unitStock.deleteAll(unitStock.findByVillage(village));
        List<Movement> own = movements.findByOriginVillageOrTargetVillage(village, village);
        for (Movement m : own) {
            if (m.getOriginVillage().getId().equals(village.getId())) movements.delete(m); // troops of the old owner that were away
        }
        STARTER_BASE.forEach((type, level) -> {
            Building b = buildings.findByVillageAndType(village, type).orElseGet(() -> {
                Building n = new Building();
                n.setVillage(village);
                n.setType(type);
                n.setLevel(0);
                return n;
            });
            if (b.getLevel() < level) {
                b.setLevel(level);
                buildings.save(b);
            }
        });
        Account previous = village.getOwner();
        village.setOwner(conqueror);
        village.setOwnerType(OwnerType.PLAYER);
        villageService.setLoyalty(village, LOYALTY_AFTER_CONQUEST);
        villages.save(village);
        if (village.getWorld() != null) {
            live.toWorld(village.getWorld().getId(), LiveUpdates.MAP);
            live.toAccount(previous, village.getWorld().getId(), LiveUpdates.VILLAGE);
            live.toAccount(conqueror, village.getWorld().getId(), LiveUpdates.VILLAGE);
        }
        nobles.addConquest(conqueror, village.getWorld());
    }
}
