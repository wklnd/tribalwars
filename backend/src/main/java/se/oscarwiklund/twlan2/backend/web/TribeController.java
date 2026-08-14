package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.Tribe;
import se.oscarwiklund.twlan2.backend.service.AuthService;
import se.oscarwiklund.twlan2.backend.service.TribeService;
import se.oscarwiklund.twlan2.backend.service.WarService;
import se.oscarwiklund.twlan2.backend.web.dto.TribeDto;
import se.oscarwiklund.twlan2.backend.web.dto.TribeDto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TribeController {

    private final TribeService tribes;
    private final GameFacade gameFacade;
    private final WarService wars;

    public TribeController(TribeService tribes, GameFacade gameFacade, WarService wars) {
        this.wars = wars;
        this.tribes = tribes;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    private State state() { return tribes.state(me(), gameFacade.currentWorld(), 0); }

    // ---- reading -------------------------------------------------------------------------------------------------

    @GetMapping("/api/tribes")
    public List<Row> ranking() {
        me();
        return tribes.ranking(gameFacade.currentWorld());
    }

    @GetMapping("/api/tribes/{id}")
    public Profile profile(@PathVariable Long id) {
        return tribes.profile(id, me(), gameFacade.currentWorld());
    }

    // Most units destroyed first; tribe restricts them to one tribe, own=true to the viewer's.
    @GetMapping("/api/tribes/wars")
    public List<WarService.War> wars(@RequestParam(required = false) Long tribe, @RequestParam(defaultValue = "false") boolean own) {
        Account a = me();
        var world = gameFacade.currentWorld();
        Long id = own ? tribes.tribeOf(a, world).map(Tribe::getId).orElse(-1L) : tribe;
        return wars.list(world, id);
    }

    @GetMapping("/api/tribe/map")
    public MapInfo map() {
        return tribes.mapInfo(me(), gameFacade.currentWorld());
    }

    @GetMapping("/api/tribe")
    public State own(@RequestParam(defaultValue = "0") int start) {
        return tribes.state(me(), gameFacade.currentWorld(), start);
    }

    // ---- founding, joining, leaving ------------------------------------------------------------------------------

    public record FoundRequest(String name, String tag) {}

    @PostMapping("/api/tribe")
    public State found(@RequestBody FoundRequest request) {
        tribes.found(me(), gameFacade.currentWorld(), request.name(), request.tag());
        return state();
    }

    public record InviteRequest(String name) {}

    @PostMapping("/api/tribe/invite")
    public State invite(@RequestBody InviteRequest request) {
        tribes.invite(me(), gameFacade.currentWorld(), request.name());
        return state();
    }

    public record PlayerRequest(Long playerId) {}

    @PostMapping("/api/tribe/invite/withdraw")
    public State withdraw(@RequestBody PlayerRequest request) {
        tribes.withdrawInvitation(me(), gameFacade.currentWorld(), request.playerId());
        return state();
    }

    @PostMapping("/api/tribe/invitations/{id}/accept")
    public State accept(@PathVariable Long id) {
        tribes.accept(me(), gameFacade.currentWorld(), id);
        return state();
    }

    @PostMapping("/api/tribe/invitations/{id}/reject")
    public State reject(@PathVariable Long id) {
        tribes.reject(me(), gameFacade.currentWorld(), id);
        return state();
    }

    @PostMapping("/api/tribe/leave")
    public State leave() {
        tribes.leave(me(), gameFacade.currentWorld());
        return state();
    }

    // ---- members -------------------------------------------------------------------------------------------------

    @PostMapping("/api/tribe/kick")
    public State kick(@RequestBody PlayerRequest request) {
        tribes.kick(me(), gameFacade.currentWorld(), request.playerId());
        return state();
    }

    @PutMapping("/api/tribe/members/{id}/rights")
    public State rights(@PathVariable Long id, @RequestBody RightsEdit edit) {
        tribes.editRights(me(), gameFacade.currentWorld(), id, edit);
        return state();
    }

    // ---- properties and texts ------------------------------------------------------------------------------------

    public record PropertiesRequest(String name, String tag, String homepage, String irc) {}

    @PutMapping("/api/tribe/properties")
    public State properties(@RequestBody PropertiesRequest r) {
        tribes.editProperties(me(), gameFacade.currentWorld(), r.name(), r.tag(), r.homepage(), r.irc());
        return state();
    }

    public record RecruitmentRequest(boolean allowApply, String template) {}

    @PutMapping("/api/tribe/recruitment")
    public State recruitment(@RequestBody RecruitmentRequest r) {
        tribes.editRecruitment(me(), gameFacade.currentWorld(), r.allowApply(), r.template());
        return state();
    }

    public record TextRequest(String text) {}

    @PutMapping("/api/tribe/description")
    public State description(@RequestBody TextRequest r) {
        tribes.editDescription(me(), gameFacade.currentWorld(), r.text());
        return state();
    }

    @PutMapping("/api/tribe/announcement")
    public State announcement(@RequestBody TextRequest r) {
        tribes.editAnnouncement(me(), gameFacade.currentWorld(), r.text());
        return state();
    }

    public record RelationRequest(String tag, String kind) {}

    @PostMapping("/api/tribe/relations")
    public State addRelation(@RequestBody RelationRequest r) {
        tribes.addRelation(me(), gameFacade.currentWorld(), r.tag(), r.kind());
        return state();
    }

    @DeleteMapping("/api/tribe/relations/{tribeId}")
    public State endRelation(@PathVariable Long tribeId) {
        tribes.endRelation(me(), gameFacade.currentWorld(), tribeId);
        return state();
    }

    @PostMapping("/api/tribe/disband")
    public State disband() {
        tribes.disband(me(), gameFacade.currentWorld());
        return state();
    }
}
