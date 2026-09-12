import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';

import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { DashboardService } from './dashboard.service';
import { HealthInfo } from '../core/api.models';
import { DashboardStats } from './dashboard.models';
import { Bar, BarChartComponent } from '../shared/bar-chart.component';

interface KpiCard {
  icon: string;
  label: string;
  value: number;
  accent: string;
}

/**
 * Admin dashboard (Phase 2): one /dashboard/stats round trip feeds the
 * KPI cards, charts and queues. The health probe from Phase 0 stays as
 * a compact system strip. Forbidden roles (MANAGER/EMPLOYEE) get an
 * inline explanation instead of a broken page.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTooltipModule,
    BarChartComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly stats = signal<DashboardStats | null>(null);
  readonly health = signal<HealthInfo | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly forbidden = signal(false);

  readonly deptBars = computed<Bar[]>(() =>
    (this.stats()?.departmentDistribution ?? []).map((d) => ({ label: d.name, value: d.count })),
  );

  readonly pipelineBars = computed<Bar[]>(() =>
    (this.stats()?.hiringPipeline ?? []).map((p) => ({ label: titleCase(p.status), value: p.count })),
  );

  readonly pipelineTotal = computed(() =>
    (this.stats()?.hiringPipeline ?? []).reduce((sum, p) => sum + p.count, 0),
  );

  readonly leaveBars = computed<Bar[]>(() =>
    (this.stats()?.leaveSummary ?? []).map((l) => ({ label: titleCase(l.status), value: l.count })),
  );

  readonly trendBars = computed<Bar[]>(() =>
    (this.stats()?.hiringTrend ?? []).map((t) => ({ label: `${yearMonth(t.year, t.month)}`, value: t.count })),
  );

  readonly kpiCards = computed<KpiCard[]>(() => {
    const k = this.stats()?.kpis;
    if (!k) {
      return [];
    }
    return [
      { icon: 'people', label: 'Total Employees', value: k.totalEmployees, accent: '#3f51b5' },
      { icon: 'verified', label: 'Active Employees', value: k.activeEmployees, accent: '#2e7d32' },
      { icon: 'fiber_new', label: 'New Hires (30d)', value: k.newHiresLast30Days, accent: '#00838f' },
      { icon: 'work', label: 'Open Positions', value: k.openPositions, accent: '#ef6c00' },
      { icon: 'person_search', label: 'Candidates', value: k.totalCandidates, accent: '#6a1b9a' },
      { icon: 'event_busy', label: 'Pending Leaves', value: k.pendingLeaveRequests, accent: '#c62828' },
    ];
  });

  readonly attendance = computed(() => this.stats()?.attendanceToday ?? null);

  readonly attendancePercent = computed(() => this.stats()?.attendanceToday?.attendancePercent ?? 0);

  /** Template helper — month number to display name. */
  monthName(month: number): string {
    return monthName(month);
  }

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.forbidden.set(false);

    this.dashboardService.getStats().subscribe({
      next: (res) => {
        this.stats.set(res.data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err?.status === 403) {
          this.forbidden.set(true);
        } else if (err?.status !== 401) {
          // 401 is handled globally (interceptor); other failures show inline state.
          this.loadError.set(true);
        }
      },
    });

    this.dashboardService.getHealth().subscribe({
      next: (res) => this.health.set(res.data),
      error: () => this.health.set(null),
    });
  }
}

function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

function yearMonth(year: number, month: number): string {
  const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${names[Math.max(1, Math.min(12, month)) - 1]} ${year}`;
}

function monthName(month: number): string {
  const names = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];
  return names[Math.max(1, Math.min(12, month)) - 1];
}
