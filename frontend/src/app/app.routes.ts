import { Routes } from '@angular/router';

import { LayoutComponent } from './layout/layout.component';
import { authGuard, guestGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'dashboard',
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'employees',
        loadComponent: () =>
          import('./employees/employees.component').then((m) => m.EmployeesComponent),
      },
      {
        path: 'departments',
        loadComponent: () =>
          import('./departments/departments.component').then((m) => m.DepartmentsComponent),
      },
      {
        path: 'recruitment',
        loadComponent: () =>
          import('./recruitment/recruitment.component').then((m) => m.RecruitmentComponent),
      },
      {
        path: 'onboarding',
        loadComponent: () =>
          import('./onboarding/onboarding.component').then((m) => m.OnboardingComponent),
      },
      {
        path: 'attendance',
        loadComponent: () =>
          import('./attendance/attendance.component').then((m) => m.AttendanceComponent),
      },
      {
        path: 'leave',
        loadComponent: () =>
          import('./leave/leave.component').then((m) => m.LeaveComponent),
      },
      {
        path: 'payroll',
        loadComponent: () =>
          import('./payroll/payroll.component').then((m) => m.PayrollComponent),
      },
      {
        path: 'performance',
        loadComponent: () =>
          import('./performance/performance.component').then((m) => m.PerformanceComponent),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./documents/documents.component').then((m) => m.DocumentsComponent),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./notifications/notifications.component').then((m) => m.NotificationsComponent),
      },
      {
        path: 'analytics',
        loadComponent: () =>
          import('./analytics/analytics.component').then((m) => m.AnalyticsComponent),
      },
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
];
