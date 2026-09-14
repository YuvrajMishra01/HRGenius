package com.hrgenius.leave;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Leave request queries: overlap guard, queue, balances, projections. */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    long countByStatus(LeaveRequest.LeaveStatus status);

    long countByStatusIn(java.util.Collection<LeaveRequest.LeaveStatus> statuses);

    /** Delete guard: requests referencing a leave type. */
    long countByLeaveTypeId(Long leaveTypeId);

    @Query("select l.status, count(l) from LeaveRequest l group by l.status")
    List<Object[]> countByStatusRaw();

    /** Pending requests, oldest first (dashboard approval queue). */
    List<LeaveRequest> findByStatusOrderByStartDateAsc(LeaveRequest.LeaveStatus status, org.springframework.data.domain.Pageable pageable);

    /** Overlap guard: any PENDING or APPROVED request intersecting the range. */
    @Query("""
            select count(l) from LeaveRequest l
            where l.employee.id = :employeeId
              and l.status in :statuses
              and l.startDate <= :end
              and l.endDate >= :start
            """)
    long countOverlapping(@Param("employeeId") Long employeeId,
                          @Param("statuses") java.util.Collection<LeaveRequest.LeaveStatus> statuses,
                          @Param("start") LocalDate start,
                          @Param("end") LocalDate end);

    /** Rich list with employee/leave-type resolved. */
    @Query("""
            select l from LeaveRequest l
            join fetch l.employee e
            left join fetch e.department d
            join fetch l.leaveType t
            order by l.id desc
            """)
    List<LeaveRequest> findAllWithDetails();

    /**
     * DB-side paged rich list (Phase 17): status + search filters and the
     * page window are computed in SQL. The count query deliberately repeats
     * the filters without the fetch joins — Spring cannot derive a correct
     * count through `join fetch`, and an extra join would skew the total.
     * Typed binds only (enum + escaped VARCHAR pattern).
     */
    @Query(value = """
            select l from LeaveRequest l
            join fetch l.employee e
            left join fetch e.department d
            join fetch l.leaveType t
            where (:status is null or l.status = :status)
              and (:pattern is null
                   or lower(concat(concat(e.firstName, ' '), e.lastName)) like :pattern escape '\\'
                   or lower(e.employeeCode) like :pattern escape '\\'
                   or lower(t.name) like :pattern escape '\\'
                   or lower(coalesce(l.reason, '')) like :pattern escape '\\')
            order by l.id desc
            """,
            countQuery = """
            select count(l) from LeaveRequest l
            join l.employee e
            join l.leaveType t
            where (:status is null or l.status = :status)
              and (:pattern is null
                   or lower(concat(concat(e.firstName, ' '), e.lastName)) like :pattern escape '\\'
                   or lower(e.employeeCode) like :pattern escape '\\'
                   or lower(t.name) like :pattern escape '\\'
                   or lower(coalesce(l.reason, '')) like :pattern escape '\\')
            """)
    Page<LeaveRequest> findPagedWithDetails(@Param("status") LeaveRequest.LeaveStatus status,
                                            @Param("pattern") String pattern,
                                            Pageable pageable);

    /** Rich list filtered by status (approval queue). */
    @Query("""
            select l from LeaveRequest l
            join fetch l.employee e
            left join fetch e.department d
            join fetch l.leaveType t
            where l.status = :status
            order by l.id desc
            """)
    List<LeaveRequest> findByStatusWithDetails(@Param("status") LeaveRequest.LeaveStatus status);

    /** Single request with relations resolved (decide path). */
    @Query("""
            select l from LeaveRequest l
            join fetch l.employee e
            left join fetch e.department d
            join fetch l.leaveType t
            where l.id = :id
            """)
    java.util.Optional<LeaveRequest> findByIdWithDetails(@Param("id") Long id);

    /** APPROVED request ranges in one year, for Java-side day summation. */
    @Query("""
            select l.leaveType.id, l.startDate, l.endDate
            from LeaveRequest l
            where l.employee.id = :employeeId
              and l.status in :statuses
              and l.startDate >= :yearStart and l.startDate <= :yearEnd
            """)
    List<Object[]> approvedRanges(@Param("employeeId") Long employeeId,
                                  @Param("statuses") java.util.Collection<LeaveRequest.LeaveStatus> statuses,
                                  @Param("yearStart") LocalDate yearStart,
                                  @Param("yearEnd") LocalDate yearEnd);

    /** Requests whose start date falls inside one calendar year. */
    @Query("""
            select count(l) from LeaveRequest l
            where l.status = :status
              and l.startDate >= :yearStart and l.startDate <= :yearEnd
            """)
    long countByStatusBetweenStartDates(@Param("status") LeaveRequest.LeaveStatus status,
                                        @Param("yearStart") LocalDate yearStart,
                                        @Param("yearEnd") LocalDate yearEnd);

    /** Analytics: request count per leave type within one year, busiest first. */
    @Query("""
            select t.name, count(l)
            from LeaveRequest l join l.leaveType t
            where l.startDate >= :yearStart and l.startDate <= :yearEnd
            group by t.name
            order by count(l) desc
            """)
    List<Object[]> countByTypeWithinYear(@Param("yearStart") LocalDate yearStart,
                                         @Param("yearEnd") LocalDate yearEnd);
}
