import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuditEntry, AuditService } from './audit.service';
import { ReportService } from '../shared/report.service';

/** Action → icon so the timeline reads at a glance. */
function actionIcon(action: string): string {
  if (action.startsWith('EMPLOYEE')) return 'person';
  if (action.startsWith('DEPARTMENT') || action.startsWith('DESIGNATION')) return 'account_tree';
  if (action.startsWith('JOB') || action.startsWith('CANDIDATE') || action.startsWith('APPLICATION')) return 'work';
  if (action.startsWith('INTERVIEW')) return 'record_voice_over';
  if (action.startsWith('ONBOARDING')) return 'badge';
  if (action.startsWith('ATTENDANCE')) return 'fact_check';
  if (action.startsWith('LEAVE')) return 'event_busy';
  if (action.startsWith('PAY')) return 'payments';
  if (action.startsWith('REVIEW')) return 'trending_up';
  if (action.startsWith('DOCUMENT')) return 'folder_shared';
  return 'history';
}

/**
 * HR-facing audit trail (Phase 18): every important state-changing action
 * with who did it, what changed, and when — paged and filtered entirely in
 * SQL by the backend. ADMIN/HR only; any other role sees the restricted
 * state (the backend enforces the same policy).
 */
@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AuditComponent implements OnInit {
  private readonly api = inject(AuditService);
  private readonly reports = inject(ReportService);

  readonly PAGE_SIZE = 15;
  readonly actionIcon = actionIcon;

  readonly entries = signal<AuditEntry[]>([]);
  readonly entityTypes = signal<string[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly forbidden = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly page = signal(0);
  readonly pageSize = signal(this.PAGE_SIZE);

  // Filter state (server-side, AND-combined).
  readonly search = signal('');
  readonly entityType = signal<string>('');
  readonly action = signal<string>('');
  /** matDatepicker emits Date objects via the picker but raw strings while typing. */
  readonly fromDate = signal<Date | string | null>(null);
  readonly toDate = signal<Date | string | null>(null);

  readonly actions = [
    'EMPLOYEE_CREATED', 'EMPLOYEE_UPDATED', 'EMPLOYEE_TERMINATED',
    'DEPARTMENT_CREATED', 'DEPARTMENT_UPDATED', 'DEPARTMENT_DELETED',
    'DESIGNATION_CREATED', 'DESIGNATION_UPDATED', 'DESIGNATION_DELETED',
    'JOB_CREATED', 'JOB_UPDATED', 'JOB_DELETED',
    'CANDIDATE_CREATED', 'CANDIDATE_UPDATED', 'CANDIDATE_DELETED',
    'APPLICATION_CREATED', 'APPLICATION_MOVED',
    'INTERVIEW_SCHEDULED', 'INTERVIEW_RESCHEDULED', 'INTERVIEW_COMPLETED', 'INTERVIEW_CANCELLED',
    'ONBOARDING_STARTED', 'ONBOARDING_FROM_APPLICATION', 'ONBOARDING_CHECKLIST_UPDATED',
    'ATTENDANCE_MARKED',
    'LEAVE_TYPE_CREATED', 'LEAVE_TYPE_UPDATED', 'LEAVE_TYPE_DELETED',
    'LEAVE_REQUEST_SUBMITTED', 'LEAVE_REQUEST_APPROVED', 'LEAVE_REQUEST_REJECTED',
    'LEAVE_REQUEST_CANCELLED', 'LEAVE_REQUEST_DELETED',
    'PAYROLL_RUN_EXECUTED', 'PAYSLIP_UPDATED', 'PAYSLIP_PROCESSED', 'PAYSLIP_PAID', 'PAYSLIP_DELETED',
    'REVIEW_CREATED', 'REVIEW_UPDATED', 'REVIEW_SUBMITTED', 'REVIEW_ACKNOWLEDGED', 'REVIEW_DELETED',
    'DOCUMENT_UPLOADED', 'DOCUMENT_DELETED',
  ];

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.loadFacets();
    this.reload();
  }

  reload(): void {
    this.loadError.set(null);
    if (this.entries().length === 0) {
      this.loading.set(true);
    }
    this.api
      .list({
        action: this.action() || undefined,
        entityType: this.entityType() || undefined,
        from: this.iso(this.fromDate()),
        to: this.iso(this.toDate()),
        search: this.search().trim() || undefined,
        page: this.page(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (res) => {
          this.entries.set(res.data.content);
          this.total.set(res.data.totalElements);
          this.loading.set(false);
          this.forbidden.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          if (err?.status === 403) {
            this.forbidden.set(true);
          } else {
            this.loadError.set(err?.error?.message || 'Could not load the audit log');
          }
        },
      });
  }

  onFilterChange(): void {
    this.page.set(0);
    this.reload();
  }

  /** Debounced so typing stays smooth while each keystroke hits the server. */
  onSearchInput(term: string): void {
    this.search.set(term);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.onFilterChange(), 350);
  }

  clearFilters(): void {
    this.search.set('');
    this.entityType.set('');
    this.action.set('');
    this.fromDate.set(null);
    this.toDate.set(null);
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return (
      this.search().trim() !== '' ||
      this.entityType() !== '' ||
      this.action() !== '' ||
      this.fromDate() !== null ||
      this.toDate() !== null
    );
  }

  onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.reload();
  }

  /**
   * Exports the trail as CSV with the filters currently applied to the
   * page — what you see is what you get. The backend caps rows SQL-side;
   * paging never applies to the export.
   */
  exportCsv(): void {
    this.reports.download(
      this.api.exportCsv({
        action: this.action() || undefined,
        entityType: this.entityType() || undefined,
        from: this.iso(this.fromDate()),
        to: this.iso(this.toDate()),
        search: this.search().trim() || undefined,
      }),
      'audit-log.csv',
    );
  }

  /** Human title for an action code: LEAVE_REQUEST_APPROVED → Leave request approved. */
  actionTitle(action: string): string {
    const words = action.toLowerCase().split('_');
    return words
      .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
      .join(' ');
  }

  private loadFacets(): void {
    this.api.entityTypes().subscribe({
      next: (res) => this.entityTypes.set(res.data),
      error: () => undefined,
    });
  }

  /**
   * Normalizes picker output to the backend's YYYY-MM-DD contract. A raw
   * string is only forwarded when it is already a complete ISO date —
   * partial text mid-typing must never reach the server (it crashed as
   * date.getFullYear during live verification).
   */
  private iso(value: Date | string | null): string | undefined {
    if (!value) return undefined;
    if (typeof value === 'string') {
      const s = value.trim();
      if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
      // The datepicker's en-US short-date typing format: M/D/YYYY.
      const md = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
      if (md) {
        return `${md[3]}-${md[1].padStart(2, '0')}-${md[2].padStart(2, '0')}`;
      }
      return undefined;
    }
    const y = value.getFullYear();
    const m = String(value.getMonth() + 1).padStart(2, '0');
    const d = String(value.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
