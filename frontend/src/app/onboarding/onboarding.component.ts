import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuthService } from '../core/auth.service';
import { Onboarding, OnboardingService } from './onboarding.service';
import { RecruitmentService } from '../recruitment/recruitment.service';

/** Data for the start-onboarding dialog. */
interface StartDialogData {
  mode: 'application' | 'employee';
}

/** Start onboarding from a SELECTED application or for an existing employee. */
@Component({
  selector: 'app-start-onboarding-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.mode === 'application' ? 'Start onboarding from pipeline' : 'Start onboarding for employee' }}
    </h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <div class="form">
        @if (data.mode === 'application') {
          <mat-form-field appearance="outline">
            <mat-label>Selected application</mat-label>
            <mat-select [(ngModel)]="applicationId">
              @for (a of applications(); track a.id) {
                <mat-option [value]="a.id">{{ a.candidateName }} · {{ a.jobTitle }}</mat-option>
              }
            </mat-select>
            <mat-hint>Only SELECTED applications can be onboarded</mat-hint>
          </mat-form-field>
        } @else {
          <mat-form-field appearance="outline">
            <mat-label>Employee</mat-label>
            <mat-select [(ngModel)]="employeeId">
              @for (e of employees(); track e.id) {
                <mat-option [value]="e.id">{{ e.label }}</mat-option>
              }
            </mat-select>
            <mat-hint>One onboarding record per employee</mat-hint>
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Joining date (optional)</mat-label>
          <input matInput type="date" [(ngModel)]="joiningDate" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Starting…' : 'Start onboarding' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; min-width: 340px; }
      .alert { display: flex; gap: 8px; align-items: center; background: #fdecea; color: #b71c1c;
               border: 1px solid #f5c6cb; border-radius: 8px; padding: 10px 14px; margin-bottom: 12px;
               font-size: 14px; }
    `,
  ],
})
export class StartOnboardingDialog {
  private readonly onboardingService = inject(OnboardingService);
  private readonly recruitmentService = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<StartOnboardingDialog>>(MatDialogRef);
  readonly data = inject<StartDialogData>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly applications = signal<{ id: number; candidateName: string; jobTitle: string }[]>([]);
  readonly employees = signal<{ id: number; label: string }[]>([]);

  applicationId: number | null = null;
  employeeId: number | null = null;
  joiningDate = '';

  constructor() {
    if (this.data.mode === 'application') {
      this.recruitmentService.applications().subscribe({
        next: (res) => this.applications.set(res.data.filter((a) => a.status === 'SELECTED')),
        error: () => this.serverError.set('Could not load applications.'),
      });
    } else {
      this.recruitmentService.employeeOptions().subscribe({
        next: (res) => this.employees.set(res.data),
        error: () => this.serverError.set('Could not load employees.'),
      });
    }
  }

  save(): void {
    this.saving.set(true);
    const date = this.joiningDate || null;
    const call =
      this.data.mode === 'application'
        ? this.onboardingService.startFromApplication(this.applicationId!, date)
        : this.onboardingService.startForEmployee(this.employeeId!, date);
    call.subscribe({
      next: (res) => this.dialogRef.close(res.data),
      error: (err) => {
        this.saving.set(false);
        this.serverError.set(
          (err as { error?: { message?: string } })?.error?.message ?? 'Start failed.',
        );
      },
    });
  }
}

/** Onboarding page: progress cards with interactive checklists. */
@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [
    DatePipe,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatCheckboxModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.scss',
})
export class OnboardingComponent implements OnInit {
  private readonly service = inject(OnboardingService);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AuthService);

  readonly canWrite = computed(
    () => this.auth.user()?.role === 'ADMIN' || this.auth.user()?.role === 'HR',
  );

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly onboardings = signal<Onboarding[]>([]);
  /** Which card's checklist is expanded. */
  readonly expandedId = signal<number | null>(null);

  readonly inProgressCount = computed(
    () => this.onboardings().filter((o) => o.status === 'IN_PROGRESS').length,
  );
  readonly completedCount = computed(
    () => this.onboardings().filter((o) => o.status === 'COMPLETED').length,
  );

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.service.list().subscribe({
      next: (res) => {
        this.onboardings.set(res.data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(
          (err as { error?: { message?: string } })?.error?.message ?? 'Failed to load onboarding records.',
        );
        this.loading.set(false);
      },
    });
  }

  startFromPipeline(): void {
    this.dialog
      .open(StartOnboardingDialog, { width: '480px', data: { mode: 'application' } satisfies StartDialogData })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  startForEmployee(): void {
    this.dialog
      .open(StartOnboardingDialog, { width: '480px', data: { mode: 'employee' } satisfies StartDialogData })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  toggleExpanded(id: number): void {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  toggleItem(onboarding: Onboarding, item: { label: string; done: boolean }, index: number): void {
    if (!this.canWrite()) {
      return;
    }
    this.service.toggleItem(onboarding.id, index, !item.done).subscribe({
      next: (res) => {
        // Replace the record in place so the progress bar animates.
        this.onboardings.update((list) =>
          list.map((o) => (o.id === onboarding.id ? res.data : o)),
        );
      },
      error: (err) =>
        this.loadError.set(
          (err as { error?: { message?: string } })?.error?.message ?? 'Could not update checklist.',
        ),
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'COMPLETED': return 'status-completed';
      case 'IN_PROGRESS': return 'status-in-progress';
      default: return 'status-pending';
    }
  }
}
