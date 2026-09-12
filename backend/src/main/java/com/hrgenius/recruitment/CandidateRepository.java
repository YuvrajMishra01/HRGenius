package com.hrgenius.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    long countByStatus(CandidateStatus status);
}
