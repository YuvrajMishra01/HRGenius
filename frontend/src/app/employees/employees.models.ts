/** Mirrors backend employee module DTOs. */

export type EmployeeStatus = 'ACTIVE' | 'ON_LEAVE' | 'RESIGNED' | 'TERMINATED';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERN';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

export interface Employee {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  dateOfBirth: string | null;
  gender: Gender | null;
  address: string | null;
  joiningDate: string;
  employmentType: EmploymentType;
  status: EmployeeStatus;
  departmentId: number | null;
  departmentName: string | null;
  designationId: number | null;
  designationTitle: string | null;
  managerId: number | null;
  managerName: string | null;
}

export interface EmployeeRequest {
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  dateOfBirth: string | null;
  gender: Gender | null;
  address: string | null;
  joiningDate: string;
  employmentType: EmploymentType;
  status: EmployeeStatus;
  departmentId: number | null;
  designationId: number | null;
  managerId: number | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Option {
  id: number;
  name: string;
  departmentId: number | null;
}
