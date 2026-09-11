package com.hrgenius.common;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness + database connectivity probe. Public by design.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
@Slf4j
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "Health check with database probe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("application", "HRGenius API");
        status.put("status", "UP");

        String dbStatus;
        try {
            String product = jdbcTemplate.queryForObject("SELECT 1 FROM DUAL", String.class);
            String url = dataSource.getConnection().getMetaData().getURL();
            dbStatus = "UP (" + sanitize(url) + ")";
            log.debug("Health DB probe ok: {}", product);
        } catch (Exception ex) {
            log.error("Database health probe failed", ex);
            dbStatus = "DOWN: " + ex.getClass().getSimpleName();
            status.put("status", "DOWN");
        }
        status.put("database", dbStatus);
        status.put("timestamp", OffsetDateTime.now().toString());

        HttpStatus code = "UP".equals(status.get("status")) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code).body(ApiResponse.of(code == HttpStatus.OK ? "OK" : "DEGRADED", status));
    }

    /** Never leak credentials that can be embedded in JDBC URLs. */
    private String sanitize(String url) {
        return url.replaceAll("password=[^;]*", "password=***");
    }
}
