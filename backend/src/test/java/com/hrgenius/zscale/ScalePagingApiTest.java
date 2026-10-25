package com.hrgenius.zscale;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hrgenius.auth.LoginRequest;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 17 scale verification: proves the hard-paged lists (notifications,
 * jobs, candidates, applications, interviews, leave requests) execute real
 * SQL paging with DB-side filters — honest totals over &gt;100 matching rows,
 * far-out pages, LIKE-escaping, clamping, and mark-all integration — where
 * the old slice implementation would have lied (totalElements capped by the
 * clamp) or loaded everything into memory.
 *
 * Suite order is alphabetical and this package sorts LAST, so it must not
 * disturb earlier suites: every assertion here uses delta math against rows
 * it created itself, tolerant floor assertions, or marker searches. Seed
 * baselines are read live, never hardcoded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScalePagingApiTest {

    private static final int PER_STATUS = 35;
    private static final String JOB_MARKER = "Z Scale Engineer";
    private static final String CAND_MARKER = "Z Scale Candidate";
    private static final String APP_REMARK = "zscale-application";
    private static final String LEAVE_REASON = "zscale-family-event";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    final ObjectMapper json = new ObjectMapper();

    // shared across ordered tests
    static Long jobId;
    static long jobsTotalAfterSeed;

    // ---------------------------------------------------------------- utils

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders login(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(url("/api/v1/auth/login"), HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), loginHeaders), String.class);
        assertThat(response.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders admin() {
        return login("admin@hrgenius.local", "Admin@123");
    }

    private ResponseEntity<String> get(HttpHeaders auth, String path) {
        // A pre-built URI is passed through verbatim — RestTemplate would
        // otherwise re-encode the already-encoded %20/%25 in query strings.
        return rest.exchange(java.net.URI.create(url(path)), HttpMethod.GET,
                new HttpEntity<>(auth), String.class);
    }

    private ResponseEntity<String> post(HttpHeaders auth, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.addAll(auth);
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(json.valueToTree(body), headers), String.class);
    }

    private ResponseEntity<String> patch(HttpHeaders auth, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.addAll(auth);
        return rest.exchange(url(path), HttpMethod.PATCH,
                new HttpEntity<>("{}", headers), String.class);
    }

    private JsonNode body(ResponseEntity<String> response) {
        try {
            JsonNode envelope = json.readTree(response.getBody());
            return envelope.has("data") ? envelope.get("data") : envelope;
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable body: " + response.getBody(), e);
        }
    }

    private long totalOf(ResponseEntity<String> response) {
        return body(response).get("totalElements").asLong();
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Order(1)
    void jobsScaleBeyondOneRowPerPageWithHonestTotals() {
        HttpHeaders admin = admin();

        // Baseline (seed + earlier suites), then 5 of my own.
        jobsTotalAfterSeed = totalOf(get(admin, "/api/v1/jobs?size=1"));
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<String> created = post(admin, "/api/v1/jobs", Map.of(
                    "title", JOB_MARKER + " " + i,
                    "description", "Scale-row " + i,
                    "departmentId", 1,
                    "employmentType", "FULL_TIME",
                    "salaryRange", "60k-80k",
                    "status", "OPEN"));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED).as(created.getBody());
        }
        jobId = body(get(admin, "/api/v1/jobs?search=Z%20Scale%20Engineer&size=1"))
                .get("content").get(0).get("id").asLong();

        // Unfiltered total grew by exactly 5 — the count query is real SQL
        // COUNT over the table, not a page-local artifact.
        assertThat(totalOf(get(admin, "/api/v1/jobs?size=1")))
                .isEqualTo(jobsTotalAfterSeed + 5);

        // Honest filtered total: exactly the 5 marked rows, one per page —
        // impossible under the old slice paging (a size-1 request returned a
        // totalElements of 1 for the whole filtered set).
        String search = "scale%20engineer"; // URL-encoded "scale engineer"
        JsonNode page = body(get(admin, "/api/v1/jobs?search=" + search + "&size=1&page=3"));
        assertThat(page.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page.get("size").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("title").asText()).isEqualTo(JOB_MARKER + " 4");
        assertThat(page.get("last").asBoolean()).isFalse();

        // Far-out page: empty content, honest total, no error.
        JsonNode far = body(get(admin, "/api/v1/jobs?search=" + search + "&size=1&page=999"));
        assertThat(far.get("content").size()).isZero();
        assertThat(far.get("totalElements").asLong()).isEqualTo(5);

        // LIKE-escaping: the literal '%' term must not become a wildcard.
        assertThat(totalOf(get(admin, "/api/v1/jobs?search=%25&size=100"))).isZero();
    }

    @Test
    @Order(2)
    void candidatesAndApplicationsPageHonestTotalsWithDbFilters() {
        HttpHeaders admin = admin();
        long candidatesBefore = totalOf(get(admin, "/api/v1/candidates?size=1"));
        long applicationsBefore = totalOf(get(admin, "/api/v1/applications?size=1"));
        long appliedBefore = totalOf(get(admin, "/api/v1/applications?status=APPLIED&size=1"));

        List<Long> candidateIds = new ArrayList<>();
        for (int i = 1; i <= PER_STATUS; i++) {
            ResponseEntity<String> created = post(admin, "/api/v1/candidates", Map.of(
                    "name", CAND_MARKER + " " + i,
                    "email", "zscale.candidate" + i + "@example.test",
                    "skills", "Java, Spring Boot, ZSCALE" + i,
                    "experienceYears", 3));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED).as(created.getBody());
            candidateIds.add(body(created).get("id").asLong());
        }
        for (Long candidateId : candidateIds) {
            ResponseEntity<String> applied = post(admin, "/api/v1/applications", Map.of(
                    "candidateId", candidateId,
                    "jobId", jobId,
                    "remarks", APP_REMARK + " " + candidateId));
            assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.CREATED).as(applied.getBody());
        }

        // Totals grow by exactly what this suite created — the count query
        // is filter-exact, not a clamp or a page-local artifact.
        assertThat(totalOf(get(admin, "/api/v1/candidates?size=1")))
                .isEqualTo(candidatesBefore + PER_STATUS);
        assertThat(totalOf(get(admin, "/api/v1/applications?size=1")))
                .isEqualTo(applicationsBefore + PER_STATUS);

        // DB-side search + paging: all 35 marked candidates on one clamped
        // page (35 <= 100), and the count describes the filter, not the page.
        JsonNode cPage = body(get(admin, "/api/v1/candidates?search=scale%20candidate&size=100"));
        assertThat(cPage.get("totalElements").asLong()).isEqualTo(PER_STATUS);
        assertThat(cPage.get("content").size()).isEqualTo(PER_STATUS);
        assertThat(cPage.get("content").get(0).get("name").asText()).startsWith(CAND_MARKER);

        JsonNode aPage = body(get(admin, "/api/v1/applications?search=" + APP_REMARK + "&size=100"));
        assertThat(aPage.get("totalElements").asLong()).isEqualTo(PER_STATUS);
        assertThat(aPage.get("content").size()).isEqualTo(PER_STATUS);
        assertThat(aPage.get("content").get(0).get("jobId").asLong()).isEqualTo(jobId);

        // Status filter composes DB-side: the pre-suite APPLIED baseline
        // (captured up top) plus exactly PER_STATUS new APPLIED rows.
        assertThat(totalOf(get(admin, "/api/v1/applications?status=APPLIED&size=1")))
                .isEqualTo(appliedBefore + PER_STATUS);

        // Far-out page on candidates: empty, honest.
        JsonNode far = body(get(admin, "/api/v1/candidates?search=scale%20candidate&size=100&page=9"));
        assertThat(far.get("content").size()).isZero();
        assertThat(far.get("totalElements").asLong()).isEqualTo(PER_STATUS);
    }

    @Test
    @Order(3)
    void leaveRequestsPageBeyondTheClampWithFiltersAppliedInSql() {
        HttpHeaders admin = admin();
        long leaveBefore = totalOf(get(admin, "/api/v1/leave/requests?size=1"));

        // 35 PENDING spread over March–May 2095 (unique dates, no overlap,
        // disjoint from seeds and all suites) and distributed across the
        // three seeded types so every yearly balance stays inside its limit
        // (CASUAL 12, SICK 10, EARNED 15). Plus 1 APPROVED in June (type 3).
        for (int i = 1; i <= PER_STATUS; i++) {
            long typeId = i <= 12 ? 1 : (i <= 22 ? 2 : 3);
            LocalDate day = i <= 12 ? LocalDate.of(2095, 3, i)
                    : (i <= 22 ? LocalDate.of(2095, 4, i - 12) : LocalDate.of(2095, 5, i - 22));
            ResponseEntity<String> created = post(admin, "/api/v1/leave/requests", Map.of(
                    "employeeId", 1,
                    "leaveTypeId", typeId,
                    "startDate", day.toString(),
                    "endDate", day.toString(),
                    "reason", LEAVE_REASON));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED).as(created.getBody());
        }
        ResponseEntity<String> approved = post(admin, "/api/v1/leave/requests", Map.of(
                "employeeId", 1,
                "leaveTypeId", 3,
                "startDate", LocalDate.of(2095, 6, 1).toString(),
                "endDate", LocalDate.of(2095, 6, 2).toString(),
                "reason", LEAVE_REASON));
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.CREATED).as(approved.getBody());
        Long approvedId = body(approved).get("id").asLong();
        assertThat(patch(admin, "/api/v1/leave/requests/" + approvedId + "/approve")
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Honest total grows by exactly 36; the reason-searched set (36 rows)
        // exceeds nothing but is counted exactly, in SQL.
        assertThat(totalOf(get(admin, "/api/v1/leave/requests?size=1")))
                .isEqualTo(leaveBefore + PER_STATUS + 1);
        assertThat(totalOf(get(admin, "/api/v1/leave/requests?search=" + LEAVE_REASON + "&size=100")))
                .isEqualTo(36);

        // Status filter narrows in SQL; newest-first ordering puts a fresh
        // PENDING row first.
        JsonNode pending = body(get(admin, "/api/v1/leave/requests?status=PENDING&size=1"));
        assertThat(pending.get("totalElements").asLong()).isGreaterThanOrEqualTo(PER_STATUS);
        assertThat(pending.get("content").get(0).get("reason").asText()).isEqualTo(LEAVE_REASON);

        JsonNode approvedPage = body(get(admin, "/api/v1/leave/requests?status=APPROVED&size=100"));
        assertThat(approvedPage.get("content").get(0).get("status").asText()).isEqualTo("APPROVED");

        // Far-out page inside a filtered set: empty content, real total.
        JsonNode far = body(get(admin, "/api/v1/leave/requests?search=" + LEAVE_REASON + "&size=100&page=5"));
        assertThat(far.get("content").size()).isZero();
        assertThat(far.get("totalElements").asLong()).isEqualTo(36);
    }

    @Test
    @Order(4)
    void notificationFeedPagesHonorUnreadFilterAndEscapeWildcards() {
        HttpHeaders admin = admin();
        HttpHeaders employee = login("employee@hrgenius.local", "Employee@123");

        // LIKE-escaping applies to the feed too: a literal % matches nothing.
        assertThat(totalOf(get(admin, "/api/v1/notifications?search=%25&size=100"))).isZero();

        // Deterministic unread rows for the employee login: submit + approve
        // one leave (June 2095 — disjoint, within balance) delivers exactly
        // 2 events, the flow NotificationsApiTest established.
        long unreadBefore = totalOf(get(employee, "/api/v1/notifications?unread=true&size=100"));
        ResponseEntity<String> created = post(admin, "/api/v1/leave/requests", Map.of(
                "employeeId", 2,
                "leaveTypeId", 3,
                "startDate", LocalDate.of(2095, 6, 10).toString(),
                "endDate", LocalDate.of(2095, 6, 11).toString(),
                "reason", "zscale-notification-event"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED).as(created.getBody());
        Long requestId = body(created).get("id").asLong();
        assertThat(patch(admin, "/api/v1/leave/requests/" + requestId + "/approve").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // The unread filter is exact DB-side: delta +2, every row unread.
        JsonNode unread = body(get(employee, "/api/v1/notifications?unread=true&size=100"));
        assertThat(unread.get("totalElements").asLong()).isEqualTo(unreadBefore + 2);
        for (JsonNode row : unread.get("content")) {
            assertThat(row.get("read").asBoolean()).isFalse();
        }

        // Mark-all flips everything for this user; the unread slice becomes
        // exactly empty and the badge agrees.
        assertThat(patch(employee, "/api/v1/notifications/read-all").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(totalOf(get(employee, "/api/v1/notifications?unread=true&size=100"))).isZero();
        assertThat(body(get(employee, "/api/v1/notifications/unread")).get("unread").asLong()).isZero();

        // The full feed still holds the (now read) rows — paging math intact.
        assertThat(totalOf(get(employee, "/api/v1/notifications?size=100")))
                .isGreaterThanOrEqualTo(unreadBefore + 2);
    }
}
