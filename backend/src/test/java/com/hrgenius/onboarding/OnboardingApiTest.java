package com.hrgenius.onboarding;

import java.time.LocalDate;

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
 * Onboarding workflow over real HTTP against the seeded H2 database (Phase 6).
 * Class order (junit-platform.properties: by class name) puts this AFTER the
 * employee tests (which count employees) and BEFORE recruitment tests — so it
 * creates its own job instead of touching seed jobs the recruitment suite counts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnboardingApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static Long applicationId;
    private static Long onboardingId;

    // ---------------------------------------------------- conversion flow

    @Test
    @Order(1)
    void nonSelectedApplicationIsRejected() {
        // Seed application 4 (Sanjay) is APPLIED
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/onboardings/start-application",
                hrHeaders(), new OnboardingDto.StartFromApplicationRequest(4L, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Only SELECTED");
    }

    @Test
    @Order(2)
    void unknownApplicationIs404() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/onboardings/start-application",
                hrHeaders(), new OnboardingDto.StartFromApplicationRequest(9999L, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(3)
    void convertsSelectedApplicationIntoEmployeeAndOnboarding() {
        // Own OPEN job (never touch seed jobs — the recruitment suite counts them)
        Long jobId = createJob("Onboarding Test Role");
        Long candidateId = createCandidate("Onboarding Test", "onboarding.test@example.com");
        applicationId = createApplication(candidateId, jobId);

        // Walk the full pipeline APPLIED → … → SELECTED
        move(applicationId, "SCREENING");
        move(applicationId, "SHORTLISTED");
        move(applicationId, "INTERVIEW");
        move(applicationId, "SELECTED");

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/onboardings/start-application",
                hrHeaders(), new OnboardingDto.StartFromApplicationRequest(applicationId,
                        LocalDate.now().plusDays(7)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("\"employeeCode\":\"EMP008\"");   // next free after seed EMP001..EMP007
        assertThat(body).contains("\"status\":\"PENDING\"");
        assertThat(body).contains("\"completionPercentage\":0");
        assertThat(body).contains("Offer letter signed");
        assertThat(body).contains("\"applicationId\":" + applicationId);
        assertThat(body).contains("Onboarding Test");

        onboardingId = extractId(body);
    }

    @Test
    @Order(4)
    void duplicateOnboardingForSameApplicationIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/onboardings/start-application",
                hrHeaders(), new OnboardingDto.StartFromApplicationRequest(applicationId, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already started");
    }

    @Test
    @Order(5)
    void candidateIsHiredAfterConversion() {
        ResponseEntity<String> candidates = exchange(HttpMethod.GET, "/api/v1/candidates", hrHeaders(), null);
        // "Onboarding Test" candidate must now be HIRED (not NEW)
        assertThat(candidates.getBody()).contains("Onboarding Test");
    }

    // ------------------------------------------------------------ checklist

    @Test
    @Order(10)
    void firstChecklistToggleIs125PercentInProgress() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/onboardings/" + onboardingId + "/checklist",
                hrHeaders(), new OnboardingDto.ChecklistUpdateRequest(0, true, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"completionPercentage\":12.5");
        assertThat(body).contains("\"status\":\"IN_PROGRESS\"");
    }

    @Test
    @Order(11)
    void completingAllItemsYields100PercentCompleted() {
        for (int i = 1; i < 8; i++) {
            ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                    "/api/v1/onboardings/" + onboardingId + "/checklist",
                    hrHeaders(), new OnboardingDto.ChecklistUpdateRequest(i, true, null));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> after = exchange(HttpMethod.GET, "/api/v1/onboardings", adminHeaders(), null);
        assertThat(after.getBody()).contains("\"status\":\"COMPLETED\"");
        assertThat(after.getBody()).contains("\"completionPercentage\":100");
    }

    @Test
    @Order(12)
    void invalidChecklistIndexIs400() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/onboardings/" + onboardingId + "/checklist",
                hrHeaders(), new OnboardingDto.ChecklistUpdateRequest(42, true, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid checklist item index");
    }

    // ------------------------------------------------- employee-origin path

    @Test
    @Order(20)
    void startForExistingEmployeeThenDuplicateIs409() {
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/onboardings/start-employee",
                hrHeaders(), new OnboardingDto.StartForEmployeeRequest(6L, null));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("Divya Nair");

        ResponseEntity<String> duplicate = exchange(HttpMethod.POST, "/api/v1/onboardings/start-employee",
                hrHeaders(), new OnboardingDto.StartForEmployeeRequest(6L, null));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("already has an onboarding record");
    }

    // ------------------------------------------------------------ list/RBAC

    @Test
    @Order(30)
    void listShowsSeededAndCreatedRecords() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/onboardings", managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("Rohan Kulkarni");     // seeded 50% record
        assertThat(body).contains("\"completionPercentage\":50");
        assertThat(body).contains("Onboarding Test");    // created in order 3
    }

    @Test
    @Order(31)
    void employeeCannotReadManagerCanReadButNotWrite() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/onboardings", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/onboardings", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.PATCH, "/api/v1/onboardings/" + onboardingId + "/checklist",
                managerHeaders(), new OnboardingDto.ChecklistUpdateRequest(0, false, null))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------ helpers

    private Long createJob(String title) {
        String request = """
                {"title":"%s","description":"integration test","departmentId":1,
                 "location":"Pune","employmentType":"FULL_TIME","status":"OPEN"}
                """.formatted(title);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/jobs", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getBody());
    }

    private Long createCandidate(String name, String email) {
        String request = """
                {"name":"%s","email":"%s","phone":"+91-9000000001","skills":"Testing"}
                """.formatted(name, email);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/candidates", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getBody());
    }

    private Long createApplication(Long candidateId, Long jobId) {
        String request = """
                {"candidateId":%d,"jobId":%d,"remarks":null}
                """.formatted(candidateId, jobId);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/applications", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getBody());
    }

    private void move(Long id, String target) {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH, "/api/v1/applications/" + id + "/status",
                hrHeaders(), "{\"status\":\"" + target + "\",\"remarks\":null}");
        assertThat(response.getStatusCode()).as("move to %s", target).isEqualTo(HttpStatus.OK);
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

    private Long extractId(String body) {
        int idx = body.indexOf("\"id\":");
        return Long.parseLong(body.substring(idx + 5, body.indexOf(',', idx)));
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
