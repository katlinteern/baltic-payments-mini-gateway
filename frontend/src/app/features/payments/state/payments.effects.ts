import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, filter, map, Observable, of, retry, switchMap, takeUntil, tap, timer } from 'rxjs';
import { problemMessage } from '@shared/utils/problem-message';
import { loadAccounts } from '@features/accounts/state';
import { Payment } from './payment.model';
import { PaymentsApiService } from './payments-api.service';
import {
  cancelPayment,
  clearPayments,
  createPayment,
  editPayment,
  loadPayment,
  paymentFailed,
  paymentUpdated,
  startConfirmation,
  stopPolling,
} from './payments.actions';

export const POLL_INTERVAL_MS = 1000;

@Injectable()
export class PaymentsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(PaymentsApiService);
  private readonly router = inject(Router);

  readonly createPayment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(createPayment),
      exhaustMap(({ request }) => this.update(this.api.createPayment(request), 'The payment could not be prepared.')),
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

  readonly cancelPayment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(cancelPayment),
      exhaustMap(({ id }) =>
        this.api.cancelPayment(id).pipe(
          map(() => editPayment()),
          catchError(() => of(loadPayment({ id }))),
        ),
      ),
    ),
  );

  readonly loadPayment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadPayment),
      switchMap(({ id }) => this.update(this.api.getPayment(id), 'The payment could not be loaded.')),
    ),
  );

  readonly poll$ = createEffect(() =>
    this.actions$.pipe(
      ofType(paymentUpdated),
      filter(({ payment }) => payment.status === 'AWAITING_AUTHENTICATION'),
      switchMap(({ payment }) =>
        timer(POLL_INTERVAL_MS).pipe(
          switchMap(() => this.api.getPayment(payment.id).pipe(retry({ count: 3, delay: POLL_INTERVAL_MS }))),
          map((updated) => paymentUpdated({ payment: updated })),
          catchError((error: HttpErrorResponse) =>
            of(paymentFailed({ error: problemMessage(error, 'The payment status could not be read.') })),
          ),
          takeUntil(this.actions$.pipe(ofType(stopPolling, cancelPayment, editPayment, clearPayments))),
        ),
      ),
    ),
  );

  readonly refreshAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(paymentUpdated),
      filter(({ payment }) => payment.status === 'COMPLETED'),
      map(() => loadAccounts()),
    ),
  );

  readonly showReceipt$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(paymentUpdated),
        filter(({ payment }) => payment.status === 'COMPLETED' && !this.router.url.endsWith('/receipt')),
        tap(({ payment }) => void this.router.navigate(['/payments', payment.id, 'receipt'])),
      ),
    { dispatch: false },
  );

  private update(request: Observable<Payment>, fallback: string) {
    return request.pipe(
      map((payment) => paymentUpdated({ payment })),
      catchError((error: HttpErrorResponse) => of(paymentFailed({ error: problemMessage(error, fallback) }))),
    );
  }
}
