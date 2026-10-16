import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of, tap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { login, loginFailed, loginSucceeded } from './login.actions';

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
          catchError(() => of(loginFailed({ error: 'Login failed. Check your credentials and try again.' }))),
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
