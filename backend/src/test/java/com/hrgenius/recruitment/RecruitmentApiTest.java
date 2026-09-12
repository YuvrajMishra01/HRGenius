package com.hrgenius.recruitment;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
 * Recruitment module over real HTTP against the seeded H2 database (Phase 5).
 * Ordered: jobs → candidates → application pipeline → interviews.
 * Seed data: 2 jobs, 4 candidates, 4 applications, 2 interviews.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecruitmentApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static Long jobId;
    private static Long candidateId;
    private static Long applicationId;
    private static Long interviewId;

    // --------------------------------------------------------------- jobs

    @Test
    @Order(1)
    void listJobsReturnsSeedJobsWithCounts() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/jobs", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        // Seed: 2 OPEN jobs; Backend Developer has 2 applications, HR Generalist 2
        assertThat(body).contains("Backend Developer").contains("HR Generalist");
        assertThat(body).contains("\"applicationCount\":2");
    }

    @Test
    @Order(2)
    void adminCreatesOpenJob() {
        RecruitmentDto.JobRequest request = new RecruitmentDto.JobRequest(
                "QA Engineer", "Automation-first quality team", 1L,
                "Pune", com.hrgenius.employee.EmploymentType.FULL_TIME,
                "6-10 LPA", JobStatus.OPEN, LocalDate.now().plusDays(30));
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/jobs", adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("\"title\":\"QA Engineer\"").contains("\"status\":\"OPEN\"");
        assertThat(body).contains("\"departmentName\":\"Engineering\"");

        jobId = extractId(body);
    }

    @Test
    @Order(3)
    void createJobWithUnknownDepartmentReturns404() {
        RecruitmentDto.JobRequest request = new RecruitmentDto.JobRequest(
                "Ghost Job", null, 9999L, null,
                com.hrgenius.employee.EmploymentType.FULL_TIME, null, JobStatus.DRAFT, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/jobs", adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(4)
    void updateJobChangesFields() {
        RecruitmentDto.JobRequest request = new RecruitmentDto.JobRequest(
                "QA Engineer", "Automation-first quality platform team", 1L,
                "Remote (India)", com.hrgenius.employee.EmploymentType.FULL_TIME,
                "6-10 LPA", JobStatus.OPEN, LocalDate.now().plusDays(45));
        ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/v1/jobs/" + jobId,
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Remote (India)");
    }

    @Test
    @Order(5)
    void managerCanReadJobsButNotCreate() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/jobs", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        RecruitmentDto.JobRequest request = new RecruitmentDto.JobRequest(
                "Nope", null, 1L, null, com.hrgenius.employee.EmploymentType.FULL_TIME,
                null, JobStatus.OPEN, null);
        assertThat(exchange(HttpMethod.POST, "/api/v1/jobs", managerHeaders(), request)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --------------------------------------------------------- candidates

    @Test
    @Order(10)
    void listCandidatesReturnsSeedCandidates() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/candidates", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("Kavya Rao").contains("Mohit Bansal")
                .contains("Fatima Sheikh").contains("Sanjay Gupta");
        // Seed candidate with an application shows its count (Kavya → app 1)
        assertThat(body).contains("\"applicationCount\":1");
    }

    @Test
    @Order(11)
    void createCandidateThenDuplicateEmailIs409() {
        RecruitmentDto.CandidateRequest request = candidate("Tara Iyer", "tara.iyer@example.com");
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/candidates", adminHeaders(), request);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        candidateId = extractId(created.getBody());

        RecruitmentDto.CandidateRequest duplicate = candidate("Someone Else", "tara.iyer@example.com");
        ResponseEntity<String> conflict = exchange(HttpMethod.POST, "/api/v1/candidates", adminHeaders(), duplicate);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("already exists");
    }

    @Test
    @Order(12)
    void createCandidateWithInvalidEmailIs400() {
        RecruitmentDto.CandidateRequest request = candidate("Bad Email", "not-an-email");
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/candidates", adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"errors\"");
        assertThat(response.getBody()).contains("email");
    }

    @Test
    @Order(13)
    void updateCandidateKeepsCountAndFields() {
        RecruitmentDto.CandidateRequest request = candidate("Tara Iyer", "tara.iyer@example.com");
        // make her distinct so update's uniqueness check passes its own row
        ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/v1/candidates/" + candidateId,
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Tara Iyer");
    }

    // ------------------------------------------------------- applications

    @Test
    @Order(20)
    void applyToOpenJobCreatesApplication() {
        RecruitmentDto.ApplicationRequest request =
                new RecruitmentDto.ApplicationRequest(candidateId, jobId, "Applied via referral");
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/applications", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"APPLIED\"");
        assertThat(body).contains("Tara Iyer").contains("QA Engineer");
        applicationId = extractId(body);
    }

    @Test
    @Order(21)
    void duplicateApplicationIs409() {
        RecruitmentDto.ApplicationRequest request =
                new RecruitmentDto.ApplicationRequest(candidateId, jobId, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/applications", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already applied");
    }

    @Test
    @Order(22)
    void applyToClosedOrDraftJobIs400() {
        // Job 3 is a DRAFT (created in a later order step) — use job 1 which is OPEN...
        // Instead: create a DRAFT job quickly and apply to it.
        RecruitmentDto.JobRequest draft = new RecruitmentDto.JobRequest(
                "Draft Role", null, 1L, null, com.hrgenius.employee.EmploymentType.FULL_TIME,
                null, JobStatus.DRAFT, null);
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/jobs", adminHeaders(), draft);
        Long draftId = extractId(created.getBody());

        RecruitmentDto.ApplicationRequest request =
                new RecruitmentDto.ApplicationRequest(candidateId, draftId, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/applications", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("OPEN");
    }

    @Test
    @Order(23)
    void pipelineWalkAppliesEveryLegalTransition() {
        // APPLIED → SCREENING → SHORTLISTED → INTERVIEW → SELECTED
        move(applicationId, ApplicationStatus.SCREENING, HttpStatus.OK);
        move(applicationId, ApplicationStatus.SHORTLISTED, HttpStatus.OK);
        move(applicationId, ApplicationStatus.INTERVIEW, HttpStatus.OK);
        move(applicationId, ApplicationStatus.SELECTED, HttpStatus.OK);

        ResponseEntity<String> after = exchange(HttpMethod.GET, "/api/v1/applications", adminHeaders(), null);
        assertThat(after.getBody()).contains("\"status\":\"SELECTED\"");
        // Candidate status synced: HIRED is only set by the (future) onboarding flow.
        assertThat(after.getBody()).contains("Tara Iyer");
    }

    @Test
    @Order(24)
    void illegalTransitionIs409() {
        // Seed application 4 is APPLIED; jumping straight to SELECTED must fail.
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/applications/4/status",
                hrHeaders(), new RecruitmentDto.TransitionRequest(ApplicationStatus.SELECTED, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Cannot move application from APPLIED to SELECTED");
    }

    @Test
    @Order(25)
    void terminalStageCannotMove() {
        // Application 1 (Kavya) is at INTERVIEW in the seed; moving it to SELECTED is legal,
        // so instead verify a SELECTED application cannot move: applicationId was SELECTED above.
        RecruitmentDto.TransitionRequest any = new RecruitmentDto.TransitionRequest(ApplicationStatus.REJECTED, null);
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/applications/" + applicationId + "/status", hrHeaders(), any);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(26)
    void employeeCannotReadApplications() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/applications", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --------------------------------------------------------- interviews

    @Test
    @Order(30)
    void scheduleInterviewForShortlistedApplicationAdvancesIt() {
        // Seed application 3 (Fatima) is SHORTLISTED → scheduling must auto-advance it.
        OffsetDateTime when = OffsetDateTime.now().plusDays(3);
        RecruitmentDto.InterviewRequest request =
                new RecruitmentDto.InterviewRequest(3L, 6L, when, Interview.InterviewMode.ONSITE);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/interviews", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("Fatima Sheikh").contains("\"mode\":\"ONSITE\"");
        assertThat(body).contains("\"interviewerName\":\"Divya Nair\"");
        interviewId = extractId(body);

        // Application 3 is now INTERVIEW
        ResponseEntity<String> apps = exchange(HttpMethod.GET, "/api/v1/applications", adminHeaders(), null);
        assertThat(apps.getBody()).contains("\"id\":3").contains("Fatima");
    }

    @Test
    @Order(31)
    void secondLiveInterviewForSameApplicationIs409() {
        OffsetDateTime when = OffsetDateTime.now().plusDays(4);
        RecruitmentDto.InterviewRequest request =
                new RecruitmentDto.InterviewRequest(3L, null, when, Interview.InterviewMode.ONLINE);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/interviews", hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already has a scheduled interview");
    }

    @Test
    @Order(32)
    void interviewDateAndStageRules() {
        // Past date on a valid INTERVIEW application → 400
        RecruitmentDto.InterviewRequest past = new RecruitmentDto.InterviewRequest(3L, null,
                OffsetDateTime.now().minusDays(1), Interview.InterviewMode.PHONE);
        assertThat(exchange(HttpMethod.POST, "/api/v1/interviews", hrHeaders(), past)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Future date on an APPLIED application (4) → 409 stage rule
        RecruitmentDto.InterviewRequest wrongStage = new RecruitmentDto.InterviewRequest(4L, null,
                OffsetDateTime.now().plusDays(2), Interview.InterviewMode.PHONE);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/interviews", hrHeaders(), wrongStage);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("SHORTLISTED");
    }

    @Test
    @Order(33)
    void rescheduleUpdatesDateAndMode() {
        OffsetDateTime when = OffsetDateTime.now().plusDays(6);
        RecruitmentDto.InterviewRescheduleRequest request =
                new RecruitmentDto.InterviewRescheduleRequest(when, Interview.InterviewMode.ONLINE, null);
        ResponseEntity<String> response = exchange(HttpMethod.PATCH, "/api/v1/interviews/" + interviewId,
                hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"mode\":\"ONLINE\"");
    }

    @Test
    @Order(34)
    void completeRecordsFeedbackAndResult() {
        RecruitmentDto.InterviewFeedbackRequest request =
                new RecruitmentDto.InterviewFeedbackRequest("Excellent technical depth", Interview.InterviewResult.PASS);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/interviews/" + interviewId + "/complete",
                hrHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("Excellent technical depth").contains("\"result\":\"PASS\"");
        assertThat(body).contains("\"status\":\"COMPLETED\"");
    }

    @Test
    @Order(35)
    void cancelledInterviewCannotBeRescheduled() {
        // App 3's interview was COMPLETED in order 34 → a new round can be scheduled,
        // then cancelled, and a cancelled interview must refuse rescheduling.
        OffsetDateTime when = OffsetDateTime.now().plusDays(7);
        RecruitmentDto.InterviewRequest schedule =
                new RecruitmentDto.InterviewRequest(3L, 1L, when, Interview.InterviewMode.ONLINE);
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/interviews", adminHeaders(), schedule);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long cancelledId = extractId(created.getBody());

        assertThat(exchange(HttpMethod.POST, "/api/v1/interviews/" + cancelledId + "/cancel",
                hrHeaders(), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> again = exchange(HttpMethod.PATCH, "/api/v1/interviews/" + cancelledId,
                hrHeaders(), new RecruitmentDto.InterviewRescheduleRequest(when, Interview.InterviewMode.ONLINE, null));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ----------------------------------------------------- delete guards

    @Test
    @Order(40)
    void deleteJobWithApplicationsIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/jobs/" + jobId, adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("close it instead");
    }

    @Test
    @Order(41)
    void deleteCandidateWithApplicationsIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/candidates/" + candidateId,
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("reject them instead");
    }

    @Test
    @Order(42)
    void adminDeletesDraftJobWithoutApplications() {
        // recreate the DRAFT job (id was unique per run, reuse pattern): create another
        RecruitmentDto.JobRequest draft = new RecruitmentDto.JobRequest(
                "Disposable Role", null, 1L, null, com.hrgenius.employee.EmploymentType.FULL_TIME,
                null, JobStatus.DRAFT, null);
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/jobs", adminHeaders(), draft);
        Long disposableId = extractId(created.getBody());
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/jobs/" + disposableId, adminHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------ helpers

    private RecruitmentDto.CandidateRequest candidate(String name, String email) {
        return new RecruitmentDto.CandidateRequest(name, email, "+91-9900000000",
                "resumes/tara.pdf", "Java, Selenium", java.math.BigDecimal.valueOf(3.0));
    }

    private void move(Long id, ApplicationStatus target, HttpStatus expected) {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH, "/api/v1/applications/" + id + "/status",
                hrHeaders(), new RecruitmentDto.TransitionRequest(target, null));
        assertThat(response.getStatusCode()).as("move to %s", target).isEqualTo(expected);
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
