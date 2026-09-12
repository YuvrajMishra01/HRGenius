package com.hrgenius.attendance;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** Attendance status counts for one day: [status, count]. */
    @Query("""
            select a.status, count(a)
            from Attendance a
            where a.attendanceDate = :day
            group by a.status
            """)
    List<Object[]> countByDayRaw(@Param("day") LocalDate day);
}
