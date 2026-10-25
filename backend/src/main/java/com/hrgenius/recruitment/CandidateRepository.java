package com.hrgenius.recruitment;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Candidate queries: uniqueness guard, dashboard KPI, paged rich list. */
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    long countByStatus(CandidateStatus status);

    /** Candidate email uniqueness is enforced in the service (not a DB constraint). */
    boolean existsByEmailIgnoreCase(String email);

    Optional<Candidate> findByEmailIgnoreCase(String email);

    /**
     * DB-side paged projection (Phase 17): filters bind as typed values —
     * an enum and a VARCHAR LIKE pattern, never null-Boolean flags — so the
     * whole list, count included, is computed in the database.
     */
    @Query(value = """
            select c.id, c.name, c.email, c.phone, c.resumePath, c.skills,
                   c.experienceYears, c.status, c.createdAt, count(a)
            from Candidate c
            left join JobApplication a on a.candidate = c
            where (:status is null or c.status = :status)
              and (:pattern is null
                   or lower(c.name) like :pattern escape '\\'
                   or lower(c.email) like :pattern escape '\\'
                   or lower(coalesce(c.skills, '')) like :pattern escape '\\')
            group by c.id, c.name, c.email, c.phone, c.resumePath, c.skills,
                     c.experienceYears, c.status, c.createdAt
            order by c.createdAt desc, c.id desc
            """,
            countQuery = """
            select count(distinct c.id) from Candidate c
            left join JobApplication a on a.candidate = c
            where (:status is null or c.status = :status)
              and (:pattern is null
                   or lower(c.name) like :pattern escape '\\'
                   or lower(c.email) like :pattern escape '\\'
                   or lower(coalesce(c.skills, '')) like :pattern escape '\\')
            """)
    Page<Object[]> findPagedProjected(@Param("status") CandidateStatus status,
                                      @Param("pattern") String pattern,
                                      Pageable pageable);
}
