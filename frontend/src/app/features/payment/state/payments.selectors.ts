import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PaymentsState } from './payments.reducer';

export const selectPaymentsState = createFeatureSelector<PaymentsState>('payments');
export const selectIntent = createSelector(selectPaymentsState, (state) => state.intent);
export const selectPaymentBusy = createSelector(selectPaymentsState, (state) => state.busy);
export const selectPaymentError = createSelector(selectPaymentsState, (state) => state.error);
