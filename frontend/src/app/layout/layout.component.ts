import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatBadgeModule } from '@angular/material/badge';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { NotificationService } from '../notifications/notification.service';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Phases not yet implemented are hidden from the sidebar. */
  phase: number;
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatBadgeModule,
    MatTooltipModule,
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly notificationApi = inject(NotificationService);

  /** Toolbar badge; the notifications page writes the same shared signal. */
  readonly unreadCount = computed(() => this.notificationApi.badge());
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.pollBadge();
    this.pollTimer = setInterval(() => this.pollBadge(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
  }

  private pollBadge(): void {
    this.notificationApi.unread().subscribe({
      next: (res) => this.notificationApi.badge.set(res.data.unread),
      error: () => undefined,
    });
  }

  /** Phase gates which nav items are visible; bump as modules land. */
  readonly currentPhase = 13;

  readonly navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard', phase: 0 },
    { label: 'Employees', icon: 'people', route: '/employees', phase: 3 },
    { label: 'Departments', icon: 'account_tree', route: '/departments', phase: 4 },
    { label: 'Recruitment', icon: 'work', route: '/recruitment', phase: 5 },
    { label: 'Onboarding', icon: 'badge', route: '/onboarding', phase: 6 },
    { label: 'Attendance', icon: 'fact_check', route: '/attendance', phase: 7 },
    { label: 'Leave', icon: 'event_busy', route: '/leave', phase: 8 },
    { label: 'Payroll', icon: 'payments', route: '/payroll', phase: 9 },
    { label: 'Performance', icon: 'trending_up', route: '/performance', phase: 10 },
    { label: 'Documents', icon: 'folder_shared', route: '/documents', phase: 11 },
    { label: 'Notifications', icon: 'notifications', route: '/notifications', phase: 12 },
    { label: 'Analytics', icon: 'analytics', route: '/analytics', phase: 13 },
  ];

  get visibleNavItems(): NavItem[] {
    return this.navItems.filter((item) => item.phase <= this.currentPhase);
  }
}
