import { Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { ThemeService } from '../core/theme.service';
import { AuthService } from '../core/auth.service';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

interface RoleCard {
  role: string;
  icon: string;
  title: string;
  description: string;
  tone: string;
}

interface Kpi {
  label: string;
  value: number;
  delta: string;
  tone: 'success' | 'info' | 'warning' | 'primary';
}

/**
 * Public marketing page. Fully static/demo content — the authenticated app
 * lives under /app and always uses real API data (see DATA RULE).
 */
@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatButtonModule, DecimalPipe],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent {
  private readonly router = inject(Router);
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);

  readonly year = new Date().getFullYear();

  readonly features: Feature[] = [
    { icon: 'people', title: 'Employee Management', description: 'Centralized employee profiles and organizational information.' },
    { icon: 'work', title: 'Recruitment', description: 'Manage jobs, candidates, applications and interviews.' },
    { icon: 'badge', title: 'Onboarding', description: 'Track onboarding progress and employee documents.' },
    { icon: 'fact_check', title: 'Attendance', description: 'Monitor attendance and workforce presence.' },
    { icon: 'event_busy', title: 'Leave Management', description: 'Request, approve and track employee leave.' },
    { icon: 'payments', title: 'Payroll', description: 'Manage salary information and payroll processing.' },
    { icon: 'trending_up', title: 'Performance', description: 'Track reviews, goals and performance records.' },
    { icon: 'folder_shared', title: 'Documents', description: 'Securely organize employee documents.' },
    { icon: 'insights', title: 'Analytics', description: 'Understand workforce trends and HR metrics.' },
  ];

  readonly roles: RoleCard[] = [
    {
      role: 'ADMIN',
      icon: 'admin_panel_settings',
      title: 'Admin',
      description: 'System administration, users, configuration and complete access.',
      tone: 'primary',
    },
    {
      role: 'HR',
      icon: 'manage_accounts',
      title: 'HR',
      description: 'Employees, recruitment, onboarding, payroll, leave and HR operations.',
      tone: 'violet',
    },
    {
      role: 'MANAGER',
      icon: 'supervisor_account',
      title: 'Manager',
      description: 'Team attendance, leave, performance and employee information according to permissions.',
      tone: 'info',
    },
    {
      role: 'EMPLOYEE',
      icon: 'person',
      title: 'Employee',
      description: 'Personal information, attendance, leave, documents and performance information.',
      tone: 'success',
    },
  ];

  readonly kpis: Kpi[] = [
    { label: 'Employees', value: 1248, delta: '+3.2%', tone: 'primary' },
    { label: 'Present Today', value: 1086, delta: '94.2%', tone: 'success' },
    { label: 'Pending Leave', value: 24, delta: 'needs review', tone: 'warning' },
    { label: 'Payroll (net)', value: 18.4, delta: '₹ lakh · Sep', tone: 'info' },
  ];

  readonly pipeline = [
    { label: 'Applied', value: 86 },
    { label: 'Screening', value: 42 },
    { label: 'Interview', value: 18 },
    { label: 'Offer', value: 7 },
  ];

  readonly activities = [
    { icon: 'person_add', title: 'Employee added', detail: 'Rahul Sharma · Engineering', time: '5 min ago' },
    { icon: 'event_available', title: 'Leave approved', detail: 'Priya Singh · Casual leave', time: '20 min ago' },
    { icon: 'handshake', title: 'Interview scheduled', detail: 'Backend Developer · Kavya Rao', time: '1 hr ago' },
    { icon: 'receipt_long', title: 'Payslip generated', detail: 'September payroll run', time: '3 hrs ago' },
  ];

  readonly interviews = [
    { name: 'Kavya Rao', role: 'Backend Developer', time: '10:30 AM', mode: 'Online' },
    { name: 'Arjun Mehta', role: 'Product Designer', time: '02:00 PM', mode: 'Onsite' },
  ];

  readonly stats = [
    { value: '1,248+', label: 'Employees managed' },
    { value: '12', label: 'HR modules' },
    { value: '4', label: 'Role levels' },
    { value: '99.9%', label: 'Secure workflows' },
  ];

  readonly security = [
    { label: 'JWT authentication', desc: 'Signed tokens guard every API request.' },
    { label: 'Role-based authorization', desc: 'ADMIN, HR, MANAGER and EMPLOYEE scopes enforced server-side.' },
    { label: 'Protected APIs', desc: 'All business endpoints reject unauthenticated calls.' },
    { label: 'Audit logging', desc: 'Every state change is attributable and searchable.' },
    { label: 'Secure password handling', desc: 'Credentials are hashed, never stored in plain text.' },
    { label: 'Session & logout protection', desc: 'Logging out invalidates tokens immediately.' },
  ];

  scrollTop(): void {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  openApp(): void {
    if (this.auth.isAuthenticated()) {
      this.router.navigateByUrl('/app/dashboard');
    } else {
      this.router.navigateByUrl('/login');
    }
  }
}
