package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.service.AuthService;
import se.oscarwiklund.twlan2.backend.service.ForumService;
import se.oscarwiklund.twlan2.backend.web.dto.ForumDto.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tribe/forum")
public class ForumController {

    private final ForumService forum;
    private final GameFacade gameFacade;

    public ForumController(ForumService forum, GameFacade gameFacade) {
        this.forum = forum;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    @GetMapping
    public Forum boards() { return forum.forum(me(), gameFacade.currentWorld()); }

    @GetMapping("/unread")
    public Map<String, Boolean> unread() { return Map.of("unread", forum.hasUnread(me(), gameFacade.currentWorld())); }

    @PostMapping("/read")
    public Forum readAll(@RequestParam(required = false) Long boardId) {
        forum.markBoardRead(me(), gameFacade.currentWorld(), boardId);
        return boards();
    }

    @GetMapping("/boards/{id}")
    public BoardPage board(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) {
        return forum.threads(me(), gameFacade.currentWorld(), id, page);
    }

    @PostMapping("/boards")
    public Forum createBoard(@RequestBody BoardRequest r) {
        forum.createBoard(me(), gameFacade.currentWorld(), r);
        return boards();
    }

    @PutMapping("/boards/{id}")
    public Forum editBoard(@PathVariable Long id, @RequestBody BoardRequest r) {
        forum.editBoard(me(), gameFacade.currentWorld(), id, r);
        return boards();
    }

    @DeleteMapping("/boards/{id}")
    public Forum deleteBoard(@PathVariable Long id) {
        forum.deleteBoard(me(), gameFacade.currentWorld(), id);
        return boards();
    }

    @PostMapping("/boards/{id}/threads")
    public Map<String, Long> newThread(@PathVariable Long id, @RequestBody NewThread r) {
        return Map.of("id", forum.createThread(me(), gameFacade.currentWorld(), id, r));
    }

    @GetMapping("/threads/{id}")
    public ThreadView thread(@PathVariable Long id, @RequestParam(defaultValue = "-1") int page) {
        // page -1 = the last page (where new replies are)
        var first = forum.view(me(), gameFacade.currentWorld(), id, page < 0 ? Integer.MAX_VALUE / 2 : page);
        return first;
    }

    @PostMapping("/threads/{id}/posts")
    public ThreadView reply(@PathVariable Long id, @RequestBody BodyRequest r) {
        forum.reply(me(), gameFacade.currentWorld(), id, r.body());
        return thread(id, -1);
    }

    @PostMapping("/threads/{id}/{what}")
    public ThreadView moderate(@PathVariable Long id, @PathVariable String what) {
        forum.moderate(me(), gameFacade.currentWorld(), id, what);
        return thread(id, -1);
    }

    @PostMapping("/threads/{id}/move")
    public ThreadView move(@PathVariable Long id, @RequestBody MoveRequest r) {
        forum.move(me(), gameFacade.currentWorld(), id, r.boardId());
        return thread(id, -1);
    }

    @PostMapping("/threads/{id}/vote")
    public ThreadView vote(@PathVariable Long id, @RequestBody VoteRequest r) {
        forum.vote(me(), gameFacade.currentWorld(), id, r.optionId());
        return thread(id, -1);
    }

    @DeleteMapping("/threads/{id}")
    public Forum deleteThread(@PathVariable Long id) {
        forum.deleteThread(me(), gameFacade.currentWorld(), id);
        return boards();
    }

    @PutMapping("/posts/{id}")
    public Map<String, String> edit(@PathVariable Long id, @RequestBody BodyRequest r) {
        forum.editPost(me(), gameFacade.currentWorld(), id, r.body());
        return Map.of("ok", "true");
    }

    @DeleteMapping("/posts/{id}")
    public Map<String, Long> deletePost(@PathVariable Long id) {
        Long gone = forum.deletePost(me(), gameFacade.currentWorld(), id);
        return gone == null ? Map.of() : Map.of("threadDeleted", gone);
    }
}
