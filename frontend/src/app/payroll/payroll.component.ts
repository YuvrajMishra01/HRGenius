import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';

import { AuthService } from '../core/auth.service';
import {
  PayrollRow,
  PayrollService,
  PayrollStatus,
  PeriodResponse,
  PeriodsResponse,
} from './payroll.service';

/** Payroll page (Phase 9): runs overview, period view, payslip lifecycle. */
@Component({
  selector: 'app-payroll',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './payroll.component.html',
  styleUrl: './payroll.component.scss',
})
export class PayrollComponent implements OnInit {
  private readonly api = inject(PayrollService);
  private readonly dialogs = inject(MatDialog);
  readonly auth = inject(AuthService);

  readonly canWrite = computed(() => {
    const role = this.auth.user()?.role;
    return role === 'ADMIN' || role === 'HR';
  });

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly periods = signal<PeriodsResponse | null>(null);
  readonly period = signal<PeriodResponse | null>(null);
  readonly selected = signal<string | null>(null); // "2026-9"

  readonly monthNames = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  ngOnInit(): void {
    this.reloadPeriods();
  }

  reloadPeriods(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.periods().subscribe({
      next: (res) => {
        this.periods.set(res.data);
        this.loading.set(false);
        const first = res.data.periods[0];
        const sel = this.selected() ?? (first ? `${first.year}-${first.month}` : null);
        if (sel) {
          this.selected.set(sel);
          this.loadPeriod(sel);
        }
      },
      error: () => {
        this.error.set('Could not load payroll periods');
        this.loading.set(false);
      },
    });
  }

  selectPeriod(key: string): void {
    this.selected.set(key);
    this.loadPeriod(key);
  }

  private loadPeriod(key: string): void {
    const [y, m] = key.split('-').map(Number);
    this.api.period(y, m).subscribe({
      next: (res) => this.period.set(res.data),
      error: () => this.error.set('Could not load the period'),
    });
  }

  // ------------------------------------------------------------- run

  openRunDialog(): void {
    const ref = this.dialogs.open(RunDialog, { width: '380px', data: { disabledPeriods: this.periods()?.periods ?? [] } });
    ref.afterClosed().subscribe((ran) => {
      if (ran) {
        this.reloadPeriods();
      }
    });
  }

  // -------------------------------------------------------- lifecycle

  editComponents(row: PayrollRow): void {
    const ref = this.dialogs.open(ComponentsDialog, { width: '440px', data: { row } });
    ref.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reloadPeriods();
      }
    });
  }

  process(row: PayrollRow): void {
    this.api.process(row.id).subscribe({ next: () => this.refreshPeriod(), error: () => this.error.set('Process failed') });
  }

  pay(row: PayrollRow): void {
    this.api.markPaid(row.id).subscribe({ next: () => this.refreshPeriod(), error: () => this.error.set('Payment marking failed') });
  }

  delete(row: PayrollRow): void {
    const ref = this.dialogs.open(ConfirmDialog, {
      width: '380px',
      data: { title: 'Delete payslip', message: `Delete the ${this.monthNames[row.payMonth - 1]} draft for ${row.employeeName}?` },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.delete(row.id).subscribe({
        next: () => this.refreshPeriod(),
        error: () => this.error.set('PAID payslips are permanent and cannot be deleted'),
      });
    });
  }

  private refreshPeriod(): void {
    const key = this.selected();
    if (key) {
      this.loadPeriod(key);
    }
    this.api.periods().subscribe({ next: (res) => this.periods.set(res.data) });
  }

  // ------------------------------------------------------------ utils

  monthName(month: number): string {
    return this.monthNames[month - 1];
  }

  money(value: number | null | undefined): string {
    if (value == null) {
      return '—';
    }
    return '₹' + value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

// ==================================================================== dialogs

interface RunDialogData {
  disabledPeriods: { year: number; month: number }[];
}

/** Trigger a monthly payroll run. */
@Component({
  selector: 'app-payroll-run-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Run payroll</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        <mat-form-field appearance="outline">
          <mat-label>Year</mat-label>
          <input matInput type="number" [(ngModel)]="year" min="2000" max="2999" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Month</mat-label>
          <mat-select [(ngModel)]="month">
            @for (m of months; track m.value) {
              <mat-option [value]="m.value" [disabled]="isDisabled(m.value)">{{ m.label }}</mat-option>
            }
          </mat-select>
          <mat-hint>Existing payslips for the period are kept as-is</mat-hint>
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!valid()" (click)="run()">Run payroll</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 260px; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class RunDialog {
  readonly data: RunDialogData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RunDialog>);
  private readonly api = inject(PayrollService);

  readonly serverError = signal<string | null>(null);
  readonly months = Array.from({ length: 12 }, (_, i) => ({
    value: i + 1,
    label: new Date(2026, i, 1).toLocaleString('en', { month: 'long' }),
  }));

  year: number | null = new Date().getFullYear();
  month: number | null = new Date().getMonth() + 1;

  isDisabled(month: number): boolean {
    return (this.data.disabledPeriods ?? []).some((p) => p.year === this.year && p.month === month);
  }

  valid(): boolean {
    return this.year != null && this.year >= 2000 && this.year <= 2999 && this.month != null && this.month >= 1 && this.month <= 12;
  }

  run(): void {
    this.api.run(this.year!, this.month!).subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.serverError.set(err?.error?.message ?? 'Run failed'),
    });
  }
}

interface ComponentsDialogData {
  row: PayrollRow;
}

/** Adjust the four editable components of a DRAFT/PROCESSED payslip. */
@Component({
  selector: 'app-payroll-components-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Payslip — {{ data.row.employeeName }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        <div class="field">
          <label>Basic salary</label>
          <input matInput type="number" min="0" step="0.01" [(ngModel)]="basicSalary" />
        </div>
        <div class="field">
          <label>Allowances</label>
          <input matInput type="number" min="0" step="0.01" [(ngModel)]="allowances" />
        </div>
        <div class="field">
          <label>Deductions</label>
          <input matInput type="number" min="0" step="0.01" [(ngModel)]="deductions" />
        </div>
        <div class="field">
          <label>Tax</label>
          <input matInput type="number" min="0" step="0.01" [(ngModel)]="tax" />
        </div>
        <div class="net-preview">
          <span>Net salary</span>
          <strong>{{ previewNet() }}</strong>
        </div>
        <p class="hint">Net is recomputed server-side as basic + allowances − deductions − tax (never below 0).</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!valid()" (click)="save()">Save components</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 10px; min-width: 340px; }
      .field { display: flex; flex-direction: column; gap: 2px; }
      .field label { font-size: 12px; color: #666; }
      .net-preview {
        display: flex; justify-content: space-between; align-items: center;
        background: #f1f5f9; border-radius: 8px; padding: 10px 14px; font-size: 15px;
      }
      .hint { font-size: 12px; color: #8a8f98; margin: 0; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class ComponentsDialog {
  readonly data: ComponentsDialogData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ComponentsDialog>);
  private readonly api = inject(PayrollService);

  readonly serverError = signal<string | null>(null);

  basicSalary: number = this.data.row.basicSalary;
  allowances: number = this.data.row.allowances;
  deductions: number = this.data.row.deductions;
  tax: number = this.data.row.tax;

  previewNet(): string {
    const net = Math.max(0,
      (this.basicSalary || 0) + (this.allowances || 0) - (this.deductions || 0) - (this.tax || 0));
    return net.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  valid(): boolean {
    return [this.basicSalary, this.allowances, this.deductions, this.tax]
      .every((v) => v != null && v >= 0);
  }

  save(): void {
    this.api.updateComponents(this.data.row.id, {
      basicSalary: this.basicSalary,
      allowances: this.allowances,
      deductions: this.deductions,
      tax: this.tax,
    }).subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.serverError.set(err?.error?.message ?? 'Could not save components'),
    });
  }
}

interface ConfirmData {
  title: string;
  message: string;
}

/** Minimal confirm dialog. */
@Component({
  selector: 'app-payroll-confirm-dialog',
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
