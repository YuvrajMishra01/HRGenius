package com.hrgenius.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hrgenius.employee.Employee;

import lombok.Getter;
import lombok.Setter;

/**
 * Performance review with goals, rating, and lifecycle state.
 * Mapped to PERFORMANCE_REVIEWS (V1).
 */
@Entity
@Table(name = "PERFORMANCE_REVIEWS")
@Getter
@Setter
public class PerformanceReview {

    public enum ReviewStatus {
        DRAFT, SUBMITTED, ACKNOWLEDGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The employee being reviewed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    private Employee employee;

    /** The manager/reviewer who writes the review. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REVIEWER_ID", nullable = false)
    private Employee reviewer;

    @Column(name = "REVIEW_PERIOD", length = 20)
    private String reviewPeriod;

    @Column(name = "RATING")
    private Integer rating;

    @Column(name = "STRENGTHS", length = 1000)
    private String strengths;

    @Column(name = "WEAKNESSES", length = 1000)
    private String weaknesses;

    @Column(name = "GOALS", length = 1000)
    private String goals;

    @Column(name = "COMMENTS", length = 1000)
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.DRAFT;
}