package com.hrgenius.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrgenius.attendance.AttendanceRepository;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.employee.EmployeeStatus;
import com.hrgenius.leave.LeaveRequestRepository;
import com.hrgenius.payroll.PayrollRepository;
import com.hrgenius.recruitment.ApplicationStatus;
import com.hrgenius.recruitment.CandidateRepository;
import com.hrgenius.recruitment.Interview.InterviewStatus;
import com.hrgenius.recruitment.InterviewRepository;
import com.hrgenius.recruitment.JobApplicationRepository;
import com.hrgenius.recruitment.JobRepository;
import com.hrgenius.recruitment.JobStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aggregates everything the admin dashboard shows in ONE round trip.
 * All counting/grouping happens in the database; this class only shapes
 * the results. Read-only — wrap in a read transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final EmployeeRepository employeeRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollRepository payrollRepository;

    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate thirtyDaysAgo = today.minusDays(30);
        YearMonth currentMonth = YearMonth.from(today);

        return new DashboardStats(
                buildKpis(today, thirtyDaysAgo),
                departmentDistribution(),
                hiringTrend(thirtyDaysAgo),
                hiringPipeline(),
                attendanceToday(today),
                leaveSummary(),
                payrollSummary(currentMonth),
                recentHires(thirtyDaysAgo),
                pendingApprovals(),
                upcomingInterviews());
    }

    // ------------------------------------------------------------- parts

    private DashboardStats.Kpis buildKpis(LocalDate today, LocalDate thirtyDaysAgo) {
        long total = employeeRepository.count();
        long active = employeeRepository.countByStatus(EmployeeStatus.ACTIVE);
        long newHires = employeeRepository.countByJoiningDateGreaterThanEqual(thirtyDaysAgo);
        return new DashboardStats.Kpis(
                total,
                active,
                newHires,
                jobRepository.countByStatus(JobStatus.OPEN),
                candidateRepository.count(),
                leaveRequestRepository.countByStatus(
                        com.hrgenius.leave.LeaveRequest.LeaveStatus.PENDING));
    }

    private List<DashboardStats.NameCount> departmentDistribution() {
        return employeeRepository.countByDepartmentRaw().stream()
                .map(row -> new DashboardStats.NameCount((String) row[0], (Long) row[1]))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }

    private List<DashboardStats.MonthCount> hiringTrend(LocalDate since) {
        return employeeRepository.countHiresByMonthSince(since).stream()
                .map(row -> new DashboardStats.MonthCount(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    private List<DashboardStats.StatusCount> hiringPipeline() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        applicationRepository.countByStatusRaw()
                .forEach(row -> counts.put((ApplicationStatus) row[0], (Long) row[1]));
        // Emit every pipeline stage so the chart keeps a stable order.
        return List.of(ApplicationStatus.values()).stream()
                .map(status -> new DashboardStats.StatusCount(status.name(), counts.getOrDefault(status, 0L)))
                .toList();
    }

    private DashboardStats.AttendanceSummary attendanceToday(LocalDate today) {
        Map<com.hrgenius.attendance.Attendance.AttendanceStatus, Long> counts =
                new EnumMap<>(com.hrgenius.attendance.Attendance.AttendanceStatus.class);
        List<Object[]> rows = attendanceRepository.countByDayRaw(today);
        rows.forEach(row -> counts.put(
                (com.hrgenius.attendance.Attendance.AttendanceStatus) row[0], (Long) row[1]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new DashboardStats.AttendanceSummary(
                today,
                counts.getOrDefault(com.hrgenius.attendance.Attendance.AttendanceStatus.PRESENT, 0L),
                counts.getOrDefault(com.hrgenius.attendance.Attendance.AttendanceStatus.ABSENT, 0L),
                counts.getOrDefault(com.hrgenius.attendance.Attendance.AttendanceStatus.HALF_DAY, 0L),
                counts.getOrDefault(com.hrgenius.attendance.Attendance.AttendanceStatus.LEAVE, 0L),
                counts.getOrDefault(com.hrgenius.attendance.Attendance.AttendanceStatus.HOLIDAY, 0L),
                total);
    }

    private List<DashboardStats.StatusCount> leaveSummary() {
        return leaveRequestRepository.countByStatusRaw().stream()
                .map(row -> new DashboardStats.StatusCount(
                        ((com.hrgenius.leave.LeaveRequest.LeaveStatus) row[0]).name(), (Long) row[1]))
                .toList();
    }

    private DashboardStats.PayrollSummary payrollSummary(YearMonth month) {
        List<Object[]> totals = payrollRepository.totalsForPeriod(month.getYear(), month.getMonthValue());
        Object[] row = totals.isEmpty() ? new Object[]{0L, BigDecimal.ZERO} : totals.get(0);
        return new DashboardStats.PayrollSummary(
                month.getYear(),
                month.getMonthValue(),
                ((Number) row[0]).longValue(),
                (BigDecimal) row[1]);
    }

    private List<DashboardStats.RecentHire> recentHires(LocalDate since) {
        return employeeRepository
                .findByJoiningDateGreaterThanEqualOrderByJoiningDateDesc(since, PageRequest.of(0, 5))
                .stream()
                .map(DashboardService::toRecentHire)
                .toList();
    }

    private static DashboardStats.RecentHire toRecentHire(Employee e) {
        return new DashboardStats.RecentHire(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName() + " " + e.getLastName(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getDesignation() != null ? e.getDesignation().getTitle() : null,
                e.getJoiningDate(),
                e.getStatus().name());
    }

    private List<DashboardStats.PendingApproval> pendingApprovals() {
        return leaveRequestRepository
                .findByStatusOrderByStartDateAsc(
                        com.hrgenius.leave.LeaveRequest.LeaveStatus.PENDING, PageRequest.of(0, 5))
                .stream()
                .map(l -> new DashboardStats.PendingApproval(
                        l.getId(),
                        l.getEmployee().getFirstName() + " " + l.getEmployee().getLastName(),
                        l.getLeaveType().getName(),
                        l.getStartDate(),
                        l.getEndDate(),
                        l.getStatus().name()))
                .toList();
    }

    private List<DashboardStats.UpcomingInterview> upcomingInterviews() {
        return interviewRepository
                .findByStatusAndInterviewDateAfterOrderByInterviewDateAsc(
                        InterviewStatus.SCHEDULED, OffsetDateTime.now(ZONE))
                .stream()
                .limit(5)
                .map(i -> new DashboardStats.UpcomingInterview(
                        i.getId(),
                        i.getApplication().getCandidate().getName(),
                        i.getApplication().getJob().getTitle(),
                        i.getInterviewDate(),
                        i.getMode().name()))
                .toList();
    }
}
