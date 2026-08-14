package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.*;
import se.oscarwiklund.twlan2.backend.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// The Academy's coin system (world "noblemanSystem": coins). Gold coins are minted with resources; the n-th nobleman
// needs n coins in total (1, 3, 6, ... coins for a limit of 1, 2, 3, ...). Every nobleman alive, in training or already
// spent conquering a village counts against the limit.
@Service
public class NobleService {

    public record Info(int coins, int limit, int existing, int inProduction, int conquered, int possible,
                       int coinsMissing, int coinsAlready, int costWood, int costClay, int costIron) {}

    public static class NobleException extends RuntimeException {
        public NobleException(String message) { super(message); }
    }

    private final NobleCoinsRepository coinRepository;
    private final VillageRepository villages;
    private final UnitStockRepository unitStock;
    private final TrainQueueItemRepository trainQueue;
    private final MovementRepository movements;
    private final VillageService villageService;

    public NobleService(NobleCoinsRepository coinRepository, VillageRepository villages, UnitStockRepository unitStock,
                        TrainQueueItemRepository trainQueue, MovementRepository movements, VillageService villageService) {
        this.coinRepository = coinRepository;
        this.villages = villages;
        this.unitStock = unitStock;
        this.trainQueue = trainQueue;
        this.movements = movements;
        this.villageService = villageService;
    }

    static int coinsForLimit(int n) { return n * (n + 1) / 2; }

    static int limitFor(int coins) {
        int n = 0;
        while (coinsForLimit(n + 1) <= coins) n++;
        return n;
    }

    private NobleCoins row(Account account, World world) {
        return coinRepository.findByAccountIdAndWorldId(account.getId(), world.getId()).orElseGet(() -> {
            NobleCoins c = new NobleCoins();
            c.setAccountId(account.getId());
            c.setWorldId(world.getId());
            return c;
        });
    }

    @Transactional(readOnly = true)
    public Info info(Account account, World world) {
        NobleCoins c = row(account, world);
        int limit = limitFor(c.getCoins());
        int existing = 0;
        int inProduction = 0;
        List<Village> owned = villages.findByWorldAndOwner(world, account);
        for (Village v : owned) {
            existing += unitStock.findByVillageAndType(v, UnitType.SNOB).map(UnitStock::getCount).orElse(0);
            for (TrainQueueItem t : trainQueue.findByVillageOrderByPositionAsc(v)) {
                if (t.getType() == UnitType.SNOB) inProduction += t.getTotalCount() - t.getProducedCount();
            }
            for (Movement m : movements.findByOriginVillageOrTargetVillage(v, v)) {
                if (m.getOriginVillage().getId().equals(v.getId()) && (m.getType() == MovementType.ATTACK || m.getType() == MovementType.SUPPORT)
                        || m.getTargetVillage().getId().equals(v.getId()) && m.getType() == MovementType.RETURN) {
                    existing += m.getUnits().getOrDefault(UnitType.SNOB, 0);
                }
            }
        }
        int possible = Math.max(0, limit - existing - inProduction - c.getConquered());
        return new Info(c.getCoins(), limit, existing, inProduction, c.getConquered(), possible,
                coinsForLimit(limit + 1) - c.getCoins(), c.getCoins() - coinsForLimit(limit),
                (int) WorldSettings.number(world, "coinWood"), (int) WorldSettings.number(world, "coinClay"), (int) WorldSettings.number(world, "coinIron"));
    }

    @Transactional(noRollbackFor = NobleException.class)
    public void mint(Village village) {
        Account account = village.getOwner();
        World world = village.getWorld();
        if (account == null || world == null) throw new NobleException("This village has no owner.");
        if ("off".equals(WorldSettings.get(world, "noblemanSystem"))) throw new NobleException("Noblemen are disabled on this world");
        if (villageService.levelOf(village, BuildingType.ACADEMY) < 1) throw new NobleException("Requires an Academy to mint gold coins");
        villageService.settleResources(village);
        double wood = WorldSettings.number(world, "coinWood");
        double clay = WorldSettings.number(world, "coinClay");
        double iron = WorldSettings.number(world, "coinIron");
        if (Math.max(wood, Math.max(clay, iron)) > villageService.warehouseCapacity(village)) throw new NobleException("The warehouse is too small");
        if (village.getWood() < wood || village.getClay() < clay || village.getIron() < iron) throw new NobleException("Not enough resources to mint a gold coin");
        village.setWood(village.getWood() - wood);
        village.setClay(village.getClay() - clay);
        village.setIron(village.getIron() - iron);
        villages.save(village);
        NobleCoins c = row(account, world);
        c.setCoins(c.getCoins() + 1);
        coinRepository.save(c);
    }

    public void checkCanEducate(Village village, int count) {
        if ("off".equals(WorldSettings.get(village.getWorld(), "noblemanSystem"))) throw new TrainService.TrainException("Noblemen are disabled on this world");
        if (villageService.levelOf(village, BuildingType.ACADEMY) < 1) throw new TrainService.TrainException("Requires an Academy to educate noblemen");
        if (village.getOwner() == null) throw new TrainService.TrainException("This village has no owner.");
        if (info(village.getOwner(), village.getWorld()).possible() < count) throw new TrainService.TrainException("No more noblemen can be produced");
    }

    // Called when a village was conquered; it now counts against the conqueror's noblemen limit.
    @Transactional
    public void addConquest(Account account, World world) {
        NobleCoins c = row(account, world);
        c.setConquered(c.getConquered() + 1);
        coinRepository.save(c);
    }

    @Transactional
    public void deleteWorldData(Long worldId) { coinRepository.deleteByWorldId(worldId); }

    @Transactional
    public void deleteAccountData(Long accountId) { coinRepository.deleteByAccountId(accountId); }
}
