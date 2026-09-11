import { Injectable, inject } from '@angular/core';

import { ApiService } from '../core/api.service';
import { HealthInfo } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly api = inject(ApiService);

  getHealth() {
    return this.api.get<HealthInfo>('/health');
  }
}
