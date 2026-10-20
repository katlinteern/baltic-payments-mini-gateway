import { PaymentIntent } from './payment-intent.model';

/** A prepared payment from Alice to Bob; tests override what they care about. */
export function intentFixture(overrides: Partial<PaymentIntent> = {}): PaymentIntent {
  return {
    id: 'intent-1',
    status: 'READY',
    fromIban: 'LT121000011101001000',
    toIban: 'EE382200221020145685',
    beneficiaryName: 'Bob Tamm',
    amount: 10,
    currency: 'EUR',
    reference: 'rent',
    nameCheck: 'MATCH',
    registeredName: null,
    verificationCode: null,
    expiresAt: '2026-10-19T10:10:00Z',
    transactionId: null,
    completedAt: null,
    balanceAfter: null,
    failureCode: null,
    failureMessage: null,
    ...overrides,
  };
}
