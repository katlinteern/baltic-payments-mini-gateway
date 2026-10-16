import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TransactionsState } from './transactions.reducer';

export const selectTransactionsState = createFeatureSelector<TransactionsState>('transactions');
export const selectTransactions = createSelector(selectTransactionsState, (state) => state.items);
export const selectTransactionsTotal = createSelector(selectTransactionsState, (state) => state.total);
export const selectTransactionsLoading = createSelector(selectTransactionsState, (state) => state.loading);
export const selectTransactionsError = createSelector(selectTransactionsState, (state) => state.error);
