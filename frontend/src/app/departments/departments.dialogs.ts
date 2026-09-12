import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { Department, DepartmentRequest, DesignationRequest, EmployeeOption } from './departments.service';
import { DepartmentsService } from './departments.service';

/** Shared bits for the create/edit dialogs. */
@Component({
  standalone: true,
  imports: [],
  template: '',
})
abstract class BaseDialog {
  readonly saving = signal(false);
  readonly serverError = signal<string | null>(null);

  /** Maps a 409/400 backend message into the alert box. */
  setError(err: unknown): void {
    const anyErr = err as { error?: { message?: string; errors?: Record<string, string> } };
    const fieldErrors = anyErr?.error?.errors;
    if (fieldErrors && Object.keys(fieldErrors).length > 0) {
      this.serverError.set(Object.values(fieldErrors)[0]);
    } else {
      this.serverError.set(anyErr?.error?.message ?? 'Save failed.');
    }
  }
}

/** Department create/edit dialog. */
@Component({
  selector: 'app-department-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.department ? 'Edit department' : 'Add department' }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" placeholder="Quality Assurance" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Manager</mat-label>
          <mat-select formControlName="managerId">
            <mat-option [value]="null">— none —</mat-option>
            @for (o of employeeOptions(); track o.id) {
              <mat-option [value]="o.id">{{ o.label }}</mat-option>
            }
          </mat-select>
          <mat-hint>Department head</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput rows="2" formControlName="description"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; }
      .full { width: 100%; }
      .alert { display: flex; gap: 8px; align-items: center; background: #fdecea; color: #b71c1c;
               border: 1px solid #f5c6cb; border-radius: 8px; padding: 10px 14px; margin-bottom: 12px;
               font-size: 14px; }
    `,
  ],
})
export class DepartmentDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DepartmentsService);
  readonly dialogRef = inject<MatDialogRef<DepartmentDialog>>(MatDialogRef);
  readonly data = inject<{ department?: Department }>(MAT_DIALOG_DATA);

  readonly employeeOptions = signal<EmployeeOption[]>([]);

  readonly form = this.fb.nonNullable.group({
    name: [this.data.department?.name ?? '', [Validators.required, Validators.maxLength(100)]],
    description: [this.data.department?.description ?? ''],
    managerId: [this.data.department?.managerId ?? null],
  });

  constructor() {
    super();
    this.service.employeeOptions().subscribe((res) => this.employeeOptions.set(res.data));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const request: DepartmentRequest = {
      name: v.name.trim(),
      description: v.description?.trim() || null,
      managerId: v.managerId,
    };
    const call = this.data.department
      ? this.service.update(this.data.department.id, request)
      : this.service.create(request);
    call.subscribe({
      next: (res) => this.dialogRef.close(res.data),
      error: (err) => {
        this.saving.set(false);
        this.setError(err);
      },
    });
  }
}

/** Designation create/edit dialog. */
@Component({
  selector: 'app-designation-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.designation ? 'Edit designation' : 'Add designation' }}</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Department</mat-label>
          <mat-select formControlName="departmentId">
            @for (d of departments(); track d.id) {
              <mat-option [value]="d.id">{{ d.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" placeholder="QA Engineer" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput rows="2" formControlName="description"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Saving…' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form { display: flex; flex-direction: column; gap: 4px; }
      .full { width: 100%; }
      .alert { display: flex; gap: 8px; align-items: center; background: #fdecea; color: #b71c1c;
               border: 1px solid #f5c6cb; border-radius: 8px; padding: 10px 14px; margin-bottom: 12px;
               font-size: 14px; }
    `,
  ],
})
export class DesignationDialog extends BaseDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DepartmentsService);
  readonly dialogRef = inject<MatDialogRef<DesignationDialog>>(MatDialogRef);
  readonly data = inject<{ designation?: import('./departments.service').Designation }>(MAT_DIALOG_DATA);

  readonly departments = signal<Department[]>([]);

  readonly form = this.fb.nonNullable.group({
    departmentId: [this.data.designation?.departmentId ?? null, Validators.required],
    title: [this.data.designation?.title ?? '', [Validators.required, Validators.maxLength(100)]],
    description: [this.data.designation?.description ?? ''],
  });

  constructor() {
    super();
    this.service.list().subscribe((res) => this.departments.set(res.data));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const request: DesignationRequest = {
      title: v.title.trim(),
      description: v.description?.trim() || null,
      departmentId: v.departmentId!,
    };
    const call = this.data.designation
      ? this.service.updateDesignation(this.data.designation.id, request)
      : this.service.createDesignation(request);
    call.subscribe({
      next: (res) => this.dialogRef.close(res.data),
      error: (err) => {
        this.saving.set(false);
        this.setError(err);
      },
    });
  }
}

/** Guarded-delete confirmation used by both tables. */
@Component({
  selector: 'app-delete-guarded-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <div class="warn-box">
        <mat-icon class="warn-icon">warning_amber</mat-icon>
        <div>
          <p>Delete <strong>{{ data.name }}</strong>?</p>
          <p class="warning">{{ data.warning }}</p>
        </div>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Delete</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .warn-box { display: flex; gap: 12px; align-items: flex-start; max-width: 360px; }
      .warn-icon { color: #ef6c00; }
      p { margin: 0 0 6px; }
      .warning { font-size: 13px; color: rgba(0, 0, 0, 0.6); }
    `,
  ],
})
export class DeleteGuardedDialog {
  readonly data = inject<{ title: string; name: string; warning: string }>(MAT_DIALOG_DATA);
}
