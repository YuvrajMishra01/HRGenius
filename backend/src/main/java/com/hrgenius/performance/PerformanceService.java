package com.hrgenius.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.notification.Notification;
import com.hrgenius.notification.NotificationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Performance reviews (Phase 10): create with goals, iterate on a DRAFT,
 * then rate + submit in one step, and let the reviewed employee acknowledge.
 *
 * Lifecycle: DRAFT → SUBMITTED → ACKNOWLEDGED (one-way).
 * Rating must be 1–5 (DB constraint mirrors the service guard).
 *
 * Error contract: EntityNotFoundException → 404, IllegalArgumentException
 * → 400, IllegalStateException → 409 (via GlobalExceptionHandler).
 */
@Service
public class PerformanceService {

    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notifications;

    public PerformanceService(PerformanceReviewRepository reviewRepository,
                              EmployeeRepository employeeRepository,
                              NotificationService notifications) {
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------ views

    @Transactional(readOnly = true)
    public List<PerformanceDto.ReviewResponse> list(Long employeeId) {
        List<PerformanceReview> rows = employeeId == null
                ? reviewRepository.findAllWithDetails()
                : reviewRepository.findByEmployeeWithDetails(employeeId);
        return rows.stream().map(PerformanceService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PerformanceDto.SummaryResponse summary() {
        long total = reviewRepository.count();
        long drafts = reviewRepository.countByStatus(PerformanceReview.ReviewStatus.DRAFT);
        long submitted = reviewRepository.countByStatus(PerformanceReview.ReviewStatus.SUBMITTED);
        long acknowledged = reviewRepository.countByStatus(PerformanceReview.ReviewStatus.ACKNOWLEDGED);

        Double avg = reviewRepository.averageRating();
        BigDecimal average = avg == null ? null
                : BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);

        List<PerformanceDto.RatingBucket> distribution = reviewRepository.ratingDistribution().stream()
                .map(row -> new PerformanceDto.RatingBucket(
                        ((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
        return new PerformanceDto.SummaryResponse(total, drafts, submitted, acknowledged, average, distribution);
    }

    // ------------------------------------------------------------ lifecycle

    @Transactional
    public PerformanceDto.ReviewResponse create(PerformanceDto.CreateRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + request.employeeId()));
        Employee reviewer = employeeRepository.findById(request.reviewerId())
                .orElseThrow(() -> new EntityNotFoundException("Reviewer not found: " + request.reviewerId()));
        if (employee.getId().equals(reviewer.getId())) {
            throw new IllegalStateException("A review cannot be written by the reviewed employee");
        }
        if (reviewRepository.existsByEmployee_IdAndReviewPeriodIgnoreCase(
                employee.getId(), request.reviewPeriod().trim())) {
            throw new IllegalStateException("A review already exists for " + request.reviewPeriod()
                    + " — one review per employee per period");
        }

        PerformanceReview review = new PerformanceReview();
        review.setEmployee(employee);
        review.setReviewer(reviewer);
        review.setReviewPeriod(request.reviewPeriod().trim());
        review.setGoals(request.goals());
        review.setStatus(PerformanceReview.ReviewStatus.DRAFT);
        return toResponse(reviewRepository.save(review));
    }

    /** DRAFT-only content editing (goals, strengths, weaknesses, comments). */
    @Transactional
    public PerformanceDto.ReviewResponse update(Long id, PerformanceDto.UpdateRequest request) {
        PerformanceReview review = get(id);
        assertDraft(review);
        review.setGoals(request.goals());
        review.setStrengths(request.strengths());
        review.setWeaknesses(request.weaknesses());
        review.setComments(request.comments());
        return toResponse(reviewRepository.save(review));
    }

    /** Rate + submit in one step; only a DRAFT can be submitted. */
    @Transactional
    public PerformanceDto.ReviewResponse rate(Long id, PerformanceDto.RateRequest request) {
        PerformanceReview review = get(id);
        assertDraft(review);
        review.setRating(request.rating());
        review.setComments(request.comments());
        review.setStatus(PerformanceReview.ReviewStatus.SUBMITTED);
        PerformanceReview saved = reviewRepository.save(review);
        notifications.notifyEmployee(saved.getEmployee(), Notification.NotificationType.PERFORMANCE,
                "Performance review submitted",
                "Your " + saved.getReviewPeriod() + " review was submitted with rating "
                        + saved.getRating() + "/5. Please acknowledge it.");
        return toResponse(saved);
    }

    /** The reviewed employee acknowledges the submitted review (one-way). */
    @Transactional
    public PerformanceDto.ReviewResponse acknowledge(Long id) {
        PerformanceReview review = get(id);
        if (review.getStatus() != PerformanceReview.ReviewStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED reviews can be acknowledged");
        }
        review.setStatus(PerformanceReview.ReviewStatus.ACKNOWLEDGED);
        PerformanceReview saved = reviewRepository.save(review);
        notifications.notifyEmployee(saved.getReviewer(), Notification.NotificationType.PERFORMANCE,
                "Review acknowledged",
                saved.getEmployee().getFirstName() + " acknowledged the " + saved.getReviewPeriod()
                        + " review.");
        return toResponse(saved);
    }

    /** DRAFT reviews can be deleted; anything submitted keeps history. */
    @Transactional
    public void delete(Long id) {
        PerformanceReview review = get(id);
        if (review.getStatus() != PerformanceReview.ReviewStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT reviews can be deleted");
        }
        reviewRepository.delete(review);
    }

    // ------------------------------------------------------------ helpers

    private PerformanceReview get(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + id));
    }

    private static void assertDraft(PerformanceReview review) {
        if (review.getStatus() != PerformanceReview.ReviewStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT reviews can be modified");
        }
    }

    private static String fullName(Employee employee) {
        String last = employee.getLastName();
        return last == null || last.isBlank()
                ? employee.getFirstName()
                : employee.getFirstName() + " " + last;
    }

    private static PerformanceDto.ReviewResponse toResponse(PerformanceReview review) {
        Employee employee = review.getEmployee();
        return new PerformanceDto.ReviewResponse(
                review.getId(),
                employee.getId(),
                fullName(employee),
                employee.getEmployeeCode(),
                employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                review.getReviewer().getId(),
                fullName(review.getReviewer()),
                review.getReviewPeriod(),
                review.getRating(),
                review.getStrengths(),
                review.getWeaknesses(),
                review.getGoals(),
                review.getComments(),
                review.getStatus());
    }
}