package com.hrgenius.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Attendance module (Phase 7): check-in/check-out, manual marking, today
 * summary + month grid. One record per employee per day (UK_ATT_EMP_DATE).
 *
 * Error contract: EntityNotFoundException → 404, IllegalArgumentException → 400,
 * IllegalStateException → 409.
 */
@Service
public class AttendanceService {

    /** Statuses where check-in/out times make sense; marking clears them otherwise. */
    private static final Set<Attendance.AttendanceStatus> WORKING_STATUSES =
            Set.of(Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.HALF_DAY);

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    // -------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public AttendanceDto.TodayResponse today() {
        LocalDate day = LocalDate.now();
        List<AttendanceDto.RecordResponse> records = attendanceRepository.findDayWithDetails(day).stream()
                .map(AttendanceService::toRecord)
                .toList();
        return new AttendanceDto.TodayResponse(day, summarize(day), records);
    }

    @Transactional(readOnly = true)
    public AttendanceDto.MonthResponse month(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<Attendance> records = attendanceRepository.findMonthWithDetails(ym.atDay(1), ym.atEndOfMonth());

        // Group records per employee, then build each MonthRow exactly once.
        Map<Long, List<Attendance>> byEmployee = new LinkedHashMap<>();
        for (Attendance a : records) {
            byEmployee.computeIfAbsent(a.getEmployee().getId(), k -> new ArrayList<>()).add(a);
        }

        List<AttendanceDto.MonthRow> rows = new ArrayList<>();
        for (Employee e : employeeRepository.findAll()) {
            List<Attendance> list = byEmployee.getOrDefault(e.getId(), List.of());
            rows.add(buildRow(e, list));
        }
        rows.sort(Comparator.comparing(AttendanceDto.MonthRow::employeeName));
        return new AttendanceDto.MonthResponse(year, month, rows);
    }

    // ------------------------------------------------------- check-in/out

    @Transactional
    public AttendanceDto.RecordResponse checkIn(Long employeeId) {
        LocalDate day = LocalDate.now();
        if (attendanceRepository.findByEmployee_IdAndAttendanceDate(employeeId, day).isPresent()) {
            throw new IllegalStateException("Already checked in today");
        }
        Attendance attendance = new Attendance();
        attendance.setEmployee(loadEmployee(employeeId));
        attendance.setAttendanceDate(day);
        attendance.setCheckIn(OffsetDateTime.now());
        attendance.setStatus(Attendance.AttendanceStatus.PRESENT);
        return toRecord(attendanceRepository.save(attendance));
    }

    @Transactional
    public AttendanceDto.RecordResponse checkOut(Long employeeId) {
        Attendance attendance = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employeeId, LocalDate.now())
                .orElseThrow(() -> new IllegalStateException("No check-in found for today"));
        if (attendance.getCheckOut() != null) {
            throw new IllegalStateException("Already checked out today");
        }
        attendance.setCheckOut(OffsetDateTime.now());
        attendance.setWorkingHours(computeWorkingHours(attendance));
        return toRecord(attendanceRepository.save(attendance));
    }

    // ------------------------------------------------------ manual marking

    /**
     * Upsert one day. Working statuses (PRESENT/HALF_DAY) keep any existing
     * check-in/out times; non-working statuses clear them (a day marked LEAVE
     * must not advertise check-in times).
     */
    @Transactional
    public AttendanceDto.RecordResponse mark(AttendanceDto.MarkRequest request) {
        Employee employee = loadEmployee(request.employeeId());
        Attendance attendance = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(request.employeeId(), request.date())
                .orElseGet(() -> {
                    Attendance created = new Attendance();
                    created.setEmployee(employee);
                    created.setAttendanceDate(request.date());
                    return created;
                });
        attendance.setStatus(request.status());
        if (!WORKING_STATUSES.contains(request.status())) {
            attendance.setCheckIn(null);
            attendance.setCheckOut(null);
            attendance.setWorkingHours(null);
        }
        return toRecord(attendanceRepository.save(attendance));
    }

    // ------------------------------------------------------------- helpers

    private AttendanceDto.DaySummary summarize(LocalDate day) {
        int present = 0;
        int halfDay = 0;
        int absent = 0;
        int leave = 0;
        int holiday = 0;
        for (Object[] row : attendanceRepository.countByDayRaw(day)) {
            Attendance.AttendanceStatus status = (Attendance.AttendanceStatus) row[0];
            int count = ((Number) row[1]).intValue();
            switch (status) {
                case PRESENT -> present = count;
                case HALF_DAY -> halfDay = count;
                case ABSENT -> absent = count;
                case LEAVE -> leave = count;
                case HOLIDAY -> holiday = count;
            }
        }
        int total = present + halfDay + absent + leave + holiday;
        return new AttendanceDto.DaySummary(present, halfDay, absent, leave, holiday, total);
    }

    private AttendanceDto.MonthRow buildRow(Employee e, List<Attendance> records) {
        int present = 0;
        int halfDay = 0;
        int absent = 0;
        int leave = 0;
        int holiday = 0;
        BigDecimal hours = BigDecimal.ZERO;
        Map<String, Attendance.AttendanceStatus> days = new LinkedHashMap<>();

        for (Attendance a : records) {
            switch (a.getStatus()) {
                case PRESENT -> present++;
                case HALF_DAY -> halfDay++;
                case ABSENT -> absent++;
                case LEAVE -> leave++;
                case HOLIDAY -> holiday++;
            }
            days.put(String.valueOf(a.getAttendanceDate().getDayOfMonth()), a.getStatus());
            if (a.getWorkingHours() != null) {
                hours = hours.add(a.getWorkingHours());
            }
        }

        int total = records.size();
        // HALF_DAY counts as 0.5 of an attended day.
        double percent = total == 0 ? 0.0
                : Math.round((present + 0.5 * halfDay) * 1000.0 / total) / 10.0;

        return new AttendanceDto.MonthRow(
                e.getId(), e.getEmployeeCode(),
                e.getFirstName() + " " + e.getLastName(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                present, halfDay, absent, leave, holiday, total,
                hours, percent, days);
    }

    private BigDecimal computeWorkingHours(Attendance attendance) {
        Duration duration = Duration.between(attendance.getCheckIn(), attendance.getCheckOut());
        if (duration.isNegative()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(duration.toMinutes() / 60.0)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Employee loadEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));
    }

    private static AttendanceDto.RecordResponse toRecord(Attendance a) {
        Employee e = a.getEmployee();
        return new AttendanceDto.RecordResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName() + " " + e.getLastName(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                a.getAttendanceDate(),
                a.getStatus(),
                a.getCheckIn(),
                a.getCheckOut(),
                a.getWorkingHours());
    }
}
