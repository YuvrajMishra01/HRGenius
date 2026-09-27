import {
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';
import { NotificationService } from '../notifications/notification.service';
import { NAV_ITEMS, NavItem } from './navigation';
import { CommandPaletteComponent } from './command-palette.component';
import { AvatarComponent } from '../shared/avatar.component';

/**
 * HRGenius application shell: role-aware sidebar, sticky header with
 * breadcrumb, theme toggle, notification bell and the Ctrl+K command
 * palette. On mobile the sidebar becomes an off-canvas drawer with a
 * backdrop; navigation closes it.
 */
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    CommandPaletteComponent,
    AvatarComponent,
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly notificationApi = inject(NotificationService);

  readonly NAV_GROUPS: Array<NavItem['group']> = [
    'Overview',
    'People',
    'Recruitment',
    'Workforce',
    'System',
  ];

  /** Items visible for the signed-in user's role (UX layer only). */
  readonly navForRole = computed(() => {
    const role = this.auth.user()?.role;
    return NAV_ITEMS.filter((item) => !role || item.roles.includes(role));
  });

  itemsIn(group: NavItem['group']): NavItem[] {
    return this.navForRole().filter((item) => item.group === group);
  }

  /** Toolbar badge; the notifications page writes the same shared signal. */
  readonly unreadCount = computed(() => this.notificationApi.badge());

  /** Breadcrumb label for the current module, e.g. ['Employees']. */
  readonly crumbs = signal<string[]>([]);

  readonly sidebarOpen = signal(false);

  readonly menuOpen = signal(false);

  readonly palette = viewChild(CommandPaletteComponent);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.pollBadge();
    this.pollTimer = setInterval(() => this.pollBadge(), 30000);
    this.updateCrumbs();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.updateCrumbs();
      this.sidebarOpen.set(false); // mobile: close after selecting
    });
    window.addEventListener('keydown', this.onKeydown);
    // Re-apply the theme class (index.html normally does this pre-boot).
    if (this.theme.theme() === 'dark') {
      this.theme.set('dark');
    }
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
    window.removeEventListener('keydown', this.onKeydown);
  }

  onKeydown = (event: KeyboardEvent): void => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.openPalette();
    }
  };

  openPalette(): void {
    this.palette()?.open();
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  private updateCrumbs(): void {
    const item = NAV_ITEMS.find((i) => this.router.url.startsWith(i.route));
    this.crumbs.set(item ? [item.label] : []);
  }

  private pollBadge(): void {
    this.notificationApi.unread().subscribe({
      next: (res) => this.notificationApi.badge.set(res.data.unread),
      error: () => undefined,
    });
  }
}
