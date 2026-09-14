package com.hrgenius.recruitment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Job queries: dashboard open-positions KPI and the paged list (Phase 17).
 * The paged projection carries the per-job application count, so one query
 * (plus its count twin) computes the whole list in the database.
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    long countByStatus(JobStatus status);

    /** DB-side paged projection with the per-job application count. */
    @Query(value = """
            select j.id, j.title, j.description,
                   d.id, d.name,
                   j.location, j.employmentType, j.salaryRange,
                   j.status, j.postedDate, j.closingDate,
                   (select count(a) from JobApplication a where a.job = j)
            from Job j
            left join j.department d
            where (:status is null or j.status = :status)
              and (:pattern is null
                   or lower(j.title) like :pattern escape '\\'
                   or lower(j.location) like :pattern escape '\\'
                   or lower(d.name) like :pattern escape '\\')
            order by lower(j.title) asc
            """,
            countQuery = """
            select count(j) from Job j
            left join j.department d
            where (:status is null or j.status = :status)
              and (:pattern is null
                   or lower(j.title) like :pattern escape '\\'
                   or lower(j.location) like :pattern escape '\\'
                   or lower(d.name) like :pattern escape '\\')
            """)
    Page<Object[]> findPagedProjected(@Param("status") JobStatus status,
                                      @Param("pattern") String pattern,
                                      Pageable pageable);
}
