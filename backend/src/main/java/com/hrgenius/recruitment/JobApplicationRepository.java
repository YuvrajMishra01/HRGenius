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

    /**
     * Analytics funnel per job: [jobId, title, jobStatus, total, active,
     * selected, rejected]. "Active" = not yet terminal (SELECTED/REJECTED).
     * All counts computed DB-side with CASE sums.
     */
    @Query("""
            select a.job.id, a.job.title, a.job.status,
                   count(a),
                   sum(case when a.status not in
                        (com.hrgenius.recruitment.ApplicationStatus.SELECTED,
                         com.hrgenius.recruitment.ApplicationStatus.REJECTED)
                       then 1 else 0 end),
                   sum(case when a.status = com.hrgenius.recruitment.ApplicationStatus.SELECTED
                       then 1 else 0 end),
                   sum(case when a.status = com.hrgenius.recruitment.ApplicationStatus.REJECTED
                       then 1 else 0 end)
            from JobApplication a
            group by a.job.id, a.job.title, a.job.status
            """)
    List<Object[]> funnelByJobRaw();

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
