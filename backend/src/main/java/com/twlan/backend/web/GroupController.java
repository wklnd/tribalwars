package com.twlan.backend.web;

import com.twlan.backend.domain.Account;
import com.twlan.backend.service.AuthService;
import com.twlan.backend.service.GroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Every call answers with the fresh list of groups.
@RestController
public class GroupController {

    private final GroupService groups;
    private final GameFacade gameFacade;

    public GroupController(GroupService groups, GameFacade gameFacade) {
        this.groups = groups;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    public record NameRequest(String name) {}
    public record AssignRequest(List<Long> groupIds) {}

    @GetMapping("/api/groups")
    public List<GroupService.Group> list() { return groups.list(me(), gameFacade.currentWorld()); }

    @PostMapping("/api/groups")
    public List<GroupService.Group> create(@RequestBody NameRequest request) { return groups.create(me(), gameFacade.currentWorld(), request.name()); }

    @PutMapping("/api/groups/{id}")
    public List<GroupService.Group> rename(@PathVariable Long id, @RequestBody NameRequest request) { return groups.rename(me(), gameFacade.currentWorld(), id, request.name()); }

    @DeleteMapping("/api/groups/{id}")
    public List<GroupService.Group> delete(@PathVariable Long id) { return groups.delete(me(), gameFacade.currentWorld(), id); }

    @PutMapping("/api/groups/village/{villageId}")
    public List<GroupService.Group> assign(@PathVariable Long villageId, @RequestBody AssignRequest request) {
        return groups.assign(me(), gameFacade.currentWorld(), villageId, request.groupIds());
    }
}
