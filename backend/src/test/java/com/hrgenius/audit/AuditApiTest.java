package com.hrgenius.audit;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.util.UriComponentsBuilder;

import com.hrgenius.auth.LoginRequest;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit log over real HTTP (Phase 18).
 *
 * Class order is alphabetical: audit runs before every other suite, so all
 * business-side assertions are **deltas over captured baselines** and the
 * suite leaves the seed untouched for later suites (LeaveApiTest expects
 * the pure seed leave state — every leave row created here is decided and
 * deleted again before the suite ends; the audit rows, of course, remain —
 * they are the point).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    /** Names unique to this suite (searchable via the audit search). */
    private static final String TYPE_NAME = "Phase 18 Audit Type";
    private static final LocalDate FAR_START = LocalDate.of(2099, 3, 2);  // Monday
    private static final LocalDate FAR_END = LocalDate.of(2099, 3, 6);    // Friday

    private static volatile long createdTypeId;
    private static volatile long createdRequestId;
    /** Per-action baselines captured BEFORE this suite writes (earlier
     * suites — attendance — legitimately generate audit rows too). */
    private static final java.util.Map<String, Long> BASELINES = new java.util.HashMap<>();

    // ---------------------------------------------------------------- RBAC

    @Test
    @Order(1)
    void auditLogIsAdminHrOnly() {
        // Anonymous → 401/403, never data.
        ResponseEntity<String> anon = rest.getForEntity(URI.create(url("/api/v1/audit")), String.class);
        assertThat(anon.getStatusCode().value()).isIn(401, 403);

        // EMPLOYEE and MANAGER are deliberately excluded from HR audit data.
        assertThat(exchange(HttpMethod.GET, "/api/v1/audit", employeeHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET, "/api/v1/audit", managerHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN and HR both read.
        assertThat(exchange(HttpMethod.GET, "/api/v1/audit", adminHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/v1/audit", hrHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // The facet endpoint follows the same policy.
        assertThat(exchange(HttpMethod.GET, "/api/v1/audit/entity-types", managerHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------ recording

    @Test
    @Order(2)
    void importantActionsAreRecordedWithActorEntityAndDetails() {
        String employees = exchange(HttpMethod.GET, "/api/v1/employees?size=5", hrHeaders(), null).getBody();
        long employeeId = ((Number) JsonPath.read(employees, "$.data.content[0].id")).longValue();

        for (String action : java.util.List.of("LEAVE_TYPE_CREATED", "LEAVE_TYPE_UPDATED", "LEAVE_TYPE_DELETED",
                "LEAVE_REQUEST_SUBMITTED", "LEAVE_REQUEST_APPROVED", "LEAVE_REQUEST_DELETED")) {
            BASELINES.put(action, total("/api/v1/audit?action=" + action));
        }

        // Create + update a leave type (ADMIN/HR writes).
        Long typeId = ((Number) JsonPath.read(exchange(HttpMethod.POST, "/api/v1/leave/types", hrHeaders(),
                Map.of("name", TYPE_NAME, "description", "Suite-local type", "yearlyLimit", 30)).getBody(),
                "$.data.id")).longValue();
        createdTypeId = typeId;

        exchange(HttpMethod.PATCH, "/api/v1/leave/types/" + typeId, hrHeaders(),
                Map.of("name", TYPE_NAME, "description", "Suite-local type v2", "yearlyLimit", 25));

        // Submit a request on it (5 working days within the 30-day limit),
        // approve it — then delete request + type again so later suites
        // still see the pure seed state.
        Long requestId = ((Number) JsonPath.read(exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                Map.of("employeeId", employeeId, "leaveTypeId", typeId,
                        "startDate", FAR_START.toString(), "endDate", FAR_END.toString(),
                        "reason", "Phase 18 audit trail proof")).getBody(),
                "$.data.id")).longValue();
        createdRequestId = requestId;

        exchange(HttpMethod.PATCH, "/api/v1/leave/requests/" + requestId + "/approve", hrHeaders(), null);
        exchange(HttpMethod.DELETE, "/api/v1/leave/requests/" + requestId, adminHeaders(), null);
        exchange(HttpMethod.DELETE, "/api/v1/leave/types/" + typeId, adminHeaders(), null);

        // --- deltas: exactly the actions this test performed.
        for (String action : java.util.List.of("LEAVE_TYPE_CREATED", "LEAVE_TYPE_UPDATED", "LEAVE_TYPE_DELETED",
                "LEAVE_REQUEST_SUBMITTED", "LEAVE_REQUEST_APPROVED", "LEAVE_REQUEST_DELETED")) {
            assertThat(total("/api/v1/audit?action=" + action)).isEqualTo(BASELINES.get(action) + 1);
        }

        // The whole lifecycle of the created type is attributable to it.
        String forType = exchange(HttpMethod.GET, "/api/v1/audit?entityType=LEAVE_TYPE&size=50", hrHeaders(), null).getBody();
        java.util.List<Integer> typeIds = JsonPath.read(forType, "$.data.content[?(@.entityId == " + typeId + ")]");
        assertThat(typeIds.size()).isEqualTo(3); // created + updated + deleted

        // Newest-first: the last performed action is the top row.
        String newest = exchange(HttpMethod.GET, "/api/v1/audit?size=1", adminHeaders(), null).getBody();
        assertThat(JsonPath.<String>read(newest, "$.data.content[0].action")).isEqualTo("LEAVE_TYPE_DELETED");

        // Actor attribution: the HR-driven creates carry the HR login.
        String created = exchange(HttpMethod.GET, "/api/v1/audit?action=LEAVE_TYPE_CREATED&size=1", adminHeaders(), null).getBody();
        assertThat(JsonPath.<String>read(created, "$.data.content[0].actorEmail")).isEqualTo("hr@hrgenius.local");
        assertThat(JsonPath.<String>read(created, "$.data.content[0].actorRole")).isEqualTo("HR");
        assertThat(((Number) JsonPath.read(created, "$.data.content[0].actorId")).longValue()).isPositive();

        // Structured details survived the write.
        assertThat(JsonPath.<String>read(created, "$.data.content[0].details")).contains("30 days");
        assertThat(JsonPath.<String>read(created, "$.data.content[0].entityLabel")).isEqualTo(TYPE_NAME);
    }

    // ------------------------------------------------------------ filtering

    @Test
    @Order(3)
    void filtersSearchAndDateRangeNarrowTheTrail() {
        // Entity-type facet narrows to that family only.
        String leaveOnly = exchange(HttpMethod.GET, "/api/v1/audit?entityType=LEAVE_TYPE&size=50", hrHeaders(), null).getBody();
        java.util.List<?> nonLeave = JsonPath.read(leaveOnly, "$.data.content[?(@.entityType != 'LEAVE_TYPE')]");
        assertThat(nonLeave).isEmpty();
        assertThat((Integer) JsonPath.read(leaveOnly, "$.data.totalElements")).isGreaterThanOrEqualTo(3);

        // Action facet exact-matches.
        String approved = exchange(HttpMethod.GET, "/api/v1/audit?action=LEAVE_REQUEST_APPROVED&size=50", hrHeaders(), null).getBody();
        java.util.List<?> nonApproved = JsonPath.read(approved, "$.data.content[?(@.action != 'LEAVE_REQUEST_APPROVED')]");
        assertThat(nonApproved).isEmpty();
        assertThat((Integer) JsonPath.read(approved, "$.data.totalElements")).isGreaterThanOrEqualTo(1);

        // Search scans label + details + actor email (LIKE-escaped):
        // "Yearly" only ever appears in DETAILS (created + updated rows).
        String yearly = exchange(HttpMethod.GET, "/api/v1/audit?search=Yearly&size=20", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(yearly, "$.data.totalElements")).isEqualTo(2);
        // "Audit" appears in the LABEL of the three type-lifecycle rows and
        // in the DETAILS of the submitted/approved request rows (type name).
        String labelHits = exchange(HttpMethod.GET, "/api/v1/audit?search=Audit&size=20", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(labelHits, "$.data.totalElements")).isEqualTo(5);
        // A literal % matches nothing — user wildcards are escaped.
        String literalPercent = exchange(HttpMethod.GET, "/api/v1/audit?search=%25", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(literalPercent, "$.data.totalElements")).isEqualTo(0);

        // Inclusive date bounds: today covers everything written so far.
        String today = "/api/v1/audit?from=" + iso(LocalDate.now()) + "&to=" + iso(LocalDate.now()) + "&size=50";
        String todayBody = exchange(HttpMethod.GET, today, hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(todayBody, "$.data.totalElements"))
                .isGreaterThanOrEqualTo((Integer) JsonPath.read(todayBody, "$.data.content.size()"));
        // A range strictly before any write sees nothing.
        String old = "/api/v1/audit?from=2090-01-01&to=2090-01-31&size=50";
        assertThat((Integer) JsonPath.read(exchange(HttpMethod.GET, old, hrHeaders(), null).getBody(),
                "$.data.totalElements")).isEqualTo(0);

        // Combined filters AND together.
        String combined = exchange(HttpMethod.GET,
                "/api/v1/audit?action=LEAVE_REQUEST_APPROVED&entityType=LEAVE_REQUEST&size=20", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(combined, "$.data.totalElements")).isEqualTo(1);
        assertThat(JsonPath.<String>read(combined, "$.data.content[0].action")).isEqualTo("LEAVE_REQUEST_APPROVED");
    }

    // ---------------------------------------------------------------- paging

    @Test
    @Order(4)
    void pagingIsSqlSideWithHonestTotals() {
        long grand = total("/api/v1/audit");

        // size=2 forces at least two pages.
        String p0 = exchange(HttpMethod.GET, "/api/v1/audit?size=2&page=0", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(p0, "$.data.content.size()")).isEqualTo(2);
        assertThat((Boolean) JsonPath.read(p0, "$.data.first")).isTrue();
        assertThat((Boolean) JsonPath.read(p0, "$.data.last")).isFalse();
        assertThat((Integer) JsonPath.read(p0, "$.data.totalPages")).isGreaterThanOrEqualTo(2);

        // The exact total is stable across every page (a real count twin).
        assertThat((Integer) JsonPath.read(p0, "$.data.totalElements")).isEqualTo((int) grand);
        String p1 = exchange(HttpMethod.GET, "/api/v1/audit?size=2&page=1", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(p1, "$.data.totalElements")).isEqualTo((int) grand);

        // Far-out page: empty content, envelope stays honest.
        String far = exchange(HttpMethod.GET, "/api/v1/audit?size=2&page=5000", hrHeaders(), null).getBody();
        assertThat((Integer) JsonPath.read(far, "$.data.content.size()")).isEqualTo(0);
        assertThat((Integer) JsonPath.read(far, "$.data.totalElements")).isEqualTo((int) grand);
    }

    // -------------------------------------------------------- entity facets

    @Test
    @Order(5)
    void entityTypesFacetListsDistinctFamilies() {
        ResponseEntity<String> types = exchange(HttpMethod.GET, "/api/v1/audit/entity-types", adminHeaders(), null);
        assertThat(types.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        java.util.List<String> list = (java.util.List<String>) JsonPath.read(types.getBody(), "$.data");
        assertThat(list).contains("LEAVE_TYPE", "LEAVE_REQUEST");
    }

    // --------------------------------------------------------------- export

    @Test
    @Order(6)
    void csvExportMirrorsTheFeedAndKeepsTheAdminHrOnlyPolicy() {
        // RBAC: the export follows the audit read matrix, not the reports one —
        // MANAGER can export the other reports but never the HR audit trail.
        assertThat(rest.exchange(url("/api/v1/audit/export.csv"), HttpMethod.GET,
                new HttpEntity<>(employeeHeaders()), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(url("/api/v1/audit/export.csv"), HttpMethod.GET,
                new HttpEntity<>(managerHeaders()), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(url("/api/v1/audit/export.csv"), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), byte[].class).getStatusCode().value())
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());

        ResponseEntity<byte[]> ok = rest.exchange(url("/api/v1/audit/export.csv"), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(ok.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("audit-log.csv");

        String body = new String(ok.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("\ufeff"); // UTF-8 BOM for Excel
        String[] lines = body.split("\r\n");
        assertThat(lines[0]).isEqualTo(
                "\ufeffID,Timestamp,Actor,Actor Email,Role,Action,Entity Type,Entity ID,Entity Label,Details");

        // Row-for-row with the JSON feed: same filters, same newest-first order.
        String feed = exchange(HttpMethod.GET, "/api/v1/audit?size=50", hrHeaders(), null).getBody();
        int feedTotal = (Integer) JsonPath.read(feed, "$.data.totalElements");
        assertThat(lines.length - 1).isEqualTo(feedTotal);
        String newestFeedId = JsonPath.read(feed, "$.data.content[0].id").toString();
        assertThat(lines[1]).startsWith(newestFeedId + ",");

        // Attribution and payload survive the CSV round trip.
        assertThat(body).contains("hr@hrgenius.local");
        assertThat(body).contains(TYPE_NAME);
    }

    @Test
    @Order(7)
    void csvExportAppliesTheSameFiltersSearchAndSqlSideLimit() {
        // Action facet: header + exactly the single approved request row.
        ResponseEntity<byte[]> actionOnly = rest.exchange(
                url("/api/v1/audit/export.csv?action=LEAVE_REQUEST_APPROVED"), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class);
        String actionBody = new String(actionOnly.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(actionBody.split("\r\n")).hasSize(2);
        assertThat(actionBody).contains("LEAVE_REQUEST_APPROVED");

        // Search narrows exactly like the list endpoint ("Yearly" only ever
        // appears in the created/updated DETAILS; a literal % matches nothing).
        String yearly = new String(rest.exchange(
                url("/api/v1/audit/export.csv?search=Yearly"), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class).getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(yearly.split("\r\n")).hasSize(3);

        String noMatch = new String(rest.exchange(
                URI.create(url("/api/v1/audit/export.csv?search=%25")), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class).getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(noMatch.split("\r\n")).hasSize(1);

        // The limit is a SQL-side cap: limit=2 returns the two NEWEST rows.
        String newest = exchange(HttpMethod.GET, "/api/v1/audit?size=1", hrHeaders(), null).getBody();
        String newestId = JsonPath.read(newest, "$.data.content[0].id").toString();
        String limited = new String(rest.exchange(
                url("/api/v1/audit/export.csv?limit=2"), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class).getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(limited.split("\r\n")).hasSize(3);
        assertThat(limited.split("\r\n")[1]).startsWith(newestId + ",");

        // limit=0 falls back to the default; an oversized limit is clamped,
        // so both return the same full result set as no limit at all.
        String plain = new String(rest.exchange(
                url("/api/v1/audit/export.csv"), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class).getBody(), java.nio.charset.StandardCharsets.UTF_8);
        for (String q : new String[] { "?limit=0", "?limit=99999999", "" }) {
            String body = new String(rest.exchange(
                    URI.create(url("/api/v1/audit/export.csv" + q)), HttpMethod.GET,
                    new HttpEntity<>(hrHeaders()), byte[].class).getBody(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(body.split("\r\n").length).as("export %s", q).isEqualTo(plain.split("\r\n").length);
        }

        // Malformed dates fail validation (400), matching the list endpoint.
        assertThat(rest.exchange(URI.create(url("/api/v1/audit/export.csv?from=not-a-date")), HttpMethod.GET,
                new HttpEntity<>(hrHeaders()), byte[].class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------- helpers

    private long total(String pathWithQuery) {
        String body = exchange(HttpMethod.GET, pathWithQuery, hrHeaders(), null).getBody();
        return ((Number) JsonPath.read(body, "$.data.totalElements")).longValue();
    }

    private String iso(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private <T> ResponseEntity<String> exchange(HttpMethod method, String pathWithQuery, HttpHeaders headers, T body) {
        URI uri = UriComponentsBuilder.fromHttpUrl(url(pathWithQuery)).build().toUri();
        return rest.exchange(uri, method, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders headers(String email, String password) {
        ResponseEntity<String> login = rest.postForEntity(URI.create(url("/api/v1/auth/login")),
                new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()), String.class);
        String token = JsonPath.read(login.getBody(), "$.data.token");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders adminHeaders() {
        return headers("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders hrHeaders() {
        return headers("hr@hrgenius.local", "Hr@12345");
    }

    private HttpHeaders managerHeaders() {
        return headers("manager@hrgenius.local", "Manager@123");
    }

    private HttpHeaders employeeHeaders() {
        return headers("employee@hrgenius.local", "Employee@123");
    }
}
