package com.hrgenius.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUser_IdAndReadFalse(Long userId);

    /**
     * READ_FLAG is NUMBER(1), so the bulk flip is native SQL with 1/0
     * literals — a JPQL boolean literal binds as BOOLEAN and is rejected by
     * Oracle (and H2 in Oracle mode) as "not comparable" with NUMERIC(1).
     */
    @Modifying
    @Query(value = "UPDATE notifications SET read_flag = 1 WHERE user_id = :userId AND read_flag = 0",
            nativeQuery = true)
    int markAllRead(@Param("userId") Long userId);
}
