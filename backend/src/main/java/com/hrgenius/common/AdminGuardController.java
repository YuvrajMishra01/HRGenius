package com.hrgenius.common;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately minimal RBAC smoke endpoint. Proves end to end that the
 * JWT → authorities → @PreAuthorize chain works, and is used by the
 * auth integration tests and Swagger to demonstrate role restrictions.
 * Real admin features will live in their own feature controllers.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminGuardController {

    @GetMapping("/only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> adminOnly() {
        return ResponseEntity.ok(ApiResponse.of(true));
    }
}
