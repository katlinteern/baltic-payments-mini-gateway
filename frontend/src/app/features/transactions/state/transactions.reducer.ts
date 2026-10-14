import { createReducer, on } from '@ngrx/store';
import { Transaction } from '../../../shared/models/transaction.model';
import { clearTransactions, loadTransactions, loadTransactionsFailure, loadTransactionsSuccess } from './transactions.actions';

export interface TransactionsState { items: Transaction[]; total: number; loading: boolean; error: string | null; }
export const initialTransactionsState: TransactionsState = { items: [], total: 0, loading: false, error: null };

export const transactionsReducer = createReducer(
  initialTransactionsState,
  on(loadTransactions, state => ({ ...state, loading: true, error: null })),
  on(loadTransactionsSuccess, (state, { result }) => ({ ...state, items: result.content, total: result.totalElements, loading: false })),
  on(loadTransactionsFailure, (state, { error }) => ({ ...state, loading: false, error })),
  on(clearTransactions, () => initialTransactionsState),
);