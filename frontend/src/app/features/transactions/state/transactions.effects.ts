import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';
import { TransactionsApiService } from '../data-access/transactions-api.service';
import { loadTransactions, loadTransactionsFailure, loadTransactionsSuccess } from './transactions.actions';

@Injectable()
export class TransactionsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(TransactionsApiService);

  readonly loadTransactions$ = createEffect(() => this.actions$.pipe(
    ofType(loadTransactions),
    switchMap(({ filters }) => this.api.list(filters).pipe(
      map(result => loadTransactionsSuccess({ result })),
      catchError(error => of(loadTransactionsFailure({ error: error.error?.detail ?? 'Could not load transaction history.' }))),
    )),
  ));
}