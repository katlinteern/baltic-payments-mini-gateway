import { createAction, props } from '@ngrx/store';
import { PageResponse } from '../../../shared/models/page-response.model';
import { Transaction } from '../../../shared/models/transaction.model';
import { TransactionFilters } from './transaction-filters.model';

export const loadTransactions = createAction('[Transactions] Load', props<{ filters: TransactionFilters }>());
export const loadTransactionsSuccess = createAction(
  '[Transactions] Load Success',
  props<{ result: PageResponse<Transaction> }>(),
);
export const loadTransactionsFailure = createAction('[Transactions] Load Failure', props<{ error: string }>());
export const clearTransactions = createAction('[Transactions] Clear');
