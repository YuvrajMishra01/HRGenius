package com.hrgenius.payroll;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Request/response payloads for the payroll module (Phase 9). */
public final class PayrollDto {

    private PayrollDto() {
    }

    // ------------------------------------------------------------ run

    /** Trigger a monthly run: generate DRAFT payslips for active employees. */
    public record RunRequest(
            @NotNull @Min(2000) @Max(2999) Integer year,
            @NotNull @Min(1) @Max(12) Integer month) {
    }

    public record RunResponse(
            int year,
            int month,
            int created,
            int skipped,
            long periodPayslips,
            BigDecimal periodTotalNet) {
    }

    // ------------------------------------------------------------ payslips

    public record ComponentUpdateRequest(
            @NotNull @PositiveOrZero BigDecimal basicSalary,
            @NotNull @PositiveOrZero BigDecimal allowances,
            @NotNull @PositiveOrZero BigDecimal deductions,
            @NotNull @PositiveOrZero BigDecimal tax) {
    }

    public record PayrollRow(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String departmentName,
            int payYear,
            int payMonth,
            BigDecimal basicSalary,
            BigDecimal allowances,
            BigDecimal deductions,
            BigDecimal tax,
            BigDecimal netSalary,
            Payroll.PayrollStatus status) {
    }

    public record PeriodSummary(
            int year,
            int month,
            long payslipCount,
            BigDecimal totalGross,
            BigDecimal totalNet,
            long draftCount,
            long processedCount,
            long paidCount) {
    }

    public record PeriodResponse(
            PeriodSummary summary,
            List<PayrollRow> payslips) {
    }

    /** One row of the runs overview: a month that has payroll data. */
    public record PeriodRow(
            int year,
            int month,
            long payslipCount,
            BigDecimal totalNet) {
    }

    public record PeriodsResponse(
            List<PeriodRow> periods,
            LocalDateTime generatedAt) {
    }
}
