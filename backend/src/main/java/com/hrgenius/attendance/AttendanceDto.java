package com.hrgenius.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** Attendance API contracts. Entities stay internal. */
public final class AttendanceDto {

    private AttendanceDto() {
    }

    /** Manual marking / correction of one day. */
    public record MarkRequest(
            @NotNull Long employeeId,
            @NotNull LocalDate date,
            @NotNull Attendance.AttendanceStatus status) {
    }

    public record RecordResponse(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            LocalDate date,
            Attendance.AttendanceStatus status,
            OffsetDateTime checkIn,
            OffsetDateTime checkOut,
            BigDecimal workingHours) {
    }

    public record DaySummary(
            int present, int halfDay, int absent, int leave, int holiday, int total) {
    }

    /** GET /attendance/today payload. */
    public record TodayResponse(
            LocalDate date,
            DaySummary summary,
            java.util.List<RecordResponse> records) {
    }

    /** Per-employee month roll-up used by the monthly grid. */
    public record MonthRow(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            int present,
            int halfDay,
            int absent,
            int leave,
            int holiday,
            int totalRecords,
            BigDecimal workingHours,
            double attendancePercent,
            /** Day-of-month → status; days without a record are absent keys. */
            Map<String, Attendance.AttendanceStatus> days) {
    }

    /** GET /attendance/month payload. */
    public record MonthResponse(
            int year,
            int month,
            java.util.List<MonthRow> rows) {
    }
}
