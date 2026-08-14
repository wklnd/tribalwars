package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.service.AuthService;
import se.oscarwiklund.twlan2.backend.web.dto.AccountDto;
import se.oscarwiklund.twlan2.backend.web.dto.AuthRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/register")
    public AccountDto register(@RequestBody AuthRequest request) {
        var login = authService.register(request.username(), request.password());
        return new AccountDto(login.account().getId(), login.account().getUsername(), login.token(), login.account().isAdmin());
    }

    @PostMapping("/api/auth/login")
    public AccountDto login(@RequestBody AuthRequest request) {
        var login = authService.login(request.username(), request.password());
        return new AccountDto(login.account().getId(), login.account().getUsername(), login.token(), login.account().isAdmin());
    }

    @PostMapping("/api/auth/logout")
    public void logout(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        authService.logout(auth != null && auth.startsWith("Bearer ") ? auth.substring(7).trim() : null);
    }

    // 401 if the token is no longer valid.
    @GetMapping("/api/auth/me")
    public AccountDto me() {
        var account = AccountContext.get();
        return new AccountDto(account.getId(), account.getUsername(), null, account.isAdmin());
    }
}
