import { createReducer, on } from '@ngrx/store';
import { PaymentIntent } from './payment.model';
import {
  cancelIntent,
  clearPayments,
  createIntent,
  editPayment,
  intentFailed,
  intentUpdated,
  loadIntent,
  startConfirmation,
} from './payments.actions';

export interface PaymentsState {
  intent: PaymentIntent | null;
  busy: boolean;
  error: string | null;
}
export const initialPaymentsState: PaymentsState = { intent: null, busy: false, error: null };

const busy = (state: PaymentsState): PaymentsState => ({ ...state, busy: true, error: null });

export const paymentsReducer = createReducer(
  initialPaymentsState,
  on(createIntent, (state) => ({ ...busy(state), intent: null })),
  on(startConfirmation, cancelIntent, loadIntent, busy),
  on(intentUpdated, (state, { intent }) => ({ ...state, intent, busy: false, error: null })),
  on(intentFailed, (state, { error }) => ({ ...state, busy: false, error })),
  on(editPayment, (state) => ({ ...state, intent: null, busy: false, error: null })),
  on(clearPayments, () => initialPaymentsState),
);
