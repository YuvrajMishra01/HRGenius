/** Mirrors backend com.hrgenius.analytics.DashboardStats (Phase 2). */

export interface DashboardKpis {
  totalEmployees: number;
  activeEmployees: number;
  newHiresLast30Days: number;
  openPositions: number;
  totalCandidates: number;
  pendingLeaveRequests: number;
}

export interface NameCount {
  name: string;
  count: number;
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface MonthCount {
  year: number;
  month: number;
  count: number;
}

export interface AttendanceSummary {
  day: string;
  present: number;
  absent: number;
  halfDay: number;
  onLeave: number;
  holiday: number;
  totalRecords: number;
  attendancePercent: number;
}

export interface PayrollSummary {
  year: number;
  month: number;
  payslips: number;
  totalNet: number;
}

export interface RecentHire {
  id: number;
  employeeCode: string;
  fullName: string;
  department: string | null;
  designation: string | null;
  joiningDate: string;
  status: string;
}

export interface PendingApproval {
  id: number;
  employeeName: string;
  leaveType: string;
  startDate: string;
  endDate: string;
  status: string;
}

export interface UpcomingInterview {
  id: number;
  candidateName: string;
  jobTitle: string;
  interviewDate: string;
  mode: string;
}

export interface DashboardStats {
  kpis: DashboardKpis;
  departmentDistribution: NameCount[];
  hiringTrend: MonthCount[];
  hiringPipeline: StatusCount[];
  attendanceToday: AttendanceSummary;
  leaveSummary: StatusCount[];
  payrollThisMonth: PayrollSummary;
  recentHires: RecentHire[];
  pendingApprovals: PendingApproval[];
  upcomingInterviews: UpcomingInterview[];
}
