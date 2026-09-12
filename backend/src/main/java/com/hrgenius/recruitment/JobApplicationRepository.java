package com.hrgenius.recruitment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Application queries: duplicate check, pipeline chart, guard counts. */
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("select a.status, count(a) from JobApplication a group by a.status")
    List<Object[]> countByStatusRaw();

    /** Duplicate applications are rejected per UK_APP_CAND_JOB. */
    boolean existsByCandidate_IdAndJob_Id(Long candidateId, Long jobId);

    /** Delete guard for jobs + per-job application counts. */
    long countByJob_Id(Long jobId);

    /** Delete guard for candidates. */
    long countByCandidate_Id(Long candidateId);

    @Query("select a.job.id, count(a) from JobApplication a group by a.job.id")
    List<Object[]> countByJobGrouped();

    /** Rich application list with candidate + job + department resolved. */
    @Query("""
            select a.id, c.id, c.name, c.email,
                   j.id, j.title, d.name,
                   a.applicationDate, a.status, a.remarks
            from JobApplication a
            join a.candidate c
            join a.job j
            left join j.department d
            """)
    List<Object[]> findAllProjected();
}
