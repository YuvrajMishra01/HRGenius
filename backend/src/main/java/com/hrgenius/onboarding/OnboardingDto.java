package com.hrgenius.onboarding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/** Onboarding API contracts. Entities stay internal. */
public final class OnboardingDto {

    private OnboardingDto() {
    }

    /** Kick off onboarding for a SELECTED application (origin-linked). */
    public record StartFromApplicationRequest(
            @NotNull Long applicationId,
            LocalDate joiningDate) {
    }

    /** Kick off onboarding for an existing employee (no recruitment origin). */
    public record StartForEmployeeRequest(
            @NotNull Long employeeId,
            LocalDate joiningDate) {
    }

    /** Toggle one checklist item. */
    public record ChecklistUpdateRequest(
            @NotNull int itemIndex,
            @NotNull boolean done,
            String note) {
    }

    public record ChecklistItemDto(String label, boolean done) {
    }

    public record OnboardingResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String departmentName,
            Long applicationId,
            String candidateName,
            String jobTitle,
            LocalDate joiningDate,
            OnboardingStatus status,
            BigDecimal completionPercentage,
            List<ChecklistItemDto> checklist) {
    }
}
