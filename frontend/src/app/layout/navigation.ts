import { UserRole } from '../core/auth.service';

/**
 * Single source for the sidebar and the command palette (Ctrl+K).
 *
 * `roles` mirrors the backend's @PreAuthorize rules per module (UX layer
 * only — the API remains authoritative; hiding a link never grants access).
 * EMPLOYEE sees only what the API actually serves them: notifications.
 */
export interface NavItem {
  label: string;
  description: string;
  icon: string;
  route: string;
  group: 'Overview' | 'People' | 'Recruitment' | 'Workforce' | 'System';
  roles: UserRole[];
}

const ALL: UserRole[] = ['ADMIN', 'HR', 'MANAGER', 'EMPLOYEE'];
/** Roles that pass the backend's hasAnyRole('ADMIN','HR','MANAGER') gate. */
const OHM: UserRole[] = ['ADMIN', 'HR', 'MANAGER'];

export const NAV_GROUPS: Array<NavItem['group']> = ['Overview', 'People', 'Recruitment', 'Workforce', 'System'];

export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', description: 'Workforce metrics at a glance', icon: 'dashboard', route: '/app/dashboard', group: 'Overview', roles: ['ADMIN', 'HR'] },
  { label: 'Analytics', description: 'Trends across attendance, hiring and leave', icon: 'insights', route: '/app/analytics', group: 'Overview', roles: ['ADMIN', 'HR'] },

  { label: 'Employees', description: 'Directory of all employee records', icon: 'people', route: '/app/employees', group: 'People', roles: OHM },
  { label: 'Departments', description: 'Departments and their heads', icon: 'account_tree', route: '/app/departments', group: 'People', roles: OHM },

  { label: 'Recruitment', description: 'Jobs, candidates, applications and interviews', icon: 'work', route: '/app/recruitment', group: 'Recruitment', roles: OHM },

  { label: 'Onboarding', description: 'Checklist progress for new joiners', icon: 'badge', route: '/app/onboarding', group: 'Workforce', roles: OHM },
  { label: 'Attendance', description: 'Daily presence records', icon: 'fact_check', route: '/app/attendance', group: 'Workforce', roles: OHM },
  { label: 'Leave', description: 'Requests, approvals and balances', icon: 'event_busy', route: '/app/leave', group: 'Workforce', roles: OHM },
  { label: 'Payroll', description: 'Payslips and payroll runs', icon: 'payments', route: '/app/payroll', group: 'Workforce', roles: ['ADMIN', 'HR'] },
  { label: 'Performance', description: 'Reviews, goals and ratings', icon: 'trending_up', route: '/app/performance', group: 'Workforce', roles: OHM },
  { label: 'Documents', description: 'Employee document vault', icon: 'folder_shared', route: '/app/documents', group: 'Workforce', roles: OHM },

  { label: 'Notifications', description: 'Your personal alert feed', icon: 'notifications', route: '/app/notifications', group: 'System', roles: ALL },
  { label: 'Audit log', description: 'Who changed what, newest first', icon: 'history', route: '/app/audit', group: 'System', roles: ['ADMIN', 'HR'] },
];
