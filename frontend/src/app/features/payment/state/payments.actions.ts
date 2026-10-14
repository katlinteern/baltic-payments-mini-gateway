import { createAction, props } from '@ngrx/store';
import { Transaction } from '../../../shared/models/transaction.model';
import { PaymentRequest } from './payment-request.model';

export const submitPayment = createAction('[Payments] Submit', props<{ request: PaymentRequest; idempotencyKey: string }>());
export const paymentCompleted = createAction('[Payments] Completed', props<{ transaction: Transaction }>());
export const paymentFailed = createAction('[Payments] Failed', props<{ error: string }>());
export const clearPayments = createAction('[Payments] Clear');