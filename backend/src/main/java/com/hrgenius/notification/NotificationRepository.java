package com.hrgenius.notification;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Personal feed, newest first — now real DB paging (Phase 17): DB-side
     * filters, LIMIT/OFFSET, and a separate count query, so scale is a DB
     * concern instead of a JVM-heap one.
     *
     * Binding rules (the Phase 12 lesson, applied deliberately):
     *  - `userId` and `unreadFlag` bind as NUMBERS. READ_FLAG is NUMBER(1) in
     *    Oracle; a JPQL Boolean would bind as BOOLEAN and be rejected by
     *    Oracle-mode H2. We never bind a Boolean here.
     *  - `unreadFlag` is null (no filter) or 1. No null-flag Boolean patterns.
     *  - `pattern` binds as a VARCHAR LIKE pattern; ESCAPE '\' makes the
     *    user-supplied wildcards (% _) literal.
     */
    @Query(value = """
            SELECT * FROM notifications n
            WHERE n.user_id = :userId
              AND (:unreadFlag IS NULL OR n.read_flag = :unreadFlag)
              AND (:pattern IS NULL
                   OR LOWER(n.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(n.message) LIKE :pattern ESCAPE '\\')
            ORDER BY n.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM notifications n
            WHERE n.user_id = :userId
              AND (:unreadFlag IS NULL OR n.read_flag = :unreadFlag)
              AND (:pattern IS NULL
                   OR LOWER(n.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(n.message) LIKE :pattern ESCAPE '\\')
            """,
            nativeQuery = true)
    org.springframework.data.domain.Page<Notification> findPagedFeed(
            @Param("userId") Long userId,
            @Param("unreadFlag") Integer unreadFlag,
            @Param("pattern") String pattern,
            Pageable pageable);

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
