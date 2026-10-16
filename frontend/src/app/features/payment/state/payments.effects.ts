import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of } from 'rxjs';
import { loadAccounts } from '@features/accounts/state';
import { PaymentsApiService } from './payments-api.service';
import { paymentCompleted, paymentFailed, submitPayment } from './payments.actions';

@Injectable()
export class PaymentsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(PaymentsApiService);

  /** A completed payment changes balances, so the shared accounts slice is refreshed. */
  readonly refreshAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(paymentCompleted),
      map(() => loadAccounts()),
    ),
  );

  readonly submitPayment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(submitPayment),
      exhaustMap(({ request, idempotencyKey }) =>
        this.api.submit(request, idempotencyKey).pipe(
          map((transaction) => paymentCompleted({ transaction })),
          catchError((error) => {
            const fieldErrors = error.error?.errors as Record<string, string> | undefined;
            const validationMessage = fieldErrors ? Object.values(fieldErrors).join(' ') : undefined;
            return of(
              paymentFailed({
                error: validationMessage ?? error.error?.detail ?? 'Payment could not be completed.',
              }),
            );
          }),
        ),
      ),
    ),
  );
}
