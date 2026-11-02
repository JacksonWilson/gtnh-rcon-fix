package com.rconfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoginGuardTest {

    private long now = 1_000_000;
    private final LoginGuard guard = new LoginGuard(() -> now);

    @Test
    void locksOutAfterMaxFailures() {
        assertEquals(0, guard.fail("1.2.3.4", 3, 60_000));
        assertEquals(0, guard.fail("1.2.3.4", 3, 60_000));
        assertEquals(60_000, guard.fail("1.2.3.4", 3, 60_000));
        assertEquals(60_000, guard.lockedFor("1.2.3.4"));
        assertEquals(0, guard.lockedFor("5.6.7.8"));
        now += 60_000;
        assertEquals(0, guard.lockedFor("1.2.3.4"));
    }

    @Test
    void eachLockoutDoublesUpToADay() {
        long[] expected = { 60_000, 120_000, 240_000 };
        for (long lockout : expected) {
            assertEquals(lockout, guard.fail("1.2.3.4", 1, 60_000));
            now += lockout;
        }
        for (int i = 0; i < 30; i++) guard.fail("1.2.3.4", 1, 60_000);
        assertEquals(24L * 60 * 60 * 1000, guard.lockedFor("1.2.3.4"));
    }

    @Test
    void successClearsFailures() {
        guard.fail("1.2.3.4", 2, 60_000);
        guard.succeed("1.2.3.4");
        assertEquals(0, guard.fail("1.2.3.4", 2, 60_000));
    }
}
