package com.hrgenius.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hrgenius.auth.User;

import lombok.Getter;
import lombok.Setter;

/**
 * In-app notification (Phase 12). Rows are addressed to login USERS, never
 * to employees — the User↔Employee relationship resolves by matching email
 * (EMPLOYEES.EMAIL is unique and holds the login address in seed data).
 *
 * READ_FLAG is NUMBER(1) on the database; Hibernate maps the boolean the
 * same way the seed's 0/1 literals are stored.
 */
@Entity
@Table(name = "NOTIFICATIONS")
@Getter
@Setter
public class Notification {

    public enum NotificationType {
        SYSTEM, LEAVE, ONBOARDING, PAYROLL, PERFORMANCE, RECRUITMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "TITLE", nullable = false, length = 150)
    private String title;

    @Column(name = "MESSAGE", length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 30)
    private NotificationType type;

    @Column(name = "READ_FLAG", nullable = false)
    private boolean read;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
