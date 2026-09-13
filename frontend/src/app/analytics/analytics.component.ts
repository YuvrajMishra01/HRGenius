import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { BarChartComponent } from '../shared/bar-chart.component';
import { AnalyticsData, AnalyticsService } from './analytics.service';

interface Kpi {
  label: string;
  value: string;
  hint?: string;
  icon: string;
}

/** Analytics page (Phase 13): deeper, chart-shaped views of real data. */
@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatIconModule, MatTooltipModule, BarChartComponent],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss',
})
export class AnalyticsComponent implements OnInit {
  private readonly api = inject(AnalyticsService);

  readonly data = signal<AnalyticsData | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly forbidden = signal(false);

  readonly workforceBars = signal<{ label: string; value: number }[]>([]);
  readonly typeBars = signal<{ label: string; value: number }[]>([]);
  readonly tenureBars = signal<{ label: string; value: number }[]>([]);
  readonly funnelBars = signal<{ label: string; value: number }[]>([]);
  readonly leaveBars = signal<{ label: string; value: number }[]>([]);
  readonly ratingBars = signal<{ label: string; value: number }[]>([]);
  readonly payrollBars = signal<{ label: string; value: number }[]>([]);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.forbidden.set(false);

    this.api.loadAll().subscribe({
      next: (data) => {
        this.data.set(data);
        this.buildCharts(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err?.status === 403) {
          this.forbidden.set(true);
        } else if (err?.status !== 401) {
          this.loadError.set(true);
        }
      },
    });
  }

  /** Money axis: totalNet per period, labels "Aug 2026". */
  private buildCharts(data: AnalyticsData): void {
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    this.workforceBars.set(data.workforce.byDepartment.map((d) => ({ label: d.name, value: d.count })));
    this.typeBars.set(data.workforce.byEmploymentType.map((t) => ({ label: titleCase(t.type), value: t.count })));
    this.tenureBars.set(data.workforce.byTenureBucket.map((b) => ({ label: b.label, value: b.count })));
    this.funnelBars.set(
      data.funnel.perJob.map((j) => ({
        label: `${j.title} — ${j.applications} applied`,
        value: j.applications,
      })),
    );
    this.leaveBars.set(data.leave.byType.map((l) => ({ label: titleCase(l.name), value: l.count })));
    this.ratingBars.set(
      [1, 2, 3, 4, 5].map((rating) => {
        const row = data.performance.byRating.find((r) => r.rating === rating);
        return { label: `${rating} ★`, value: row?.count ?? 0 };
      }),
    );
    this.payrollBars.set(
      data.payrollTrend.map((p) => ({
        label: `${monthNames[p.month - 1]} ${p.year}`,
        value: Number(p.totalNet),
      })),
    );
  }

  /** Funnel table rows are labelled per job with stage counts. */
  funnelStage(job: { active: number; selected: number; rejected: number }): string {
    return `${job.active} active · ${job.selected} selected · ${job.rejected} rejected`;
  }

  /** Template-visible label helper (OPEN → Open). */
  statusLabel(value: string): string {
    return titleCase(value);
  }
}

function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}
