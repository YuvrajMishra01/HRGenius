package com.hrgenius.notification;

import java.util.List;

import com.hrgenius.common.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification API (Phase 12). Every endpoint is scoped to the JWT
 * principal, so all authenticated roles — including EMPLOYEE — may read and
 * clear their own rows; there is no cross-user access to expose.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto.NotificationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(service.list()));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<NotificationDto.UnreadResponse>> unread() {
        return ResponseEntity.ok(ApiResponse.of(service.unread()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto.NotificationResponse>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("Notification marked read", service.markRead(id)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<NotificationDto.UnreadResponse>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.of("All notifications marked read", service.markAllRead()));
    }
}
