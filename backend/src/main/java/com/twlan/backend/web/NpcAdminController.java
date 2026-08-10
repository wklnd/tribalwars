package com.twlan.backend.web;

import com.twlan.backend.service.npc.NpcAdminService;
import org.springframework.web.bind.annotation.*;

// Restricted to admin accounts like everything under /api/admin.
@RestController
@RequestMapping("/api/admin")
public class NpcAdminController {

    private final NpcAdminService npcs;

    public NpcAdminController(NpcAdminService npcs) {
        this.npcs = npcs;
    }

    public record ProfileRequest(String archetype, Integer skill) {}

    @GetMapping("/worlds/{id}/npcs")
    public NpcAdminService.NpcList list(@PathVariable Long id) { return npcs.list(id); }

    @PutMapping("/npcs/{accountId}/profile")
    public NpcAdminService.NpcRow update(@PathVariable Long accountId, @RequestBody ProfileRequest request) {
        return npcs.update(accountId, request.archetype(), request.skill());
    }

    @GetMapping("/worlds/{id}/npc-log")
    public NpcAdminService.LogPage log(@PathVariable Long id, @RequestParam(required = false) String kind,
                                       @RequestParam(required = false) Long npc, @RequestParam(defaultValue = "100") int limit) {
        return npcs.log(id, kind, npc, limit);
    }
}
