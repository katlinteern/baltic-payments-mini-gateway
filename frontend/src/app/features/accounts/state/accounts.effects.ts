import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of } from 'rxjs';
import { problemMessage } from '@shared/utils/problem-message';
import { AccountsApiService } from './accounts-api.service';
import { loadAccounts, loadAccountsFailure, loadAccountsSuccess } from './accounts.actions';

@Injectable()
export class AccountsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(AccountsApiService);

  readonly loadAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadAccounts),
      exhaustMap(() =>
        this.api.list().pipe(
          map((accounts) => loadAccountsSuccess({ accounts })),
          catchError((error: HttpErrorResponse) =>
            of(loadAccountsFailure({ error: problemMessage(error, 'Could not load accounts.') })),
          ),
        ),
      ),
    ),
  );
}
