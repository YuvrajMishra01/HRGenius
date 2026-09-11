import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard.component';
import { HealthService } from './health.service';
import { ApiResponse, HealthInfo } from '../core/api.models';

const UP_HEALTH: HealthInfo = {
  application: 'HRGenius API',
  status: 'UP',
  database: 'UP (jdbc:h2:mem:hrgenius)',
  timestamp: '2026-09-11T00:00:00Z',
};

function healthResponse(): ApiResponse<HealthInfo> {
  return { success: true, message: 'OK', data: UP_HEALTH };
}

describe('DashboardComponent', () => {
  function setup(health$: Observable<ApiResponse<HealthInfo>>) {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: HealthService, useValue: { getHealth: () => health$ } }],
    });
    return TestBed.createComponent(DashboardComponent);
  }

  it('renders live health data when the backend responds', () => {
    const fixture = setup(of(healthResponse()));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('HRGenius API');
    expect(el.textContent).toContain('UP');
    expect(el.textContent).not.toContain('did not respond');
  });

  it('renders an inline error state when the backend is unreachable', () => {
    const failing = throwError(() => new Error('down')) as unknown as Observable<
      ApiResponse<HealthInfo>
    >;
    const fixture = setup(failing);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('did not respond');
    expect(el.textContent).not.toContain('HRGenius API');
  });
});
