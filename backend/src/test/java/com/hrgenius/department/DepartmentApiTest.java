package com.hrgenius.department;

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
 * Department & designation management over real HTTP (Phase 4).
 * Ordered: create → read/counts → update → guards → delete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DepartmentApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static Long createdDeptId;
    private static Long createdDesgId;

    // -------------------------------------------------------- departments

    @Test
    @Order(1)
    void adminCreatesDepartment() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/departments",
                adminHeaders(),
                new DepartmentDto.DepartmentRequest("Quality Assurance", "Testing and release quality", 1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"name\":\"Quality Assurance\"");
        assertThat(response.getBody()).contains("\"managerName\":\"Rahul Verma\"");
        createdDeptId = extractId(response.getBody());
    }

    @Test
    @Order(2)
    void createDuplicateDepartmentReturns409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/departments",
                adminHeaders(),
                new DepartmentDto.DepartmentRequest("quality assurance", null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already exists");
    }

    @Test
    @Order(3)
    void listIncludesEmployeeCounts() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/departments",
                hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        // Seed: Engineering has 5 (EMP001,2,3,7,8-in-test-run-independent: actually 4 seed + none),
        // the API must include counts for non-empty departments.
        assertThat(body).contains("\"employeeCount\":4");
        assertThat(body).contains("\"managerName\"");
    }

    @Test
    @Order(4)
    void updateChangesManager() {
        ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/v1/departments/" + createdDeptId,
                adminHeaders(),
                new DepartmentDto.DepartmentRequest("Quality Assurance", "QA org", 6L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"managerId\":6");
    }

    @Test
    @Order(5)
    void hrCannotMutateDepartments() {
        ResponseEntity<String> create = exchange(HttpMethod.POST, "/api/v1/departments",
                hrHeaders(),
                new DepartmentDto.DepartmentRequest("Nope", null, null));
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> delete = exchange(HttpMethod.DELETE, "/api/v1/departments/1",
                hrHeaders(), null);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(6)
    void deleteDepartmentWithEmployeesIsBlocked() {
        // Department 1 (Engineering) has employees.
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/departments/1",
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Reassign");
    }

    @Test
    @Order(7)
    void emptyDepartmentCanBeDeleted() {
        // First remove the manager link (manager belongs to another dept, that's fine),
        // QA has no employees or designations → deletable.
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/departments/" + createdDeptId,
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------- designations

    @Test
    @Order(10)
    void adminCreatesDesignation() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/designations",
                adminHeaders(),
                new DepartmentDto.DesignationRequest("QA Engineer", "Automates tests", 1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"title\":\"QA Engineer\"");
        assertThat(response.getBody()).contains("\"departmentName\":\"Engineering\"");
        createdDesgId = extractId(response.getBody());
    }

    @Test
    @Order(11)
    void duplicateTitleInSameDepartmentReturns409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/designations",
                adminHeaders(),
                new DepartmentDto.DesignationRequest("qa engineer", null, 1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(12)
    void sameTitleAllowedInDifferentDepartment() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/designations",
                adminHeaders(),
                new DepartmentDto.DesignationRequest("QA Engineer", null, 3L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long otherDeptDesgId = extractId(response.getBody());

        // Clean up immediately.
        exchange(HttpMethod.DELETE, "/api/v1/designations/" + otherDeptDesgId, adminHeaders(), null);
    }

    @Test
    @Order(13)
    void designationWithoutDepartmentIsRejected() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/designations",
                adminHeaders(),
                new DepartmentDto.DesignationRequest("Freelancer", null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(14)
    void deleteDesignationInUseIsBlocked() {
        // Designation 1 (Software Engineer) is held by seed employees.
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/designations/1",
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("held by");
    }

    @Test
    @Order(15)
    void unusedDesignationCanBeDeleted() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/designations/" + createdDesgId,
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(16)
    void employeeOptionsEndpointWorks() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/employees/options",
                managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Rahul Verma (EMP001)");
    }

    // ------------------------------------------------------------ helpers

    private HttpHeaders adminHeaders() {
        return bearer("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders hrHeaders() {
        return bearer("hr@hrgenius.local", "Hr@12345");
    }

    private HttpHeaders managerHeaders() {
        return bearer("manager@hrgenius.local", "Manager@123");
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
