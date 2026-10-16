import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { catchError, throwError } from 'rxjs';
import { uuid } from '@shared/utils/uuid';
import { AuthService } from './auth.service';
import { sessionEnded } from './session';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const store = inject(Store);
  let headers = request.headers.set('X-Correlation-Id', uuid());
  const token = auth.token();
  if (token) headers = headers.set('Authorization', `Bearer ${token}`);
  return next(request.clone({ headers })).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.endsWith('/auth/login')) {
        auth.logout();
        store.dispatch(sessionEnded());
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
