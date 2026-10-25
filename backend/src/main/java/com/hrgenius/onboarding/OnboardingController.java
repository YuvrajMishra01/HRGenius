package com.hrgenius.onboarding;

import java.util.List;

import com.hrgenius.common.ApiResponse;
import com.hrgenius.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Onboarding endpoints (Phase 6): reads ADMIN/HR/MANAGER, writes ADMIN/HR. */
@RestController
@RequestMapping("/api/v1")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/onboardings")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<OnboardingDto.OnboardingResponse>>> list(
            @RequestParam(required = false) OnboardingStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(onboardingService.list(status, search, page, size)));
    }

    /** Convert a SELECTED application into an employee + onboarding record. */
    @PostMapping("/onboardings/start-application")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<OnboardingDto.OnboardingResponse>> startFromApplication(
            @Valid @RequestBody OnboardingDto.StartFromApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Onboarding started", onboardingService.startFromApplication(request)));
    }

    /** Open an onboarding record for an existing employee (no recruitment origin). */
    @PostMapping("/onboardings/start-employee")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<OnboardingDto.OnboardingResponse>> startForEmployee(
            @Valid @RequestBody OnboardingDto.StartForEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Onboarding started", onboardingService.startForEmployee(request)));
    }

    /** Toggle one checklist item; completion % and status are recomputed. */
    @PatchMapping("/onboardings/{id}/checklist")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<OnboardingDto.OnboardingResponse>> updateChecklist(
            @PathVariable Long id, @Valid @RequestBody OnboardingDto.ChecklistUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Checklist updated",
                onboardingService.updateChecklist(id, request)));
    }
}
