package com.hrgenius.common;

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
 * Cross-cutting list hardening (Phase 14) over real HTTP.
 *
 * Every list endpoint now shares one contract: PageResponse envelope
 * (content/page/size/totalElements/totalPages/first/last), `search` +
 * `status` filters, clamped paging (size ≤ 100), and 400 (never 500) for
 * bad enum/numeric params or missing required params.
 *
 * Assertions are written to survive alphabetical suite order: exact counts
 * only for entities no earlier-running suite mutates (jobs, candidates,
 * applications, interviews — RecruitmentApiTest runs after this class);
 * everything else asserts filtered subsets or pure envelope structure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ListHardeningApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ------------------------------------------------------------ envelope

    @Test
    @Order(1)
    void everyListUsesThePageResponseEnvelopeWithClampedDefaults() {
        // Jobs: 2 seeded, none of the earlier suites create jobs.
        ResponseEntity<String> jobs = exchange(HttpMethod.GET, "/api/v1/jobs", adminHeaders(), null);
        assertThat(jobs.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = jobs.getBody();
        assertThat(body).contains("\"content\":[");
        assertThat(body).contains("\"totalElements\":2");
        assertThat(body).contains("\"totalPages\":1");
        assertThat(body).contains("\"page\":0");
        assertThat(body).contains("\"size\":20"); // default page size
        assertThat(body).contains("\"first\":true");
        assertThat(body).contains("\"last\":true");
        assertThat(body).contains("Backend Developer");

        assertThat(exchange(HttpMethod.GET, "/api/v1/candidates", adminHeaders(), null).getBody())
                .contains("\"totalElements\":4");
        assertThat(exchange(HttpMethod.GET, "/api/v1/applications", adminHeaders(), null).getBody())
                .contains("\"totalElements\":4");
        assertThat(exchange(HttpMethod.GET, "/api/v1/interviews", adminHeaders(), null).getBody())
                .contains("\"totalElements\":2");
    }

    // ----------------------------------------------------- search + status

    @Test
    @Order(2)
    void searchAndStatusFiltersNarrowEveryList() {
        // search: case-insensitive, across title/department/location.
        String backend = exchange(HttpMethod.GET, "/api/v1/jobs?search=backend", adminHeaders(), null).getBody();
        assertThat(backend).contains("Backend Developer");
        assertThat(backend).contains("\"totalElements\":1");

        String none = exchange(HttpMethod.GET, "/api/v1/jobs?search=zzz-no-such-role", adminHeaders(), null).getBody();
        assertThat(none).contains("\"content\":[]");
        assertThat(none).contains("\"totalElements\":0");

        // status: enum filters on every list that has one.
        assertThat(exchange(HttpMethod.GET, "/api/v1/jobs?status=OPEN", adminHeaders(), null).getBody())
                .contains("\"totalElements\":2");
        assertThat(exchange(HttpMethod.GET, "/api/v1/candidates?status=NEW", adminHeaders(), null).getBody())
                .contains("Sanjay Gupta");
        assertThat(exchange(HttpMethod.GET, "/api/v1/applications?status=INTERVIEW", adminHeaders(), null).getBody())
                .contains("\"totalElements\":1");
        String completed = exchange(HttpMethod.GET, "/api/v1/interviews?status=COMPLETED", adminHeaders(), null).getBody();
        assertThat(completed).contains("\"totalElements\":1");
        assertThat(completed).contains("PASS");

        // leave requests: search combines with status (seed row is PENDING).
        String fever = exchange(HttpMethod.GET, "/api/v1/leave/requests?search=fever", adminHeaders(), null).getBody();
        assertThat(fever).contains("Fever and medical rest");
        assertThat(fever).contains("\"totalElements\":1");
        assertThat(exchange(HttpMethod.GET, "/api/v1/leave/requests?search=fever&status=APPROVED", adminHeaders(), null).getBody())
                .contains("\"content\":[]");

        // performance / onboarding: filtered subset contains the seed row.
        assertThat(exchange(HttpMethod.GET, "/api/v1/performance/reviews?status=DRAFT", adminHeaders(), null).getBody())
                .contains("DRAFT");
        assertThat(exchange(HttpMethod.GET, "/api/v1/onboardings?status=IN_PROGRESS", adminHeaders(), null).getBody())
                .contains("Rohan");

        // documents: search over type/file name (employee 2 has seed docs).
        String docs = exchange(HttpMethod.GET, "/api/v1/documents?employeeId=2", adminHeaders(), null).getBody();
        assertThat(docs).contains("\"content\":[");

        // departments /designations: dedicated paged admin views.
        String depts = exchange(HttpMethod.GET, "/api/v1/departments/page?search=eng", adminHeaders(), null).getBody();
        assertThat(depts).contains("Engineering");
        String desigs = exchange(HttpMethod.GET, "/api/v1/designations/page?size=2", adminHeaders(), null).getBody();
        assertThat(desigs).contains("\"size\":2");
        assertThat(desigs).contains("\"content\":[");
    }

    // ------------------------------------------------------------ clamping

    @Test
    @Order(3)
    void paginationInputsAreClampedAndFarPagesAreEmpty() {
        String page0 = exchange(HttpMethod.GET, "/api/v1/jobs?page=0&size=1", adminHeaders(), null).getBody();
        assertThat(page0).contains("\"totalPages\":2");
        assertThat(page0).contains("\"last\":false");

        String page1 = exchange(HttpMethod.GET, "/api/v1/jobs?page=1&size=1", adminHeaders(), null).getBody();
        assertThat(page1).contains("\"last\":true");

        // A far-out page yields empty content, never an exception.
        String far = exchange(HttpMethod.GET, "/api/v1/jobs?page=99&size=1", adminHeaders(), null).getBody();
        assertThat(far).contains("\"content\":[]");
        assertThat(far).contains("\"page\":99");
        assertThat(far).contains("\"last\":true");

        // size clamps: 0/undefined → default 20; huge → 100; negative page → 0.
        assertThat(exchange(HttpMethod.GET, "/api/v1/jobs?size=0", adminHeaders(), null).getBody())
                .contains("\"size\":20");
        assertThat(exchange(HttpMethod.GET, "/api/v1/jobs?size=9999", adminHeaders(), null).getBody())
                .contains("\"size\":100");
        assertThat(exchange(HttpMethod.GET, "/api/v1/jobs?page=-5", adminHeaders(), null).getBody())
                .contains("\"page\":0");

        // Over-long search terms are truncated, not echoed.
        String longSearch = "x".repeat(200);
        ResponseEntity<String> truncated = exchange(HttpMethod.GET,
                "/api/v1/jobs?search=" + longSearch, adminHeaders(), null);
        assertThat(truncated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(truncated.getBody()).contains("\"content\":[]");

        // Employees (Phase 3 real SQL paging) keep the same clamp contract.
        assertThat(exchange(HttpMethod.GET, "/api/v1/employees?size=500", adminHeaders(), null).getBody())
                .contains("\"size\":100");
    }

    // ---------------------------------------------------------- validation

    @Test
    @Order(4)
    void invalidParamsAndMissingParamsAre400Never500() {
        ResponseEntity<String> badEnum = exchange(HttpMethod.GET, "/api/v1/jobs?status=NOT_A_STATUS",
                adminHeaders(), null);
        assertThat(badEnum.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badEnum.getBody()).contains("Invalid value for parameter");

        ResponseEntity<String> badNumber = exchange(HttpMethod.GET, "/api/v1/employees?size=abc",
                adminHeaders(), null);
        assertThat(badNumber.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // documents requires employeeId — missing or malformed → 400.
        ResponseEntity<String> missing = exchange(HttpMethod.GET, "/api/v1/documents", adminHeaders(), null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody()).contains("Missing required parameter: employeeId");

        ResponseEntity<String> malformed = exchange(HttpMethod.GET, "/api/v1/documents?employeeId=abc",
                adminHeaders(), null);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------- KPI counts endpoint

    @Test
    @Order(5)
    void applicationCountsFeedKpiStripsIndependentlyOfPaging() {
        String counts = exchange(HttpMethod.GET, "/api/v1/applications/counts", adminHeaders(), null).getBody();
        assertThat(counts).contains("\"APPLIED\":1");
        assertThat(counts).contains("\"INTERVIEW\":1");
        assertThat(counts).contains("\"SCREENING\":1");
        assertThat(counts).contains("\"SHORTLISTED\":1");
    }

    // ---------------------------------------------------------------- RBAC

    @Test
    @Order(6)
    void listEndpointsKeepTheirRoleGuards() {
        String[] listPaths = {
                "/api/v1/jobs", "/api/v1/applications", "/api/v1/leave/requests",
                "/api/v1/performance/reviews", "/api/v1/onboardings", "/api/v1/departments/page",
                "/api/v1/designations/page", "/api/v1/employees",
                "/api/v1/documents?employeeId=2",
        };
        for (String path : listPaths) {
            assertThat(exchange(HttpMethod.GET, path, employeeHeaders(), null).getStatusCode())
                    .as("EMPLOYEE %s", path).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exchange(HttpMethod.GET, path, anonymousHeaders(), null).getStatusCode())
                    .as("ANON %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(exchange(HttpMethod.GET, "/api/v1/jobs", managerHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Notifications stay open to every authenticated role (personal feed).
        assertThat(exchange(HttpMethod.GET, "/api/v1/notifications", employeeHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------- helpers

    private HttpHeaders login(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), loginHeaders), String.class);
        assertThat(response.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        return login("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders managerHeaders() {
        return login("manager@hrgenius.local", "Manager@123");
    }

    private HttpHeaders employeeHeaders() {
        return login("employee@hrgenius.local", "Employee@123");
    }

    private HttpHeaders anonymousHeaders() {
        return new HttpHeaders();
    }

    private String url() {
        return "http://localhost:" + port;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return rest.exchange(url() + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
