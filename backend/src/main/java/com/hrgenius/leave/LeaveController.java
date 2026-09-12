package com.hrgenius.leave;

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
 * Leave management API (Phase 8). Reads: ADMIN/HR/MANAGER.
 * Writes (types, create, approve/reject, delete): ADMIN/HR.
 */
@RestController
@RequestMapping("/api/v1/leave")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    // ------------------------------------------------------------ types

    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveDto.LeaveTypeResponse>>> types() {
        return ResponseEntity.ok(ApiResponse.of(service.listTypes()));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<LeaveDto.LeaveTypeResponse>> createType(
            @Valid @RequestBody LeaveDto.LeaveTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Leave type created", service.createType(request)));
    }

    @PatchMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<LeaveDto.LeaveTypeResponse>> updateType(
            @PathVariable Long id, @Valid @RequestBody LeaveDto.LeaveTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Leave type updated", service.updateType(id, request)));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Void> deleteType(@PathVariable Long id) {
        service.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ requests

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveDto.LeaveRequestResponse>>> requests(
            @RequestParam(required = false) LeaveRequest.LeaveStatus status) {
        return ResponseEntity.ok(ApiResponse.of(service.listRequests(status)));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<LeaveDto.LeaveRequestResponse>> create(
            @Valid @RequestBody LeaveDto.CreateLeaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Leave request submitted", service.create(request)));
    }

    /** Approve a PENDING request; records the approving user. */
    @PatchMapping("/requests/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<LeaveDto.LeaveRequestResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Request approved", service.decide(id, LeaveRequest.LeaveStatus.APPROVED)));
    }

    /** Reject a PENDING request; records the deciding user. */
    @PatchMapping("/requests/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<LeaveDto.LeaveRequestResponse>> reject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Request rejected", service.decide(id, LeaveRequest.LeaveStatus.REJECTED)));
    }

    /** Withdraw a PENDING request. */
    @PatchMapping("/requests/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseEntity.ok(ApiResponse.message("Request cancelled"));
    }

    /** Hard delete for decided requests only (audit-safe). */
    @DeleteMapping("/requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------- balances & summary

    @GetMapping("/balances/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<LeaveDto.BalanceResponse>> balances(
            @PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.of(service.balances(employeeId, year)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<LeaveDto.LeaveSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.of(service.summary()));
    }
}
