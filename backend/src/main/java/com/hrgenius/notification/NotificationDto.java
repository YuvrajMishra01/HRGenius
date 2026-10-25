package com.hrgenius.notification;

/** API payloads for the notification module (Phase 12). */
public final class NotificationDto {

    private NotificationDto() {
    }

    /** Row shown in the bell dropdown / notifications page. */
    public record NotificationResponse(
            Long id,
            Long userId,
            String title,
            String message,
            String type,
            boolean read,
            String createdAt) {
    }

    /** Badge + page KPI strip. */
    public record UnreadResponse(long unread) {
    }
}
