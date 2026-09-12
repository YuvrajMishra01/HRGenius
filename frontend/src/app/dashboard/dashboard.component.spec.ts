import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';
import { ApiResponse, HealthInfo } from '../core/api.models';
import { DashboardStats } from './dashboard.models';

const HEALTH: HealthInfo = {
  application: 'HRGenius API',
  status: 'UP',
  database: 'UP (jdbc:h2:mem:hrgenius)',
  timestamp: '2026-09-11T00:00:00Z',
};

const STATS: DashboardStats = {
  kpis: {
    totalEmployees: 7,
    activeEmployees: 7,
    newHiresLast30Days: 1,
    openPositions: 2,
    totalCandidates: 4,
    pendingLeaveRequests: 2,
  },
  departmentDistribution: [
    { name: 'Engineering', count: 4 },
    { name: 'Finance', count: 1 },
  ],
  hiringTrend: [{ year: 2026, month: 9, count: 1 }],
  hiringPipeline: [{ status: 'APPLIED', count: 2 }],
  attendanceToday: {
    day: '2026-09-11',
    present: 5,
    absent: 0,
    halfDay: 1,
    onLeave: 1,
    holiday: 0,
    totalRecords: 7,
    attendancePercent: 78.6,
  },
  leaveSummary: [{ status: 'PENDING', count: 2 }],
  payrollThisMonth: { year: 2026, month: 9, payslips: 6, totalNet: 350000 },
  recentHires: [
    {
      id: 7,
      employeeCode: 'EMP007',
      fullName: 'Rohan Kulkarni',
      department: 'Engineering',
      designation: 'Software Engineer',
      joiningDate: '2026-09-01',
      status: 'ACTIVE',
    },
  ],
  pendingApprovals: [
    {
      id: 2,
      employeeName: 'Anita Desai',
      leaveType: 'CASUAL_LEAVE',
      startDate: '2026-09-16',
      endDate: '2026-09-18',
      status: 'PENDING',
    },
  ],
  upcomingInterviews: [
    {
      id: 1,
      candidateName: 'Kavya Rao',
      jobTitle: 'Backend Developer',
      interviewDate: '2026-09-13T10:00:00+05:30',
      mode: 'ONLINE',
    },
  ],
};

function statsResponse(): ApiResponse<DashboardStats> {
  return { success: true, message: 'OK', data: STATS };
}

function healthResponse(): ApiResponse<HealthInfo> {
  return { success: true, message: 'OK', data: HEALTH };
}

describe('DashboardComponent', () => {
  function setup(
    stats$: Observable<ApiResponse<DashboardStats>>,
    health$: Observable<ApiResponse<HealthInfo>>,
  ) {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        {
          provide: DashboardService,
          useValue: { getStats: () => stats$, getHealth: () => health$ },
        },
      ],
    });
    return TestBed.createComponent(DashboardComponent);
  }

  it('renders live KPI values from the stats API', () => {
    const fixture = setup(of(statsResponse()), of(healthResponse()));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Total Employees');
    expect(el.textContent).toContain('Rohan Kulkarni');
    expect(el.textContent).toContain('Kavya Rao');
    expect(el.textContent).toContain('Anita Desai');
    expect(el.textContent).not.toContain("didn't load");
  });

  it('renders charts with department labels', () => {
    const fixture = setup(of(statsResponse()), of(healthResponse()));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Department distribution');
    expect(el.textContent).toContain('Engineering');
    expect(el.textContent).toContain('Hiring pipeline');
  });

  it('shows the restricted state on 403', () => {
    const forbidden = throwError(() => ({ status: 403 })) as unknown as Observable<
      ApiResponse<DashboardStats>
    >;
    const fixture = setup(forbidden, of(healthResponse()));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Dashboard is restricted');
    expect(el.textContent).not.toContain('Total Employees');
  });

  it('shows the error state when the backend is unreachable', () => {
    const failing = throwError(() => ({ status: 0 })) as unknown as Observable<
      ApiResponse<DashboardStats>
    >;
    const fixture = setup(failing, throwError(() => new Error('down')));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain("Dashboard didn't load");
  });
});
