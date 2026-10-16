import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of } from 'rxjs';
import { problemMessage } from '@shared/utils/problem-message';
import { loadAccounts } from '@features/accounts/state';
import { PaymentsApiService } from './payments-api.service';
import {
  payeeVerificationFailed,
  payeeVerified,
  paymentCompleted,
  paymentFailed,
  submitPayment,
  verifyPayee,
} from './payments.actions';

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

  readonly verifyPayee$ = createEffect(() =>
    this.actions$.pipe(
      ofType(verifyPayee),
      exhaustMap(({ toIban, beneficiaryName }) =>
        this.api.verifyPayee(toIban, beneficiaryName).pipe(
          map((verification) => payeeVerified({ verification })),
          catchError((error: HttpErrorResponse) =>
            of(payeeVerificationFailed({ error: problemMessage(error, 'The beneficiary could not be checked.') })),
          ),
        ),
      ),
    ),
  );

  readonly submitPayment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(submitPayment),
      exhaustMap(({ request, idempotencyKey }) =>
        this.api.submit(request, idempotencyKey).pipe(
          map((transaction) => paymentCompleted({ transaction })),
          catchError((error: HttpErrorResponse) =>
            of(paymentFailed({ error: problemMessage(error, 'Payment could not be completed.') })),
          ),
        ),
      ),
    ),
  );
}
