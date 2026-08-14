package se.oscarwiklund.twlan2.backend.live;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.web.AccountContext;
import se.oscarwiklund.twlan2.backend.web.WorldContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// The stream the browser keeps open (see LiveUpdates). Login token and world come from the same headers as every request.
@RestController
public class LiveController {

    private final LiveUpdates live;

    public LiveController(LiveUpdates live) {
        this.live = live;
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse response) {
        Account account = AccountContext.get(); // (the interceptor has already refused anonymous requests)
        Long world = WorldContext.get();
        if (world == null) throw new IllegalArgumentException("No world selected.");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // (a reverse proxy must not hold the events back)
        return live.connect(account.getId(), world);
    }
}
