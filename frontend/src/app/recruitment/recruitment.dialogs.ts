import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import {
  ApplicationStatus,
  Candidate,
  Interview,
  InterviewMode,
  InterviewResult,
  Job,
  JobStatus,
  NEXT_STAGES,
} from './recruitment.models';
import { RecruitmentService } from './recruitment.service';

/** Shared error/saving plumbing for recruitment dialogs. */
@Component({ standalone: true, imports: [], template: '' })
abstract class BaseDialog {
  readonly saving = signal(false);
  readonly serverError = signal<string | null>(null);

  setError(err: unknown): void {
    const anyErr = err as { error?: { message?: string; errors?: Record<string, string> } };
    const fieldErrors = anyErr?.error?.errors;
    if (fieldErrors && Object.keys(fieldErrors).length > 0) {
      this.serverError.set(Object.values(fieldErrors)[0]);
    } else {
      this.serverError.set(anyErr?.error?.message ?? 'Save failed.');
    }
  }

  /** datetime-local value for a given ISO instant, in local time. */
  protected static toLocalInput(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}

const ALERT_STYLES = `
  .form { display: flex; flex-direction: column; gap: 4px; }
  .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .full { width: 100%; }
  .alert { display: flex; gap: 8px; align-items: center; background: #fdecea; color: #b71c1c;
           border: 1px solid #f5c6cb; border-radius: 8px; padding: 10px 14px; margin-bottom: 12px;
           font-size: 14px; }
`;

/** Job create/edit dialog. */
@Component({
  selector: 'app-job-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.job ? 'Edit job posting' : 'Post a job' }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" placeholder="Backend Developer" />
        </mat-form-field>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>Department</mat-label>
            <mat-select formControlName="departmentId">
              @for (d of departments(); track d.id) {
                <mat-option [value]="d.id">{{ d.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Employment type</mat-label>
            <mat-select formControlName="employmentType">
              <mat-option value="FULL_TIME">Full time</mat-option>
              <mat-option value="PART_TIME">Part time</mat-option>
              <mat-option value="CONTRACT">Contract</mat-option>
              <mat-option value="INTERN">Intern</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="DRAFT">Draft</mat-option>
              <mat-option value="OPEN">Open</mat-option>
              <mat-option value="CLOSED">Closed</mat-option>
            </mat-select>
            <mat-hint>Only OPEN jobs accept applications</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Closing date</mat-label>
            <input matInput type="date" formControlName="closingDate" />
          </mat-form-field>
        </div>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>Location</mat-label>
            <input matInput formControlName="location" placeholder="Pune / Remote" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Salary range</mat-label>
            <input matInput formControlName="salaryRange" placeholder="8-14 LPA" />
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput rows="3" formControlName="description"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [ALERT_STYLES],
})
export class JobDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<JobDialog>>(MatDialogRef);
  readonly data = inject<{ job?: Job }>(MAT_DIALOG_DATA);

  readonly departments = signal<{ id: number; name: string }[]>([]);

  readonly form = this.fb.nonNullable.group({
    title: [this.data.job?.title ?? '', [Validators.required, Validators.maxLength(150)]],
    departmentId: [this.data.job?.departmentId ?? null, Validators.required],
    employmentType: [this.data.job?.employmentType ?? 'FULL_TIME', Validators.required],
    status: [this.data.job?.status ?? 'OPEN' as JobStatus, Validators.required],
    closingDate: [this.data.job?.closingDate ?? ''],
    location: [this.data.job?.location ?? ''],
    salaryRange: [this.data.job?.salaryRange ?? ''],
    description: [this.data.job?.description ?? ''],
  });

  constructor() {
    super();
    this.service.departments().subscribe((res) => this.departments.set(res.data));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const request = {
      title: v.title.trim(),
      description: v.description?.trim() || null,
      departmentId: v.departmentId!,
      location: v.location?.trim() || null,
      employmentType: v.employmentType,
      salaryRange: v.salaryRange?.trim() || null,
      status: v.status,
      closingDate: v.closingDate || null,
    };
    const call = this.data.job
      ? this.service.updateJob(this.data.job.id, request)
      : this.service.createJob(request);
    call.subscribe({
      next: (res) => this.dialogRef.close(res.data),
      error: (err) => {
        this.saving.set(false);
        this.setError(err);
      },
    });
  }
}

/** Candidate create dialog. */
@Component({
  selector: 'app-candidate-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Add candidate</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="name" placeholder="Tara Iyer" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" />
        </mat-form-field>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>Phone</mat-label>
            <input matInput formControlName="phone" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Experience (years)</mat-label>
            <input matInput type="number" min="0" step="0.5" formControlName="experienceYears" />
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Skills</mat-label>
          <input matInput formControlName="skills" placeholder="Java, Angular, SQL" />
          <mat-hint>Comma-separated</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Resume path</mat-label>
          <input matInput formControlName="resumePath" placeholder="resumes/tara.pdf" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : 'Add candidate' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [ALERT_STYLES],
})
export class CandidateDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<CandidateDialog>>(MatDialogRef);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    phone: [''],
    experienceYears: [null as number | null],
    skills: [''],
    resumePath: [''],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.service
      .createCandidate({
        name: v.name.trim(),
        email: v.email.trim(),
        phone: v.phone?.trim() || null,
        experienceYears: v.experienceYears,
        skills: v.skills?.trim() || null,
        resumePath: v.resumePath?.trim() || null,
      })
      .subscribe({
        next: (res) => this.dialogRef.close(res.data),
        error: (err) => {
          this.saving.set(false);
          this.setError(err);
        },
      });
  }
}

/** Apply an existing candidate to a job. */
@Component({
  selector: 'app-apply-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Apply candidate</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Job</mat-label>
          <mat-select formControlName="jobId">
            @for (j of openJobs(); track j.id) {
              <mat-option [value]="j.id">{{ j.title }} · {{ j.departmentName }}</mat-option>
            }
          </mat-select>
          <mat-hint>Only OPEN jobs accept applications</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Candidate</mat-label>
          <mat-select formControlName="candidateId">
            @for (c of candidates(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }} · {{ c.email }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Remarks</mat-label>
          <textarea matInput rows="2" formControlName="remarks"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Submitting…' : 'Submit application' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [ALERT_STYLES],
})
export class ApplyDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<ApplyDialog>>(MatDialogRef);
  readonly data = inject<{ jobId?: number }>(MAT_DIALOG_DATA);

  readonly candidates = signal<Candidate[]>([]);
  readonly openJobs = computed(() => this.allJobs().filter((j) => j.status === 'OPEN'));
  private readonly allJobs = signal<Job[]>([]);

  readonly form = this.fb.nonNullable.group({
    jobId: [this.data.jobId ?? null, Validators.required],
    candidateId: [null as number | null, Validators.required],
    remarks: [''],
  });

  constructor() {
    super();
    this.service.candidates().subscribe((res) => this.candidates.set(res.data));
    this.service.jobs().subscribe((res) => this.allJobs.set(res.data));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.service
      .createApplication({
        jobId: v.jobId!,
        candidateId: v.candidateId!,
        remarks: v.remarks?.trim() || null,
      })
      .subscribe({
        next: (res) => this.dialogRef.close(res.data),
        error: (err) => {
          this.saving.set(false);
          this.setError(err);
        },
      });
  }
}

/** Move an application to the chosen next pipeline stage. */
@Component({
  selector: 'app-move-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Move application</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <p class="who">
        <strong>{{ data.application.candidateName }}</strong> · {{ data.application.jobTitle }}
        — currently <span class="stage">{{ data.application.status }}</span>
      </p>
      @if (targets().length === 0) {
        <p class="terminal">This application is in a terminal stage and cannot move.</p>
      } @else {
        <form [formGroup]="form" class="form">
          <mat-form-field appearance="outline">
            <mat-label>Move to</mat-label>
            <mat-select formControlName="status">
              @for (t of targets(); track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Remarks (optional)</mat-label>
            <textarea matInput rows="2" formControlName="remarks"></textarea>
          </mat-form-field>
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      @if (targets().length > 0) {
        <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
          {{ saving() ? 'Moving…' : 'Move' }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    ALERT_STYLES,
    `
      .who { margin: 0 0 12px; font-size: 14px; }
      .stage { font-weight: 600; }
      .terminal { color: rgba(0, 0, 0, 0.6); }
    `,
  ],
})
export class MoveDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<MoveDialog>>(MatDialogRef);
  readonly data = inject<{ application: import('./recruitment.models').JobApplication }>(MAT_DIALOG_DATA);

  readonly targets = signal<ApplicationStatus[]>(NEXT_STAGES[this.data.application.status]);

  readonly form = this.fb.nonNullable.group({
    status: [NEXT_STAGES[this.data.application.status][0] ?? null, Validators.required],
    remarks: [''],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.service
      .transition(this.data.application.id, v.status!, v.remarks?.trim() || null)
      .subscribe({
        next: (res) => this.dialogRef.close(res.data),
        error: (err) => {
          this.saving.set(false);
          this.setError(err);
        },
      });
  }
}

/** Schedule (or reschedule) an interview. */
@Component({
  selector: 'app-interview-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.interview ? 'Reschedule interview' : 'Schedule interview' }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <form [formGroup]="form" class="form">
        @if (!data.interview) {
          <mat-form-field appearance="outline">
            <mat-label>Application</mat-label>
            <mat-select formControlName="applicationId">
              @for (a of eligible(); track a.id) {
                <mat-option [value]="a.id">{{ a.candidateName }} · {{ a.jobTitle }} ({{ a.status }})</mat-option>
              }
            </mat-select>
            <mat-hint>Only SHORTLISTED or INTERVIEW applications</mat-hint>
          </mat-form-field>
        }
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>Date &amp; time</mat-label>
            <input matInput type="datetime-local" formControlName="interviewDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Mode</mat-label>
            <mat-select formControlName="mode">
              <mat-option value="ONLINE">Online</mat-option>
              <mat-option value="ONSITE">Onsite</mat-option>
              <mat-option value="PHONE">Phone</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Interviewer</mat-label>
          <mat-select formControlName="interviewerId">
            <mat-option [value]="null">— unassigned —</mat-option>
            @for (e of interviewers(); track e.id) {
              <mat-option [value]="e.id">{{ e.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : data.interview ? 'Reschedule' : 'Schedule' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [ALERT_STYLES],
})
export class InterviewDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<InterviewDialog>>(MatDialogRef);
  readonly data = inject<{
    interview?: Interview;
    applications?: import('./recruitment.models').JobApplication[];
  }>(MAT_DIALOG_DATA);

  readonly interviewers = signal<import('./recruitment.models').EmployeeOption[]>([]);
  readonly eligible = signal<import('./recruitment.models').JobApplication[]>([]);

  readonly form = this.fb.nonNullable.group({
    applicationId: [null as number | null, Validators.required],
    interviewDate: [BaseDialog.toLocalInput(this.data.interview?.interviewDate ?? null), Validators.required],
    mode: [(this.data.interview?.mode ?? 'ONLINE') as InterviewMode, Validators.required],
    interviewerId: [this.data.interview?.interviewerId ?? null] as [number | null],
  });

  constructor() {
    super();
    this.service.employeeOptions().subscribe((res) => this.interviewers.set(res.data));
    if (!this.data.interview) {
      this.service
        .applications()
        .subscribe((res) =>
          this.eligible.set(res.data.filter((a) => a.status === 'SHORTLISTED' || a.status === 'INTERVIEW')),
        );
    }
  }

  save(): void {
    if (this.form.invalid || !this.form.getRawValue().interviewDate) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const iso = new Date(v.interviewDate).toISOString();
    if (this.data.interview) {
      this.service
        .rescheduleInterview(this.data.interview.id, {
          interviewDate: iso,
          mode: v.mode,
          interviewerId: v.interviewerId,
        })
        .subscribe({
          next: (res) => this.dialogRef.close(res.data),
          error: (err) => {
            this.saving.set(false);
            this.setError(err);
          },
        });
    } else {
      if (!v.applicationId) {
        this.saving.set(false);
        return;
      }
      this.service
        .createInterview({
          applicationId: v.applicationId,
          interviewerId: v.interviewerId,
          interviewDate: iso,
          mode: v.mode,
        })
        .subscribe({
          next: (res) => this.dialogRef.close(res.data),
          error: (err) => {
            this.saving.set(false);
            this.setError(err);
          },
        });
    }
  }
}

/** Record feedback + result when completing an interview. */
@Component({
  selector: 'app-feedback-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Complete interview</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <p class="who"><strong>{{ data.interview.candidateName }}</strong> · {{ data.interview.jobTitle }}</p>
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Result</mat-label>
          <mat-select formControlName="result">
            <mat-option value="PASS">Pass</mat-option>
            <mat-option value="FAIL">Fail</mat-option>
            <mat-option value="ON_HOLD">On hold</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Feedback</mat-label>
          <textarea matInput rows="3" formControlName="feedback"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : 'Complete' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    ALERT_STYLES,
    `
      .who { margin: 0 0 12px; font-size: 14px; }
    `,
  ],
})
export class FeedbackDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecruitmentService);
  readonly dialogRef = inject<MatDialogRef<FeedbackDialog>>(MatDialogRef);
  readonly data = inject<{ interview: Interview }>(MAT_DIALOG_DATA);

  readonly form = this.fb.nonNullable.group({
    result: ['PASS' as InterviewResult, Validators.required],
    feedback: [''],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.service
      .completeInterview(this.data.interview.id, {
        result: v.result,
        feedback: v.feedback?.trim() || null,
      })
      .subscribe({
        next: (res) => this.dialogRef.close(res.data),
        error: (err) => {
          this.saving.set(false);
          this.setError(err);
        },
      });
  }
}

/** Generic confirm (delete job/candidate, cancel interview). */
@Component({
  selector: 'app-recruitment-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <div class="warn-box">
        <mat-icon class="warn-icon">warning_amber</mat-icon>
        <div>
          <p>{{ data.message }}</p>
          @if (data.warning) {
            <p class="warning">{{ data.warning }}</p>
          }
        </div>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">{{ data.confirmLabel ?? 'Confirm' }}</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .warn-box { display: flex; gap: 12px; align-items: flex-start; max-width: 380px; }
      .warn-icon { color: #ef6c00; }
      p { margin: 0 0 6px; }
      .warning { font-size: 13px; color: rgba(0, 0, 0, 0.6); }
    `,
  ],
})
export class RecruitmentConfirmDialog {
  readonly data = inject<{
    title: string;
    message: string;
    warning?: string;
    confirmLabel?: string;
  }>(MAT_DIALOG_DATA);
}
