import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PaymentsState } from './payments.reducer';

export const selectPaymentsState = createFeatureSelector<PaymentsState>('payments');
export const selectPayeeVerifying = createSelector(selectPaymentsState, (state) => state.verifying);
export const selectPayeeVerification = createSelector(selectPaymentsState, (state) => state.verification);
export const selectPaymentSubmitting = createSelector(selectPaymentsState, (state) => state.submitting);
export const selectPaymentError = createSelector(selectPaymentsState, (state) => state.error);
export const selectCompletedTransaction = createSelector(selectPaymentsState, (state) => state.completedTransaction);
