package com.twlan.backend.service;

import com.twlan.backend.domain.Account;
import com.twlan.backend.domain.AuthSession;
import com.twlan.backend.repo.AccountRepository;
import com.twlan.backend.repo.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {

    public static class AuthException extends RuntimeException {
        public AuthException(String message) { super(message); }
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{4,50}");
    // Account used by the fidelity tools in frontend/tools; it never inherits the pre-accounts save.
    private static final String DEV_ACCOUNT = "devtool";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final WorldService worldService;

    public AuthService(AccountRepository accounts, AuthSessionRepository sessions, WorldService worldService) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.worldService = worldService;
    }

    public record Login(Account account, String token) {}

    @Transactional
    public Login register(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("The user name must be 4 to 50 characters long and may only contain letters, digits, '_', '-' and '.'.");
        }
        if (password == null || password.length() < 4 || password.length() > 50) {
            throw new IllegalArgumentException("The password must be 4 to 50 characters long.");
        }
        if (accounts.findByUsernameLower(name.toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("The requested user name is already being used!");
        }
        Account account = new Account();
        account.setUsername(name);
        account.setPasswordHash(PasswordHasher.hash(password));
        accounts.save(account);
        if (!name.equalsIgnoreCase(DEV_ACCOUNT)) {
            // the save that existed before accounts belongs to the first real player who registers
            // (worldService only hands over villages that still have no owner)
            worldService.claimLegacyVillages(account);
            // whoever founds the game (first real account) runs it: they get the admin panel
            if (accounts.findAll().stream().noneMatch(a -> a.isAdmin() && !a.isNpc())) {
                account.setAdmin(true);
                accounts.save(account);
            }
        }
        return new Login(account, newSession(account));
    }

    @Transactional
    public Login login(String username, String password) {
        Optional<Account> found = accounts.findByUsernameLower(username == null ? "" : username.trim().toLowerCase());
        if (found.isEmpty() || password == null || !PasswordHasher.matches(password, found.get().getPasswordHash())) {
            throw new AuthException("Wrong user name or password.");
        }
        return new Login(found.get(), newSession(found.get()));
    }

    @Transactional
    public void logout(String token) {
        if (token != null) sessions.deleteById(token);
    }

    @Transactional(readOnly = true)
    public Optional<Account> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findById(token).flatMap(s -> accounts.findById(s.getAccountId()));
    }

    private String newSession(Account account) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        AuthSession s = new AuthSession();
        s.setToken(HexFormat.of().formatHex(raw));
        s.setAccountId(account.getId());
        sessions.save(s);
        account.setLastLoginAt(java.time.Instant.now());
        accounts.save(account);
        return s.getToken();
    }
}
