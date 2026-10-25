package com.hrgenius.recruitment;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Interview queries: dashboard queue, application guard, rich list. */
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    /** Interviews from the given instant, status SCHEDULED, soonest first. */
    List<Interview> findByStatusAndInterviewDateAfterOrderByInterviewDateAsc(
            Interview.InterviewStatus status, OffsetDateTime after);

    /** Interview guard: only one live (SCHEDULED) interview per application. */
    boolean existsByApplication_IdAndStatus(Long applicationId, Interview.InterviewStatus status);

    /** Analytics: [status, count] and [result, count] groupings. */
    @Query("select i.status, count(i) from Interview i group by i.status")
    List<Object[]> countByStatusRaw();

    @Query("select i.result, count(i) from Interview i where i.result is not null group by i.result")
    List<Object[]> countByResultRaw();

    /**
     * Rich interview list with application/candidate/job/interviewer
     * resolved — DB-side paged (Phase 17) with typed binds.
     */
    @Query(value = """
            select i.id, i.application.id, c.name, j.title,
                   e.id, concat(concat(e.firstName, ' '), e.lastName),
                   i.interviewDate, i.mode, i.status, i.feedback, i.result
            from Interview i
            join i.application a
            join a.candidate c
            join a.job j
            left join i.interviewer e
            where (:status is null or i.status = :status)
              and (:pattern is null
                   or lower(c.name) like :pattern escape '\\'
                   or lower(j.title) like :pattern escape '\\'
                   or lower(concat(concat(coalesce(e.firstName, ''), ' '), coalesce(e.lastName, '')))
                       like :pattern escape '\\')
            order by i.id asc
            """,
            countQuery = """
            select count(i) from Interview i
            join i.application a
            join a.candidate c
            join a.job j
            left join i.interviewer e
            where (:status is null or i.status = :status)
              and (:pattern is null
                   or lower(c.name) like :pattern escape '\\'
                   or lower(j.title) like :pattern escape '\\'
                   or lower(concat(concat(coalesce(e.firstName, ''), ' '), coalesce(e.lastName, '')))
                       like :pattern escape '\\')
            """)
    Page<Object[]> findPagedProjected(@Param("status") Interview.InterviewStatus status,
                                      @Param("pattern") String pattern,
                                      Pageable pageable);
}
