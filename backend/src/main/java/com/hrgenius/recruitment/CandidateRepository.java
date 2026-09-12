package com.hrgenius.recruitment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Candidate queries: uniqueness guard, dashboard KPI, rich list. */
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    long countByStatus(CandidateStatus status);

    /** Candidate email uniqueness is enforced in the service (not a DB constraint). */
    boolean existsByEmailIgnoreCase(String email);

    Optional<Candidate> findByEmailIgnoreCase(String email);

    /** Rich candidate list with application counts, newest first. */
    @Query("""
            select c.id, c.name, c.email, c.phone, c.resumePath, c.skills,
                   c.experienceYears, c.status, c.createdAt, count(a)
            from Candidate c
            left join JobApplication a on a.candidate = c
            group by c.id, c.name, c.email, c.phone, c.resumePath, c.skills,
                     c.experienceYears, c.status, c.createdAt
            order by c.createdAt desc
            """)
    List<Object[]> findAllProjected();
}
