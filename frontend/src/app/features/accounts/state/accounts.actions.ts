import { createAction, props } from '@ngrx/store';
import { Account } from '../../../core/models';

export const loadAccounts = createAction('[Accounts] Load');
export const loadAccountsSuccess = createAction('[Accounts] Load Success', props<{ accounts: Account[] }>());
export const loadAccountsFailure = createAction('[Accounts] Load Failure', props<{ error: string }>());
export const clearAccounts = createAction('[Accounts] Clear');