package com.hrgenius.leave;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    long countByStatus(LeaveRequest.LeaveStatus status);

    @Query("select l.status, count(l) from LeaveRequest l group by l.status")
    List<Object[]> countByStatusRaw();

    /** Pending requests, oldest first (manager/HR approval queue). */
    List<LeaveRequest> findByStatusOrderByStartDateAsc(LeaveRequest.LeaveStatus status, Pageable pageable);
}
