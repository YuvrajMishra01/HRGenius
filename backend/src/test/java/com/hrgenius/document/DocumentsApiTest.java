package com.hrgenius.document;

import java.nio.charset.StandardCharsets;

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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Employee documents over real HTTP (Phase 11).
 *
 * Suite order is alphabetical: analytics < attendance < auth < dashboard < department
 * < document < employee … — DocumentsApiTest runs BEFORE EmployeeApiTest, which is
 * the only suite that deletes employees. We upload for employee 4 (Sneha Patil) and
 * clean up after ourselves, so no other suite is affected. No seed documents exist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentsApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static volatile Long uploadedId;

    // ------------------------------------------------------------- upload

    @Test
    @Order(1)
    void uploadStoresMetadataAndReturns201() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("employeeId", 4L);
        form.add("documentType", "RESUME");
        form.add("file", new ByteArrayResource("PDF-bytes-for-hrgenius-test".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "sneha-resume.pdf";
            }
        });
        ResponseEntity<String> response = post("/api/v1/documents", form, hrHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("\"employeeId\":4");
        assertThat(body).contains("\"employeeName\":\"Sneha Patil\"");
        assertThat(body).contains("\"fileName\":\"sneha-resume.pdf\"");
        assertThat(body).contains("\"documentType\":\"RESUME\"");
        assertThat(body).contains("\"fileSize\":27");
        uploadedId = extractId(body);
        assertThat(uploadedId).isNotNull();
    }

    @Test
    @Order(2)
    void uploadRejectsDisallowedExtension() {
        MultiValueMap<String, Object> form = multipart(4L, "OTHER");
        form.add("file", file("evil.exe", "MZ"));
        assertThat(post("/api/v1/documents", form, hrHeaders()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(3)
    void uploadRejectsUnknownType() {
        MultiValueMap<String, Object> form = multipart(4L, "PASSPORT");
        form.add("file", file("a.pdf", "x"));
        ResponseEntity<String> response = post("/api/v1/documents", form, hrHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Unknown document type");
    }

    @Test
    @Order(4)
    void uploadRejectsUnknownEmployee() {
        MultiValueMap<String, Object> form = multipart(9999L, "ID_PROOF");
        form.add("file", file("a.pdf", "x"));
        assertThat(post("/api/v1/documents", form, hrHeaders()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void uploadRejectsEmptyFile() {
        MultiValueMap<String, Object> form = multipart(4L, "CERTIFICATE");
        form.add("file", file("empty.pdf", ""));
        assertThat(post("/api/v1/documents", form, hrHeaders()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // The >5 MB service guard and Spring's 6MB multipart cap cannot be asserted
        // over real HTTP (Tomcat aborts the connection mid-upload), so they are
        // covered by code review of DocumentService.upload only.
    }

    @Test
    @Order(6)
    void listReturnsMetadataWithoutFileBytes() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/documents?employeeId=4", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"fileName\":\"sneha-resume.pdf\"");
        assertThat(body).doesNotContain("PDF-bytes-for-hrgenius-test");
    }

    // ----------------------------------------------------------- download

    @Test
    @Order(10)
    void downloadReturnsOriginalBytesAndName() {
        ResponseEntity<byte[]> response = rest.exchange(url() + "/api/v1/documents/" + uploadedId + "/download",
                HttpMethod.GET, new HttpEntity<>(hrHeaders()), byte[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("PDF-bytes-for-hrgenius-test");
        assertThat(String.valueOf(response.getHeaders().getFirst("Content-Disposition")))
                .contains("sneha-resume.pdf");
        assertThat(String.valueOf(response.getHeaders().getFirst("Content-Type"))).contains("application/pdf");
    }

    @Test
    @Order(11)
    void downloadUnknownDocumentIs404() {
        ResponseEntity<byte[]> response = rest.exchange(url() + "/api/v1/documents/999999/download",
                HttpMethod.GET, new HttpEntity<>(hrHeaders()), byte[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------- delete

    @Test
    @Order(20)
    void deleteRemovesMetadataAndFile() {
        ResponseEntity<Void> response = rest.exchange(url() + "/api/v1/documents/" + uploadedId,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> remaining = exchange(HttpMethod.GET,
                "/api/v1/documents?employeeId=4", hrHeaders(), null);
        assertThat(remaining.getBody()).doesNotContain("sneha-resume.pdf");

        ResponseEntity<byte[]> gone = rest.exchange(url() + "/api/v1/documents/" + uploadedId + "/download",
                HttpMethod.GET, new HttpEntity<>(hrHeaders()), byte[].class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        uploadedId = null;
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void rbacMatrix() {
        // MANAGER can read but never upload (deliberate policy) nor delete.
        MultiValueMap<String, Object> form = multipart(4L, "OTHER");
        form.add("file", file("mgr-note.pdf", "content"));
        assertThat(post("/api/v1/documents", form, managerHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/documents?employeeId=4", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET, "/api/v1/documents?employeeId=4", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/v1/documents?employeeId=4", adminHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange(url() + "/api/v1/documents/" + (uploadedId == null ? 1 : uploadedId),
                HttpMethod.DELETE, new HttpEntity<>(managerHeaders()), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------- helpers

    private MultiValueMap<String, Object> multipart(Long employeeId, String type) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("employeeId", employeeId);
        form.add("documentType", type);
        return form;
    }

    private ByteArrayResource file(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.ISO_8859_1)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    private ResponseEntity<String> post(String path, MultiValueMap<String, Object> form, HttpHeaders headers) {
        HttpHeaders multipartHeaders = new HttpHeaders();
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        multipartHeaders.set(HttpHeaders.AUTHORIZATION, headers.getFirst(HttpHeaders.AUTHORIZATION));
        return rest.exchange(url() + path, HttpMethod.POST, new HttpEntity<>(form, multipartHeaders), String.class);
    }

    private static Long extractId(String body) {
        int idx = body.indexOf("\"id\":");
        return Long.parseLong(body.substring(idx + 5, body.indexOf(',', idx)));
    }

    private String url() {
        return "http://localhost:" + port;
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
        ResponseEntity<String> login;
        try {
            login = login(email, password, loginHeaders);
        } catch (ResourceAccessException staleKeepAlive) {
            // The streaming-download test can leave a half-closed pooled connection;
            // one retry on a fresh connection keeps this suite deterministic.
            login = login(email, password, loginHeaders);
        }
        assertThat(login.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = login.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('\"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return rest.exchange(url() + path, method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> login(String email, String password, HttpHeaders loginHeaders) {
        return rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new com.hrgenius.auth.LoginRequest(email, password), loginHeaders), String.class);
    }
}
