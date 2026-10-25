package com.hrgenius.analytics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrgenius.attendance.AttendanceRepository;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.employee.EmployeeStatus;
import com.hrgenius.employee.EmploymentType;
import com.hrgenius.leave.LeaveRequest;
import com.hrgenius.leave.LeaveRequestRepository;
import com.hrgenius.payroll.PayrollRepository;
import com.hrgenius.performance.PerformanceReview;
import com.hrgenius.performance.PerformanceReviewRepository;
import com.hrgenius.recruitment.Interview.InterviewResult;
import com.hrgenius.recruitment.Interview.InterviewStatus;
import com.hrgenius.recruitment.InterviewRepository;
import com.hrgenius.recruitment.JobApplicationRepository;
import com.hrgenius.recruitment.JobStatus;

/**
 * Phase 13 analytics: deeper, chart-shaped aggregates beyond the live
 * dashboard. Every grouping happens in the database; this class only maps
 * Object[] rows into the DTO tree and computes a few Java-side derivations
 * that SQL cannot express cleanly (tenure buckets, pass rate, funnels).
 */
@Service
public class AnalyticsService {

    private final EmployeeRepository employeeRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;
    private final PerformanceReviewRepository reviewRepository;

    public AnalyticsService(EmployeeRepository employeeRepository,
                            JobApplicationRepository applicationRepository,
                            InterviewRepository interviewRepository,
                            LeaveRequestRepository leaveRequestRepository,
                            PayrollRepository payrollRepository,
                            PerformanceReviewRepository reviewRepository) {
        this.employeeRepository = employeeRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollRepository = payrollRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.WorkforceResponse workforce() {
        LocalDate today = LocalDate.now();
        long active = employeeRepository.countByStatus(EmployeeStatus.ACTIVE);
        long terminated = employeeRepository.countByStatus(EmployeeStatus.TERMINATED);

        List<AnalyticsDto.NameCount> byDepartment = employeeRepository.countByDepartmentRaw().stream()
                .map(row -> new AnalyticsDto.NameCount((String) row[0], (Long) row[1]))
                .sorted(Comparator.comparingLong(AnalyticsDto.NameCount::count).reversed())
                .toList();

        List<AnalyticsDto.TypeCount> byType = employeeRepository.countByEmploymentTypeRaw().stream()
                .map(row -> new AnalyticsDto.TypeCount(((EmploymentType) row[0]).name(), (Long) row[1]))
                .toList();

        List<LocalDate> joiningDates = employeeRepository.findAllJoiningDates();
        double avgTenureYears = joiningDates.isEmpty() ? 0.0
                : joiningDates.stream()
                        .mapToLong(d -> Math.max(0, ChronoUnit.MONTHS.between(d, today)))
                        .average().orElse(0) / 12.0;
        avgTenureYears = Math.round(avgTenureYears * 10.0) / 10.0;

        List<AnalyticsDto.TenureBucket> buckets = List.of(
                tenureBucket("< 1 year", joiningDates, today, 0, 1),
                tenureBucket("1–3 years", joiningDates, today, 1, 3),
                tenureBucket("3–5 years", joiningDates, today, 3, 5),
                tenureBucket("5+ years", joiningDates, today, 5, Integer.MAX_VALUE));

        return new AnalyticsDto.WorkforceResponse(
                active, terminated, avgTenureYears, byDepartment, byType, buckets);
    }

    private static AnalyticsDto.TenureBucket tenureBucket(String label, List<LocalDate> joiningDates,
                                                          LocalDate today, int minYears, int maxYears) {
        long count = joiningDates.stream()
                .filter(d -> {
                    long years = ChronoUnit.YEARS.between(d, today);
                    return years >= minYears && years < maxYears;
                })
                .count();
        return new AnalyticsDto.TenureBucket(label, count);
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.FunnelResponse funnel() {
        List<AnalyticsDto.JobFunnel> perJob = applicationRepository.funnelByJobRaw().stream()
                .map(row -> new AnalyticsDto.JobFunnel(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((JobStatus) row[2]).name(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue(),
                        ((Number) row[5]).longValue(),
                        ((Number) row[6]).longValue()))
                .toList();
        long total = perJob.stream().mapToLong(AnalyticsDto.JobFunnel::applications).sum();
        return new AnalyticsDto.FunnelResponse(total, perJob);
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.InterviewsResponse interviews() {
        Map<InterviewStatus, Long> byStatus = new EnumMap<>(InterviewStatus.class);
        interviewRepository.countByStatusRaw()
                .forEach(row -> byStatus.put((InterviewStatus) row[0], (Long) row[1]));
        Map<InterviewResult, Long> byResult = new EnumMap<>(InterviewResult.class);
        interviewRepository.countByResultRaw()
                .forEach(row -> byResult.put((InterviewResult) row[0], (Long) row[1]));

        long completed = byStatus.getOrDefault(InterviewStatus.COMPLETED, 0L);
        long pass = byResult.getOrDefault(InterviewResult.PASS, 0L);
        double passRate = completed == 0 ? 0.0
                : Math.round(pass * 1000.0 / completed) / 10.0;
        return new AnalyticsDto.InterviewsResponse(
                completed,
                byStatus.getOrDefault(InterviewStatus.SCHEDULED, 0L),
                byStatus.getOrDefault(InterviewStatus.CANCELLED, 0L),
                pass,
                byResult.getOrDefault(InterviewResult.FAIL, 0L),
                byResult.getOrDefault(InterviewResult.ON_HOLD, 0L),
                passRate);
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.LeaveResponse leave() {
        LocalDate today = LocalDate.now();
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(today.getYear(), 12, 31);
        List<AnalyticsDto.NameCount> byType = leaveRequestRepository
                .countByTypeWithinYear(yearStart, yearEnd).stream()
                .map(row -> new AnalyticsDto.NameCount((String) row[0], (Long) row[1]))
                .toList();
        List<AnalyticsDto.StatusCount> byStatus = leaveRequestRepository.countByStatusRaw().stream()
                .map(row -> new AnalyticsDto.StatusCount(
                        ((LeaveRequest.LeaveStatus) row[0]).name(), (Long) row[1]))
                .toList();
        return new AnalyticsDto.LeaveResponse(today.getYear(), byType, byStatus);
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.PayrollTrendResponse payrollTrend() {
        // periodTotals is newest first; charts read oldest → newest.
        List<AnalyticsDto.PeriodNet> periods = payrollRepository.periodTotals().stream()
                .map(row -> new AnalyticsDto.PeriodNet(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue(),
                        (java.math.BigDecimal) row[3]))
                .toList();
        List<AnalyticsDto.PeriodNet> chronological = new java.util.ArrayList<>(periods);
        java.util.Collections.reverse(chronological);
        return new AnalyticsDto.PayrollTrendResponse(List.copyOf(chronological));
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.PerformanceResponse performance() {
        long total = reviewRepository.count();
        Double avg = reviewRepository.averageRating();
        double average = avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
        List<AnalyticsDto.StatusCount> byStatus = List.of(
                new AnalyticsDto.StatusCount(PerformanceReview.ReviewStatus.DRAFT.name(),
                        reviewRepository.countByStatus(PerformanceReview.ReviewStatus.DRAFT)),
                new AnalyticsDto.StatusCount(PerformanceReview.ReviewStatus.SUBMITTED.name(),
                        reviewRepository.countByStatus(PerformanceReview.ReviewStatus.SUBMITTED)),
                new AnalyticsDto.StatusCount(PerformanceReview.ReviewStatus.ACKNOWLEDGED.name(),
                        reviewRepository.countByStatus(PerformanceReview.ReviewStatus.ACKNOWLEDGED)));
        List<AnalyticsDto.RatingCount> byRating = reviewRepository.ratingDistribution().stream()
                .map(row -> new AnalyticsDto.RatingCount(
                        ((Number) row[0]).intValue(), (Long) row[1]))
                .toList();
        return new AnalyticsDto.PerformanceResponse(total, average, byStatus, byRating);
    }
}
