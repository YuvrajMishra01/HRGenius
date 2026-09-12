package com.hrgenius.performance;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response payloads for the performance module (Phase 10). */
public final class PerformanceDto {

    private PerformanceDto() {
    }

    // ------------------------------------------------------------ create/update

    public record CreateRequest(
            @NotNull Long employeeId,
            @NotNull Long reviewerId,
            @NotBlank @Size(max = 20) String reviewPeriod,
            @Size(max = 1000) String goals) {
    }

    public record UpdateRequest(
            @Size(max = 1000) String goals,
            @Size(max = 1000) String strengths,
            @Size(max = 1000) String weaknesses,
            @Size(max = 1000) String comments) {
    }

    /** Rating + submit in one step; rating 1–5 is mandatory. */
    public record RateRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 1000) String comments) {
    }

    // ------------------------------------------------------------ responses

    public record ReviewResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String departmentName,
            Long reviewerId,
            String reviewerName,
            String reviewPeriod,
            Integer rating,
            String strengths,
            String weaknesses,
            String goals,
            String comments,
            PerformanceReview.ReviewStatus status) {
    }

    public record RatingBucket(
            int rating,
            long count) {
    }

    public record SummaryResponse(
            long totalReviews,
            long draftCount,
            long submittedCount,
            long acknowledgedCount,
            BigDecimal averageRating,
            List<RatingBucket> ratingDistribution) {
    }
}