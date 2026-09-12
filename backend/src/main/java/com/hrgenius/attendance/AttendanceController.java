package com.hrgenius.attendance;

import java.time.LocalDate;

import com.hrgenius.common.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendance endpoints (Phase 7). Reads: ADMIN/HR/MANAGER.
 * Writes (check-in/out, manual marking): ADMIN/HR — user accounts are not
 * yet linked to employees, so self-service would be spoofable.
 */
@RestController
@RequestMapping("/api/v1")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/attendance/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<AttendanceDto.TodayResponse>> today() {
        return ResponseEntity.ok(ApiResponse.of(attendanceService.today()));
    }

    /** Defaults to the current month when params are omitted. */
    @GetMapping("/attendance/month")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<AttendanceDto.MonthResponse>> month(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return ResponseEntity.ok(ApiResponse.of(attendanceService.month(y, m)));
    }

    @PostMapping("/attendance/check-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AttendanceDto.RecordResponse>> checkIn(
            @RequestParam Long employeeId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Checked in", attendanceService.checkIn(employeeId)));
    }

    @PostMapping("/attendance/check-out")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AttendanceDto.RecordResponse>> checkOut(
            @RequestParam Long employeeId) {
        return ResponseEntity.ok(ApiResponse.of("Checked out", attendanceService.checkOut(employeeId)));
    }

    @PostMapping("/attendance/mark")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<AttendanceDto.RecordResponse>> mark(
            @Valid @RequestBody AttendanceDto.MarkRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Attendance marked", attendanceService.mark(request)));
    }
}
