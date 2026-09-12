package com.hrgenius.analytics;

import com.hrgenius.common.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints. HR-level visibility: ADMIN and HR see the whole
 * organization; MANAGER will get a team-scoped variant in a later phase.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<DashboardStats>> stats() {
        return ResponseEntity.ok(ApiResponse.of(dashboardService.getStats()));
    }
}
