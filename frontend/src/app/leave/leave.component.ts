import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { ReportService } from '../shared/report.service';
import {
  BalanceResponse,
  LeaveRequest,
  LeaveService,
  LeaveStatus,
  LeaveType,
} from './leave.service';

/** Leave management page (Phase 8): request queue, approvals, balances. */
@Component({
  selector: 'app-leave',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './leave.component.html',
  styleUrl: './leave.component.scss',
})
export class LeaveComponent implements OnInit {
  private readonly api = inject(LeaveService);
  private readonly dialogs = inject(MatDialog);
  private readonly reports = inject(ReportService);
  readonly auth = inject(AuthService);

  readonly canWrite = computed(() => {
    const role = this.auth.user()?.role;
    return role === 'ADMIN' || role === 'HR';
  });

  readonly statuses: (LeaveStatus | 'ALL')[] = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];
  readonly statusFilter = signal<LeaveStatus | 'ALL'>('ALL');

  /** Exports requests with the status tab currently selected. */
  export(format: 'csv' | 'pdf'): void {
    const filter = this.statusFilter();
    const qs = filter === 'ALL' ? '' : `?status=${filter}`;
    this.reports.download(
      `/api/v1/reports/leave.${format}${qs}`,
      `leave${filter === 'ALL' ? '' : '-' + filter.toLowerCase()}.${format}`,
    );
  }

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly requests = signal<LeaveRequest[]>([]);
  readonly types = signal<LeaveType[]>([]);
  readonly summary = signal<{ pendingCount: number; approvedThisYear: number; rejectedCount: number; approvalRate: number } | null>(null);

  readonly balance = signal<BalanceResponse | null>(null);
  readonly balanceEmployeeId = signal<number | null>(null);
  readonly employeeOptions = signal<{ id: number; label: string }[]>([]);

  ngOnInit(): void {
    this.reload();
  }
  setFilter(status: LeaveStatus | 'ALL'): void {
    this.statusFilter.set(status);
    this.reloadRequests();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin([this.api.requests(this.currentStatus()), this.api.types(), this.api.summary(), this.api.employeeOptions()])
      .subscribe({
        next: ([requests, types, summary, employees]) => {
          this.requests.set(requests.data);
          this.types.set(types.data);
          this.summary.set(summary.data);
          this.employeeOptions.set(employees.data);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(
            (err as { error?: { message?: string } })?.error?.message ?? 'Could not load leave data');
          this.loading.set(false);
        },
      });
  }

  private reloadRequests(): void {
    this.api.requests(this.currentStatus()).subscribe({
      next: (res) => this.requests.set(res.data),
      error: (err) =>
        this.error.set(
          (err as { error?: { message?: string } })?.error?.message ?? 'Could not load leave requests'),
    });
  }

  private currentStatus(): LeaveStatus | undefined {
    const filter = this.statusFilter();
    return filter === 'ALL' ? undefined : filter;
  }

  // ------------------------------------------------------------ actions

  approve(row: LeaveRequest): void {
    this.api.approve(row.id).subscribe({ next: () => this.refreshAfterDecision(), error: () => this.error.set('Approval failed') });
  }

  reject(row: LeaveRequest): void {
    this.api.reject(row.id).subscribe({ next: () => this.refreshAfterDecision(), error: () => this.error.set('Rejection failed') });
  }

  cancel(row: LeaveRequest): void {
    this.api.cancel(row.id).subscribe({ next: () => this.reload(), error: () => this.error.set('Cancel failed') });
  }

  private refreshAfterDecision(): void {
    this.reload();
    const employeeId = this.balanceEmployeeId();
    if (employeeId != null) {
      this.loadBalance(employeeId);
    }
  }

  // ------------------------------------------------------------- balance

  loadBalance(employeeId: number | null): void {
    this.balanceEmployeeId.set(employeeId);
    if (employeeId == null) {
      this.balance.set(null);
      return;
    }
    this.api.balances(employeeId).subscribe({
      next: (res) => this.balance.set(res.data),
      error: () => this.error.set('Could not load balances'),
    });
  }

  // ------------------------------------------------------------- dialogs

  openNewRequestDialog(): void {
    const ref = this.dialogs.open(NewRequestDialog, { width: '460px', data: { types: this.types() } });
    ref.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  openTypeDialog(type: LeaveType | null): void {
    const ref = this.dialogs.open(TypeDialog, { width: '420px', data: { type } });
    ref.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  deleteType(type: LeaveType): void {
    const ref = this.dialogs.open(ConfirmDialog, {
      width: '380px',
      data: { title: 'Delete leave type', message: `Delete "${type.name}"? This only works while it has no requests.` },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deleteType(type.id).subscribe({
        next: () => this.reload(),
        error: () => this.error.set('Only unused leave types can be deleted'),
      });
    });
  }
}

// ==================================================================== dialogs

interface NewRequestData {
  types: LeaveType[];
}

/** File a leave request for an employee. */
@Component({
  selector: 'app-new-leave-request-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>New leave request</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        <mat-form-field appearance="outline">
          <mat-label>Employee</mat-label>
          <mat-select [(ngModel)]="employeeId">
            @for (e of employees(); track e.id) {
              <mat-option [value]="e.id">{{ e.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Leave type</mat-label>
          <mat-select [(ngModel)]="leaveTypeId">
            @for (t of data.types; track t.id) {
              <mat-option [value]="t.id">{{ t.name }} · {{ t.yearlyLimit }}d/yr</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Start date</mat-label>
          <input matInput type="date" [(ngModel)]="startDate" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>End date</mat-label>
          <input matInput type="date" [(ngModel)]="endDate" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Reason (optional)</mat-label>
          <input matInput [(ngModel)]="reason" maxlength="500" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!valid()" (click)="save()">Submit request</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 360px; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class NewRequestDialog {
  readonly data: NewRequestData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<NewRequestDialog>);
  private readonly api = inject(LeaveService);

  readonly serverError = signal<string | null>(null);
  readonly employees = signal<{ id: number; label: string }[]>([]);

  employeeId: number | null = null;
  leaveTypeId: number | null = null;
  startDate = '';
  endDate = '';
  reason = '';

  constructor() {
    this.api.employeeOptions().subscribe({
      next: (res) => this.employees.set(res.data),
      error: () => this.serverError.set('Could not load employees'),
    });
  }

  valid(): boolean {
    return this.employeeId != null && this.leaveTypeId != null
      && this.startDate !== '' && this.endDate !== '' && this.startDate <= this.endDate;
  }

  save(): void {
    this.api.createRequest({
      employeeId: this.employeeId!,
      leaveTypeId: this.leaveTypeId!,
      startDate: this.startDate,
      endDate: this.endDate,
      reason: this.reason.trim() ? this.reason.trim() : null,
    }).subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.serverError.set(err?.error?.message ?? 'Could not create request'),
    });
  }
}

interface TypeDialogData {
  type: LeaveType | null;
}

/** Create or edit a leave type. */
@Component({
  selector: 'app-leave-type-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.type ? 'Edit leave type' : 'New leave type' }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput [(ngModel)]="name" maxlength="50" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description (optional)</mat-label>
          <input matInput [(ngModel)]="description" maxlength="255" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Yearly limit (days)</mat-label>
          <input matInput type="number" [(ngModel)]="yearlyLimit" min="1" max="365" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!valid()" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 320px; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class TypeDialog {
  readonly data: TypeDialogData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<TypeDialog>);
  private readonly api = inject(LeaveService);

  readonly serverError = signal<string | null>(null);

  name = this.data.type?.name ?? '';
  description = this.data.type?.description ?? '';
  yearlyLimit: number | null = this.data.type?.yearlyLimit ?? null;

  valid(): boolean {
    return this.name.trim() !== '' && this.yearlyLimit != null && this.yearlyLimit >= 1;
  }

  save(): void {
    const payload = {
      name: this.name.trim(),
      description: this.description.trim() ? this.description.trim() : null,
      yearlyLimit: this.yearlyLimit!,
    };
    const call = this.data.type
      ? this.api.updateType(this.data.type.id, payload)
      : this.api.createType(payload);
    call.subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.serverError.set(err?.error?.message ?? 'Could not save leave type'),
    });
  }
}

interface ConfirmData {
  title: string;
  message: string;
}

/** Minimal confirm dialog. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Delete</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialog {
  readonly data: ConfirmData = inject(MAT_DIALOG_DATA);
}
