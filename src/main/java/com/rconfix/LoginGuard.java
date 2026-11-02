package com.rconfix;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts failed RCON logins per client address and locks an address out after too many. Each lockout of the same
 * address lasts twice as long as the one before, up to a day. A successful login clears the address.
 */
public final class LoginGuard {

    private static final long MAX_LOCKOUT_MILLIS = 24L * 60 * 60 * 1000;

    public interface Clock {

        long millis();
    }

    private static final class Entry {

        int failures;
        int lockouts;
        long lockedUntil;
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Clock clock;

    public LoginGuard(Clock clock) {
        this.clock = clock;
    }

    /** Milliseconds until this address may try again, or 0 if it isn't locked out. */
    public synchronized long lockedFor(String address) {
        Entry e = entries.get(address);
        return e == null ? 0 : Math.max(0, e.lockedUntil - clock.millis());
    }

    /** Records a failed login. Returns the new lockout in milliseconds, or 0 if this failure didn't cause one. */
    public synchronized long fail(String address, int maxFailures, long lockoutMillis) {
        Entry e = entries.computeIfAbsent(address, k -> new Entry());
        e.failures++;
        if (e.failures < maxFailures) {
            return 0;
        }
        e.failures = 0;
        long lockout = Math.min(MAX_LOCKOUT_MILLIS, lockoutMillis << Math.min(e.lockouts, 20));
        e.lockouts++;
        e.lockedUntil = clock.millis() + lockout;
        return lockout;
    }

    public synchronized void succeed(String address) {
        entries.remove(address);
    }
}
