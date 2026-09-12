package com.hrgenius.analytics;

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

import com.hrgenius.auth.LoginRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard aggregation over real HTTP: correct numbers computed from the
 * seed data, and HR-level RBAC (ADMIN/HR pass, EMPLOYEE rejected).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @Order(1)
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response = rest.getForEntity(url() + "/api/v1/dashboard/stats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void employeeRoleIsForbidden() {
        ResponseEntity<String> response = getWithRole("employee@hrgenius.local", "Employee@123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void managerRoleIsForbiddenForNow() {
        ResponseEntity<String> response = getWithRole("manager@hrgenius.local", "Manager@123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(4)
    void adminGetsCorrectAggregates() {
        ResponseEntity<String> response = getWithRole("admin@hrgenius.local", "Admin@123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        // Seed data: 7 employees (all ACTIVE), 2 OPEN jobs, 4 candidates,
        // 2 PENDING leave requests, 1 hire in the last 30 days.
        assertThat(body)
                .contains("\"totalEmployees\":7")
                .contains("\"activeEmployees\":7")
                .contains("\"newHiresLast30Days\":1")
                .contains("\"openPositions\":2")
                .contains("\"totalCandidates\":4")
                .contains("\"pendingLeaveRequests\":2");

        // Department distribution and hiring pipeline are non-empty.
        assertThat(body).contains("\"departmentDistribution\"");
        assertThat(body).contains("\"hiringPipeline\"");
        // Upcoming interview seeded +2 days from now.
        assertThat(body).contains("\"upcomingInterviews\"");
        assertThat(body).contains("Kavya Rao");
        // Pending approvals contain the seeded employees.
        assertThat(body).contains("Anita Desai");
    }

    @Test
    @Order(5)
    void hrRoleAlsoGetsAggregates() {
        ResponseEntity<String> response = getWithRole("hr@hrgenius.local", "Hr@12345");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"kpis\"");
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<String> getWithRole(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.exchange(url() + "/api/v1/auth/login",
                HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), headers), String.class);
        assertThat(login.getStatusCode()).as("login as %s", email).isEqualTo(HttpStatus.OK);

        // Body is the ApiResponse envelope: extract data.token.
        String body = login.getBody();
        int idx = body.indexOf("\"token\":\"");
        assertThat(idx).as("token present in %s", body).isGreaterThan(0);
        String token = body.substring(idx + "\"token\":\"".length(), body.indexOf('"', idx + "\"token\":\"".length()));

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        return rest.exchange(url() + "/api/v1/dashboard/stats",
                HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
