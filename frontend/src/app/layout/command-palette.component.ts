import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { EMPTY, Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs';

import { AuthService, UserRole } from '../core/auth.service';
import { EmployeesService } from '../employees/employees.service';
import { NAV_ITEMS } from './navigation';
import { AvatarComponent } from '../shared/avatar.component';

interface PaletteItem {
  kind: 'nav' | 'employee';
  label: string;
  sub?: string;
  icon: string;
  route: string;
}

/**
 * Global command palette (Ctrl/Cmd+K). Filters the navigation items and,
 * for roles the directory endpoint serves (ADMIN/HR/MANAGER), live-searches
 * employees through the existing /employees API (debounced, server-side).
 */
@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [MatIconModule, AvatarComponent],
  template: `
    @if (visible()) {
      <div class="pal-backdrop" (click)="close()" aria-hidden="true"></div>
      <div class="palette" role="dialog" aria-modal="true" aria-label="Command palette">
        <div class="pal-input-row">
          <mat-icon>search</mat-icon>
          <input
            #searchInput
            class="pal-input"
            type="text"
            placeholder="Search pages, employees…"
            [value]="query()"
            (input)="onInput(searchInput.value)"
            (keydown)="onKeydown($event)"
            aria-label="Search"
          />
          <button class="pal-close" type="button" (click)="close()" aria-label="Close search">ESC</button>
        </div>

        <div class="pal-results" role="listbox" aria-label="Results">
          @for (item of filtered(); track item.kind + item.label; let i = $index) {
            <button
              class="pal-item"
              type="button"
              role="option"
              [class.selected]="i === active()"
              [attr.aria-selected]="i === active()"
              (mouseenter)="active.set(i)"
              (click)="go(item)"
            >
              @if (item.kind === 'employee') {
                <app-avatar [name]="item.label" [size]="26" />
              } @else {
                <mat-icon class="pal-icon">{{ item.icon }}</mat-icon>
              }
              <span class="pal-labels">
                <span class="pal-label">{{ item.label }}</span>
                @if (item.sub) {
                  <span class="pal-sub">{{ item.sub }}</span>
                }
              </span>
              <mat-icon class="pal-go">arrow_forward</mat-icon>
            </button>
          } @empty {
            <div class="pal-empty">No matches for “{{ query() }}”</div>
          }
        </div>

        <div class="pal-foot">
          <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
          <span><kbd>Enter</kbd> open</span>
          <span><kbd>Esc</kbd> close</span>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .pal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(9, 11, 16, 0.45);
        backdrop-filter: blur(3px);
        z-index: 90;
        animation: palFade 0.15s ease both;
      }

      .palette {
        position: fixed;
        top: 12vh;
        left: 50%;
        transform: translateX(-50%);
        width: min(620px, calc(100vw - 28px));
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: 14px;
        box-shadow: var(--shadow-lg);
        z-index: 95;
        overflow: hidden;
        animation: palIn 0.16s ease both;
      }

      @keyframes palIn {
        from {
          opacity: 0;
          transform: translateX(-50%) translateY(-8px);
        }
        to {
          opacity: 1;
          transform: translateX(-50%) translateY(0);
        }
      }

      @keyframes palFade {
        from {
          opacity: 0;
        }
        to {
          opacity: 1;
        }
      }

      .pal-input-row {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 12px 14px;
        border-bottom: 1px solid var(--border);
      }

      .pal-input-row .material-icons {
        color: var(--text-3);
        font-size: 20px;
      }

      .pal-input {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        color: var(--text);
        font: 400 15px 'Inter', sans-serif;
      }

      .pal-input::placeholder {
        color: var(--text-3);
      }

      .pal-close {
        border: 1px solid var(--border-strong);
        background: var(--surface-2);
        color: var(--text-3);
        border-radius: 6px;
        font: 600 10px 'Inter', sans-serif;
        letter-spacing: 0.08em;
        padding: 3px 7px;
        cursor: pointer;
      }

      .pal-results {
        max-height: 46vh;
        overflow-y: auto;
        padding: 8px;
      }

      .pal-item {
        display: flex;
        align-items: center;
        gap: 12px;
        width: 100%;
        padding: 9px 12px;
        border: none;
        border-radius: 9px;
        background: transparent;
        cursor: pointer;
        text-align: left;
        transition: background 0.12s ease;
      }

      .pal-item.selected {
        background: var(--primary-soft);
      }

      .pal-item .pal-icon {
        color: var(--text-2);
        font-size: 20px;
        width: 20px;
      }

      .pal-labels {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        line-height: 1.3;
      }

      .pal-label {
        font-size: 13.5px;
        font-weight: 600;
        color: var(--text);
      }

      .pal-sub {
        font-size: 12px;
        color: var(--text-3);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .pal-go {
        color: var(--text-3);
        font-size: 16px;
        opacity: 0;
        transition: opacity 0.12s ease;
      }

      .pal-item.selected .pal-go {
        opacity: 1;
        color: var(--primary);
      }

      .pal-empty {
        padding: 28px 16px;
        text-align: center;
        color: var(--text-3);
        font-size: 13.5px;
      }

      .pal-foot {
        display: flex;
        gap: 16px;
        padding: 9px 14px;
        border-top: 1px solid var(--border);
        background: var(--surface-2);
        color: var(--text-3);
        font-size: 11.5px;
      }

      .pal-foot kbd {
        font: 600 10px 'Inter', sans-serif;
        border: 1px solid var(--border-strong);
        border-radius: 4px;
        padding: 1px 5px;
        margin-right: 4px;
        background: var(--surface);
      }
    `,
  ],
})
export class CommandPaletteComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly employeesApi = inject(EmployeesService);

  /** Whether the overlay is rendered. */
  readonly visible = signal(false);
  readonly query = signal('');
  readonly active = signal(0);
  readonly employeeItems = signal<PaletteItem[]>([]);

  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const role = this.auth.user()?.role;
    const navItems: PaletteItem[] = NAV_ITEMS.filter(
      (item) =>
        (!role || item.roles.includes(role)) &&
        (!q || `${item.label} ${item.description}`.toLowerCase().includes(q)),
    ).map((item) => ({
      kind: 'nav' as const,
      label: item.label,
      sub: item.description,
      icon: item.icon,
      route: item.route,
    }));
    if (!q) {
      return navItems;
    }
    return [...navItems, ...this.employeeItems()];
  });

  private readonly searchTerms = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.searchTerms
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) => {
          this.employeeItems.set([]);
          // Directory search is only for roles the /employees API serves;
          // EMPLOYEE gets nav results only (avoids a guaranteed 403).
          if (this.auth.user()?.role === 'EMPLOYEE') {
            return EMPTY;
          }
          return this.employeesApi
            .list({ search: term || undefined, page: 0, size: 6, sortBy: 'employeeCode', sortDir: 'asc' })
            .pipe(takeUntil(this.destroy$));
        }),
      )
      .subscribe({
        next: (res) => {
          const page = res.data;
          this.employeeItems.set(
            (page.content ?? []).map((e) => ({
              kind: 'employee' as const,
              label: `${e.firstName} ${e.lastName}`,
              sub: `${e.employeeCode} · ${e.departmentName ?? '—'}`,
              icon: 'person',
              route: '/app/employees',
            })),
          );
        },
        error: () => this.employeeItems.set([]),
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onInput(value: string): void {
    this.query.set(value);
    this.active.set(0);
    this.searchTerms.next(value);
  }

  onKeydown(event: KeyboardEvent): void {
    const items = this.filtered();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.active.update((i) => Math.min(items.length - 1, i + 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.update((i) => Math.max(0, i - 1));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const item = items[this.active()];
      if (item) {
        this.go(item);
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
    }
  }

  open(): void {
    this.query.set('');
    this.employeeItems.set([]);
    this.active.set(0);
    this.visible.set(true);
    setTimeout(() => this.searchInput()?.nativeElement.focus());
  }

  close(): void {
    this.visible.set(false);
  }

  go(item: PaletteItem): void {
    this.close();
    this.router.navigateByUrl(item.route);
  }
}
