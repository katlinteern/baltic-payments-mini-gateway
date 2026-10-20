import { inject, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of, tap } from 'rxjs';
import { AuthService } from '@core/auth.service';
import { login, loginFailed, loginSucceeded } from './login.actions';

/** Tells a rate-limited or unreachable sign-in apart from wrong credentials, so the user is not told to retype a correct password. */
export function loginErrorMessage(error: HttpErrorResponse): string {
  if (error.status === 429) {
    const seconds = Number(error.headers?.get('Retry-After'));
    if (!Number.isFinite(seconds) || seconds <= 0) return 'Too many failed attempts. Try again later.';
    const minutes = Math.ceil(seconds / 60);
    return `Too many failed attempts. Try again in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.`;
  }
  if (error.status === 0 || error.status >= 500) return 'Sign-in is temporarily unavailable. Try again shortly.';
  return 'Login failed. Check your credentials and try again.';
}

@Injectable()
export class LoginEffects {
  private readonly actions$ = inject(Actions);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(login),
      exhaustMap(({ username, password }) =>
        this.auth.login(username, password).pipe(
          map(() => loginSucceeded()),
          catchError((error: HttpErrorResponse) => of(loginFailed({ error: loginErrorMessage(error) }))),
        ),
      ),
    ),
  );

  readonly navigateAfterLogin$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(loginSucceeded),
        tap(() => void this.router.navigate(['/accounts'])),
      ),
    { dispatch: false },
  );
}
