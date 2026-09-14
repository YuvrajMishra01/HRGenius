import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';

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

/**
 * Feeds the toolbar bell and the /notifications page (Phase 12). Since
 * Phase 17 the feed is server-paginated with a DB-side unread filter, so
 * it scales to any history length.
 */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTooltipModule,
    MatButtonToggleModule,
    MatPaginatorModule,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private readonly api = inject(NotificationService);

  readonly PAGE_SIZE = 10;

  readonly notifications = signal<Notification[]>([]);
  /** Shared with the toolbar bell so both stay in sync instantly. */
  readonly unread = this.api.badge;
  readonly loading = signal(true);
  readonly serverError = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  readonly page = signal(0);
  readonly pageSize = signal(this.PAGE_SIZE);
  readonly total = signal(0);
  readonly unreadOnly = signal(false);

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
    this.loading.set(true);
    this.api.listPage(this.page(), this.pageSize(), this.unreadOnly()).subscribe({
      next: (res) => {
        this.notifications.set(res.data.content);
        this.total.set(res.data.totalElements);
        this.loading.set(false);
        this.refreshBadge();
      },
      error: () => {
        this.serverError.set('Could not load notifications');
        this.loading.set(false);
      },
    });
  }

  onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.reload();
  }

  onFilterChange(unreadOnly: boolean): void {
    this.unreadOnly.set(unreadOnly);
    this.page.set(0);
    this.reload();
  }

  /** Polls the badge only — never disturbs the open list or error state. */
  refresh(): void {
    this.api.unread().subscribe({
      next: (res) => this.unread.set(res.data.unread),
      error: () => undefined,
    });
  }

  refreshBadge(): void {
    // The badge is the DB-side unread total, not the page's rows — a single
    // page can never speak for the whole feed.
    this.refresh();
  }

  markRead(notification: Notification): void {
    if (notification.read || this.busyId() !== null) return;
    this.busyId.set(notification.id);
    this.api.markRead(notification.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.reload();
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
        this.unread.set(0);
        this.reload();
      },
      error: () => this.serverError.set('Could not mark all as read'),
    });
  }

  style(type: string | null): { icon: string; css: string } {
    return typeStyle(type);
  }
}
