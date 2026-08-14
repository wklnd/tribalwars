package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.service.AchievementService;
import se.oscarwiklund.twlan2.backend.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AchievementController {

    private final AchievementService achievements;
    private final GameFacade gameFacade;

    public AchievementController(AchievementService achievements, GameFacade gameFacade) {
        this.achievements = achievements;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    @GetMapping("/api/achievements")
    public List<AchievementService.Entry> list() {
        World world = gameFacade.currentWorld();
        achievements.evaluate(me(), world);
        return achievements.list(me(), world);
    }

    // Newly earned levels the browser has not shown yet; asking marks them as shown.
    @PostMapping("/api/achievements/unseen")
    public List<AchievementService.Unseen> unseen() {
        return achievements.takeUnseen(me(), gameFacade.currentWorld());
    }

    @GetMapping("/api/achievements/ranking")
    public List<AchievementService.Standing> ranking() {
        return achievements.ranking(gameFacade.currentWorld());
    }
}
