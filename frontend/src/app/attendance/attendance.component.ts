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
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuthService } from '../core/auth.service';
import {
  AttendanceRecord,
  AttendanceService,
  AttendanceStatus,
  MonthResponse,
  TodayResponse,
} from './attendance.service';

/** Mark-attendance dialog data. */
interface MarkDialogData {
  employeeId: number;
  employeeName: string;
  date: string; // yyyy-MM-dd
}

/** Manual marking / correction of one employee-day. */
@Component({
  selector: 'app-mark-attendance-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Mark attendance</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <p class="who"><strong>{{ data.employeeName }}</strong> · {{ data.date }}</p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Status</mat-label>
        <mat-select [(ngModel)]="status">
          <mat-option value="PRESENT">Present</mat-option>
          <mat-option value="HALF_DAY">Half day</mat-option>
          <mat-option value="ABSENT">Absent</mat-option>
          <mat-option value="LEAVE">On leave</mat-option>
          <mat-option value="HOLIDAY">Holiday</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .who { margin: 0 0 12px; }
      .full { width: 100%; min-width: 300px; }
      .alert { display: flex; gap: 8px; align-items: center; background: #fdecea; color: #b71c1c;
               border: 1px solid #f5c6cb; border-radius: 8px; padding: 10px 14px; margin-bottom: 12px;
               font-size: 14px; }
    `,
  ],
})
export class MarkAttendanceDialog {
  private readonly service = inject(AttendanceService);
  readonly dialogRef = inject<MatDialogRef<MarkAttendanceDialog>>(MatDialogRef);
  readonly data = inject<MarkDialogData>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly serverError = signal<string | null>(null);
  status: AttendanceStatus = 'PRESENT';

  save(): void {
    this.saving.set(true);
    this.service.mark({ employeeId: this.data.employeeId, date: this.data.date, status: this.status }).subscribe({
      next: (res) => this.dialogRef.close(res.data),
      error: (err) => {
        this.saving.set(false);
        this.serverError.set((err as { error?: { message?: string } })?.error?.message ?? 'Save failed.');
      },
    });
  }
}

/** Attendance page: today strip + monthly grid. */
@Component({
  selector: 'app-attendance',
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
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './attendance.component.html',
  styleUrl: './attendance.component.scss',
})
export class AttendanceComponent implements OnInit {
  private readonly service = inject(AttendanceService);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AuthService);

  readonly canWrite = computed(
    () => this.auth.user()?.role === 'ADMIN' || this.auth.user()?.role === 'HR',
  );

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly todayData = signal<TodayResponse | null>(null);
  readonly monthData = signal<MonthResponse | null>(null);
  /** Year/month being viewed. */
  readonly viewYear = signal(new Date().getFullYear());
  readonly viewMonth = signal(new Date().getMonth() + 1);
  /** Check-in / check-out busy flags (prevents double clicks). */
  readonly busyEmployeeId = signal<number | null>(null);

  readonly monthLabel = computed(() => {
    const name = new Date(this.viewYear(), this.viewMonth() - 1, 1)
      .toLocaleString('en-US', { month: 'long' });
    return `${name} ${this.viewYear()}`;
  });

  /** Days 1..N of the viewed month for the grid header. */
  readonly daysInMonth = computed(() => {
    const days = new Date(this.viewYear(), this.viewMonth(), 0).getDate();
    return Array.from({ length: days }, (_, i) => i + 1);
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set(null);
    let done = 0;
    const finish = () => {
      if (++done === 2) this.loading.set(false);
    };
    this.service.today().subscribe({
      next: (res) => {
        this.todayData.set(res.data);
        finish();
      },
      error: (err) => {
        this.loadError.set((err as { error?: { message?: string } })?.error?.message ?? 'Failed to load attendance.');
        finish();
      },
    });
    this.service.month(this.viewYear(), this.viewMonth()).subscribe({
      next: (res) => {
        this.monthData.set(res.data);
        finish();
      },
      error: (err) => {
        this.loadError.set((err as { error?: { message?: string } })?.error?.message ?? 'Failed to load attendance.');
        finish();
      },
    });
  }

  shiftMonth(delta: number): void {
    let m = this.viewMonth() + delta;
    let y = this.viewYear();
    if (m < 1) { m = 12; y--; }
    if (m > 12) { m = 1; y++; }
    this.viewMonth.set(m);
    this.viewYear.set(y);
    this.loading.set(true);
    this.loadError.set(null);
    this.service.month(y, m).subscribe({
      next: (res) => {
        this.monthData.set(res.data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set((err as { error?: { message?: string } })?.error?.message ?? 'Failed to load month.');
        this.loading.set(false);
      },
    });
  }

  isToday(year: number, month: number, day: number): boolean {
    const now = new Date();
    return now.getFullYear() === year && now.getMonth() + 1 === month && now.getDate() === day;
  }

  /** Weekends get a subtle background in the grid. */
  isWeekend(day: number): boolean {
    const dow = new Date(this.viewYear(), this.viewMonth() - 1, day).getDay();
    return dow === 0 || dow === 6;
  }

  cellClass(status: AttendanceStatus | undefined): string {
    switch (status) {
      case 'PRESENT': return 'cell-present';
      case 'HALF_DAY': return 'cell-half';
      case 'ABSENT': return 'cell-absent';
      case 'LEAVE': return 'cell-leave';
      case 'HOLIDAY': return 'cell-holiday';
      default: return 'cell-empty';
    }
  }

  /** Template-facing day key (templates cannot access the global String). */
  dayKey(day: number): string {
    return String(day);
  }

  // -------------------------------------------------------- actions

  checkIn(record: AttendanceRecord | undefined, employeeId: number): void {
    if (record) return; // already has a record today
    this.busyEmployeeId.set(employeeId);
    this.service.checkIn(employeeId).subscribe({
      next: () => this.reload(),
      error: (err) => {
        this.busyEmployeeId.set(null);
        this.loadError.set((err as { error?: { message?: string } })?.error?.message ?? 'Check-in failed.');
      },
    });
  }

  checkOut(record: AttendanceRecord | undefined): void {
    if (!record?.checkIn || record.checkOut) return;
    this.busyEmployeeId.set(record.employeeId);
    this.service.checkOut(record.employeeId).subscribe({
      next: () => this.reload(),
      error: (err) => {
        this.busyEmployeeId.set(null);
        this.loadError.set((err as { error?: { message?: string } })?.error?.message ?? 'Check-out failed.');
      },
    });
  }

  openMark(row: { employeeId: number; employeeName: string }, day: number): void {
    if (!this.canWrite()) return;
    const date = `${this.viewYear()}-${String(this.viewMonth()).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    this.dialog
      .open(MarkAttendanceDialog, {
        width: '400px',
        data: { employeeId: row.employeeId, employeeName: row.employeeName, date } satisfies MarkDialogData,
      })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }
}
