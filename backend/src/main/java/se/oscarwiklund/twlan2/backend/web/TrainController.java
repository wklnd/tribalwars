package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.UnitType;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.domain.PaladinProfile;
import se.oscarwiklund.twlan2.backend.repo.PaladinProfileRepository;
import se.oscarwiklund.twlan2.backend.service.NobleService;
import se.oscarwiklund.twlan2.backend.service.ResearchService;
import se.oscarwiklund.twlan2.backend.service.TrainService;
import se.oscarwiklund.twlan2.backend.web.dto.TrainRequest;
import se.oscarwiklund.twlan2.backend.web.dto.VillageStateDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainController {

    private final TrainService trainService;
    private final GameFacade gameFacade;
    private final PaladinProfileRepository paladinProfileRepository;
    private final NobleService nobleService;
    private final ResearchService researchService;

    public TrainController(TrainService trainService, GameFacade gameFacade, PaladinProfileRepository paladinProfileRepository,
                           NobleService nobleService, ResearchService researchService) {
        this.researchService = researchService;
        this.nobleService = nobleService;
        this.paladinProfileRepository = paladinProfileRepository;
        this.trainService = trainService;
        this.gameFacade = gameFacade;
    }

    @PostMapping("/api/village/train")
    public VillageStateDto train(@RequestBody TrainRequest request) {
        Village village = gameFacade.playerHomeVillage();
        UnitType type = UnitType.valueOf(request.type());
        trainService.enqueue(village, type, request.count());
        return gameFacade.toDto(village);
    }

    @PostMapping("/api/village/decommission")
    public VillageStateDto decommission(@RequestBody TrainRequest request) {
        Village village = gameFacade.playerHomeVillage();
        UnitType type = UnitType.valueOf(request.type());
        trainService.decommission(village, type, request.count());
        return gameFacade.toDto(village);
    }

    public record ResearchRequest(String type) {}

    @PostMapping("/api/village/research")
    public VillageStateDto research(@RequestBody ResearchRequest request) {
        Village village = gameFacade.playerHomeVillage();
        researchService.start(village, UnitType.valueOf(request.type()));
        return gameFacade.toDto(village);
    }

    @DeleteMapping("/api/village/research/{id}")
    public VillageStateDto cancelResearch(@PathVariable Long id) {
        Village village = gameFacade.playerHomeVillage();
        researchService.cancel(village, id);
        return gameFacade.toDto(village);
    }

    public record CoinRequest(Long villageId, Integer count) {}

    // count defaults to 1; villageId may name another village of the player, not just the current one.
    @PostMapping("/api/village/coin")
    public VillageStateDto mintCoin(@RequestBody(required = false) CoinRequest request) {
        Village current = gameFacade.playerHomeVillage();
        Village village = current;
        if (request != null && request.villageId() != null && !request.villageId().equals(current.getId())) {
            village = gameFacade.ownVillage(request.villageId());
        }
        int count = request == null || request.count() == null ? 1 : request.count();
        if (count < 1 || count > 100) throw new IllegalArgumentException("Choose between 1 and 100 gold coins");
        for (int i = 0; i < count; i++) nobleService.mint(village);
        return gameFacade.toDto(current);
    }

    @DeleteMapping("/api/village/train/{id}")
    public VillageStateDto cancel(@PathVariable Long id) {
        Village village = gameFacade.playerHomeVillage();
        trainService.cancel(village, id);
        return gameFacade.toDto(village);
    }

    public record PaladinNameRequest(String name) {}

    @PutMapping("/api/village/paladin-name")
    public VillageStateDto renamePaladin(@RequestBody PaladinNameRequest request) {
        Village village = gameFacade.playerHomeVillage();
        String name = request.name() == null ? "" : request.name().trim();
        if (name.length() < 3 || name.length() > 50 || name.matches(".*[<>\"&].*")) {
            throw new IllegalArgumentException("A paladin's name must be between 3 and 50 characters long and may not contain < > & or quotes.");
        }
        PaladinProfile profile = paladinProfileRepository.findById(village.getOwner().getId()).orElseGet(() -> {
            PaladinProfile p = new PaladinProfile();
            p.setAccountId(village.getOwner().getId());
            return p;
        });
        profile.setName(name);
        paladinProfileRepository.save(profile);
        return gameFacade.toDto(village);
    }
}
