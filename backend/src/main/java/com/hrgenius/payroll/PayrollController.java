package com.hrgenius.payroll;

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
import org.springframework.web.bind.annotation.RestController;

/**
 * Payroll API (Phase 9). Reads: ADMIN/HR/MANAGER.
 * Writes (run, component updates, lifecycle, delete): ADMIN/HR.
 * Payslips are sensitive financial records — EMPLOYEE role has no access.
 */
@RestController
@RequestMapping("/api/v1/payrolls")
public class PayrollController {

    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    // ------------------------------------------------------------ run

    /** Generate DRAFT payslips for a period (existing rows are skipped). */
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PayrollDto.RunResponse>> run(
            @Valid @RequestBody PayrollDto.RunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                "Payroll run completed", service.run(request.year(), request.month())));
    }

    // ------------------------------------------------------------ views

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PayrollDto.PeriodsResponse>> periods() {
        return ResponseEntity.ok(ApiResponse.of(service.periods()));
    }

    @GetMapping("/{year}/{month}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PayrollDto.PeriodResponse>> period(
            @PathVariable int year, @PathVariable int month) {
        return ResponseEntity.ok(ApiResponse.of(service.period(year, month)));
    }

    // ------------------------------------------------------------ lifecycle

    /** Adjust the four editable components; net is recomputed server-side. */
    @PatchMapping("/{id}/components")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PayrollDto.PayrollRow>> updateComponents(
            @PathVariable Long id, @Valid @RequestBody PayrollDto.ComponentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Components updated",
                service.updateComponents(id, request)));
    }

    @PatchMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PayrollDto.PayrollRow>> process(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Payslip processed", service.markProcessed(id)));
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PayrollDto.PayrollRow>> pay(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Payslip marked PAID", service.markPaid(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
