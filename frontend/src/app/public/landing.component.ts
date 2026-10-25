import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

import { ThemeService } from '../core/theme.service';
import { AuthService } from '../core/auth.service';
import { BrandMarkComponent } from '../shared/brand-mark.component';

interface PreviewKpi {
  label: string;
  value: string;
  delta: string;
  tone: 'success' | 'info' | 'warning' | 'primary';
}

interface BentoCard {
  icon: string;
  title: string;
  description: string;
  /** Which mock visual the template renders inside the card. */
  visual: 'employees' | 'pipeline' | 'attendance' | 'leave' | 'payroll' | 'performance' | 'chart' | 'documents';
  wide?: boolean;
}

interface SecurityCard {
  icon: string;
  title: string;
  description: string;
}

interface RoleCard {
  role: string;
  icon: string;
  description: string;
  tone: 'primary' | 'violet' | 'info' | 'success';
}

interface ReportCard {
  icon: string;
  title: string;
  detail: string;
}

interface Step {
  number: string;
  title: string;
  description: string;
}

interface AiSkill {
  name: string;
  matched: boolean;
}

/**
 * Public marketing page — fully static/demo content. No API calls are made
 * from this page; the authenticated app under /app always uses real data
 * (see the DATA RULE in the project README).
 *
 * Styles live in the global src/landing.scss (registered in angular.json),
 * namespaced under `.landing-scope` so they never leak into the app shell.
 */
@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink, MatIconModule, BrandMarkComponent],
  templateUrl: './landing.component.html',
})
export class LandingComponent {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);

  readonly year = new Date().getFullYear();

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly menuButton = viewChild<ElementRef<HTMLButtonElement>>('menuButton');

  /** Mobile menu state; Escape closes and focus returns to the trigger. */
  readonly menuOpen = signal(false);

  // ------------------------------------------------------------------ preview
  readonly previewKpis: PreviewKpi[] = [
    { label: 'Total Employees', value: '248', delta: '+12 this month', tone: 'primary' },
    { label: 'Open Positions', value: '12', delta: '4 jobs closing', tone: 'info' },
    { label: 'Pending Leave', value: '8', delta: 'needs review', tone: 'warning' },
    { label: 'Payroll This Month', value: '₹24.8L', delta: 'processed', tone: 'success' },
  ];

  readonly growth = [34, 42, 48, 55, 63, 66, 74, 81, 90];

  readonly previewPipeline = [
    { label: 'Applied', value: 64 },
    { label: 'Screening', value: 31 },
    { label: 'Interview', value: 14 },
    { label: 'Selected', value: 5 },
  ];

  readonly previewAttendance = [
    { label: 'Present', value: 212, tone: 'success' },
    { label: 'Late', value: 14, tone: 'warning' },
    { label: 'Leave', value: 9, tone: 'info' },
    { label: 'Absent', value: 6, tone: 'danger' },
  ];

  readonly previewActivity = [
    { icon: 'person_add', title: 'Meera Nair moved to Active', time: '12m' },
    { icon: 'event_available', title: 'Leave approved · Priya Singh', time: '48m' },
    { icon: 'handshake', title: 'Interview · Backend Developer', time: '2h' },
    { icon: 'receipt_long', title: 'September payroll processed', time: '5h' },
  ];

  readonly previewTeam = ['AR', 'PS', 'KN', 'MB', '+5'];

  // -------------------------------------------------------------------- bento
  readonly bento: BentoCard[] = [
    {
      icon: 'people',
      title: 'Employee Management',
      description:
        'Keep employee profiles, departments, designations and organizational relationships in one structured system.',
      visual: 'employees',
      wide: true,
    },
    {
      icon: 'work',
      title: 'Recruitment',
      description: 'Manage jobs, candidates, applications and interviews from one recruitment pipeline.',
      visual: 'pipeline',
    },
    {
      icon: 'fact_check',
      title: 'Attendance',
      description: 'Daily presence records with check-in, half-day and holiday handling.',
      visual: 'attendance',
    },
    {
      icon: 'event_busy',
      title: 'Leave Management',
      description: 'Requests, approvals, balances and yearly leave calendars.',
      visual: 'leave',
    },
    {
      icon: 'payments',
      title: 'Payroll',
      description: 'Salary structure, allowances, deductions and monthly payslips.',
      visual: 'payroll',
    },
    {
      icon: 'trending_up',
      title: 'Performance',
      description: 'Reviews, goals and feedback with a clear lifecycle.',
      visual: 'performance',
    },
    {
      icon: 'insights',
      title: 'Analytics',
      description: 'Workforce trends across hiring, attendance, leave and payroll.',
      visual: 'chart',
    },
    {
      icon: 'folder_shared',
      title: 'Documents',
      description: 'A per-employee vault for resumes, IDs, offers and certificates.',
      visual: 'documents',
    },
  ];

  readonly docChips = ['Resume', 'ID Proof', 'Offer Letter', 'Certificate'];

  // ----------------------------------------------------------------- lifecycle
  readonly lifecycleStages = [
    { label: 'Recruitment', icon: 'work' },
    { label: 'Selection', icon: 'fact_check' },
    { label: 'Employee', icon: 'badge' },
    { label: 'Onboarding', icon: 'checklist' },
    { label: 'Active Employee', icon: 'person_pin_circle' },
  ];

  readonly opsTiles = [
    { icon: 'fact_check', title: 'Attendance', detail: 'Check-in/out, half-day, holiday' },
    { icon: 'event_busy', title: 'Leave', detail: 'Requests, approvals, balances' },
    { icon: 'payments', title: 'Payroll', detail: 'Runs, payslips, components' },
    { icon: 'folder_shared', title: 'Documents', detail: 'Per-employee vault' },
  ];

  readonly insightTiles = [
    { icon: 'pie_chart', title: 'Employee distribution', detail: 'Across departments' },
    { icon: 'show_chart', title: 'Hiring trend', detail: 'New hires per month' },
    { icon: 'fact_check', title: 'Attendance', detail: 'Daily presence summary' },
    { icon: 'event_busy', title: 'Leave', detail: 'Status split and balances' },
    { icon: 'payments', title: 'Payroll', detail: 'Net totals by period' },
  ];

  // ------------------------------------------------------------------------ AI
  readonly aiExtracted: AiSkill[] = [
    { name: 'Java', matched: true },
    { name: 'Spring Boot', matched: true },
    { name: 'SQL', matched: true },
    { name: 'React', matched: false },
  ];

  readonly aiRequired: AiSkill[] = [
    { name: 'Java', matched: true },
    { name: 'Spring Boot', matched: true },
    { name: 'SQL', matched: true },
    { name: 'Docker', matched: false },
  ];

  // ------------------------------------------------------------------ security
  readonly securityCards: SecurityCard[] = [
    {
      icon: 'key',
      title: 'JWT Authentication',
      description: 'Authenticated API access using signed JWT tokens.',
    },
    {
      icon: 'admin_panel_settings',
      title: 'Role-Based Access',
      description: 'Different permissions for ADMIN, HR, MANAGER and EMPLOYEE roles.',
    },
    {
      icon: 'password',
      title: 'Password Protection',
      description: 'Passwords are securely hashed using BCrypt.',
    },
    {
      icon: 'history',
      title: 'Audit Logging',
      description: 'Important state-changing HR operations can be traced through audit records.',
    },
    {
      icon: 'logout',
      title: 'Session Security',
      description: 'Invalidated sessions cannot continue using revoked authentication tokens.',
    },
  ];

  // --------------------------------------------------------------------- roles
  readonly roles: RoleCard[] = [
    {
      role: 'ADMIN',
      icon: 'admin_panel_settings',
      description: 'System and organization administration.',
      tone: 'primary',
    },
    {
      role: 'HR',
      icon: 'manage_accounts',
      description: 'Employee, recruitment and HR operations.',
      tone: 'violet',
    },
    {
      role: 'MANAGER',
      icon: 'supervisor_account',
      description: 'Team-related HR workflows.',
      tone: 'info',
    },
    {
      role: 'EMPLOYEE',
      icon: 'person',
      description: 'Personal HR information and self-service.',
      tone: 'success',
    },
  ];

  // ------------------------------------------------------------------- metrics
  readonly metrics = [
    { value: '12+', label: 'HR modules' },
    { value: '4', label: 'User roles' },
    { value: '100+', label: 'HR workflows' },
    { value: '1', label: 'Unified workspace' },
  ];

  // -------------------------------------------------------------------- steps
  readonly steps: Step[] = [
    {
      number: '01',
      title: 'Set up your organization',
      description: 'Create departments, designations and leave types that mirror your structure.',
    },
    {
      number: '02',
      title: 'Manage your people',
      description: 'Build the employee directory with profiles, statuses and documents.',
    },
    {
      number: '03',
      title: 'Run HR workflows',
      description: 'Recruit, onboard, track attendance, approve leave and process payroll.',
    },
    {
      number: '04',
      title: 'Analyze and report',
      description: 'Turn the data your team generates into dashboards, exports and audit trails.',
    },
  ];

  // ------------------------------------------------------------------- reports
  readonly reports: ReportCard[] = [
    { icon: 'people', title: 'Employee Report', detail: 'Directory with statuses and filters' },
    { icon: 'fact_check', title: 'Attendance Report', detail: 'Monthly presence summary' },
    { icon: 'event_busy', title: 'Leave Report', detail: 'Requests and balances' },
    { icon: 'payments', title: 'Payroll Report', detail: 'Payslips and net totals' },
    { icon: 'work', title: 'Recruitment Report', detail: 'Pipeline and hiring outcomes' },
    { icon: 'history', title: 'Audit Report', detail: 'State changes with actors' },
  ];

  // -------------------------------------------------------------------- footer
  readonly footerProduct = [
    { label: 'Dashboard', route: '/app/dashboard' },
    { label: 'Employees', route: '/app/employees' },
    { label: 'Recruitment', route: '/app/recruitment' },
    { label: 'Attendance', route: '/app/attendance' },
    { label: 'Leave', route: '/app/leave' },
    { label: 'Payroll', route: '/app/payroll' },
  ];

  readonly footerWorkflows = [
    { label: 'Onboarding', route: '/app/onboarding' },
    { label: 'Performance', route: '/app/performance' },
    { label: 'Documents', route: '/app/documents' },
    { label: 'Analytics', route: '/app/analytics' },
    { label: 'Audit log', route: '/app/audit' },
  ];

  readonly footerCompany = [
    { label: 'About', href: '#product-story' },
    { label: 'Features', href: '#features' },
    { label: 'Security', href: '#security' },
  ];

  constructor() {
    // Reveal-on-scroll: a tiny IntersectionObserver drives the .is-in class.
    afterNextRender(() => {
      const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const revealables = Array.from(
        (this.host.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.reveal'),
      );

      if (prefersReduced || !('IntersectionObserver' in window)) {
        revealables.forEach((el) => el.classList.add('is-in'));
        return;
      }

      const observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              entry.target.classList.add('is-in');
              observer.unobserve(entry.target);
            }
          }
        },
        { threshold: 0.12 },
      );
      revealables.forEach((el) => observer.observe(el));
      this.destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  // ------------------------------------------------------------------ handlers
  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
    this.menuButton()?.nativeElement.focus();
  }

  openApp(): void {
    if (this.auth.isAuthenticated()) {
      this.router.navigateByUrl('/app/dashboard');
    } else {
      this.router.navigateByUrl('/login');
    }
  }
}
