package com.hrgenius.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hrgenius.auth.User;
import com.hrgenius.auth.UserRepository;
import com.hrgenius.common.Lists;
import com.hrgenius.common.PageResponse;
import com.hrgenius.common.SqlPaging;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * In-app notifications (Phase 12).
 *
 * Reading/marking is scoped to the JWT principal — a user can only ever see
 * and clear their own rows. Writing is done through {@link #notify(Employee,
 * NotificationType, String, String)}-style helpers that resolve the
 * employee → user link by email; events for employees without a matching
 * login are silently dropped (nothing to address).
 *
 * Event emission joins the caller's transaction, so a notification commits
 * exactly when the triggering event commits and never describes rolled-back
 * work. Resolution failures are deliberately lenient (see notifyEmployee).
 */
@Service
public class NotificationService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotificationRepository repository;
    private final UserRepository users;
    private final EmployeeRepository employees;

    public NotificationService(NotificationRepository repository,
                               UserRepository users,
                               EmployeeRepository employees) {
        this.repository = repository;
        this.users = users;
        this.employees = employees;
    }

    // ------------------------------------------------------------- queries

    /**
     * Paginated personal feed (Phase 17): newest first, all filtering and
     * paging happen in SQL. The unread flag binds as a NUMBER (1 or null)
     * against READ_FLAG — deliberately never a Boolean (the Phase 12
     * Oracle-mode hazard) — and the search term is LIKE-escaped before
     * binding. A far-out page yields empty content, never an error.
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto.NotificationResponse> list(String search, Boolean unread,
                                                                   Integer page, Integer size) {
        Long userId = currentUserId();
        String term = Lists.cleanSearch(search);
        // unread=true binds 0 (READ_FLAG = 0), never a Boolean — the Phase 12
        // Oracle-mode hazard. Any other value leaves the flag unfiltered.
        Integer unreadFlag = unread != null && unread ? 0 : null;
        String pattern = term == null ? null : SqlPaging.likeEscape(term);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(repository
                .findPagedFeed(userId, unreadFlag, pattern, pageable)
                .map(NotificationService::toResponse));
    }

    @Transactional(readOnly = true)
    public NotificationDto.UnreadResponse unread() {
        return new NotificationDto.UnreadResponse(repository.countByUser_IdAndReadFalse(currentUserId()));
    }

    // ---------------------------------------------------------------- marks

    @Transactional
    public NotificationDto.NotificationResponse markRead(Long id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
        if (!notification.getUser().getId().equals(currentUserId())) {
            // Not an error: someone else's row simply is not visible to you.
            throw new EntityNotFoundException("Notification not found: " + id);
        }
        notification.setRead(true);
        return toResponse(repository.save(notification));
    }

    /** Marks every unread row of the caller; returns how many changed. */
    @Transactional
    public NotificationDto.UnreadResponse markAllRead() {
        int changed = repository.markAllRead(currentUserId());
        return new NotificationDto.UnreadResponse(changed);
    }

    // -------------------------------------------------------- event helpers

    /**
     * Resolves the employee → user link by email and stores one row in the
     * caller's transaction. No linked login → nothing to address; resolution
     * is by unique email so it cannot fan out to multiple users.
     */
    @Transactional
    public void notifyEmployee(Employee employee, Notification.NotificationType type,
                               String title, String message) {
        if (employee == null || employee.getEmail() == null || employee.getEmail().isBlank()) {
            return;
        }
        users.findByEmailIgnoreCase(employee.getEmail()).ifPresent(user -> {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            repository.save(notification);
        });
    }

    // -------------------------------------------------------------- helpers

    /** JWT principal → USERS.ID. Notifications are per-login, per @PreAuthorize. */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails details) {
            return users.findByEmailIgnoreCase(details.getUsername())
                    .map(User::getId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + details.getUsername()));
        }
        throw new EntityNotFoundException("No authenticated user");
    }

    private static NotificationDto.NotificationResponse toResponse(Notification notification) {
        return new NotificationDto.NotificationResponse(
                notification.getId(),
                notification.getUser().getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType() == null ? null : notification.getType().name(),
                notification.isRead(),
                notification.getCreatedAt() == null ? null : notification.getCreatedAt().format(TS));
    }
}
