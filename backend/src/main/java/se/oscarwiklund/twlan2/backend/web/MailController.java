package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.service.AuthService;
import se.oscarwiklund.twlan2.backend.service.MailService;
import se.oscarwiklund.twlan2.backend.web.dto.MailDto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final MailService mail;
    private final GameFacade gameFacade;

    public MailController(MailService mail, GameFacade gameFacade) {
        this.mail = mail;
        this.gameFacade = gameFacade;
    }

    private Account me() {
        Account a = AccountContext.get();
        if (a == null) throw new AuthService.AuthException("Please log in.");
        return a;
    }

    @GetMapping
    public Inbox inbox(@RequestParam(defaultValue = "0") long folder, @RequestParam(defaultValue = "0") int page) {
        return mail.inbox(me(), gameFacade.currentWorld(), folder, page);
    }

    @GetMapping("/unread")
    public Map<String, Long> unread() { return Map.of("unread", mail.unreadCount(me(), gameFacade.currentWorld())); }

    @GetMapping("/{id}")
    public ThreadView view(@PathVariable Long id) { return mail.view(me(), gameFacade.currentWorld(), id); }

    @PostMapping
    public Map<String, Long> send(@RequestBody SendRequest r) {
        return Map.of("id", mail.send(me(), gameFacade.currentWorld(), r.to(), r.subject(), r.body()));
    }

    @PostMapping("/{id}/reply")
    public Map<String, Long> reply(@PathVariable Long id, @RequestBody BodyRequest r) {
        return Map.of("id", mail.reply(me(), gameFacade.currentWorld(), id, r.body()));
    }

    @PostMapping("/delete")
    public Inbox delete(@RequestBody IdsRequest r) {
        mail.delete(me(), gameFacade.currentWorld(), r.ids());
        return inbox(0, 0);
    }

    @PostMapping("/read")
    public Map<String, Boolean> read(@RequestBody IdsRequest r, @RequestParam(defaultValue = "true") boolean read) {
        mail.markRead(me(), gameFacade.currentWorld(), r.ids(), read);
        return Map.of("ok", true);
    }

    @PostMapping("/move")
    public Map<String, Boolean> move(@RequestBody MoveRequest r) {
        mail.move(me(), gameFacade.currentWorld(), r.ids(), r.folderId());
        return Map.of("ok", true);
    }

    // ---- folders

    @PostMapping("/folders")
    public Inbox createFolder(@RequestBody NameRequest r) {
        mail.createFolder(me(), gameFacade.currentWorld(), r.name());
        return inbox(0, 0);
    }

    @PutMapping("/folders/{id}")
    public Inbox renameFolder(@PathVariable Long id, @RequestBody NameRequest r) {
        mail.renameFolder(me(), gameFacade.currentWorld(), id, r.name());
        return inbox(0, 0);
    }

    @DeleteMapping("/folders/{id}")
    public Inbox deleteFolder(@PathVariable Long id) {
        mail.deleteFolder(me(), gameFacade.currentWorld(), id);
        return inbox(0, 0);
    }

    // ---- address book and blocked senders

    @GetMapping("/contacts")
    public List<Contact> addresses() { return mail.contacts(me(), gameFacade.currentWorld(), false); }

    @PostMapping("/contacts")
    public List<Contact> addAddress(@RequestBody NameRequest r) {
        mail.addContact(me(), gameFacade.currentWorld(), r.name(), false);
        return addresses();
    }

    @DeleteMapping("/contacts/{id}")
    public List<Contact> removeAddress(@PathVariable Long id) {
        mail.removeContact(me(), gameFacade.currentWorld(), id);
        return addresses();
    }

    @GetMapping("/blocked")
    public List<Contact> blocked() { return mail.contacts(me(), gameFacade.currentWorld(), true); }

    @PostMapping("/blocked")
    public List<Contact> block(@RequestBody NameRequest r) {
        mail.addContact(me(), gameFacade.currentWorld(), r.name(), true);
        return blocked();
    }

    @DeleteMapping("/blocked/{id}")
    public List<Contact> unblock(@PathVariable Long id) {
        mail.removeContact(me(), gameFacade.currentWorld(), id);
        return blocked();
    }

    // ---- circular mail

    @GetMapping("/circular")
    public CircularPage circulars() { return mail.circulars(me(), gameFacade.currentWorld()); }

    @PostMapping("/circular")
    public CircularPage sendCircular(@RequestBody CircularRequest r) {
        mail.sendCircular(me(), gameFacade.currentWorld(), r.subject(), r.body());
        return circulars();
    }
}
