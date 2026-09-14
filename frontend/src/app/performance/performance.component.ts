import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
  EmployeeOption,
  PerformanceReview,
  PerformanceService,
  ReviewStatus,
} from './performance.service';

/** Performance page (Phase 10): reviews, ratings, lifecycle. */
@Component({
  selector: 'app-performance',
  standalone: true,
  imports: [
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
  templateUrl: './performance.component.html',
  styleUrl: './performance.component.scss',
})
export class PerformanceComponent implements OnInit {
  private readonly api = inject(PerformanceService);
  private readonly dialogs = inject(MatDialog);
  readonly auth = inject(AuthService);

  readonly canWrite = computed(() => {
    const role = this.auth.user()?.role;
    return role === 'ADMIN' || role === 'HR';
  });

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly reviews = signal<PerformanceReview[]>([]);
  readonly summary = signal<{ totalReviews: number; draftCount: number; submittedCount: number; acknowledgedCount: number; averageRating: number | null; ratingDistribution: { rating: number; count: number }[] } | null>(null);

  readonly statusFilter = signal<ReviewStatus | 'ALL'>('ALL');
  readonly statuses: (ReviewStatus | 'ALL')[] = ['ALL', 'DRAFT', 'SUBMITTED', 'ACKNOWLEDGED'];

  ngOnInit(): void {
    this.reload();
  }

  setFilter(status: ReviewStatus | 'ALL'): void {
    this.statusFilter.set(status);
  }

  /** Client-side filter: the list is small and already fully loaded. */
  readonly filteredReviews = computed(() => {
    const filter = this.statusFilter();
    const rows = this.reviews();
    return filter === 'ALL' ? rows : rows.filter((r) => r.status === filter);
  });

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin([this.api.reviews(), this.api.summary()])
      .subscribe({
        next: ([reviews, summary]) => {
          this.reviews.set(reviews.data);
          this.summary.set(summary.data);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(
            (err as { error?: { message?: string } })?.error?.message ?? 'Could not load performance data');
          this.loading.set(false);
        },
      });
  }

  // ------------------------------------------------------------- actions

  rate(row: PerformanceReview): void {
    const ref = this.dialogs.open(RateDialog, { width: '440px', data: { row } });
    ref.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  edit(row: PerformanceReview): void {
    const ref = this.dialogs.open(EditDialog, { width: '520px', data: { row } });
    ref.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  acknowledge(row: PerformanceReview): void {
    this.api.acknowledge(row.id).subscribe({
      next: () => this.reload(),
      error: () => this.error.set('Only SUBMITTED reviews can be acknowledged'),
    });
  }

  createReview(): void {
    const ref = this.dialogs.open(NewReviewDialog, { width: '460px' });
    ref.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  deleteReview(row: PerformanceReview): void {
    const ref = this.dialogs.open(ConfirmDialog, {
      width: '380px',
      data: {
        title: 'Delete draft review',
        message: `Delete the ${row.reviewPeriod} draft for ${row.employeeName}? Only drafts can be deleted.`,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.delete(row.id).subscribe({
        next: () => this.reload(),
        error: () => this.error.set('Only DRAFT reviews can be deleted'),
      });
    });
  }

  stars(rating: number | null): string {
    return rating == null ? '—' : '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }
}

// ==================================================================== dialogs

interface NewReviewData {
  // empty — uses shared employee options
}

/** Start a new DRAFT review for an employee/period. */
@Component({
  selector: 'app-new-review-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>New performance review</h2>
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
          <mat-label>Reviewer</mat-label>
          <mat-select [(ngModel)]="reviewerId">
            @for (e of employees(); track e.id) {
              <mat-option [value]="e.id">{{ e.label }}</mat-option>
            }
          </mat-select>
          <mat-hint>Cannot be the reviewed employee</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Review period</mat-label>
          <input matInput [(ngModel)]="reviewPeriod" placeholder="e.g. 2026-H1" maxlength="20" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Goals (optional)</mat-label>
          <input matInput [(ngModel)]="goals" maxlength="1000" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!valid()" (click)="save()">Create draft</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 360px; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class NewReviewDialog {
  private readonly ref = inject(MatDialogRef<NewReviewDialog>);
  private readonly api = inject(PerformanceService);

  readonly serverError = signal<string | null>(null);
  readonly employees = signal<EmployeeOption[]>([]);

  employeeId: number | null = null;
  reviewerId: number | null = null;
  reviewPeriod = '';
  goals = '';

  constructor() {
    this.api.employeeOptions().subscribe({
      next: (res) => this.employees.set(res.data),
      error: () => this.serverError.set('Could not load employees'),
    });
  }

  valid(): boolean {
    return this.employeeId != null && this.reviewerId != null && this.reviewPeriod.trim() !== '';
  }

  save(): void {
    this.api.create({
      employeeId: this.employeeId!,
      reviewerId: this.reviewerId!,
      reviewPeriod: this.reviewPeriod.trim(),
      goals: this.goals.trim() ? this.goals.trim() : null,
    }).subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.serverError.set(err?.error?.message ?? 'Could not create review'),
    });
  }
}

interface EditDialogData {
  row: PerformanceReview;
}

/** Edit a DRAFT review's narrative fields. */
@Component({
  selector: 'app-edit-review-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Edit review — {{ data.row.employeeName }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        <mat-form-field appearance="outline">
          <mat-label>Goals</mat-label>
          <input matInput [(ngModel)]="goals" maxlength="1000" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Strengths</mat-label>
          <input matInput [(ngModel)]="strengths" maxlength="1000" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Areas to improve</mat-label>
          <input matInput [(ngModel)]="weaknesses" maxlength="1000" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Comments</mat-label>
          <input matInput [(ngModel)]="comments" maxlength="1000" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!valid()" (click)="save()">Save draft</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 400px; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class EditDialog {
  readonly data: EditDialogData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<EditDialog>);
  private readonly api = inject(PerformanceService);

  readonly serverError = signal<string | null>(null);

  goals = this.data.row.goals ?? '';
  strengths = this.data.row.strengths ?? '';
  weaknesses = this.data.row.weaknesses ?? '';
  comments = this.data.row.comments ?? '';

  valid(): boolean {
    return true;
  }

  save(): void {
    this.api.update(this.data.row.id, {
      goals: this.goals.trim() ? this.goals.trim() : null,
      strengths: this.strengths.trim() ? this.strengths.trim() : null,
      weaknesses: this.weaknesses.trim() ? this.weaknesses.trim() : null,
      comments: this.comments.trim() ? this.comments.trim() : null,
    }).subscribe({
      next: () => this.ref.close(true),
      error: (err) => this.serverError.set(err?.error?.message ?? 'Only DRAFT reviews can be modified'),
    });
  }
}

interface RateDialogData {
  row: PerformanceReview;
}

/** Rate 1–5 and submit in one step. */
@Component({
  selector: 'app-rate-review-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Rate & submit — {{ data.row.employeeName }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        <mat-form-field appearance="outline">
          <mat-label>Rating (1–5)</mat-label>
          <mat-select [(ngModel)]="rating">
            @for (r of [1, 2, 3, 4, 5]; track r) {
              <mat-option [value]="r">{{ stars(r) }} ({{ r }})</mat-option>
            }
          </mat-select>
          <mat-hint>Submitting locks the review; the employee then acknowledges</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Final comments</mat-label>
          <input matInput [(ngModel)]="comments" maxlength="1000" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="rating == null" (click)="save()">
        Submit review
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 360px; }
      .alert { display: flex; gap: 8px; align-items: center; color: #b3261e; margin-bottom: 8px; font-size: 13px; }
    `,
  ],
})
export class RateDialog {
  readonly data: RateDialogData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RateDialog>);
  private readonly api = inject(PerformanceService);

  readonly serverError = signal<string | null>(null);

  rating: number | null = null;
  comments = '';

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }

  save(): void {
    this.api.rate(this.data.row.id, this.rating!, this.comments.trim() ? this.comments.trim() : null)
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => this.serverError.set(err?.error?.message ?? 'Could not submit review'),
      });
  }
}

interface ConfirmData {
  title: string;
  message: string;
}

/** Minimal confirm dialog. */
@Component({
  selector: 'app-performance-confirm-dialog',
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