import { Injectable, inject } from '@angular/core';

import { ApiService } from '../core/api.service';
import { HealthInfo } from '../core/api.models';
import { DashboardStats } from './dashboard.models';

/** Data access for the admin dashboard (Phase 2). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiService);

  getStats() {
    return this.api.get<DashboardStats>('/dashboard/stats');
  }

  getHealth() {
    return this.api.get<HealthInfo>('/health');
  }
}
