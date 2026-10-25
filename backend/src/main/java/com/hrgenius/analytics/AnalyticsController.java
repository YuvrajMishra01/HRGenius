package com.hrgenius.analytics;

import com.hrgenius.common.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics API (Phase 13). ADMIN/HR only — the same policy as the live
 * dashboard: these are org-wide figures (attrition, pay, funnel), not
 * team-scoped views.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/workforce")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AnalyticsDto.WorkforceResponse>> workforce() {
        return ResponseEntity.ok(ApiResponse.of(service.workforce()));
    }

    @GetMapping("/funnel")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AnalyticsDto.FunnelResponse>> funnel() {
        return ResponseEntity.ok(ApiResponse.of(service.funnel()));
    }

    @GetMapping("/interviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AnalyticsDto.InterviewsResponse>> interviews() {
        return ResponseEntity.ok(ApiResponse.of(service.interviews()));
    }

    @GetMapping("/leave")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AnalyticsDto.LeaveResponse>> leave() {
        return ResponseEntity.ok(ApiResponse.of(service.leave()));
    }

    @GetMapping("/payroll-trend")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AnalyticsDto.PayrollTrendResponse>> payrollTrend() {
        return ResponseEntity.ok(ApiResponse.of(service.payrollTrend()));
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AnalyticsDto.PerformanceResponse>> performance() {
        return ResponseEntity.ok(ApiResponse.of(service.performance()));
    }
}
