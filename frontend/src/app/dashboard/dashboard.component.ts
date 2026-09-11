import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { HealthService } from './health.service';
import { HealthInfo } from '../core/api.models';

interface PlaceholderCard {
  icon: string;
  title: string;
  description: string;
  phase: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly healthService = inject(HealthService);

  readonly health = signal<HealthInfo | null>(null);
  readonly checking = signal(false);
  /** Set when the health probe fails; toasts are the error interceptor's job. */
  readonly loadError = signal(false);

  readonly upcoming: PlaceholderCard[] = [
    { icon: 'login', title: 'Authentication', description: 'JWT login, roles and guards', phase: 1 },
    { icon: 'insights', title: 'HR Dashboard', description: 'Live KPIs, charts and approvals', phase: 2 },
    { icon: 'people', title: 'Employee Management', description: 'Full CRUD with search and filters', phase: 3 },
    { icon: 'work', title: 'Recruitment', description: 'Jobs, candidates, interviews', phase: 5 },
  ];

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.checking.set(true);
    this.loadError.set(false);
    this.healthService.getHealth().subscribe({
      next: (res) => {
        this.health.set(res.data);
        this.checking.set(false);
      },
      error: () => {
        this.health.set(null);
        this.checking.set(false);
        this.loadError.set(true);
      },
    });
  }
}
