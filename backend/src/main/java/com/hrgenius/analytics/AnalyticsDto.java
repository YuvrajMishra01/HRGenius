package com.hrgenius.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only aggregation payload for the Phase 13 analytics page.
 * Every section is computed by one grouped JPQL query — the service only
 * shapes rows; no entity lists ever cross the wire. JSON field order is
 * stable for the Angular charts.
 */
public record AnalyticsDto() {

    /** Workforce composition: headcount + avg tenure + attrition counters. */
    public record WorkforceResponse(
            long active,
            long terminated,
            double avgTenureYears,
            List<NameCount> byDepartment,
            List<TypeCount> byEmploymentType,
            List<TenureBucket> byTenureBucket) {
    }

    /** Hiring funnel: applications per job with stage counts. */
    public record FunnelResponse(
            long totalApplications,
            List<JobFunnel> perJob) {
    }

    public record JobFunnel(
            Long jobId,
            String title,
            String status,
            long applications,
            long active,
            long selected,
            long rejected) {
    }

    /** Interview quality from COMPLETED interviews only. */
    public record InterviewsResponse(
            long completed,
            long scheduled,
            long cancelled,
            long pass,
            long fail,
            long onHold,
            double passRate) {
    }

    /** Leave demand per type, current year. */
    public record LeaveResponse(
            int year,
            List<NameCount> byType,
            List<StatusCount> byStatus) {
    }

    /** Net payroll trend, newest period last (chart order). */
    public record PayrollTrendResponse(
            List<PeriodNet> periods) {
    }

    public record PeriodNet(int year, int month, long payslips, BigDecimal totalNet) {
    }

    /** Performance health over official ratings only (SUBMITTED/ACKNOWLEDGED). */
    public record PerformanceResponse(
            long totalReviews,
            double averageRating,
            List<StatusCount> byStatus,
            List<RatingCount> byRating) {
    }

    public record RatingCount(int rating, long count) {
    }

    // ------------------------------------------------------------ shared

    public record NameCount(String name, long count) {
    }

    public record TypeCount(String type, long count) {
    }

    public record StatusCount(String status, long count) {
    }

    /** Tenure cohort: employees joined within the range. */
    public record TenureBucket(String label, long count) {
    }
}
