import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of } from 'rxjs';
import { PaymentsApiService } from './payments-api.service';
import { paymentCompleted, paymentFailed, submitPayment } from './payments.actions';

@Injectable()
export class PaymentsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(PaymentsApiService);

  readonly submitPayment$ = createEffect(() => this.actions$.pipe(
    ofType(submitPayment),
    exhaustMap(({ request, idempotencyKey }) => this.api.submit(request, idempotencyKey).pipe(
      map(transaction => paymentCompleted({ transaction })),
      catchError(error => of(paymentFailed({ error: error.error?.detail ?? 'Payment could not be completed.' }))),
    )),
  ));
}