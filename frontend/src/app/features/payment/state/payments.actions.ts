import { createAction, props } from '@ngrx/store';
import { Transaction } from '@shared/models/transaction.model';
import { PayeeVerification } from './payee-verification.model';
import { PaymentRequest } from './payment-request.model';

export const verifyPayee = createAction(
  '[Payments] Verify payee',
  props<{ toIban: string; beneficiaryName: string }>(),
);
export const payeeVerified = createAction('[Payments] Payee verified', props<{ verification: PayeeVerification }>());
export const payeeVerificationFailed = createAction('[Payments] Payee verification failed', props<{ error: string }>());
export const editPayment = createAction('[Payments] Edit');
export const submitPayment = createAction(
  '[Payments] Submit',
  props<{ request: PaymentRequest; idempotencyKey: string }>(),
);
export const paymentCompleted = createAction('[Payments] Completed', props<{ transaction: Transaction }>());
export const paymentFailed = createAction('[Payments] Failed', props<{ error: string }>());
export const clearPayments = createAction('[Payments] Clear');
