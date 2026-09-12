package com.hrgenius.recruitment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Application aggregations for the hiring pipeline chart: grouped in the
 * database by status so the SPA receives ready-made chart series.
 */
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("select a.status, count(a) from JobApplication a group by a.status")
    List<Object[]> countByStatusRaw();
}
