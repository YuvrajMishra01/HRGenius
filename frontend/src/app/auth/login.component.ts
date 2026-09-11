import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';

import { AuthService } from '../core/auth.service';

/**
 * Login page (Phase 1). Authenticated users never see it (guestGuard);
 * after success we return to `?returnUrl=` when it is a safe internal URL.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly hidePassword = signal(true);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => this.navigateAfterLogin(),
      error: (err) => {
        // err.error is the backend ApiError envelope.
        this.serverError.set(err?.error?.message ?? 'Login failed. Please try again.');
      },
    });
  }

  private navigateAfterLogin(): void {
    const requested = this.route.snapshot.queryParamMap.get('returnUrl');
    const safe = requested && requested.startsWith('/') && !requested.startsWith('//') ? requested : '/dashboard';
    this.router.navigateByUrl(safe);
  }
}
