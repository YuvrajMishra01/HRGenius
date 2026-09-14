import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AiService, CandidateMatch, JobMatchResponse } from './ai.service';

/**
 * AI insights for one job (Phase 16): required skills from the job text and
 * a ranked candidate list with matched/missing skill chips. Deterministic,
 * explainable scoring — no black box.
 */
@Component({
  selector: 'app-job-match-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon inline>auto_awesome</mat-icon>
      Candidate matches — {{ data?.jobTitle }}
    </h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="state"><mat-spinner diameter="28" /> Loading matches…</div>
      } @else if (error(); as err) {
        <div class="state error">{{ err }}</div>
      } @else if (result(); as r) {
        @if (r.requiredSkills.length) {
          <div class="required">
            <span class="label">Required skills (from job text):</span>
            <mat-chip-set>
              @for (s of r.requiredSkills; track s) {
                <mat-chip>{{ s }}</mat-chip>
              }
            </mat-chip-set>
          </div>
        }
        @if (r.matches.length === 0) {
          <p class="state">No candidates with extractable skills yet.</p>
        }
        <div class="match" *ngFor="let m of r.matches">
          <div class="head">
            <strong>{{ m.candidateName }}</strong>
            <span class="score" [class.good]="m.score >= 60" [class.mid]="m.score >= 30 && m.score < 60">
              {{ m.score }}%
            </span>
          </div>
          <mat-progress-bar mode="determinate" [value]="m.score" />
          <div class="skills">
            @if (m.matchedSkills.length) {
              <mat-chip-set aria-label="matched skills">
                @for (s of m.matchedSkills; track s) {
                  <mat-chip class="ok">
                    <mat-icon>check</mat-icon> {{ s }}
                  </mat-chip>
                }
              </mat-chip-set>
            }
            @if (m.missingSkills.length) {
              <mat-chip-set aria-label="missing skills">
                @for (s of m.missingSkills; track s) {
                  <mat-chip class="miss">
                    <mat-icon>close</mat-icon> {{ s }}
                  </mat-chip>
                }
              </mat-chip-set>
            }
          </div>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .state { display: flex; gap: 12px; align-items: center; color: rgba(0, 0, 0, 0.6); }
      .state.error { color: #b3261e; }
      .required { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
      .required .label { font-size: 13px; color: rgba(0, 0, 0, 0.6); }
      .match { padding: 10px 0; border-top: 1px solid rgba(0, 0, 0, 0.08); }
      .match .head { display: flex; justify-content: space-between; margin-bottom: 4px; }
      .score { font-weight: 600; }
      .score.good { color: #1b5e20; }
      .score.mid { color: #b45309; }
      .skills { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; }
      mat-chip.ok mat-icon { color: #1b5e20; }
      mat-chip.miss mat-icon { color: #b3261e; }
    `,
  ],
})
export class JobMatchDialog {
  readonly data: { jobId: number; jobTitle: string } | null = inject(MAT_DIALOG_DATA, { optional: true });
  private readonly dialogRef = inject(MatDialogRef<JobMatchDialog>);
  private readonly api = inject(AiService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly result = signal<JobMatchResponse | null>(null);

  constructor() {
    if (this.data?.jobId) {
      this.api.jobMatches(this.data.jobId).subscribe({
        next: (res) => {
          this.result.set(res.data);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not load matches.');
          this.loading.set(false);
        },
      });
    }
  }
}
