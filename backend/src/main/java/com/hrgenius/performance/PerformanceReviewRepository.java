package com.hrgenius.performance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Performance review queries: projections, duplicate guard, aggregates. */
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    /** All reviews with employee + reviewer resolved, newest first. */
    @Query("""
            select r from PerformanceReview r
            join fetch r.employee e
            left join fetch e.department d
            join fetch r.reviewer rev
            order by r.id desc
            """)
    List<PerformanceReview> findAllWithDetails();

    /** Reviews for one employee (history view), newest first. */
    @Query("""
            select r from PerformanceReview r
            join fetch r.employee e
            left join fetch e.department d
            join fetch r.reviewer rev
            where r.employee.id = :employeeId
            order by r.id desc
            """)
    List<PerformanceReview> findByEmployeeWithDetails(@Param("employeeId") Long employeeId);

    /** One review with relations resolved. */
    @Query("""
            select r from PerformanceReview r
            join fetch r.employee e
            left join fetch e.department d
            join fetch r.reviewer rev
            where r.id = :id
            """)
    Optional<PerformanceReview> findByIdWithDetails(@Param("id") Long id);

    /** Duplicate guard: one review per employee per period. */
    boolean existsByEmployee_IdAndReviewPeriodIgnoreCase(Long employeeId, String reviewPeriod);

    long countByStatus(PerformanceReview.ReviewStatus status);

    /** Average rating across official (submitted+) reviews (1 dp). */
    @Query("""
            select avg(r.rating) from PerformanceReview r
            where r.rating is not null and r.status <> com.hrgenius.performance.PerformanceReview$ReviewStatus.DRAFT
            """)
    Double averageRating();

    /** Rating distribution: [rating, count] for official (submitted+) reviews. */
    @Query("""
            select r.rating, count(r)
            from PerformanceReview r
            where r.rating is not null and r.status <> com.hrgenius.performance.PerformanceReview$ReviewStatus.DRAFT
            group by r.rating
            order by r.rating
            """)
    List<Object[]> ratingDistribution();
}