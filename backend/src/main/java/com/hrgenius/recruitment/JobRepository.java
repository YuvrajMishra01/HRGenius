package com.hrgenius.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

/** Job queries: dashboard open-positions KPI. */
public interface JobRepository extends JpaRepository<Job, Long> {
    long countByStatus(JobStatus status);
}
