package com.hrgenius.common;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full context on a random port, runs Flyway against H2 (Oracle mode)
 * and verifies the health endpoint plus the common error envelope.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void healthReportsDatabaseUp() {
        ResponseEntity<String> response = rest.getForEntity(url() + "/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"success\":true");
        assertThat(response.getBody()).contains("\"database\":\"UP");
        assertThat(response.getBody()).contains("HRGenius API");
    }

    @Test
    void unknownApiPathRequiresAuthentication() {
        // Security answers 401 before route resolution, so unknown paths do
        // not leak whether a resource exists (auth happens first).
        ResponseEntity<String> response = rest.getForEntity(url() + "/api/v1/does-not-exist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"path\"");
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
