package com.hrgenius.recruitment;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Interview queries: dashboard queue, application guard, rich list. */
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    /** Interviews from the given instant, status SCHEDULED, soonest first. */
    List<Interview> findByStatusAndInterviewDateAfterOrderByInterviewDateAsc(
            Interview.InterviewStatus status, OffsetDateTime after);

    /** Interview guard: only one live (SCHEDULED) interview per application. */
    boolean existsByApplication_IdAndStatus(Long applicationId, Interview.InterviewStatus status);

    /** Rich interview list with application/candidate/job/interviewer resolved. */
    @Query("""
            select i.id, i.application.id, c.name, j.title,
                   e.id, concat(concat(e.firstName, ' '), e.lastName),
                   i.interviewDate, i.mode, i.status, i.feedback, i.result
            from Interview i
            join i.application a
            join a.candidate c
            join a.job j
            left join i.interviewer e
            """)
    List<Object[]> findAllProjected();
}
