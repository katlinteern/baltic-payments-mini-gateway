import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import { paymentCompleted } from '../../payment/state/payments.actions';
import { loadAccounts, loadAccountsFailure, loadAccountsSuccess } from './accounts.actions';

@Injectable()
export class AccountsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(ApiService);

  readonly loadAccounts$ = createEffect(() => this.actions$.pipe(
    ofType(loadAccounts, paymentCompleted),
    exhaustMap(() => this.api.accounts().pipe(
      map(accounts => loadAccountsSuccess({ accounts })),
      catchError(error => of(loadAccountsFailure({ error: error.error?.detail ?? 'Could not load accounts.' }))),
    )),
  ));
}