import { createAction, props } from '@ngrx/store';
import { PageResponse, Transaction, TransactionFilters } from '../../../core/models';

export const loadTransactions = createAction('[Transactions] Load', props<{ filters: TransactionFilters }>());
export const loadTransactionsSuccess = createAction('[Transactions] Load Success', props<{ result: PageResponse<Transaction> }>());
export const loadTransactionsFailure = createAction('[Transactions] Load Failure', props<{ error: string }>());
export const clearTransactions = createAction('[Transactions] Clear');