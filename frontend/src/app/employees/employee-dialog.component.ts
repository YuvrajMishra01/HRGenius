import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Inject } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE, NativeDateAdapter } from '@angular/material/core';

import { AuthService } from '../core/auth.service';
import {
  Employee,
  EmployeeRequest,
  EmploymentType,
  EmployeeStatus,
  Gender,
  Option,
} from './employees.models';
import { EmployeesService } from './employees.service';

interface ManagerOption {
  id: number;
  label: string;
}

export interface EmployeeDialogData {
  /** Present = edit mode; absent = create mode. */
  employee?: Employee;
}

/**
 * Add/Edit employee dialog. Department choice filters the designation list
 * (backend also validates the pairing — UX + defense in depth).
 */
@Component({
  selector: 'app-employee-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatProgressBarModule,
  ],
  providers: [
    { provide: DateAdapter, useClass: NativeDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: { parse: { dateInput: 'YYYY-MM-DD' }, display: { dateInput: 'YYYY-MM-DD' } } },
  ],
  templateUrl: './employee-dialog.component.html',
  styleUrl: './employee-dialog.component.scss',
})
export class EmployeeDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly employeesService = inject(EmployeesService);
  readonly dialogRef = inject<MatDialogRef<EmployeeDialogComponent>>(MatDialogRef);
  readonly data = inject<EmployeeDialogData>(MAT_DIALOG_DATA);
  readonly auth = inject(AuthService);

  readonly saving = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});

  readonly editing = this.data.employee != null;

  readonly departments = signal<Option[]>([]);
  readonly designations = signal<Option[]>([]);
  readonly managers = signal<ManagerOption[]>([]);

  readonly employmentTypes: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN'];
  readonly statuses: EmployeeStatus[] = ['ACTIVE', 'ON_LEAVE', 'RESIGNED', 'TERMINATED'];
  readonly genders: Gender[] = ['MALE', 'FEMALE', 'OTHER'];

  readonly form = this.fb.nonNullable.group({
    employeeCode: [this.data.employee?.employeeCode ?? '', [Validators.required, Validators.maxLength(20)]],
    firstName: [this.data.employee?.firstName ?? '', Validators.required],
    lastName: [this.data.employee?.lastName ?? '', Validators.required],
    email: [this.data.employee?.email ?? '', [Validators.required, Validators.email]],
    phone: [this.data.employee?.phone ?? ''],
    dateOfBirth: [this.data.employee?.dateOfBirth ?? ''],
    gender: [this.data.employee?.gender ?? null],
    address: [this.data.employee?.address ?? ''],
    joiningDate: [this.data.employee?.joiningDate ?? '', Validators.required],
    employmentType: [this.data.employee?.employmentType ?? 'FULL_TIME', Validators.required],
    status: [this.data.employee?.status ?? 'ACTIVE', Validators.required],
    departmentId: [this.data.employee?.departmentId ?? null],
    designationId: [this.data.employee?.designationId ?? null],
    managerId: [this.data.employee?.managerId ?? null],
  });

  constructor() {
    this.employeesService.departments().subscribe((res) => {
      this.departments.set(res.data);
      if (this.data.employee?.departmentId != null) {
        this.loadDesignations(this.data.employee.departmentId);
      }
    });
    this.employeesService.managerOptions().subscribe((res) => this.managers.set(res.data));
    // Reload designations whenever the department changes.
    this.form.controls.departmentId.valueChanges.subscribe((depId) => {
      this.designations.set([]);
      this.form.controls.designationId.setValue(null);
      if (depId != null) {
        this.loadDesignations(depId);
      }
    });
  }

  private loadDesignations(departmentId: number): void {
    this.employeesService.designations(departmentId).subscribe((res) => this.designations.set(res.data));
  }

  save(): void {
    this.serverError.set(null);
    this.fieldErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const request: EmployeeRequest = {
      employeeCode: v.employeeCode.trim(),
      firstName: v.firstName.trim(),
      lastName: v.lastName.trim(),
      email: v.email.trim(),
      phone: v.phone?.trim() || null,
      dateOfBirth: v.dateOfBirth || null,
      gender: v.gender,
      address: v.address?.trim() || null,
      joiningDate: v.joiningDate,
      employmentType: v.employmentType,
      status: v.status,
      departmentId: v.departmentId,
      designationId: v.designationId,
      managerId: v.managerId,
    };

    const call = this.editing
      ? this.employeesService.update(this.data.employee!.id, request)
      : this.employeesService.create(request);

    call.subscribe({
      next: (res) => this.dialogRef.close(res.data),
      error: (err) => {
        this.saving.set(false);
        if (err?.status === 400 && err.error?.errors) {
          this.fieldErrors.set(err.error.errors);
        } else {
          this.serverError.set(err?.error?.message ?? 'Save failed. Please try again.');
        }
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  /** true when the signed-in user may actually press Save (EMPLOYEE/MANAGER never see the dialog anyway). */
  get canSave(): boolean {
    const role = this.auth.user()?.role;
    return role === 'ADMIN' || role === 'HR';
  }
}
