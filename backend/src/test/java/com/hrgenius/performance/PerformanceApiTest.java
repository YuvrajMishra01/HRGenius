package com.hrgenius.performance;

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
 * Performance reviews over real HTTP against the seeded H2 database (Phase 10).
 *
 * Class order is alphabetical (junit-platform.properties): … onboarding →
 * payroll → performance → recruitment. No other suite reads PERFORMANCE_REVIEWS,
 * so mutations here are safe. Seed: review id 1 (emp 2, SUBMITTED, rating 4,
 * '2025-H2', reviewer emp 1) and id 2 (emp 3, DRAFT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static volatile Long createdReviewId;
    private static volatile Long submittedReviewId;

    // ------------------------------------------------------------- create

    @Test
    @Order(1)
    void createDefaultsToDraftWithGoals() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(4L, 1L, "2026-H1", "Ship the payroll module"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"DRAFT\"");
        assertThat(body).contains("\"employeeName\":\"Sneha Patil\"");
        assertThat(body).contains("\"reviewerName\":\"Rahul Verma\"");
        assertThat(body).contains("\"reviewPeriod\":\"2026-H1\"");
        assertThat(body).contains("Ship the payroll module");
        createdReviewId = extractId(body);
        assertThat(createdReviewId).isNotNull();
    }

    @Test
    @Order(2)
    void duplicatePeriodIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(4L, 1L, "2026-H1", "Duplicate attempt"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("one review per employee per period");
    }

    @Test
    @Order(3)
    void selfReviewIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(5L, 5L, "2026-H1", "Self review"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("cannot be written by the reviewed employee");
    }

    @Test
    @Order(4)
    void unknownEmployeeOrReviewerIs404() {
        ResponseEntity<String> employee = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(9999L, 1L, "2026-H1", "?"));
        assertThat(employee.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> reviewer = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(5L, 9999L, "2026-H1", "?"));
        assertThat(reviewer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------ editing

    @Test
    @Order(10)
    void updateDraftEditsNarrativeFields() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId, hrHeaders(),
                new PerformanceDto.UpdateRequest(
                        "Ship the payroll module and mentor a junior",
                        "Reliable delivery, strong testing habits",
                        "Presentation skills",
                        "Draft — pending 1:1 discussion"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"DRAFT\"");
        assertThat(body).contains("mentor a junior");
        assertThat(body).contains("Presentation skills");
        assertThat(body).contains("pending 1:1 discussion");
    }

    @Test
    @Order(11)
    void ratingOutOfRangeIs400() {
        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId + "/rate", hrHeaders(),
                "{\"rating\":0}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId + "/rate", hrHeaders(),
                "{\"rating\":6}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(12)
    void rateSubmitsReviewWithRating() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId + "/rate", hrHeaders(),
                new PerformanceDto.RateRequest(3, "Solid progress, focus on delivery cadence"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"SUBMITTED\"");
        assertThat(body).contains("\"rating\":3");
        assertThat(body).contains("delivery cadence");

        // Re-rating a submitted review is a 409
        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId + "/rate", hrHeaders(),
                new PerformanceDto.RateRequest(4, "Changed my mind"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(13)
    void editSubmittedIs409() {
        // A second review that will be submitted, then edited → 409
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(5L, 1L, "2026-H1", "Close the Q3 backlog"));
        submittedReviewId = extractId(created.getBody());
        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + submittedReviewId + "/rate", hrHeaders(),
                new PerformanceDto.RateRequest(5, "Excellent quarter"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + submittedReviewId, hrHeaders(),
                new PerformanceDto.UpdateRequest("New goals", null, null, null))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --------------------------------------------------------- acknowledge

    @Test
    @Order(20)
    void acknowledgeSubmittedReview() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId + "/acknowledge", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"ACKNOWLEDGED\"");

        // Acknowledging twice is a 409
        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + createdReviewId + "/acknowledge", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(21)
    void acknowledgeDraftIs409() {
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(6L, 1L, "2026-H1", "Support the leave rollout"));
        Long id = extractId(created.getBody());
        assertThat(exchange(HttpMethod.PATCH,
                "/api/v1/performance/reviews/" + id + "/acknowledge", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // -------------------------------------------------------------- delete

    @Test
    @Order(30)
    void deleteDraftOnly() {
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/performance/reviews", hrHeaders(),
                new PerformanceDto.CreateRequest(7L, 1L, "2026-H1", "Delete me"));
        Long id = extractId(created.getBody());
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/performance/reviews/" + id, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Acknowledged reviews keep history
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/performance/reviews/" + createdReviewId, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------- summary

    @Test
    @Order(40)
    void summaryCountsAndAverages() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/performance/summary",
                managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();

        // total = seed 2 + order1 + order13 + order21 = 5 (order30's was deleted)
        assertThat(body).contains("\"totalReviews\":5");
        // drafts = seed emp3 + order21's = 2
        assertThat(body).contains("\"draftCount\":2");
        // submitted = seed emp2 + order13's = 2
        assertThat(body).contains("\"submittedCount\":2");
        assertThat(body).contains("\"acknowledgedCount\":1");
        // official ratings only (seed draft's rating excluded): seed 4 +
        // order1 3 (acknowledged) + order13 5 → avg (4+3+5)/3 = 4.0
        assertThat(body).contains("\"averageRating\":4.0");
        assertThat(body).contains("\"rating\":3,\"count\":1");
        assertThat(body).contains("\"rating\":4,\"count\":1");
        assertThat(body).contains("\"rating\":5,\"count\":1");
    }

    @Test
    @Order(41)
    void listFiltersByEmployee() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/performance/reviews?employeeId=2", managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"employeeName\":\"Anita Desai\"");
        assertThat(body).contains("\"rating\":4");
        // Only one review for employee 2
        assertThat(body.split("\"employeeId\":2").length - 1).isEqualTo(1);
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void employeeForbiddenManagerReadsButCannotWrite() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/performance/reviews", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/performance/summary", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.POST, "/api/v1/performance/reviews", managerHeaders(),
                new PerformanceDto.CreateRequest(4L, 1L, "2026-H2", "nope"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/performance/reviews", adminHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------- helpers

    private static Long extractId(String body) {
        int idx = body.indexOf("\"id\":");
        return Long.parseLong(body.substring(idx + 5, body.indexOf(',', idx)));
    }

    private HttpHeaders adminHeaders() {
        return bearer("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders hrHeaders() {
        return bearer("hr@hrgenius.local", "Hr@12345");
    }

    private HttpHeaders managerHeaders() {
        return bearer("manager@hrgenius.local", "Manager@123");
    }

    private HttpHeaders employeeHeaders() {
        return bearer("employee@hrgenius.local", "Employee@123");
    }

    private HttpHeaders bearer(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new com.hrgenius.auth.LoginRequest(email, password), loginHeaders), String.class);
        assertThat(login.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = login.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return rest.exchange(url() + path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String url() {
        return "http://localhost:" + port;
    }
}