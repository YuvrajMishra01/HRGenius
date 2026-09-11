package com.hrgenius.auth;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack auth flow over real HTTP: login success/failure, /me,
 * garbage and expired tokens, unauthenticated 401, RBAC 403 and logout.
 * Boots the whole context with Flyway + seed data on H2 (Oracle mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtService jwtService;

    // ------------------------------------------------------------ login

    @Test
    @Order(1)
    void loginWithValidCredentialsReturnsTokenAndUser() {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", "admin@hrgenius.local", "password", "Admin@123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"success\":true")
                .contains("\"token\":")
                .contains("\"role\":\"ADMIN\"")
                .contains("admin@hrgenius.local");
    }

    @Test
    @Order(2)
    void loginWithWrongPasswordReturns401() {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", "admin@hrgenius.local", "password", "wrong-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid email or password");
    }

    @Test
    @Order(3)
    void loginWithUnknownEmailReturns401() {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", "ghost@hrgenius.local", "password", "whatever"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(4)
    void loginWithMalformedBodyReturns400WithFieldErrors() {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", "not-an-email", "password", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"errors\"");
    }

    // ------------------------------------------------- protected routes

    @Test
    @Order(5)
    void meWithoutTokenReturns401() {
        ResponseEntity<String> response = rest.getForEntity(url() + "/api/v1/auth/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"path\":\"/api/v1/auth/me\"");
    }

    @Test
    @Order(6)
    void meWithGarbageTokenReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this.is.not.a.jwt");
        ResponseEntity<String> response = rest.exchange(url() + "/api/v1/auth/me",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(7)
    void meWithExpiredTokenReturns401() {
        // Build a token that expired 60 minutes ago by signing claims directly.
        String expired = jwtService.generateTokenWithOverride(
                "admin@hrgenius.local", "ADMIN", 1L, 0L, -60);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expired);
        ResponseEntity<String> response = rest.exchange(url() + "/api/v1/auth/me",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(8)
    void meWithValidTokenReturnsCurrentUser() {
        String token = loginAndGetToken("employee@hrgenius.local", "Employee@123");
        ResponseEntity<String> response = getWithToken("/api/v1/auth/me", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"role\":\"EMPLOYEE\"")
                .contains("employee@hrgenius.local");
    }

    // ------------------------------------------------------------- RBAC

    @Test
    @Order(9)
    void employeeCannotCallAdminOnlyEndpoint() {
        String token = loginAndGetToken("employee@hrgenius.local", "Employee@123");
        ResponseEntity<String> response = getWithToken("/api/v1/admin/only", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(10)
    void adminCanCallAdminOnlyEndpoint() {
        String token = loginAndGetToken("admin@hrgenius.local", "Admin@123");
        ResponseEntity<String> response = getWithToken("/api/v1/admin/only", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("true");
    }

    // ----------------------------------------------------------- logout

    @Test
    @Order(11)
    void logoutInvalidatesTokenImmediately() {
        String token = loginAndGetToken("hr@hrgenius.local", "Hr@12345");

        assertThat(getWithToken("/api/v1/auth/me", token).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> loggedOut = postWithToken("/api/v1/auth/logout", token, null);
        assertThat(loggedOut.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Same token reused after logout must now be rejected (version bump).
        assertThat(getWithToken("/api/v1/auth/me", token).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------- helpers

    private String loginAndGetToken(String email, String password) {
        ResponseEntity<String> response = post("/api/v1/auth/login",
                Map.of("email", email, "password", password));
        assertThat(response.getStatusCode()).as("login body: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int idx = body.indexOf("\"token\":\"");
        assertThat(idx).isGreaterThan(0);
        int start = idx + "\"token\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(url() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postWithToken(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(url() + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url() + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
