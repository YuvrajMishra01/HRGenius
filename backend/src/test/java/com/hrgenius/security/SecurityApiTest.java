package com.hrgenius.security;

import java.util.Base64;
import java.util.List;
import java.util.Map;

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

import com.hrgenius.auth.JwtService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 22 — security boundary tests over real HTTP.
 *
 * Proves (a) the anonymous sweep: every module root rejects unauthenticated
 * requests with 401 (enforced by the security filter chain, before any
 * controller code); (b) the RBAC matrix for the sensitive module groups;
 * (c) IDOR/object-level authorization on principal-scoped resources;
 * (d) that JWT role claims are NOT trusted — authorities come from the DB;
 * (e) malformed request bodies return 400, never 500; (f) CORS answers the
 * configured origin and withholds ACAO from foreign ones; (g) no password
 * hashes or token material appear in sampled responses.
 *
 * Note on probe design: for authenticated users, Spring's method security
 * runs after argument resolution, so write probes carry valid bodies —
 * otherwise bean validation would answer 400 before @PreAuthorize could
 * answer 403. The anonymous sweep needs no such care (chain-level 401).
 *
 * Suite order: `security` runs after `report`, before `zscale`. All probes
 * are read-only except: the employee's own notification read-all (no later
 * suite reads notifications) — no version bumps, no seed mutation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtService jwtService;

    // ------------------------------------------------------- anonymous sweep

    @Test
    @Order(1)
    void everyModuleRootRejectsAnonymousRequestsWith401() {
        List<String> protectedRoots = List.of(
                "/api/v1/employees", "/api/v1/departments", "/api/v1/designations",
                "/api/v1/jobs", "/api/v1/candidates", "/api/v1/applications",
                "/api/v1/interviews", "/api/v1/onboardings",
                "/api/v1/attendance/today", "/api/v1/attendance/month",
                "/api/v1/leave/requests", "/api/v1/leave/types", "/api/v1/leave/summary",
                "/api/v1/payrolls", "/api/v1/reports/payroll.csv?year=2026&month=1",
                "/api/v1/performance/reviews", "/api/v1/performance/summary",
                "/api/v1/documents", "/api/v1/notifications",
                "/api/v1/analytics/workforce", "/api/v1/dashboard/stats",
                "/api/v1/reports/employees.csv",
                "/api/v1/audit", "/api/v1/audit/export.csv",
                "/api/v1/ai/job-matches/1", "/api/v1/admin/only",
                "/api/v1/auth/me", "/api/v1/auth/logout");
        for (String path : protectedRoots) {
            ResponseEntity<String> response = rest.getForEntity(url() + path, String.class);
            assertThat(response.getStatusCode())
                    .as("anonymous %s", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody())
                    .as("error envelope for %s", path)
                    .contains("\"status\":401")
                    .doesNotContain("Exception")
                    .doesNotContain("at org.")
                    .doesNotContain("io.jsonwebtoken");
        }
    }

    // ---------------------------------------------------- JWT failure modes

    @Test
    @Order(2)
    void brokenAuthorizationHeadersAreAllRejected() {
        String path = "/api/v1/auth/me";

        // Empty header value and empty Bearer payload.
        assertThat(status(path, h(x -> x.set("Authorization", "")))).isEqualTo(401);
        assertThat(status(path, h(x -> x.setBearerAuth("")))).isEqualTo(401);

        // Garbage and structurally-valid-but-unsigned (alg=none) tokens.
        assertThat(status(path, h(x -> x.setBearerAuth("not-a-jwt")))).isEqualTo(401);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin@hrgenius.local\",\"role\":\"ADMIN\"}".getBytes());
        String unsigned = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes()) + "." + payload + ".";
        assertThat(status(path, h(x -> x.setBearerAuth(unsigned)))).isEqualTo(401);

        // Validly signed token whose signature was tampered with.
        String real = loginAndGetToken("admin@hrgenius.local", "Admin@123");
        String[] parts = real.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + "AAAA" + parts[2].substring(4);
        assertThat(status(path, h(x -> x.setBearerAuth(tampered)))).isEqualTo(401);

        // Signed by us but referencing a user that does not exist.
        String ghost = jwtService.generateTokenWithOverride("ghost@hrgenius.local", "ADMIN", 9999L, 0L, 60);
        assertThat(status(path, h(x -> x.setBearerAuth(ghost)))).isEqualTo(401);

        // Token whose token_version no longer matches the DB (revoked).
        String staleVersion = jwtService.generateTokenWithOverride(
                "admin@hrgenius.local", "ADMIN", 1L, 77L, 60);
        assertThat(status(path, h(x -> x.setBearerAuth(staleVersion)))).isEqualTo(401);
    }

    @Test
    @Order(3)
    void roleClaimsAreNotTrustedAuthoritiesComeFromTheDatabase() {
        // A validly-signed token CLAIMING role=ADMIN for the employee user must
        // not grant anything: the filter re-loads the user and derives
        // ROLE_EMPLOYEE from the DB (claim tampering cannot escalate).
        String forged = jwtService.generateTokenWithOverride(
                "employee@hrgenius.local", "ADMIN", 4L, 0L, 60);
        assertThat(status("/api/v1/admin/only", h(x -> x.setBearerAuth(forged))))
                .isEqualTo(403);
        assertThat(status("/api/v1/payrolls", h(x -> x.setBearerAuth(forged))))
                .isEqualTo(403);

        // The same forged token still works for genuinely-allowed self endpoints.
        assertThat(status("/api/v1/auth/me", h(x -> x.setBearerAuth(forged))))
                .isEqualTo(200);
    }

    // --------------------------------------------------------- RBAC matrix

    /** A request probe: path + method + optional JSON body (null = no body). */
    private record Probe(String path, HttpMethod method, String body) {
        static Probe get(String path) {
            return new Probe(path, HttpMethod.GET, null);
        }

        static Probe send(String path, HttpMethod method, String body) {
            return new Probe(path, method, body);
        }
    }

    @Test
    @Order(4)
    void managerScopeExcludesPayrollAuditAnalyticsAndAdministration() {
        HttpHeaders manager = bearer(loginAndGetToken("manager@hrgenius.local", "Manager@123"));

        List<Probe> denied = List.of(
                // Payroll writes stay ADMIN/HR-only (reads are by-design
                // manager-visible — see the authorization matrix).
                Probe.send("/api/v1/payrolls/run", HttpMethod.POST,
                        "{\"year\":2026,\"month\":9}"),
                Probe.send("/api/v1/payrolls/99999/components", HttpMethod.PATCH,
                        "{\"basicSalary\":1,\"allowances\":0,\"deductions\":0,\"tax\":0}"),
                // HR audit trail and its export.
                Probe.get("/api/v1/audit"),
                Probe.get("/api/v1/audit/export.csv"),
                // Analytics + dashboard.
                Probe.get("/api/v1/analytics/workforce"),
                Probe.get("/api/v1/analytics/payroll-trend"),
                Probe.get("/api/v1/dashboard/stats"),
                // Administration: employee lifecycle and deletes.
                Probe.send("/api/v1/employees", HttpMethod.POST,
                        "{\"employeeCode\":\"SECX\",\"firstName\":\"X\",\"lastName\":\"Y\","
                                + "\"email\":\"secx@hrgenius.local\",\"joiningDate\":\"2026-01-04\","
                                + "\"employmentType\":\"FULL_TIME\",\"status\":\"ACTIVE\"}"),
                Probe.send("/api/v1/employees/1", HttpMethod.PUT,
                        "{\"employeeCode\":\"EMP001\",\"firstName\":\"Rahul\",\"lastName\":\"Verma\","
                                + "\"email\":\"manager@hrgenius.local\",\"joiningDate\":\"2021-03-15\","
                                + "\"employmentType\":\"FULL_TIME\",\"status\":\"ACTIVE\"}"),
                new Probe("/api/v1/employees/1", HttpMethod.DELETE, null),
                // Approvals, marking, review creation, onboarding writes.
                Probe.send("/api/v1/leave/requests/1/approve", HttpMethod.PATCH, null),
                Probe.send("/api/v1/attendance/mark", HttpMethod.POST,
                        "{\"employeeId\":3,\"date\":\"2099-01-04\",\"status\":\"PRESENT\"}"),
                Probe.send("/api/v1/performance/reviews", HttpMethod.POST,
                        "{\"employeeId\":4,\"reviewerId\":1,\"reviewPeriod\":\"2099-H3\",\"goals\":\"x\"}"),
                Probe.send("/api/v1/leave/requests", HttpMethod.POST,
                        "{\"employeeId\":3,\"leaveTypeId\":2,\"startDate\":\"2099-03-02\","
                                + "\"endDate\":\"2099-03-02\",\"reason\":\"scope probe\"}"));
        for (Probe probe : denied) {
            assertThat(status(probe.path(), manager, probe.method(), probe.body()))
                    .as("MANAGER %s %s", probe.method(), probe.path())
                    .isEqualTo(403);
        }

        // The by-design manager reads still work — including payroll views.
        assertThat(status("/api/v1/employees", manager)).isEqualTo(200);
        assertThat(status("/api/v1/leave/requests", manager)).isEqualTo(200);
        assertThat(status("/api/v1/attendance/today", manager)).isEqualTo(200);
        assertThat(status("/api/v1/jobs", manager)).isEqualTo(200);
        assertThat(status("/api/v1/payrolls", manager)).isEqualTo(200);
    }

    @Test
    @Order(5)
    void employeeRoleIsLimitedToSelfService() {
        HttpHeaders employee = bearer(loginAndGetToken("employee@hrgenius.local", "Employee@123"));

        List<String> denied = List.of(
                "/api/v1/employees", "/api/v1/employees/3",
                "/api/v1/departments", "/api/v1/designations",
                "/api/v1/jobs", "/api/v1/applications", "/api/v1/onboardings",
                "/api/v1/attendance/today", "/api/v1/attendance/month",
                "/api/v1/leave/requests", "/api/v1/leave/balances/3", "/api/v1/leave/summary",
                "/api/v1/payrolls", "/api/v1/payrolls/2026/9",
                "/api/v1/performance/reviews", "/api/v1/performance/summary",
                "/api/v1/documents?employeeId=3",
                "/api/v1/analytics/workforce", "/api/v1/dashboard/stats",
                "/api/v1/reports/employees.csv", "/api/v1/reports/payroll.csv?year=2026&month=1",
                "/api/v1/audit", "/api/v1/ai/job-matches/1");
        for (String path : denied) {
            assertThat(status(path, employee))
                    .as("EMPLOYEE GET %s", path)
                    .isEqualTo(403);
        }

        // Self-service still works.
        assertThat(status("/api/v1/auth/me", employee)).isEqualTo(200);
        assertThat(status("/api/v1/notifications", employee)).isEqualTo(200);
        assertThat(status("/api/v1/notifications/unread", employee)).isEqualTo(200);
    }

    // ------------------------------------------------- IDOR / object level

    @Test
    @Order(6)
    void employeeCannotReadOrMarkAnotherUsersNotifications() {
        // Object-level authorization: ids from another principal's mailbox are
        // invisible — 404 without revealing existence, not 200 and not 403.
        HttpHeaders employee = bearer(loginAndGetToken("employee@hrgenius.local", "Employee@123"));

        // The admin's seeded welcome notification (id 1) belongs to another user.
        assertThat(status("/api/v1/notifications/1/read", employee, HttpMethod.PATCH, null))
                .as("cross-user mark-read is object-hidden").isEqualTo(404);

        // The employee's own mailbox is fully usable (leaves later suites
        // unaffected — zscale reads no notifications).
        String list = body("/api/v1/notifications", employee);
        assertThat(list).contains("\"content\"");
        assertThat(status("/api/v1/notifications/read-all", employee, HttpMethod.PATCH, null))
                .isEqualTo(200);
    }

    // ------------------------------------------- malformed request bodies

    @Test
    @Order(7)
    void malformedBodiesAreClientErrorsNot500() {
        // Malformed JSON → 400; wrong Content-Type → 415. Both are client
        // errors answered inside the standard envelope, never a 500 with
        // parser internals. Protected endpoints are probed WITH a valid token
        // (an anonymous request would correctly stop at the 401 layer before
        // the body is ever parsed).
        HttpHeaders hr = bearer(loginAndGetToken("hr@hrgenius.local", "Hr@12345"));
        record Raw(String path, String contentType, String payload, int expected,
                boolean authenticated) {}
        List<Raw> probes = List.of(
                new Raw("/api/v1/auth/login", "application/json", "{invalid-json", 400, false),
                new Raw("/api/v1/auth/login", "text/plain", "email=x@y.z&password=x", 415, false),
                new Raw("/api/v1/leave/requests", "application/json", "{oops", 400, true),
                new Raw("/api/v1/payrolls/run", "application/json", "not-json", 400, true));
        for (Raw probe : probes) {
            HttpHeaders headers = probe.authenticated()
                    ? new HttpHeaders(hr)
                    : new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(probe.contentType()));
            ResponseEntity<String> response = rest.exchange(url() + probe.path(), HttpMethod.POST,
                    new HttpEntity<>(probe.payload(), headers), String.class);
            assertThat(response.getStatusCode().value())
                    .as("malformed body to %s (%s)", probe.path(), probe.contentType())
                    .isEqualTo(probe.expected());
            assertThat(response.getBody())
                    .contains("\"status\":" + probe.expected())
                    .doesNotContain("JsonParseException")
                    .doesNotContain("HttpMessageNotReadable")
                    .doesNotContain("HttpMediaTypeNotSupported")
                    .doesNotContain("at org.");
        }
    }

    // ------------------------------------------------------ CORS boundary

    @Test
    @Order(8)
    void corsAnswersTheConfiguredOriginAndWithholdsItFromForeignOnes() {
        HttpHeaders allowed = new HttpHeaders();
        allowed.set("Origin", "http://localhost:4200");
        allowed.set("Access-Control-Request-Method", "GET");
        ResponseEntity<String> ok = rest.exchange(url() + "/api/v1/payrolls", HttpMethod.OPTIONS,
                new HttpEntity<>(allowed), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:4200");

        HttpHeaders foreign = new HttpHeaders();
        foreign.set("Origin", "http://evil.example");
        foreign.set("Access-Control-Request-Method", "GET");
        ResponseEntity<String> rejected = rest.exchange(url() + "/api/v1/payrolls", HttpMethod.OPTIONS,
                new HttpEntity<>(foreign), String.class);
        // Either the preflight is refused outright, or it answers without the
        // ACAO header — both are unusable for the foreign origin.
        assertThat(rejected.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
        assertThat(rejected.getStatusCode().value()).isIn(200, 403);
    }

    // --------------------------------------------- sensitive data sampling

    @Test
    @Order(9)
    void noPasswordHashesOrSecretsLeakIntoRepresentativeResponses() {
        HttpHeaders admin = bearer(loginAndGetToken("admin@hrgenius.local", "Admin@123"));
        List<String> samples = List.of(
                body("/api/v1/employees?size=50", admin),
                body("/api/v1/payrolls/" + java.time.LocalDate.now().minusMonths(1).getYear()
                        + "/" + java.time.LocalDate.now().minusMonths(1).getMonthValue(), admin),
                body("/api/v1/audit?size=20", admin),
                body("/api/v1/performance/reviews", admin),
                body("/api/v1/notifications", admin),
                body("/api/v1/documents?employeeId=3", admin),
                body("/api/v1/auth/me", admin));
        for (String sample : samples) {
            assertThat(sample)
                    .as("no BCrypt hash leaks")
                    .doesNotContain("$2a$")
                    .doesNotContain("$2b$")
                    .doesNotContain("$2y$")
                    .doesNotContain("passwordHash")
                    .doesNotContain("password_hash")
                    .as("no JWT material in business payloads")
                    .doesNotContain("\"token\":\"ey");
        }
        // /auth/me returns identity without re-issuing a token.
        assertThat(samples.get(samples.size() - 1)).doesNotContain("\"token\"");
    }

    // ------------------------------------------------------- console + login

    @Test
    @Order(10)
    void h2ConsoleAnswersLocallyAndLoginRemainsEnumerationSafe() {
        // Dev profile + loopback client: the console is reachable for local
        // development (the non-loopback 404 path is verified live).
        assertThat(status("/h2-console/", new HttpHeaders())).isEqualTo(200);

        // Unknown email and known email with wrong password are
        // indistinguishable: identical status AND message (timestamps differ
        // by design, so the message field is what must match).
        ResponseEntity<String> unknown = post("/api/v1/auth/login",
                Map.of("email", "who@hrgenius.local", "password", "Whatever@1"));
        ResponseEntity<String> known = post("/api/v1/auth/login",
                Map.of("email", "admin@hrgenius.local", "password", "WrongPass@1"));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getBody()).contains("\"message\":\"Invalid email or password\"");
        assertThat(known.getBody()).contains("\"message\":\"Invalid email or password\"");
    }

    // ------------------------------------------------------------- helpers

    private int status(String path, HttpHeaders headers) {
        return status(path, headers, HttpMethod.GET, null);
    }

    private int status(String path, HttpHeaders headers, HttpMethod method, String jsonBody) {
        HttpEntity<String> entity = jsonBody == null
                ? new HttpEntity<>(null, headers)
                : jsonEntity(headers, jsonBody);
        return rest.exchange(url() + path, method, entity, String.class).getStatusCode().value();
    }

    /** JSON probe: sets the content type only when a body is present. */
    private HttpEntity<String> jsonEntity(HttpHeaders headers, String json) {
        HttpHeaders copy = new HttpHeaders();
        copy.putAll(headers);
        copy.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, copy);
    }

    private String body(String path, HttpHeaders headers) {
        ResponseEntity<String> response = rest.exchange(url() + path, HttpMethod.GET,
                new HttpEntity<>(null, headers), String.class);
        assertThat(response.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders h(java.util.function.Consumer<HttpHeaders> customizer) {
        HttpHeaders headers = new HttpHeaders();
        customizer.accept(headers);
        return headers;
    }

    private String loginAndGetToken(String email, String password) {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", email, "password", password));
        assertThat(response.getStatusCode()).as("login body: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int idx = body.indexOf("\"token\":\"");
        int start = idx + "\"token\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private ResponseEntity<String> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url() + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
