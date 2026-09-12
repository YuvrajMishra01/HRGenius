package com.hrgenius.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Attendance queries: today strip, month grid, check-in guards. */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** Attendance status counts for one day: [status, count]. */
    @Query("""
            select a.status, count(a)
            from Attendance a
            where a.attendanceDate = :day
            group by a.status
            """)
    List<Object[]> countByDayRaw(@Param("day") LocalDate day);

    /** Full day strip (records joined with employee + department). */
    @Query("""
            select a from Attendance a
            join fetch a.employee e
            left join fetch e.department d
            where a.attendanceDate = :day
            order by e.firstName, e.lastName
            """)
    List<Attendance> findDayWithDetails(@Param("day") LocalDate day);

    /** A month's records for the grid (records joined with employee + department). */
    @Query("""
            select a from Attendance a
            join fetch a.employee e
            left join fetch e.department d
            where a.attendanceDate between :start and :end
            """)
    List<Attendance> findMonthWithDetails(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** One employee's record for one day (uniqueness guard UK_ATT_EMP_DATE). */
    Optional<Attendance> findByEmployee_IdAndAttendanceDate(Long employeeId, LocalDate date);

    /** Check-in guard: a checked-out day cannot be checked in again. */
    Optional<Attendance> findFirstByEmployee_IdAndAttendanceDateOrderByIdDesc(Long employeeId, LocalDate date);
}
