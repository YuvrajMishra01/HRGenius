package com.hrgenius.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request/response payloads for the leave module (Phase 8). */
public final class LeaveDto {

    private LeaveDto() {
    }

    // ------------------------------------------------------------ leave types

    public record LeaveTypeRequest(
            @NotBlank @Size(max = 50) String name,
            @Size(max = 255) String description,
            @NotNull @Positive Integer yearlyLimit) {
    }

    public record LeaveTypeResponse(
            Long id,
            String name,
            String description,
            int yearlyLimit,
            long usageCount) {
    }

    // ------------------------------------------------------------ requests

    public record CreateLeaveRequest(
            @NotNull Long employeeId,
            @NotNull Long leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason) {
    }

    public record LeaveRequestResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String departmentName,
            Long leaveTypeId,
            String leaveTypeName,
            LocalDate startDate,
            LocalDate endDate,
            double workingDays,
            String reason,
            LeaveRequest.LeaveStatus status,
            String approverEmail,
            LocalDateTime createdAt) {
    }

    /** Per-employee, per-type entitlement usage for one year. */
    public record BalanceRow(
            Long leaveTypeId,
            String leaveTypeName,
            int yearlyLimit,
            double usedDays,
            double remainingDays) {
    }

    public record BalanceResponse(
            Long employeeId,
            String employeeName,
            String employeeCode,
            int year,
            List<BalanceRow> balances) {
    }

    /** Summary KPIs for the page header. */
    public record LeaveSummary(
            long pendingCount,
            long approvedThisYear,
            long rejectedCount,
            BigDecimal approvalRate) {
    }
}
