package com.twlan.backend.web;

import com.twlan.backend.domain.BuildingType;
import com.twlan.backend.domain.Village;
import com.twlan.backend.service.BuildService;
import com.twlan.backend.web.dto.BuildRequest;
import com.twlan.backend.web.dto.VillageStateDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildController {

    private final BuildService buildService;
    private final GameFacade gameFacade;

    public BuildController(BuildService buildService, GameFacade gameFacade) {
        this.buildService = buildService;
        this.gameFacade = gameFacade;
    }

    @PostMapping("/api/village/build")
    public VillageStateDto build(@RequestBody BuildRequest request) {
        Village village = gameFacade.playerHomeVillage();
        BuildingType type = BuildingType.valueOf(request.type());
        buildService.enqueue(village, type);
        return gameFacade.toDto(village);
    }

    @PostMapping("/api/village/build/{id}/finish")
    public VillageStateDto finish(@PathVariable Long id) {
        Village village = gameFacade.playerHomeVillage();
        buildService.finishFree(village, id);
        return gameFacade.toDto(village);
    }

    @DeleteMapping("/api/village/build/{id}")
    public VillageStateDto cancel(@PathVariable Long id) {
        Village village = gameFacade.playerHomeVillage();
        buildService.cancel(village, id);
        return gameFacade.toDto(village);
    }
}
