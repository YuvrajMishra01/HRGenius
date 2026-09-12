package com.hrgenius.recruitment;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    /** Interviews from the given instant, status SCHEDULED, soonest first. */
    List<Interview> findByStatusAndInterviewDateAfterOrderByInterviewDateAsc(
            Interview.InterviewStatus status, OffsetDateTime after);
}
