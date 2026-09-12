package com.hrgenius.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
    long countByStatus(JobStatus status);
}
