import { createReducer, on } from '@ngrx/store';
import { Transaction } from '@shared/models/transaction.model';
import { PayeeVerification } from './payee-verification.model';
import {
  clearPayments,
  editPayment,
  payeeVerificationFailed,
  payeeVerified,
  paymentCompleted,
  paymentFailed,
  submitPayment,
  verifyPayee,
} from './payments.actions';

export interface PaymentsState {
  verifying: boolean;
  /** Present while the payer reviews the payment; the form is hidden until it is edited or completed. */
  verification: PayeeVerification | null;
  submitting: boolean;
  error: string | null;
  completedTransaction: Transaction | null;
}
export const initialPaymentsState: PaymentsState = {
  verifying: false,
  verification: null,
  submitting: false,
  error: null,
  completedTransaction: null,
};

export const paymentsReducer = createReducer(
  initialPaymentsState,
  on(verifyPayee, (state) => ({
    ...state,
    verifying: true,
    verification: null,
    error: null,
    completedTransaction: null,
  })),
  on(payeeVerified, (state, { verification }) => ({ ...state, verifying: false, verification })),
  on(payeeVerificationFailed, (state, { error }) => ({ ...state, verifying: false, error })),
  on(editPayment, (state) => ({ ...state, verification: null, error: null })),
  on(submitPayment, (state) => ({ ...state, submitting: true, error: null, completedTransaction: null })),
  on(paymentCompleted, (state, { transaction }) => ({
    ...state,
    submitting: false,
    verification: null,
    completedTransaction: transaction,
  })),
  on(paymentFailed, (state, { error }) => ({ ...state, submitting: false, error })),
  on(clearPayments, () => initialPaymentsState),
);
