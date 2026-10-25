package com.hrgenius.recruitment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.hrgenius.employee.EmploymentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request/response contracts for the recruitment module (Phase 5).
 * Entities never cross the API boundary.
 */
public final class RecruitmentDto {

    private RecruitmentDto() {
    }

    // ------------------------------------------------------------- jobs

    public record JobRequest(
            @NotBlank @Size(max = 150) String title,
            String description,
            @NotNull Long departmentId,
            @Size(max = 100) String location,
            @NotNull EmploymentType employmentType,
            @Size(max = 60) String salaryRange,
            @NotNull JobStatus status,
            LocalDate closingDate) {
    }

    public record JobResponse(
            Long id, String title, String description,
            Long departmentId, String departmentName,
            String location, String employmentType, String salaryRange,
            JobStatus status, LocalDate postedDate, LocalDate closingDate,
            long applicationCount) {
    }

    // -------------------------------------------------------- candidates

    public record CandidateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 20) String phone,
            @Size(max = 255) String resumePath,
            @Size(max = 500) String skills,
            BigDecimal experienceYears) {
    }

    public record CandidateResponse(
            Long id, String name, String email, String phone,
            String resumePath, String skills, BigDecimal experienceYears,
            CandidateStatus status, LocalDateTime createdAt,
            long applicationCount) {
    }

    // ------------------------------------------------------ applications

    public record ApplicationRequest(
            @NotNull Long candidateId,
            @NotNull Long jobId,
            @Size(max = 500) String remarks) {
    }

    public record ApplicationResponse(
            Long id, Long candidateId, String candidateName, String candidateEmail,
            Long jobId, String jobTitle, String departmentName,
            LocalDate applicationDate, ApplicationStatus status, String remarks) {
    }

    /** Pipeline move: target stage + optional remark. Illegal moves are 409. */
    public record TransitionRequest(
            @NotNull ApplicationStatus status,
            @Size(max = 500) String remarks) {
    }

    // -------------------------------------------------------- interviews

    public record InterviewRequest(
            @NotNull Long applicationId,
            Long interviewerId,
            @NotNull OffsetDateTime interviewDate,
            @NotNull Interview.InterviewMode mode) {
    }

    public record InterviewRescheduleRequest(
            OffsetDateTime interviewDate,
            Interview.InterviewMode mode,
            Long interviewerId) {
    }

    public record InterviewFeedbackRequest(
            @Size(max = 1000) String feedback,
            @NotNull Interview.InterviewResult result) {
    }

    public record InterviewResponse(
            Long id, Long applicationId, String candidateName, String jobTitle,
            Long interviewerId, String interviewerName,
            OffsetDateTime interviewDate, Interview.InterviewMode mode,
            Interview.InterviewStatus status, String feedback,
            Interview.InterviewResult result) {
    }
}
