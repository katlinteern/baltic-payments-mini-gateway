import { createReducer, on } from '@ngrx/store';
import { Payment } from './payment.model';
import {
  cancelPayment,
  clearPayments,
  createPayment,
  editPayment,
  loadPayment,
  paymentFailed,
  paymentUpdated,
  startConfirmation,
} from './payments.actions';

export interface PaymentsState {
  payment: Payment | null;
  busy: boolean;
  error: string | null;
}
export const initialPaymentsState: PaymentsState = { payment: null, busy: false, error: null };

const busy = (state: PaymentsState): PaymentsState => ({ ...state, busy: true, error: null });

export const paymentsReducer = createReducer(
  initialPaymentsState,
  on(createPayment, (state) => ({ ...busy(state), payment: null })),
  on(startConfirmation, cancelPayment, loadPayment, busy),
  on(paymentUpdated, (state, { payment }) => ({ ...state, payment, busy: false, error: null })),
  on(paymentFailed, (state, { error }) => ({ ...state, busy: false, error })),
  on(editPayment, (state) => ({ ...state, payment: null, busy: false, error: null })),
  on(clearPayments, () => initialPaymentsState),
);
