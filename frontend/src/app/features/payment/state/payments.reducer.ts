import { createReducer, on } from '@ngrx/store';
import { Transaction } from '@shared/models/transaction.model';
import { clearPayments, paymentCompleted, paymentFailed, submitPayment } from './payments.actions';

export interface PaymentsState {
  submitting: boolean;
  error: string | null;
  /** The bank rejected the payment because the beneficiary name does not match; the payer may send anyway. */
  nameMismatch: boolean;
  completedTransaction: Transaction | null;
}
export const initialPaymentsState: PaymentsState = {
  submitting: false,
  error: null,
  nameMismatch: false,
  completedTransaction: null,
};

export const paymentsReducer = createReducer(
  initialPaymentsState,
  on(submitPayment, (state) => ({
    ...state,
    submitting: true,
    error: null,
    nameMismatch: false,
    completedTransaction: null,
  })),
  on(paymentCompleted, (state, { transaction }) => ({
    ...state,
    submitting: false,
    completedTransaction: transaction,
  })),
  on(paymentFailed, (state, { error, nameMismatch }) => ({ ...state, submitting: false, error, nameMismatch })),
  on(clearPayments, () => initialPaymentsState),
);
