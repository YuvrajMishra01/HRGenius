package com.hrgenius.analytics;

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

import com.hrgenius.auth.LoginRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 13 analytics over real HTTP. Pure seed assertions — this class runs
 * FIRST alphabetically (analytics < attendance < auth < …) and only issues
 * read-only GETs, so the exact numbers below are the V2 seed:
 *
 *  Employees: 7 (6 fixed + EMP007 hired 10 days ago), all ACTIVE.
 *   Departments: Engineering 4 · Finance/HR/Sales 1 each.
 *   Types: FULL_TIME 6 · INTERN 1.
 *   Tenure buckets (<1 / 1–3 / 3–5 / 5+): 1 / 1 / 3 / 2; avg tenure 3.6 y.
 *  Applications: 4 — job 1 ×2 (INTERVIEW, SCREENING), job 2 ×2
 *   (SHORTLISTED, APPLIED) → all active, 0 selected/rejected.
 *  Interviews: 1 SCHEDULED (no result), 1 COMPLETED with PASS → passRate 100.0.
 *  Leave (current year): CASUAL 2 · SICK 1 · EARNED 1; PENDING 2/APPROVED 1/REJECTED 1.
 *  Payroll: one period (last month), 7 payslips, net 406,000 — the V2
 *   comment says "6 employees" but EMP007 is inserted BEFORE the payroll
 *   SELECT, so the period covers 7 (4×56,250 + 91,250 + 33,500 + 56,250);
 *   matches the Phase 9 live verification (₹4,06,000).
 *  Performance: 2 reviews (1 SUBMITTED rating 4 — official, 1 DRAFT rating 3 —
 *   excluded) → average 4.0, rating distribution [{4, 1}].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalyticsApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ------------------------------------------------------------ workforce

    @Test
    @Order(1)
    void workforceCompositionMatchesSeed() {
        String body = get("/analytics/workforce");
        assertThat(body)
                .contains("\"active\":7")
                .contains("\"terminated\":0")
                .contains("\"avgTenureYears\":3.6")
                .contains("\"name\":\"Engineering\",\"count\":4")
                .contains("\"type\":\"FULL_TIME\",\"count\":6")
                .contains("\"type\":\"INTERN\",\"count\":1")
                .contains("\"label\":\"< 1 year\",\"count\":1")
                .contains("\"label\":\"1–3 years\",\"count\":1")
                .contains("\"label\":\"3–5 years\",\"count\":3")
                .contains("\"label\":\"5+ years\",\"count\":2");
        // Engineering is the largest department — assert it leads the list.
        assertThat(body.indexOf("\"name\":\"Engineering\""))
                .isLessThan(body.indexOf("\"name\":\"Finance\""));
    }

    // --------------------------------------------------------------- funnel

    @Test
    @Order(2)
    void hiringFunnelAggregatesApplicationsPerJob() {
        String body = get("/analytics/funnel");
        assertThat(body)
                .contains("\"totalApplications\":4")
                .contains("\"title\":\"Backend Developer\",\"status\":\"OPEN\",\"applications\":2")
                .contains("\"title\":\"HR Generalist\",\"status\":\"OPEN\",\"applications\":2");
        // All four seed applications are still active; nothing terminal.
        assertThat(body)
                .contains("\"active\":2,\"selected\":0,\"rejected\":0")
                .doesNotContain("\"selected\":1");
    }

    // ----------------------------------------------------------- interviews

    @Test
    @Order(3)
    void interviewQualityUsesOnlyCompletedResults() {
        String body = get("/analytics/interviews");
        assertThat(body)
                .contains("\"completed\":1")
                .contains("\"scheduled\":1")
                .contains("\"cancelled\":0")
                .contains("\"pass\":1")
                .contains("\"fail\":0")
                .contains("\"onHold\":0")
                .contains("\"passRate\":100.0");
    }

    // ---------------------------------------------------------------- leave

    @Test
    @Order(4)
    void leaveDemandPerTypeAndStatus() {
        String body = get("/analytics/leave");
        assertThat(body).contains("\"year\":" + java.time.LocalDate.now().getYear());
        // Busiest type first (2 casual requests vs 1 each for the others).
        assertThat(body)
                .contains("\"name\":\"CASUAL_LEAVE\",\"count\":2")
                .contains("\"name\":\"SICK_LEAVE\",\"count\":1")
                .contains("\"name\":\"EARNED_LEAVE\",\"count\":1");
        assertThat(body.indexOf("CASUAL_LEAVE")).isLessThan(body.indexOf("SICK_LEAVE"));
        assertThat(body)
                .contains("\"status\":\"PENDING\",\"count\":2")
                .contains("\"status\":\"APPROVED\",\"count\":1")
                .contains("\"status\":\"REJECTED\",\"count\":1");
    }

    // -------------------------------------------------------- payroll trend

    @Test
    @Order(5)
    void payrollTrendListsEveryPeriodOldestFirst() {
        String body = get("/analytics/payroll-trend");
        java.time.YearMonth lastMonth = java.time.YearMonth.now().minusMonths(1);
        assertThat(body)
                .contains("\"payslips\":7")
                .contains("\"totalNet\":406000");
        // Seed has exactly one period, so chronology is trivially satisfied;
        // assert the year/month pair of that period is present.
        assertThat(body)
                .contains("\"year\":" + lastMonth.getYear())
                .contains("\"month\":" + lastMonth.getMonthValue());
    }

    // ---------------------------------------------------------- performance

    @Test
    @Order(6)
    void performanceCountsOfficialRatingsOnly() {
        String body = get("/analytics/performance");
        assertThat(body)
                .contains("\"totalReviews\":2")
                .contains("\"averageRating\":4.0")
                .contains("\"status\":\"DRAFT\",\"count\":1")
                .contains("\"status\":\"SUBMITTED\",\"count\":1")
                .contains("\"status\":\"ACKNOWLEDGED\",\"count\":0")
                .contains("\"rating\":4,\"count\":1");
        // The DRAFT's rating of 3 must never appear in the distribution.
        assertThat(body).doesNotContain("\"rating\":3,");
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void analyticsIsAdminAndHrOnly() {
        for (String path : new String[] { "/analytics/workforce", "/analytics/funnel",
                "/analytics/interviews", "/analytics/leave", "/analytics/payroll-trend",
                "/analytics/performance" }) {
            assertThat(statusAs("manager@hrgenius.local", "Manager@123", path))
                    .as("MANAGER %s", path).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(statusAs("employee@hrgenius.local", "Employee@123", path))
                    .as("EMPLOYEE %s", path).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(rest.getForEntity(url() + "/api/v1/analytics/workforce", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/analytics/workforce")).contains("\"active\":7");
        assertThat(getWithRole("hr@hrgenius.local", "Hr@12345", "/analytics/performance"))
                .contains("\"totalReviews\":2");
    }

    // -------------------------------------------------------------- helpers

    private String get(String path) {
        return getWithRole("admin@hrgenius.local", "Admin@123", path);
    }

    private String getWithRole(String email, String password, String path) {
        ResponseEntity<String> response = rest.exchange(url() + "/api/v1" + path,
                HttpMethod.GET, new HttpEntity<>(bearer(email, password)), String.class);
        assertThat(response.getStatusCode()).as("GET %s as %s", path, email).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private org.springframework.http.HttpStatusCode statusAs(String email, String password, String path) {
        return rest.exchange(url() + "/api/v1" + path, HttpMethod.GET,
                new HttpEntity<>(bearer(email, password)), String.class).getStatusCode();
    }

    private HttpHeaders bearer(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), loginHeaders), String.class);
        assertThat(login.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = login.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
