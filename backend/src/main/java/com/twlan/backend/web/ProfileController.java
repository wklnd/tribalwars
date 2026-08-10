package com.twlan.backend.web;

import com.twlan.backend.domain.Account;
import com.twlan.backend.service.AuthService;
import com.twlan.backend.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {

    private final ProfileService profiles;
    private final GameFacade gameFacade;
    private final com.twlan.backend.service.StatsService stats;

    public ProfileController(ProfileService profiles, GameFacade gameFacade, com.twlan.backend.service.StatsService stats) {
        this.stats = stats;
        this.profiles = profiles;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    // "me" is the logged-in player.
    @GetMapping("/api/players/{name}")
    public ProfileService.Profile profile(@PathVariable String name) {
        Account viewer = me();
        return profiles.profile("me".equals(name) ? viewer.getUsername() : name, viewer, gameFacade.currentWorld());
    }

    @PutMapping("/api/profile")
    public ProfileService.Profile edit(@RequestBody ProfileService.Edit edit) {
        profiles.edit(me(), edit);
        return profiles.profile(me().getUsername(), me(), gameFacade.currentWorld());
    }

    // range = hour | day | week | all.
    @GetMapping("/api/players/{name}/stats")
    public com.twlan.backend.service.StatsService.Stats stats(@PathVariable String name, @RequestParam(defaultValue = "day") String range) {
        Account viewer = me();
        java.time.Instant from = switch (range) {
            case "hour" -> java.time.Instant.now().minus(java.time.Duration.ofHours(1));
            case "week" -> java.time.Instant.now().minus(java.time.Duration.ofDays(7));
            case "all" -> java.time.Instant.EPOCH;
            default -> java.time.Instant.now().minus(java.time.Duration.ofDays(1));
        };
        return stats.stats("me".equals(name) ? viewer.getUsername() : name, gameFacade.currentWorld(), from);
    }
}
