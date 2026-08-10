package com.twlan.backend.web;

import com.twlan.backend.service.AdminService;
import com.twlan.backend.service.TribeService;
import com.twlan.backend.web.dto.AdminDto.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Access is restricted to admin accounts by WebConfig's interceptor (403 otherwise).
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() { return admin.dashboard(); }

    @PostMapping("/backup")
    public Backup backup() { return admin.backup(); }

    @GetMapping("/catalog")
    public Catalog catalog() { return admin.catalog(); }

    // worlds
    @GetMapping("/worlds")
    public List<WorldRow> worlds() { return admin.listWorlds(); }

    @PostMapping("/worlds")
    public WorldRow createWorld(@RequestBody WorldRequest request) { return admin.createWorld(request); }

    @PutMapping("/worlds/{id}")
    public WorldRow updateWorld(@PathVariable Long id, @RequestBody WorldRequest request) { return admin.updateWorld(id, request); }

    @DeleteMapping("/worlds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorld(@PathVariable Long id) { admin.deleteWorld(id); }

    // Re-places every village of the world into one dense disc.
    @PostMapping("/worlds/{id}/compact")
    public com.twlan.backend.service.AdminService.CompactResult compactWorld(@PathVariable Long id) { return admin.compactWorld(id); }

    // players / NPCs
    @GetMapping("/worlds/{id}/players")
    public List<Player> players(@PathVariable Long id) { return admin.players(id); }

    @PostMapping("/worlds/{id}/players/{accountId}/remove")
    public RemovePlayerResult removePlayer(@PathVariable Long id, @PathVariable Long accountId, @RequestBody RemovePlayerRequest request) {
        return admin.removePlayer(id, accountId, request);
    }

    @PostMapping("/worlds/{id}/npcs")
    public NpcResult createNpcs(@PathVariable Long id, @RequestBody NpcRequest request) { return admin.createNpcs(id, request); }

    // tribes
    @GetMapping("/worlds/{id}/tribes")
    public List<TribeService.AdminTribe> tribes(@PathVariable Long id) { return admin.tribes(id); }

    @PostMapping("/worlds/{id}/tribes/{tribeId}/disband")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disbandTribe(@PathVariable Long id, @PathVariable Long tribeId) { admin.disbandTribe(id, tribeId); }

    public record MemberRequest(Long accountId) {}

    @PostMapping("/worlds/{id}/tribes/{tribeId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTribeMember(@PathVariable Long id, @PathVariable Long tribeId, @RequestBody MemberRequest request) {
        admin.addTribeMember(id, tribeId, request.accountId());
    }

    @DeleteMapping("/worlds/{id}/tribes/members/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTribeMember(@PathVariable Long id, @PathVariable Long accountId) { admin.removeTribeMember(id, accountId); }

    // villages
    @GetMapping("/worlds/{id}/villages")
    public List<VillageRow> villages(@PathVariable Long id) { return admin.villages(id); }

    @PostMapping("/worlds/{id}/barbarians")
    public BarbarianResult createBarbarians(@PathVariable Long id, @RequestBody BarbarianRequest request) {
        return admin.createBarbarians(id, request);
    }

    // Random but rule-abiding villages (no Academy without Smithy 20, ...).
    @PostMapping("/worlds/{id}/barbarians/random")
    public RandomBarbarianResult createRandomBarbarians(@PathVariable Long id, @RequestBody RandomBarbarianRequest request) {
        return admin.createRandomBarbarians(id, request);
    }

    @PostMapping("/random-village-preview")
    public RandomLayout previewRandom(@RequestBody RandomPreviewRequest request) { return admin.previewRandom(request.development()); }

    @GetMapping("/villages/{id}")
    public VillageDetail village(@PathVariable Long id) { return admin.village(id); }

    @PutMapping("/villages/{id}")
    public VillageDetail updateVillage(@PathVariable Long id, @RequestBody VillageUpdate request) { return admin.updateVillage(id, request); }

    @PostMapping("/villages/{id}/finish-queues")
    public FinishResult finishQueues(@PathVariable Long id) { return admin.finishQueues(id); }

    @DeleteMapping("/villages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVillage(@PathVariable Long id) { admin.deleteVillage(id); }

    // accounts
    @GetMapping("/accounts")
    public List<AccountRow> accounts() { return admin.accounts(); }

    @PostMapping("/accounts/{id}/admin")
    public AccountRow setAdmin(@PathVariable Long id, @RequestBody AdminFlag flag) {
        return admin.setAdmin(id, flag.admin(), AccountContext.get());
    }

    @PostMapping("/accounts/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable Long id, @RequestBody PasswordRequest request) { admin.setPassword(id, request.password()); }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable Long id) { admin.deleteAccount(id, AccountContext.get()); }
}
