package se.oscarwiklund.twlan2.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Fire-and-forget sink for the frontend's ErrorBoundary (see lib/ErrorBoundary.jsx): without this, a real
// user's "Something went wrong loading the game" crash leaves nothing but console.error in a devtools panel
// nobody is watching. Public (no login needed) since a crash can happen before/around login itself.
@RestController
public class ClientErrorController {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClientErrorController.class);

    public record Report(String message, String stack, String componentStack, String url, String userAgent, String screen) {}

    private static String cap(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    @PostMapping("/api/client-error")
    public ResponseEntity<Void> report(@RequestBody Report r) {
        LOG.warn("Client render crash: {} | screen={} url={} ua={}\n{}",
                cap(r.message(), 500), cap(r.screen(), 100), cap(r.url(), 300), cap(r.userAgent(), 200),
                cap(r.componentStack() != null ? r.componentStack() : r.stack(), 4000));
        return ResponseEntity.noContent().build();
    }
}
