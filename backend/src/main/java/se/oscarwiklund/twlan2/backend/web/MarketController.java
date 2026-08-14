package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.Resource;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import se.oscarwiklund.twlan2.backend.service.AuthService;
import se.oscarwiklund.twlan2.backend.service.MarketService;
import se.oscarwiklund.twlan2.backend.web.dto.MarketDto.State;
import org.springframework.web.bind.annotation.*;

// Every call answers with the fresh market state of the viewer's current village.
@RestController
public class MarketController {

    private final MarketService market;
    private final GameFacade gameFacade;
    private final VillageRepository villages;

    public MarketController(MarketService market, GameFacade gameFacade, VillageRepository villages) {
        this.market = market;
        this.gameFacade = gameFacade;
        this.villages = villages;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    private State state() {
        Village current = gameFacade.playerHomeVillage();
        return market.state(me(), gameFacade.currentWorld(), current);
    }

    public record SendRequest(Long targetVillageId, Integer wood, Integer clay, Integer iron) {}
    public record OfferRequest(String sellResource, Integer sellAmount, String buyResource, Integer buyAmount, Integer times, Integer maxDistance) {}
    public record AcceptRequest(Integer times) {}

    private static int n(Integer v) { return v == null ? 0 : v; }

    private static Resource resource(String name) {
        try {
            return Resource.valueOf(name == null ? "" : name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new MarketService.MarketException("Unknown resource");
        }
    }

    @GetMapping("/api/market")
    public State get() { return state(); }

    @PostMapping("/api/market/send")
    public State send(@RequestBody SendRequest request) {
        Village origin = gameFacade.playerHomeVillage();
        Village target = request.targetVillageId() == null ? null : villages.findById(request.targetVillageId()).orElse(null);
        market.send(origin, target, n(request.wood()), n(request.clay()), n(request.iron()));
        return state();
    }

    @PostMapping("/api/market/offers")
    public State createOffer(@RequestBody OfferRequest r) {
        market.createOffer(gameFacade.playerHomeVillage(), resource(r.sellResource()), n(r.sellAmount()), resource(r.buyResource()), n(r.buyAmount()),
                r.times() == null ? 1 : r.times(), n(r.maxDistance()));
        return state();
    }

    @DeleteMapping("/api/market/offers/{id}")
    public State cancelOffer(@PathVariable Long id) {
        market.cancelOffer(me(), id);
        return state();
    }

    @PostMapping("/api/market/offers/{id}/accept")
    public State accept(@PathVariable Long id, @RequestBody(required = false) AcceptRequest request) {
        market.accept(gameFacade.playerHomeVillage(), id, request == null || request.times() == null ? 1 : request.times());
        return state();
    }
}
