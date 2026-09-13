import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Notification, NotificationService } from './notification.service';

/** Notification type → icon/colour used by both the bell and the page. */
export function typeStyle(type: string | null): { icon: string; css: string } {
  switch (type) {
    case 'LEAVE':
      return { icon: 'event_busy', css: 'leave' };
    case 'ONBOARDING':
      return { icon: 'badge', css: 'onboarding' };
    case 'PAYROLL':
      return { icon: 'payments', css: 'payroll' };
    case 'PERFORMANCE':
      return { icon: 'trending_up', css: 'performance' };
    case 'RECRUITMENT':
      return { icon: 'work', css: 'recruitment' };
    default:
      return { icon: 'notifications', css: 'system' };
  }
}

/** Feeds the toolbar bell and the /notifications page (Phase 12). */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatTooltipModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private readonly api = inject(NotificationService);

  readonly notifications = signal<Notification[]>([]);
  /** Shared with the toolbar bell so both stay in sync instantly. */
  readonly unread = this.api.badge;
  readonly loading = signal(true);
  readonly serverError = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.reload();
    this.pollTimer = setInterval(() => this.refresh(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
  }

  reload(): void {
    this.serverError.set(null);
    this.api.list().subscribe({
      next: (res) => {
        this.notifications.set(res.data);
        this.loading.set(false);
        this.refreshBadge();
      },
      error: () => {
        this.serverError.set('Could not load notifications');
        this.loading.set(false);
      },
    });
  }

  /** Polls the badge only — never disturbs the open list or error state. */
  refresh(): void {
    this.api.unread().subscribe({
      next: (res) => this.unread.set(res.data.unread),
      error: () => undefined,
    });
  }

  refreshBadge(): void {
    const unreadCount = this.notifications().filter((n) => !n.read).length;
    this.unread.set(unreadCount);
  }

  markRead(notification: Notification): void {
    if (notification.read || this.busyId() !== null) return;
    this.busyId.set(notification.id);
    this.api.markRead(notification.id).subscribe({
      next: (res) => {
        this.notifications.update((rows) =>
          rows.map((n) => (n.id === notification.id ? (res.data ?? { ...n, read: true }) : n)),
        );
        this.refreshBadge();
        this.busyId.set(null);
      },
      error: () => {
        this.serverError.set('Could not mark as read');
        this.busyId.set(null);
      },
    });
  }

  markAllRead(): void {
    this.api.markAllRead().subscribe({
      next: () => {
        this.notifications.update((rows) => rows.map((n) => ({ ...n, read: true })));
        this.unread.set(0);
      },
      error: () => this.serverError.set('Could not mark all as read'),
    });
  }

  style(type: string | null): { icon: string; css: string } {
    return typeStyle(type);
  }
}
