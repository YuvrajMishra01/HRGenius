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
      // Phase 5+ adds recruitment, onboarding, ... routes here.
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
];
