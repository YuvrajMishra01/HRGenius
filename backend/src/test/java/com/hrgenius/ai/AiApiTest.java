package com.hrgenius.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.hrgenius.auth.LoginRequest;
import com.hrgenius.report.ReportPdfBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 16 AI insights over real HTTP.
 *
 * This package sorts FIRST alphabetically, so the suite sees pure seed state:
 *  - Job 1 "Backend Developer — Spring Boot microservices and Oracle SQL"
 *    → required {Spring Boot, Microservices, Oracle SQL} (3 — the text never
 *    says "Java"; only what is written is required);
 *  - Job 2 "HR Generalist — Recruitment, onboarding and employee engagement"
 *    → required {Recruitment, Onboarding} (Communication appears in neither
 *    title nor description, so it is NOT required);
 *  - Candidates: Kavya Rao (Java, Spring Boot, Oracle SQL), Mohit Bansal
 *    (Angular, TypeScript), Fatima Sheikh (Recruitment, Onboarding),
 *    Sanjay Gupta (Accounting, Tally).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SkillExtractor extractor; // direct unit checks of the matching rules

    // ------------------------------------------------------------- matching

    @Test
    @Order(1)
    void jobMatchScoresRankCandidatesAgainstSeedJob() {
        ResponseEntity<String> res = exchange("/api/v1/ai/job-matches/1", adminHeaders());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = res.getBody();
        assertThat(body).contains("Backend Developer");
        // Job 1 requires Spring Boot, Microservices, Oracle SQL (title + description).
        assertThat(body).contains("\"requiredSkills\":[\"Spring Boot\",\"Oracle SQL\",\"Microservices\"]");
        // Kavya has 2 of 3 (no microservices) → 66; the rest have none → 0.
        assertThat(body).contains("\"score\":66");
        assertThat(body).contains("Kavya Rao");
        assertThat(body).contains("Mohit Bansal");
        // Matching is explainable: matched and missing lists are in the payload.
        assertThat(body).contains("\"missingSkills\":[\"Microservices\"]");
    }

    @Test
    @Order(2)
    void hrJobRequiresHrSkillsFromTitleAndDescription() {
        String body = exchange("/api/v1/ai/job-matches/2", adminHeaders()).getBody();
        assertThat(body).contains("\"requiredSkills\":[\"Recruitment\",\"Onboarding\"]");
        assertThat(body).contains("Fatima Sheikh");
        assertThat(body).contains("\"score\":100");
    }

    @Test
    @Order(3)
    void unknownJobIs404() {
        assertThat(exchange("/api/v1/ai/job-matches/99999", adminHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------- extraction

    @Test
    @Order(10)
    void resumeSkillsEndpointExtractsFromPastedText() {
        String payload = "{\"text\":\"Experienced Java developer. Spring Boot and Oracle SQL, "
                + "built REST APIs with JUnit tests. Also speaks about JavaScript frameworks.\"}";
        ResponseEntity<String> res = exchangePost("/api/v1/ai/resume-skills", payload, adminHeaders());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = res.getBody();
        // JavaScript must NOT collapse into Java; REST APIs recognised via alias.
        // Dictionary order (JUnit before JavaScript), boundaries intact.
        assertThat(body).contains("\"skills\":[\"Java\",\"Spring Boot\",\"Oracle SQL\",\"REST APIs\",\"JUnit\",\"JavaScript\"]");
        assertThat(body).contains("\"count\":6");
        assertThat(body).contains("\"source\":\"pasted-text\"");
    }

    @Test
    @Order(11)
    void resumeFileEndpointReadsTxtAndGeneratedPdf() {
        // .txt path
        byte[] txt = "Skills: Angular, TypeScript, Docker and Git experience".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<String> txtRes = upload("/api/v1/ai/resume-files", "resume.txt", txt);
        assertThat(txtRes.getBody()).contains("\"skills\":[\"Angular\",\"TypeScript\",\"Docker\",\"Git\"]");

        // .pdf path: generate a real PDF in-memory via the Phase 15 builder.
        byte[] pdf = ReportPdfBuilder.build("Resume of Priya Nair", "curriculum vitae",
                java.util.List.of("Skill"), java.util.List.of(java.util.List.of(
                        "Java, Spring Boot, Hibernate, Oracle SQL, microservices, Docker")));
        ResponseEntity<String> pdfRes = upload("/api/v1/ai/resume-files", "resume.pdf", pdf);
        assertThat(pdfRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = pdfRes.getBody();
        assertThat(body).contains("\"source\":\"resume.pdf\"");
        assertThat(body).contains("Hibernate");
        assertThat(body).contains("Docker");
        assertThat(body).doesNotContain("\"SQL\""); // specificity prune: Oracle SQL only
    }

    @Test
    @Order(12)
    void unsupportedFileTypeIsRejected400() {
        ResponseEntity<String> res = upload("/api/v1/ai/resume-files", "photo.png", new byte[] { 1, 2, 3 });
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains(".pdf or .txt");
    }

    // ----------------------------------------------------- extractor (unit)

    @Test
    @Order(20)
    void extractorRespectsWordBoundariesAndPrunesSpecificity() {
        // "javascript" must not yield "Java"; "oracle sql" suppresses "sql".
        var skills = extractor.extract("I know javascript, oracle sql and k8s clusters");
        assertThat(skills).contains("JavaScript", "Oracle SQL", "Kubernetes");
        assertThat(skills).doesNotContain("Java", "SQL");
        // Word boundary: "node" inside "nodemcu" is not Node.js.
        assertThat(extractor.extract("firmware for nodemcu boards")).doesNotContain("Node.js");
    }

    // ---------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void aiEndpointsKeepTheRecruitmentReadMatrix() {
        assertThat(exchange("/api/v1/ai/job-matches/1", employeeHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(url() + "/api/v1/ai/job-matches/1", HttpMethod.GET,
                new HttpEntity<>(anonymousHeaders()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange("/api/v1/ai/job-matches/1", managerHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchangePost("/api/v1/ai/resume-skills", "{\"text\":\"java\"}", employeeHeaders())
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------- helpers

    private ResponseEntity<String> upload(String path, String fileName, byte[] bytes) {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return rest.exchange(url() + path, HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

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

    private ResponseEntity<String> exchange(String path, HttpHeaders headers) {
        return rest.exchange(url() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> exchangePost(String path, String json, HttpHeaders headers) {
        HttpHeaders withType = new HttpHeaders();
        withType.setContentType(MediaType.APPLICATION_JSON);
        withType.setBearerAuth(headers.getFirst(HttpHeaders.AUTHORIZATION).replace("Bearer ", ""));
        return rest.exchange(url() + path, HttpMethod.POST, new HttpEntity<>(json, withType), String.class);
    }
}
