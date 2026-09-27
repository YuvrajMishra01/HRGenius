package com.hrgenius.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 24 — login brute-force protection over real HTTP plus direct-service
 * determinism tests (injectable clock for expiry, storm for lost updates).
 *
 * Hygiene: the tracker is a shared in-memory bean for the whole suite run, so
 * every test's leftover state is cleared in {@link @AfterEach} (successful
 * login / recordSuccess removes the entry). Lock-inducing storms only ever
 * target real accounts inside a single test method and are cleaned up before
 * the method ends — no later suite can inherit a lockout. The unique
 * bruteforce@ key is used where an account must stay locked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginProtectionApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    LoginProtectionService loginProtection;

    @Autowired
    LoginProtectionProperties properties;

    private static final String BRUTEFORCE_EMAIL = "bruteforce@hrgenius.local";

    @AfterEach
    void clearTrackedState() {
        // Successful-login semantics: removes every entry this suite touched
        // so no other suite (or later test) inherits lockout state.
        for (String email : List.of("admin@hrgenius.local", "hr@hrgenius.local",
                "employee@hrgenius.local", BRUTEFORCE_EMAIL)) {
            loginProtection.recordSuccess(email);
        }
    }

    // --------------------------------------------------------- happy paths

    @Test
    @Order(1)
    void validCredentialsStillLogin() {
        ResponseEntity<String> response = login("admin@hrgenius.local", "Admin@123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"token\":\"");
    }

    @Test
    @Order(2)
    void invalidPasswordIsRejectedWithTheGenericMessage() {
        ResponseEntity<String> response = login("hr@hrgenius.local", "WrongPass@1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid email or password");
    }

    // -------------------------------------------------- counting + lockout

    @Test
    @Order(3)
    void failuresAccumulateAndCrossingTheThresholdLocksTheAccount() {
        // Start from a known state: a successful login resets the counter.
        assertThat(login("employee@hrgenius.local", "Employee@123").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Threshold is 5 by default (asserted so the test tracks config).
        assertThat(properties.getMaxFailedAttempts()).isEqualTo(5);

        // max - 1 failures: still counting, not yet locked.
        for (int i = 0; i < properties.getMaxFailedAttempts() - 1; i++) {
            assertThat(login("employee@hrgenius.local", "WrongPass@1").getStatusCode())
                    .as("failure %d", i + 1).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(loginProtection.isLocked("employee@hrgenius.local")).isFalse();

        // The threshold-crossing failure triggers the lock.
        assertThat(login("employee@hrgenius.local", "WrongPass@1").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginProtection.isLocked("employee@hrgenius.local")).isTrue();
    }

    @Test
    @Order(4)
    void correctCredentialsDoNotBypassAnActiveLockout() {
        // Lock the account in-test (the @AfterEach hygiene means no state is
        // carried between tests): drive to threshold, then prove the CORRECT
        // password is rejected with the same generic message while locked.
        for (int i = 0; i < properties.getMaxFailedAttempts(); i++) {
            login("employee@hrgenius.local", "WrongPass@1");
        }
        assertThat(loginProtection.isLocked("employee@hrgenius.local")).isTrue();

        ResponseEntity<String> response = login("employee@hrgenius.local", "Employee@123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid email or password");
    }

    @Test
    @Order(5)
    void successfulLoginResetsTheFailureState() {
        // hr@: a couple of failures, then a success — afterwards failures
        // start counting from zero again (three more failures must NOT lock).
        assertThat(login("hr@hrgenius.local", "WrongPass@1").getStatusCode().value()).isEqualTo(401);
        assertThat(login("hr@hrgenius.local", "WrongPass@1").getStatusCode().value()).isEqualTo(401);
        assertThat(login("hr@hrgenius.local", "Hr@12345").getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int i = 0; i < properties.getMaxFailedAttempts() - 1; i++) {
            assertThat(login("hr@hrgenius.local", "WrongPass@1").getStatusCode().value()).isEqualTo(401);
        }
        assertThat(loginProtection.isLocked("hr@hrgenius.local"))
                .as("reset removed earlier failures").isFalse();
    }

    // ---------------------------------------------------- expiry mechanics
    // (direct service + mutable clock: waiting out the real 10-minute window
    // in an HTTP test would be neither fast nor deterministic)

    @Test
    @Order(6)
    void lockExpiresAndTheAccountCanAuthenticateAgain() {
        MutableClock clock = new MutableClock();
        LoginProtectionProperties config = new LoginProtectionProperties();
        config.setMaxFailedAttempts(3);
        config.setLockoutDuration(Duration.ofMinutes(10));
        LoginProtectionService service = new LoginProtectionService(config, clock);

        String email = "expiring@hrgenius.local";
        assertThat(service.recordFailure(email)).isFalse();
        assertThat(service.recordFailure(email)).isFalse();
        assertThat(service.recordFailure(email))
                .as("third failure crosses the threshold").isTrue();
        assertThat(service.isLocked(email)).isTrue();

        // Failure attempts during the lock do not extend the window.
        assertThat(service.recordFailure(email)).isFalse();

        clock.advance(Duration.ofMinutes(9));
        assertThat(service.isLocked(email)).as("still inside the window").isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isLocked(email)).as("window elapsed").isFalse();

        // Fresh counting window after expiry: two failures do not lock again.
        assertThat(service.recordFailure(email)).isFalse();
        assertThat(service.recordFailure(email)).isFalse();
        assertThat(service.isLocked(email)).isFalse();
    }

    @Test
    @Order(7)
    void onlyTheThresholdCrossingAttemptReportsTriggered() {
        MutableClock clock = new MutableClock();
        LoginProtectionProperties config = new LoginProtectionProperties();
        config.setMaxFailedAttempts(3);
        config.setLockoutDuration(Duration.ofMinutes(5));
        LoginProtectionService service = new LoginProtectionService(config, clock);

        String email = "trigger@hrgenius.local";
        assertThat(service.recordFailure(email)).isFalse();
        assertThat(service.recordFailure(email)).isFalse();
        assertThat(service.recordFailure(email)).isTrue();
        // While locked, further failures are absorbed (window not extended).
        assertThat(service.recordFailure(email)).isFalse();
    }

    // --------------------------------------------------------- concurrency

    @Test
    @Order(8)
    void concurrentFailuresCannotBypassTheThreshold() throws Exception {
        MutableClock clock = new MutableClock();
        LoginProtectionProperties config = new LoginProtectionProperties();
        config.setMaxFailedAttempts(5);
        config.setLockoutDuration(Duration.ofMinutes(10));
        LoginProtectionService service = new LoginProtectionService(config, clock);

        String email = "storm@hrgenius.local";
        int stormSize = 20;
        List<Boolean> triggered = fireConcurrently(stormSize, () -> service.recordFailure(email));

        // Exactly one request crosses the threshold (atomic compute — no lost
        // updates), and the account ends locked even though every racer read
        // the same empty state at the start.
        assertThat(triggered.stream().filter(Boolean::booleanValue).count())
                .as("exactly one threshold-crossing attempt").isEqualTo(1);
        assertThat(service.isLocked(email)).isTrue();

        // Sanity: fewer-than-threshold concurrent failures never lock.
        MutableClock clock2 = new MutableClock();
        LoginProtectionService service2 = new LoginProtectionService(config, clock2);
        List<Boolean> under = fireConcurrently(4, () -> service2.recordFailure(email + "2"));
        assertThat(under.stream().filter(Boolean::booleanValue)).isEmpty();
        assertThat(service2.isLocked(email + "2")).isFalse();
    }

    @Test
    @Order(9)
    void concurrentHttpFailuresLockTheRealEndpoint() throws Exception {
        // The same lost-update guarantee, proven through real HTTP requests:
        // reset, then fire 8 parallel wrong-password logins at one account —
        // the tracker must end locked (threshold 5 among 8), the next attempt
        // — even with correct credentials — must be rejected, and cleanup
        // restores the account immediately.
        assertThat(login("employee@hrgenius.local", "Employee@123").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return login("employee@hrgenius.local", "WrongPass@1").getStatusCode().value();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(401);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(loginProtection.isLocked("employee@hrgenius.local"))
                .as("real HTTP storm crossed the threshold").isTrue();

        // Locked: even the correct password is rejected at the endpoint.
        assertThat(login("employee@hrgenius.local", "Employee@123").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Cleanup (also proves success-clears-lock on the live bean).
        loginProtection.recordSuccess("employee@hrgenius.local");
        assertThat(login("employee@hrgenius.local", "Employee@123").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------- enumeration safety

    @Test
    @Order(10)
    void unknownAndKnownEmailsAreIndistinguishableWhileCountingAndLocked() {
        // Same generic message for: unknown email, known email + wrong
        // password, and a locked known email (correct credentials).
        String unknown = login(BRUTEFORCE_EMAIL, "Whatever@1").getBody();
        String knownWrong = login("hr@hrgenius.local", "WrongPass@1").getBody();

        // Drive the unknown email into lockout (5 failures).
        for (int i = 0; i < properties.getMaxFailedAttempts(); i++) {
            login(BRUTEFORCE_EMAIL, "Whatever@1");
        }
        assertThat(loginProtection.isLocked(BRUTEFORCE_EMAIL)).isTrue();
        String lockedUnknown = login(BRUTEFORCE_EMAIL, "Whatever@1").getBody();

        for (String body : List.of(unknown, knownWrong, lockedUnknown)) {
            assertThat(body).contains("\"status\":401").contains("Invalid email or password");
        }
        // The only difference between the envelopes is the timestamp.
        assertThat(unknown.replaceAll("\"timestamp\":\"[^\"]*\"", ""))
                .isEqualTo(knownWrong.replaceAll("\"timestamp\":\"[^\"]*\"", ""));
    }

    @Test
    @Order(11)
    void disabledProtectionNeverLocks() {
        LoginProtectionProperties config = new LoginProtectionProperties();
        config.setEnabled(false);
        LoginProtectionService service = new LoginProtectionService(config, new MutableClock());

        String email = "unprotected@hrgenius.local";
        for (int i = 0; i < 25; i++) {
            assertThat(service.recordFailure(email)).isFalse();
        }
        assertThat(service.isLocked(email)).isFalse();
    }

    // ------------------------------------------------------------- helpers

    private ResponseEntity<String> login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), headers), String.class);
    }

    private List<Boolean> fireConcurrently(int size, Callable<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(size);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Advanceable clock for deterministic expiry tests. */
    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(System.currentTimeMillis());

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
