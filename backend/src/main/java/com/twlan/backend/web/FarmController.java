package com.twlan.backend.web;

import com.twlan.backend.domain.Account;
import com.twlan.backend.service.AuthService;
import com.twlan.backend.service.FarmService;
import org.springframework.web.bind.annotation.*;

@RestController
public class FarmController {

    // config is the JSON text the frontend saved; null when nothing has been saved yet.
    public record FarmConfigDto(String config) {}

    private final FarmService farm;
    private final GameFacade gameFacade;

    public FarmController(FarmService farm, GameFacade gameFacade) {
        this.farm = farm;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    @GetMapping("/api/farm")
    public FarmConfigDto get() {
        return new FarmConfigDto(farm.get(me(), gameFacade.currentWorld()).orElse(null));
    }

    @PutMapping("/api/farm")
    public FarmConfigDto save(@RequestBody FarmConfigDto body) {
        farm.save(me(), gameFacade.currentWorld(), body.config());
        return body;
    }
}
