package com.hrgenius.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only aggregation payload for the admin dashboard.
 * Nested records keep the JSON shape predictable for the Angular charts.
 */
public record DashboardStats(
        Kpis kpis,
        List<NameCount> departmentDistribution,
        List<MonthCount> hiringTrend,
        List<StatusCount> hiringPipeline,
        AttendanceSummary attendanceToday,
        List<StatusCount> leaveSummary,
        PayrollSummary payrollThisMonth,
        List<RecentHire> recentHires,
        List<PendingApproval> pendingApprovals,
        List<UpcomingInterview> upcomingInterviews
) {

    /** Top-line KPI cards. */
    public record Kpis(
            long totalEmployees,
            long activeEmployees,
            long newHiresLast30Days,
            long openPositions,
            long totalCandidates,
            long pendingLeaveRequests
    ) {}

    /** Generic label/count pair (bar charts, pie charts). */
    public record NameCount(String name, long count) {}

    public record StatusCount(String status, long count) {}

    /** Hires per calendar month. */
    public record MonthCount(int year, int month, long count) {}

    /** Attendance breakdown for the current day. */
    public record AttendanceSummary(
            LocalDate day,
            long present,
            long absent,
            long halfDay,
            long onLeave,
            long holiday,
            long totalRecords
    ) {
        public double attendancePercent() {
            long working = present + halfDay + absent + onLeave;
            return working == 0 ? 0.0 : Math.round((present + 0.5 * halfDay) * 1000.0 / working) / 10.0;
        }
    }

    /** Current month payroll roll-up. */
    public record PayrollSummary(
            int year,
            int month,
            long payslips,
            BigDecimal totalNet
    ) {}

    /** Compact employee projection for lists. */
    public record RecentHire(
            Long id,
            String employeeCode,
            String fullName,
            String department,
            String designation,
            java.time.LocalDate joiningDate,
            String status
    ) {}

    public record PendingApproval(
            Long id,
            String employeeName,
            String leaveType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            String status
    ) {}

    public record UpcomingInterview(
            Long id,
            String candidateName,
            String jobTitle,
            java.time.OffsetDateTime interviewDate,
            String mode
    ) {}
}
