import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, filter, map, Observable, of, retry, switchMap, takeUntil, tap, timer } from 'rxjs';
import { problemMessage } from '@shared/utils/problem-message';
import { loadAccounts } from '@features/accounts/state';
import { PaymentIntent } from './payment-intent.model';
import { PaymentsApiService } from './payments-api.service';
import {
  cancelIntent,
  clearPayments,
  createIntent,
  editPayment,
  intentFailed,
  intentUpdated,
  loadIntent,
  startConfirmation,
  stopPolling,
} from './payments.actions';

/** How often the server is asked whether the payer has approved the payment in the authentication app. */
export const POLL_INTERVAL_MS = 1000;

@Injectable()
export class PaymentsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(PaymentsApiService);
  private readonly router = inject(Router);

  readonly createIntent$ = createEffect(() =>
    this.actions$.pipe(
      ofType(createIntent),
      exhaustMap(({ request }) => this.update(this.api.createIntent(request), 'The payment could not be prepared.')),
    ),
  );

  readonly startConfirmation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(startConfirmation),
      exhaustMap(({ id, acknowledgeNameMismatch }) =>
        this.update(this.api.startConfirmation(id, acknowledgeNameMismatch), 'The payment could not be confirmed.'),
      ),
    ),
  );

  /** A payment that was executed in the meantime cannot be cancelled; its real state is read instead. */
  readonly cancelIntent$ = createEffect(() =>
    this.actions$.pipe(
      ofType(cancelIntent),
      exhaustMap(({ id }) =>
        this.api.cancelIntent(id).pipe(
          map(() => editPayment()),
          catchError(() => of(loadIntent({ id }))),
        ),
      ),
    ),
  );

  readonly loadIntent$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadIntent),
      switchMap(({ id }) => this.update(this.api.getIntent(id), 'The payment could not be loaded.')),
    ),
  );

  /** While the payer is asked to approve, the server is polled; it executes the payment once it is approved. */
  readonly poll$ = createEffect(() =>
    this.actions$.pipe(
      ofType(intentUpdated),
      filter(({ intent }) => intent.status === 'AWAITING_AUTHENTICATION'),
      switchMap(({ intent }) =>
        timer(POLL_INTERVAL_MS).pipe(
          switchMap(() => this.api.getIntent(intent.id).pipe(retry({ count: 3, delay: POLL_INTERVAL_MS }))),
          map((updated) => intentUpdated({ intent: updated })),
          catchError((error: HttpErrorResponse) =>
            of(intentFailed({ error: problemMessage(error, 'The payment status could not be read.') })),
          ),
          takeUntil(this.actions$.pipe(ofType(stopPolling, cancelIntent, editPayment, clearPayments))),
        ),
      ),
    ),
  );

  /** An executed payment changes balances, so the shared accounts slice is refreshed. */
  readonly refreshAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(intentUpdated),
      filter(({ intent }) => intent.status === 'COMPLETED'),
      map(() => loadAccounts()),
    ),
  );

  readonly showReceipt$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(intentUpdated),
        filter(({ intent }) => intent.status === 'COMPLETED' && !this.router.url.endsWith('/receipt')),
        tap(({ intent }) => void this.router.navigate(['/payments', intent.id, 'receipt'])),
      ),
    { dispatch: false },
  );

  private update(request: Observable<PaymentIntent>, fallback: string) {
    return request.pipe(
      map((intent) => intentUpdated({ intent })),
      catchError((error: HttpErrorResponse) => of(intentFailed({ error: problemMessage(error, fallback) }))),
    );
  }
}
