import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AccountsState } from './accounts.reducer';

export const selectAccountsState = createFeatureSelector<AccountsState>('accounts');
export const selectAccounts = createSelector(selectAccountsState, (state) => state.items);
export const selectAccountsLoading = createSelector(selectAccountsState, (state) => state.loading);
export const selectAccountsError = createSelector(selectAccountsState, (state) => state.error);
export const selectEuroTotal = createSelector(selectAccounts, (accounts) =>
  accounts.filter((account) => account.currency === 'EUR').reduce((sum, account) => sum + Number(account.balance), 0),
);
