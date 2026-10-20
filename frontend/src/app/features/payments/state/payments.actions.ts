import { createAction, props } from '@ngrx/store';
import { PaymentIntent, PaymentRequest } from './payment-intent.model';

export const createIntent = createAction('[Payments] Create Intent', props<{ request: PaymentRequest }>());
export const startConfirmation = createAction(
  '[Payments] Start Confirmation',
  props<{ id: string; acknowledgeNameMismatch: boolean }>(),
);
export const cancelIntent = createAction('[Payments] Cancel Intent', props<{ id: string }>());
export const loadIntent = createAction('[Payments] Load Intent', props<{ id: string }>());
export const intentUpdated = createAction('[Payments] Intent Updated', props<{ intent: PaymentIntent }>());
export const intentFailed = createAction('[Payments] Intent Failed', props<{ error: string }>());
export const editPayment = createAction('[Payments] Edit');
export const stopPolling = createAction('[Payments] Stop Polling');
export const clearPayments = createAction('[Payments] Clear');
