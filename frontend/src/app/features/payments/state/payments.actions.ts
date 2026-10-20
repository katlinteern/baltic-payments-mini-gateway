import { createAction, props } from '@ngrx/store';
import { Payment, PaymentRequest } from './payment.model';

export const createPayment = createAction('[Payments] Create Payment', props<{ request: PaymentRequest }>());
export const startConfirmation = createAction(
  '[Payments] Start Confirmation',
  props<{ id: string; acknowledgeNameMismatch: boolean }>(),
);
export const cancelPayment = createAction('[Payments] Cancel Payment', props<{ id: string }>());
export const loadPayment = createAction('[Payments] Load Payment', props<{ id: string }>());
export const paymentUpdated = createAction('[Payments] Payment Updated', props<{ payment: Payment }>());
export const paymentFailed = createAction('[Payments] Payment Failed', props<{ error: string }>());
export const editPayment = createAction('[Payments] Edit');
export const stopPolling = createAction('[Payments] Stop Polling');
export const clearPayments = createAction('[Payments] Clear');
