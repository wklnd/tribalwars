package se.oscarwiklund.twlan2.backend.web;

import se.oscarwiklund.twlan2.backend.domain.Account;

// The logged-in account of the current request, set by WebConfig's interceptor from the Bearer token.
public final class AccountContext {
    private static final ThreadLocal<Account> CURRENT = new ThreadLocal<>();

    private AccountContext() {}

    public static Account get() { return CURRENT.get(); }
    public static void set(Account account) { CURRENT.set(account); }
    public static void clear() { CURRENT.remove(); }
}
