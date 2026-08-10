package com.twlan.backend.web;

import com.twlan.backend.domain.Account;
import com.twlan.backend.service.AuthService;
import com.twlan.backend.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RankingController {

    private final RankingService ranking;
    private final GameFacade gameFacade;

    public RankingController(RankingService ranking, GameFacade gameFacade) {
        this.ranking = ranking;
        this.gameFacade = gameFacade;
    }

    private void requireLogin() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
    }

    // sort = points (default) | kills; continent = map continent number (45 = K45), omitted = whole world.
    @GetMapping("/api/ranking/players")
    public RankingService.Ranking<RankingService.PlayerRow> players(@RequestParam(defaultValue = "points") String sort,
                                                                    @RequestParam(required = false) Integer continent) {
        requireLogin();
        return ranking.players(gameFacade.currentWorld(), continent, "kills".equals(sort));
    }

    @GetMapping("/api/ranking/tribes")
    public RankingService.Ranking<RankingService.TribeRow> tribes(@RequestParam(defaultValue = "points") String sort,
                                                                  @RequestParam(required = false) Integer continent) {
        requireLogin();
        return ranking.tribes(gameFacade.currentWorld(), continent, "kills".equals(sort));
    }
}
