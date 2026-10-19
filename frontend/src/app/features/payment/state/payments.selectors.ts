import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PaymentsState } from './payments.reducer';

export const selectPaymentsState = createFeatureSelector<PaymentsState>('payments');
export const selectPaymentSubmitting = createSelector(selectPaymentsState, (state) => state.submitting);
export const selectPaymentError = createSelector(selectPaymentsState, (state) => state.error);
export const selectNameMismatch = createSelector(selectPaymentsState, (state) => state.nameMismatch);
export const selectCompletedTransaction = createSelector(selectPaymentsState, (state) => state.completedTransaction);
