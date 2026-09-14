package com.hrgenius.recruitment;

import java.util.List;
import java.util.Map;

import com.hrgenius.common.ApiResponse;
import com.hrgenius.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recruitment module endpoints (Phase 5).
 *
 * RBAC: ADMIN/HR/MANAGER read the pipeline; writes are ADMIN/HR
 * (recruitment is an HR function; managers read-only for now).
 */
@RestController
@RequestMapping("/api/v1")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    public RecruitmentController(RecruitmentService recruitmentService) {
        this.recruitmentService = recruitmentService;
    }

    // ------------------------------------------------------------- jobs

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<RecruitmentDto.JobResponse>>> jobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.listJobs(search, status, page, size)));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.JobResponse>> createJob(
            @Valid @RequestBody RecruitmentDto.JobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Job created", recruitmentService.createJob(request)));
    }

    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.JobResponse>> updateJob(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDto.JobRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Job updated", recruitmentService.updateJob(id, request)));
    }

    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long id) {
        recruitmentService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.message("Job deleted"));
    }

    // ------------------------------------------------------- candidates

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<RecruitmentDto.CandidateResponse>>> candidates(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CandidateStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.listCandidates(search, status, page, size)));
    }

    @PostMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.CandidateResponse>> createCandidate(
            @Valid @RequestBody RecruitmentDto.CandidateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Candidate created", recruitmentService.createCandidate(request)));
    }

    @PutMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.CandidateResponse>> updateCandidate(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDto.CandidateRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Candidate updated", recruitmentService.updateCandidate(id, request)));
    }

    @DeleteMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(@PathVariable Long id) {
        recruitmentService.deleteCandidate(id);
        return ResponseEntity.ok(ApiResponse.message("Candidate deleted"));
    }

    // ----------------------------------------------------- applications

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<RecruitmentDto.ApplicationResponse>>> applications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.listApplications(search, status, page, size)));
    }

    /** KPI strip totals (Phase 14): pipeline counts independent of pagination. */
    @GetMapping("/applications/counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<ApplicationStatus, Long>>> applicationCounts() {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.applicationCounts()));
    }

    @PostMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.ApplicationResponse>> createApplication(
            @Valid @RequestBody RecruitmentDto.ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Application created", recruitmentService.createApplication(request)));
    }

    /** Pipeline move — illegal stage jumps are rejected with 409. */
    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.ApplicationResponse>> transitionApplication(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDto.TransitionRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Application moved",
                recruitmentService.transitionApplication(id, request)));
    }

    // ------------------------------------------------------- interviews

    @GetMapping("/interviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<RecruitmentDto.InterviewResponse>>> interviews(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Interview.InterviewStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(recruitmentService.listInterviews(search, status, page, size)));
    }

    @PostMapping("/interviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.InterviewResponse>> createInterview(
            @Valid @RequestBody RecruitmentDto.InterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Interview scheduled", recruitmentService.createInterview(request)));
    }

    @PatchMapping("/interviews/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.InterviewResponse>> rescheduleInterview(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDto.InterviewRescheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Interview updated", recruitmentService.rescheduleInterview(id, request)));
    }

    @PostMapping("/interviews/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.InterviewResponse>> completeInterview(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDto.InterviewFeedbackRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Interview completed", recruitmentService.completeInterview(id, request)));
    }

    @PostMapping("/interviews/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<RecruitmentDto.InterviewResponse>> cancelInterview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Interview cancelled", recruitmentService.cancelInterview(id)));
    }
}
