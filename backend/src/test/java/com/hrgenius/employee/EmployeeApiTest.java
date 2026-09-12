package com.hrgenius.employee;

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
 * Employee CRUD over real HTTP against the seeded H2 database.
 * Ordered: create → list/search/sort → update → soft delete → RBAC/validations
 * (login-as-needed keeps each test self-contained despite shared state).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmployeeApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static Long createdId;

    // ------------------------------------------------------------ create

    @Test
    @Order(1)
    void adminCreatesEmployee() {
        EmployeeDto request = sample("EMP900", "Test", "Person", "emp900@hrgenius.local", 1L, 1L, 1L);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/employees",
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"employeeCode\":\"EMP900\"");

        createdId = extractId(response.getBody());
        assertThat(createdId).isNotNull();
    }

    @Test
    @Order(2)
    void createWithDuplicateCodeReturns409() {
        EmployeeDto request = sample("EMP900", "Other", "Person", "other@hrgenius.local", 1L, 1L, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/employees",
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already exists");
    }

    @Test
    @Order(3)
    void createWithInvalidBodyReturns400WithFieldErrors() {
        EmployeeDto request = sample("", "A", "B", "not-an-email", null, null, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/employees",
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"errors\"");
        assertThat(response.getBody()).contains("employeeCode");
        assertThat(response.getBody()).contains("email");
    }

    @Test
    @Order(4)
    void createWithUnknownDepartmentReturns404() {
        EmployeeDto request = sample("EMP901", "Ghost", "Dept", "ghost@hrgenius.local", 9999L, 1L, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/employees",
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void designationFromDifferentDepartmentIsRejected() {
        // Designation 3 (HR Executive) with department 1 (Engineering)
        EmployeeDto request = sample("EMP902", "Cross", "Dept", "cross@hrgenius.local", 1L, 3L, null);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/employees",
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------- list & query

    @Test
    @Order(10)
    void listIsPaginatedAndSorted() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/employees?page=0&size=3&sortBy=employeeCode&sortDir=asc",
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"page\":0").contains("\"size\":3");
        assertThat(body).contains("\"totalElements\":8"); // 7 seed + 1 created
        assertThat(body).contains("\"first\":true").contains("\"last\":false");
        // First page of 3 sorted by code: EMP001..EMP003
        assertThat(body).contains("EMP001").contains("EMP002").contains("EMP003");
        assertThat(body).doesNotContain("EMP007"); // on the last page
    }

    @Test
    @Order(11)
    void searchFiltersByNameCodeAndEmail() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/employees?search=rohan", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Rohan");
        assertThat(response.getBody()).contains("\"totalElements\":1");
    }

    @Test
    @Order(12)
    void filterByStatusAndDepartment() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/employees?status=ACTIVE&departmentId=1&size=50", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"ACTIVE\"");
        // Engineering seed: EMP001, EMP002, EMP003, EMP007 + created EMP900 (dept 1)
        assertThat(body).contains("\"totalElements\":5");
    }

    @Test
    @Order(13)
    void getReturnsFullProjection() {
        // EMP002 has department, designation AND manager set in the seed data
        // (EMP001 is top of the hierarchy and has no manager).
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/employees/2", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body)
                .contains("\"employeeCode\":\"EMP002\"")
                .contains("\"departmentName\":\"Engineering\"")
                .contains("\"designationTitle\":\"Software Engineer\"")
                .contains("\"managerName\":\"Rahul Verma\"");
    }

    @Test
    @Order(14)
    void getUnknownIdReturns404() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/employees/9999", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------- update

    @Test
    @Order(20)
    void updateChangesFields() {
        EmployeeDto request = sample("EMP900", "Test", "Updated", "emp900@hrgenius.local", 2L, 3L, 1L);
        ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/v1/employees/" + createdId,
                adminHeaders(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"lastName\":\"Updated\"");
        assertThat(response.getBody()).contains("\"departmentName\":\"Human Resources\"");
    }

    // ------------------------------------------------------ soft delete

    @Test
    @Order(30)
    void deleteByHrIsForbidden() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE,
                "/api/v1/employees/" + createdId, hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(31)
    void deleteBlockedWhileEmployeeManagesOthers() {
        // EMP001 manages EMP002/EMP003/EMP005/EMP007
        ResponseEntity<String> response = exchange(HttpMethod.DELETE,
                "/api/v1/employees/1", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Reassign managed employees");
    }

    @Test
    @Order(32)
    void adminSoftDeletesEmployee() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE,
                "/api/v1/employees/" + createdId, adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Row survives with status TERMINATED.
        ResponseEntity<String> after = exchange(HttpMethod.GET,
                "/api/v1/employees/" + createdId, adminHeaders(), null);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).contains("\"status\":\"TERMINATED\"");
    }

    // --------------------------------------------------------------- RBAC

    @Test
    @Order(40)
    void employeeCannotListEmployees() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/employees",
                employeeHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(41)
    void managerCanListButNotCreate() {
        ResponseEntity<String> list = exchange(HttpMethod.GET, "/api/v1/employees",
                managerHeaders(), null);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);

        EmployeeDto request = sample("EMP950", "No", "Access", "nope@hrgenius.local", 1L, 1L, null);
        ResponseEntity<String> create = exchange(HttpMethod.POST, "/api/v1/employees",
                managerHeaders(), request);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------ helpers

    private EmployeeDto sample(String code, String first, String last, String email,
                               Long departmentId, Long designationId, Long managerId) {
        return new EmployeeDto(code, first, last, email, "+91-9000000000",
                LocalDate.of(1995, 5, 10), Gender.FEMALE, "Pune, India",
                LocalDate.of(2024, 1, 15), EmploymentType.FULL_TIME, EmployeeStatus.ACTIVE,
                departmentId, designationId, managerId);
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
