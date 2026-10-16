import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';
import { problemMessage } from '@shared/utils/problem-message';
import { TransactionsApiService } from './transactions-api.service';
import { loadTransactions, loadTransactionsFailure, loadTransactionsSuccess } from './transactions.actions';

@Injectable()
export class TransactionsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(TransactionsApiService);

  readonly loadTransactions$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadTransactions),
      switchMap(({ filters }) =>
        this.api.list(filters).pipe(
          map((result) => loadTransactionsSuccess({ result })),
          catchError((error: HttpErrorResponse) =>
            of(loadTransactionsFailure({ error: problemMessage(error, 'Could not load transaction history.') })),
          ),
        ),
      ),
    ),
  );
}
