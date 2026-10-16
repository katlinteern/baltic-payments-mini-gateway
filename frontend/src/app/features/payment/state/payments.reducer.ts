import { createReducer, on } from '@ngrx/store';
import { Transaction } from '@shared/models/transaction.model';
import { clearPayments, paymentCompleted, paymentFailed, submitPayment } from './payments.actions';

export interface PaymentsState {
  submitting: boolean;
  error: string | null;
  completedTransaction: Transaction | null;
}
export const initialPaymentsState: PaymentsState = { submitting: false, error: null, completedTransaction: null };

export const paymentsReducer = createReducer(
  initialPaymentsState,
  on(submitPayment, (state) => ({ ...state, submitting: true, error: null, completedTransaction: null })),
  on(paymentCompleted, (state, { transaction }) => ({
    ...state,
    submitting: false,
    completedTransaction: transaction,
  })),
  on(paymentFailed, (state, { error }) => ({ ...state, submitting: false, error })),
  on(clearPayments, () => initialPaymentsState),
);
