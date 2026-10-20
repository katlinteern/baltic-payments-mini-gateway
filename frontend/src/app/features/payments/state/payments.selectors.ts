import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PaymentsState } from './payments.reducer';

export const selectPaymentsState = createFeatureSelector<PaymentsState>('payments');
export const selectPayment = createSelector(selectPaymentsState, (state) => state.payment);
export const selectPaymentBusy = createSelector(selectPaymentsState, (state) => state.busy);
export const selectPaymentError = createSelector(selectPaymentsState, (state) => state.error);
