package com.hrgenius.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Login abuse protection (Phase 24): per-email failed-attempt tracking with
 * a temporary lockout once a configurable threshold is crossed.
 *
 * Design (deliberately minimal, consistent with the stateless architecture):
 *
 *  - In-memory {@link ConcurrentHashMap} keyed by lowercased email. Rate-
 *    limiting state is transient protection state, not authorization data —
 *    a restart clears it, which errs on the side of availability, and no
 *    database queries are added to any login. Keying by email rather than IP
 *    makes the protection effective against targeted brute force (Attack A)
 *    even when attempts arrive from many addresses; a restart re-opens the
 *    window, never the account.
 *  - {@link ConcurrentHashMap#compute} performs the check-and-record
 *    atomically per key, so concurrent failed attempts cannot lose an update
 *    and slip past the threshold (the Phase 21 lesson, applied here).
 *  - Expiry is computed against an injectable {@link Clock}, so tests advance
 *    time deterministically instead of sleeping.
 *  - Every outward answer is a bare boolean: the caller (AuthService) keeps
 *    responses generic and enumeration-safe.
 */
@Service
public class LoginProtectionService {

    /** Failure state for one email. */
    private record Attempts(int count, Instant lockedUntil) {
    }

    private final LoginProtectionProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> state = new ConcurrentHashMap<>();

    @Autowired
    public LoginProtectionService(LoginProtectionProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Test constructor: an injectable clock makes expiry deterministic. */
    LoginProtectionService(LoginProtectionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Whether the email is currently locked out. A lockout whose window has
     * elapsed clears the entry (expiry = full reset).
     */
    public boolean isLocked(String email) {
        if (!properties.isEnabled()) {
            return false;
        }
        boolean[] locked = {false};
        state.computeIfPresent(key(email), (k, attempts) -> {
            if (attempts.lockedUntil() != null
                    && attempts.lockedUntil().isAfter(clock.instant())) {
                locked[0] = true;
                return attempts;
            }
            // Expired lock → clear the entry; a merely-counting entry
            // (lockedUntil == null) must be KEPT — only the lock expiry
            // resets state, never a status read.
            return attempts.lockedUntil() == null ? attempts : null;
        });
        return locked[0];
    }

    /**
     * Records a failed attempt and returns whether this failure triggered the
     * lockout (the attempt that crosses the configured threshold). Expired
     * locks start a fresh counting window.
     */
    public boolean recordFailure(String email) {
        if (!properties.isEnabled()) {
            return false;
        }
        boolean[] triggered = {false};
        state.compute(key(email), (k, attempts) -> {
            Instant now = clock.instant();
            if (attempts != null && attempts.lockedUntil() != null
                    && attempts.lockedUntil().isAfter(now)) {
                return attempts; // locked: further failures do not extend the window
            }
            int count = (attempts == null || attempts.lockedUntil() != null) ? 1 : attempts.count() + 1;
            if (count >= properties.getMaxFailedAttempts()) {
                triggered[0] = true;
                return new Attempts(count, now.plus(properties.getLockoutDuration()));
            }
            return new Attempts(count, null);
        });
        return triggered[0];
    }

    /** Successful authentication clears the failure state for the email. */
    public void recordSuccess(String email) {
        state.remove(key(email));
    }

    /** Test/administration helper: clear all tracked state. */
    void reset() {
        state.clear();
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
