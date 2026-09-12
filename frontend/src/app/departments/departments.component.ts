import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuthService } from '../core/auth.service';
import { Department, Designation } from './departments.service';
import { DepartmentsService } from './departments.service';
import {
  DepartmentDialog,
  DesignationDialog,
  DeleteGuardedDialog,
} from './departments.dialogs';

/**
 * Organization management (Phase 4): two tables — departments and
 * designations — with counts, manager assignment and guarded deletes.
 */
@Component({
  selector: 'app-departments',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './departments.component.html',
  styleUrl: './departments.component.scss',
})
export class DepartmentsComponent implements OnInit {
  private readonly departmentsService = inject(DepartmentsService);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AuthService);

  readonly deptColumns = ['name', 'manager', 'count', 'actions'];
  readonly desgColumns = ['title', 'department', 'count', 'actions'];

  readonly departments = signal<Department[]>([]);
  readonly designations = signal<Designation[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal(false);

  readonly canMutate = computed(() => this.auth.user()?.role === 'ADMIN');

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.departmentsService.list().subscribe({
      next: (res) => {
        this.departments.set(res.data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
    this.departmentsService.designations().subscribe({
      next: (res) => this.designations.set(res.data),
      error: () => this.designations.set([]),
    });
  }

  openDepartmentDialog(department?: Department): void {
    this.dialog
      .open(DepartmentDialog, { data: { department }, width: '480px' })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  openDesignationDialog(designation?: Designation): void {
    this.dialog
      .open(DesignationDialog, { data: { designation }, width: '480px' })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  deleteDepartment(department: Department): void {
    const ref = this.dialog.open(DeleteGuardedDialog, {
      data: {
        title: 'Delete department',
        name: department.name,
        warning:
          department.employeeCount > 0
            ? `This department still has ${department.employeeCount} employee(s) — the server will reject the delete.`
            : 'The department must be empty (no employees, no designations).',
      },
      width: '440px',
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.departmentsService.delete(department.id).subscribe({ next: () => this.load() });
      }
    });
  }

  deleteDesignation(designation: Designation): void {
    const ref = this.dialog.open(DeleteGuardedDialog, {
      data: {
        title: 'Delete designation',
        name: designation.title,
        warning:
          designation.employeeCount > 0
            ? `This designation is held by ${designation.employeeCount} employee(s) — the server will reject the delete.`
            : 'This title is not assigned to anyone.',
      },
      width: '440px',
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.departmentsService.deleteDesignation(designation.id).subscribe({ next: () => this.load() });
      }
    });
  }
}
