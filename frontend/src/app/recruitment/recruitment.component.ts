import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';

import { AuthService } from '../core/auth.service';
import {
  ApplicationStatus,
  Candidate,
  Interview,
  Job,
  JobApplication,
} from './recruitment.models';
import { RecruitmentService } from './recruitment.service';
import {
  ApplyDialog,
  CandidateDialog,
  FeedbackDialog,
  InterviewDialog,
  JobDialog,
  MoveDialog,
  RecruitmentConfirmDialog,
} from './recruitment.dialogs';

const STAGE_CLASS: Record<string, string> = {
  APPLIED: 'stage-applied',
  SCREENING: 'stage-screening',
  SHORTLISTED: 'stage-shortlisted',
  INTERVIEW: 'stage-interview',
  SELECTED: 'stage-selected',
  REJECTED: 'stage-rejected',
  DRAFT: 'stage-draft',
  OPEN: 'stage-open',
  CLOSED: 'stage-closed',
  NEW: 'stage-applied',
  INTERVIEWED: 'stage-interview',
  SCHEDULED: 'stage-screening',
  COMPLETED: 'stage-selected',
  CANCELLED: 'stage-rejected',
  PASS: 'stage-selected',
  FAIL: 'stage-rejected',
  ON_HOLD: 'stage-draft',
};

@Component({
  selector: 'app-recruitment',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
    MatChipsModule,
    DatePipe,
  ],
  templateUrl: './recruitment.component.html',
  styleUrl: './recruitment.component.scss',
})
export class RecruitmentComponent implements OnInit {
  private readonly service = inject(RecruitmentService);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AuthService);

  readonly canWrite = computed(
    () => this.auth.user()?.role === 'ADMIN' || this.auth.user()?.role === 'HR',
  );

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  /** Preserved across reloads so dialog actions don't bounce the user to the first tab. */
  readonly selectedTab = signal(0);

  readonly jobs = signal<Job[]>([]);
  readonly candidates = signal<Candidate[]>([]);
  readonly applications = signal<JobApplication[]>([]);
  readonly interviews = signal<Interview[]>([]);

  readonly stageClass = (stage: string | null | undefined): string =>
    stage ? (STAGE_CLASS[stage] ?? 'stage-applied') : 'stage-applied';

  readonly pipelineCounts = computed(() => {
    const counts = new Map<string, number>();
    for (const a of this.applications()) {
      counts.set(a.status, (counts.get(a.status) ?? 0) + 1);
    }
    return counts;
  });

  readonly scheduledInterviews = computed(() =>
    this.interviews().filter((i) => i.status === 'SCHEDULED'),
  );

  readonly openJobCount = computed(() => this.jobs().filter((j) => j.status === 'OPEN').length);

  readonly displayedColumns: string[] = [];

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set(null);
    forkJoin({
      jobs: this.service.jobs().pipe(map((r) => r.data)),
      candidates: this.service.candidates().pipe(map((r) => r.data)),
      applications: this.service.applications().pipe(map((r) => r.data)),
      interviews: this.service.interviews().pipe(map((r) => r.data)),
    }).subscribe({
      next: (data) => {
        this.jobs.set(data.jobs);
        this.candidates.set(data.candidates);
        this.applications.set(data.applications);
        this.interviews.set(data.interviews);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(
          (err as { error?: { message?: string } })?.error?.message ?? 'Failed to load recruitment data.',
        );
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------- actions

  postJob(): void {
    this.dialog
      .open(JobDialog, { width: '560px' })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  editJob(job: Job): void {
    this.dialog
      .open(JobDialog, { width: '560px', data: { job } })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  deleteJob(job: Job): void {
    const warning =
      job.applicationCount > 0
        ? `This job has ${job.applicationCount} application(s) — the server will reject the delete. Close it instead.`
        : 'This will permanently remove the job posting.';
    const ref = this.dialog.open(RecruitmentConfirmDialog, {
      width: '420px',
      data: { title: 'Delete job', message: `Delete "${job.title}"?`, warning },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.service.deleteJob(job.id).subscribe({
        next: () => this.reload(),
        error: (err) => this.dialog.open(RecruitmentConfirmDialog, {
          width: '420px',
          data: {
            title: 'Cannot delete job',
            message: (err as { error?: { message?: string } })?.error?.message ?? 'Delete failed.',
            confirmLabel: 'OK',
          },
        }),
      });
    });
  }

  addCandidate(): void {
    this.dialog
      .open(CandidateDialog, { width: '480px' })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  deleteCandidate(candidate: Candidate): void {
    const warning =
      candidate.applicationCount > 0
        ? `This candidate has ${candidate.applicationCount} application(s) — the server will reject the delete.`
        : 'This will permanently remove the candidate.';
    const ref = this.dialog.open(RecruitmentConfirmDialog, {
      width: '420px',
      data: { title: 'Delete candidate', message: `Delete "${candidate.name}"?`, warning },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.service.deleteCandidate(candidate.id).subscribe({
        next: () => this.reload(),
        error: (err) => this.dialog.open(RecruitmentConfirmDialog, {
          width: '420px',
          data: {
            title: 'Cannot delete candidate',
            message: (err as { error?: { message?: string } })?.error?.message ?? 'Delete failed.',
            confirmLabel: 'OK',
          },
        }),
      });
    });
  }

  apply(): void {
    this.dialog
      .open(ApplyDialog, { width: '480px' })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  move(application: JobApplication): void {
    this.dialog
      .open(MoveDialog, { width: '440px', data: { application } })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  scheduleInterview(): void {
    this.dialog
      .open(InterviewDialog, { width: '520px', data: { applications: this.applications() } })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  reschedule(interview: Interview): void {
    this.dialog
      .open(InterviewDialog, { width: '520px', data: { interview } })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  complete(interview: Interview): void {
    this.dialog
      .open(FeedbackDialog, { width: '440px', data: { interview } })
      .afterClosed()
      .subscribe((saved) => saved && this.reload());
  }

  cancel(interview: Interview): void {
    const ref = this.dialog.open(RecruitmentConfirmDialog, {
      width: '420px',
      data: {
        title: 'Cancel interview',
        message: `Cancel the ${interview.mode} interview with ${interview.candidateName}?`,
        confirmLabel: 'Cancel interview',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.service.cancelInterview(interview.id).subscribe(() => this.reload());
    });
  }
}
