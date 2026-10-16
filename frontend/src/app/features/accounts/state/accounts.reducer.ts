import { createReducer, on } from '@ngrx/store';
import { Account } from '@shared/models/account.model';
import { loadAccounts, loadAccountsFailure, loadAccountsSuccess } from './accounts.actions';

export interface AccountsState {
  items: Account[];
  loading: boolean;
  error: string | null;
}
export const initialAccountsState: AccountsState = { items: [], loading: false, error: null };

export const accountsReducer = createReducer(
  initialAccountsState,
  on(loadAccounts, (state) => ({ ...state, loading: true, error: null })),
  on(loadAccountsSuccess, (state, { accounts }) => ({ ...state, items: accounts, loading: false })),
  on(loadAccountsFailure, (state, { error }) => ({ ...state, loading: false, error })),
);
