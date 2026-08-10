package com.twlan.backend.web;

import com.twlan.backend.domain.UnitType;
import com.twlan.backend.domain.Village;
import com.twlan.backend.repo.VillageRepository;
import com.twlan.backend.service.MovementService;
import com.twlan.backend.service.SupportService;
import com.twlan.backend.web.dto.AttackRequest;
import com.twlan.backend.web.dto.ReportDto;
import com.twlan.backend.web.dto.VillageStateDto;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
public class AttackController {

    private final MovementService movementService;
    private final GameFacade gameFacade;
    private final VillageRepository villageRepository;
    private final SupportService supportService;

    public AttackController(MovementService movementService, GameFacade gameFacade, VillageRepository villageRepository,
                            SupportService supportService) {
        this.supportService = supportService;
        this.movementService = movementService;
        this.gameFacade = gameFacade;
        this.villageRepository = villageRepository;
    }

    @PostMapping("/api/village/attack")
    public VillageStateDto attack(@RequestBody AttackRequest request) {
        return send(request, false);
    }

    // Sends troops to another village of the player.
    @PostMapping("/api/village/support")
    public VillageStateDto support(@RequestBody AttackRequest request) {
        return send(request, true);
    }

    public record WithdrawRequest(Long armyId, Map<String, Integer> units) {}

    // The owner takes troops (all when units is missing) back from a tribe-mate's village.
    @PostMapping("/api/village/support/withdraw")
    public VillageStateDto withdraw(@RequestBody WithdrawRequest request) {
        Map<UnitType, Integer> units = null;
        if (request.units() != null) {
            units = new EnumMap<>(UnitType.class);
            for (var e : request.units().entrySet()) units.put(UnitType.valueOf(e.getKey()), e.getValue());
        }
        supportService.withdraw(com.twlan.backend.web.AccountContext.get(), request.armyId(), units);
        return gameFacade.toDto(gameFacade.playerHomeVillage());
    }

    public record SendBackRequest(Long armyId) {}

    // The host sends a guest army back home.
    @PostMapping("/api/village/support/sendback")
    public VillageStateDto sendBack(@RequestBody SendBackRequest request) {
        supportService.sendBack(com.twlan.backend.web.AccountContext.get(), request.armyId());
        return gameFacade.toDto(gameFacade.playerHomeVillage());
    }

    private VillageStateDto send(AttackRequest request, boolean support) {
        Village origin = gameFacade.playerHomeVillage();
        Village target = villageRepository.findById(request.targetVillageId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown target village"));

        if (target.getWorld() == null || !target.getWorld().getId().equals(origin.getWorld().getId())) {
            throw new IllegalArgumentException("Target village is in another world");
        }
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        if (request.units() != null) {
            request.units().forEach((type, count) -> units.put(UnitType.valueOf(type), count));
        }

        if (support) movementService.sendSupport(origin, target, units);
        else movementService.sendAttack(origin, target, units);
        return gameFacade.toDto(origin);
    }

    @GetMapping("/api/reports")
    public List<ReportDto> reports() {
        return gameFacade.listReports();
    }
}
