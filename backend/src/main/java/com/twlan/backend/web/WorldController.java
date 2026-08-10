package com.twlan.backend.web;

import com.twlan.backend.domain.World;
import com.twlan.backend.repo.WorldRepository;
import com.twlan.backend.service.WorldService;
import com.twlan.backend.service.WorldSettings;
import com.twlan.backend.web.dto.CreateWorldRequest;
import com.twlan.backend.web.dto.WorldDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WorldController {

    private final WorldRepository worldRepository;
    private final WorldService worldService;

    public WorldController(WorldRepository worldRepository, WorldService worldService) {
        this.worldRepository = worldRepository;
        this.worldService = worldService;
    }

    @GetMapping("/api/worlds")
    public List<WorldDto> worlds() {
        return worldRepository.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    // Also creates the caller's start village and a barbarian village in it.
    @PostMapping("/api/worlds")
    public WorldDto create(@RequestBody CreateWorldRequest request) {
        return toDto(worldService.createWorld(request.name(), request.speed()));
    }

    // Like the original's "Do you want to join ...?" page.
    @PostMapping("/api/worlds/{id}/join")
    public WorldDto join(@PathVariable Long id) {
        World world = worldRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown world " + id));
        worldService.joinWorld(world, AccountContext.get());
        return toDto(world);
    }

    private WorldDto toDto(World w) {
        var account = AccountContext.get();
        return new WorldDto(w.getId(), w.getName(), w.getSpeed(), w.getCreatedAt(),
                account != null && worldService.hasVillage(w, account), WorldSettings.get(w, "description"));
    }
}
