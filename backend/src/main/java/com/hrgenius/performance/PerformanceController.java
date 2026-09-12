package com.hrgenius.performance;

import java.util.List;

import com.hrgenius.common.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Performance API (Phase 10). Reads: ADMIN/HR/MANAGER.
 * Writes (create, edit, rate, acknowledge, delete): ADMIN/HR.
 * Self-service acknowledgement needs the User↔Employee link (deferred).
 */
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PerformanceDto.ReviewResponse>>> reviews(
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(ApiResponse.of(service.list(employeeId)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PerformanceDto.SummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.of(service.summary()));
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PerformanceDto.ReviewResponse>> create(
            @Valid @RequestBody PerformanceDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Review created", service.create(request)));
    }

    /** Edit a DRAFT review's narrative fields. */
    @PatchMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PerformanceDto.ReviewResponse>> update(
            @PathVariable Long id, @Valid @RequestBody PerformanceDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Review updated", service.update(id, request)));
    }

    /** Rate 1–5 and submit in one step. */
    @PatchMapping("/reviews/{id}/rate")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PerformanceDto.ReviewResponse>> rate(
            @PathVariable Long id, @Valid @RequestBody PerformanceDto.RateRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Review submitted", service.rate(id, request)));
    }

    /** The reviewed employee acknowledges the submitted review. */
    @PatchMapping("/reviews/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PerformanceDto.ReviewResponse>> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Review acknowledged", service.acknowledge(id)));
    }

    @DeleteMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}