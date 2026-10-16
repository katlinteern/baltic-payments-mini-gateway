import { createAction, props } from '@ngrx/store';
import { Account } from '@shared/models/account.model';

export const loadAccounts = createAction('[Accounts] Load');
export const loadAccountsSuccess = createAction('[Accounts] Load Success', props<{ accounts: Account[] }>());
export const loadAccountsFailure = createAction('[Accounts] Load Failure', props<{ error: string }>());
