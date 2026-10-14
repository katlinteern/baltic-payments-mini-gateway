import { createAction, props } from '@ngrx/store';
import { PaymentRequest, Transaction } from '../../../core/models';

export const submitPayment = createAction('[Payments] Submit', props<{ request: PaymentRequest; idempotencyKey: string }>());
export const paymentCompleted = createAction('[Payments] Completed', props<{ transaction: Transaction }>());
export const paymentFailed = createAction('[Payments] Failed', props<{ error: string }>());
export const clearPayments = createAction('[Payments] Clear');