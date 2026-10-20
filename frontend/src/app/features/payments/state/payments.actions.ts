import { createAction, props } from '@ngrx/store';
import { PaymentIntent, PaymentRequest } from './payment-intent.model';

/** The payer asked to continue: the server checks the accounts and the beneficiary name, and no money moves. */
export const createIntent = createAction('[Payments] Create Intent', props<{ request: PaymentRequest }>());
/** The payer confirmed: the authentication app is asked to approve, and the server shows a code to compare. */
export const startConfirmation = createAction(
  '[Payments] Start Confirmation',
  props<{ id: string; acknowledgeNameMismatch: boolean }>(),
);
export const cancelIntent = createAction('[Payments] Cancel Intent', props<{ id: string }>());
export const loadIntent = createAction('[Payments] Load Intent', props<{ id: string }>());
export const intentUpdated = createAction('[Payments] Intent Updated', props<{ intent: PaymentIntent }>());
export const intentFailed = createAction('[Payments] Intent Failed', props<{ error: string }>());
/** Back to the form, which still holds what the payer typed. */
export const editPayment = createAction('[Payments] Edit');
export const stopPolling = createAction('[Payments] Stop Polling');
export const clearPayments = createAction('[Payments] Clear');
