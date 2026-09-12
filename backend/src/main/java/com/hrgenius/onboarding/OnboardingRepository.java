package com.hrgenius.onboarding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Onboarding queries: dashboard queue, one-record-per-employee guard. */
public interface OnboardingRepository extends JpaRepository<Onboarding, Long> {

    boolean existsByEmployee_Id(Long employeeId);

    boolean existsByApplication_Id(Long applicationId);

    Optional<Onboarding> findByEmployee_Id(Long employeeId);

    /** All onboardings, newest employee first (list page). */
    @Query("""
            select o from Onboarding o
            join fetch o.employee e
            left join fetch o.application a
            left join fetch a.candidate c
            left join fetch a.job j
            order by o.id desc
            """)
    List<Onboarding> findAllWithDetails();
}
