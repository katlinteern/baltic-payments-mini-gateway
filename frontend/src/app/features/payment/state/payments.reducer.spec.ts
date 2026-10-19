import { describe, expect, it } from 'vitest';
import { Transaction } from '@shared/models/transaction.model';
import { clearPayments, paymentCompleted, paymentFailed, submitPayment } from './payments.actions';
import { initialPaymentsState, paymentsReducer } from './payments.reducer';

describe('paymentsReducer', () => {
  it('tracks payment submission, completion and failure', () => {
    const submitting = paymentsReducer(
      initialPaymentsState,
      submitPayment({
        request: {
          fromIban: 'LT121000011101001000',
          toIban: 'EE382200221020145685',
          beneficiaryName: 'Bob',
          amount: 66,
          currency: 'EUR',
          reference: 'test',
        },
        idempotencyKey: 'unit-test-key-001',
      }),
    );
    expect(submitting.submitting).toBe(true);

    const transaction: Transaction = {
      id: 'tx-1',
      fromIban: 'LT121000011101001000',
      toIban: 'EE382200221020145685',
      beneficiaryName: 'Bob Tamm',
      nameCheck: 'MATCH',
      amount: 66,
      currency: 'EUR',
      reference: 'test',
      status: 'COMPLETED',
      createdAt: '2026-01-01T00:00:00Z',
    };
    expect(paymentsReducer(submitting, paymentCompleted({ transaction }))).toMatchObject({
      submitting: false,
      completedTransaction: transaction,
    });
    expect(
      paymentsReducer(submitting, paymentFailed({ error: 'insufficient funds', nameMismatch: false })),
    ).toMatchObject({
      submitting: false,
      error: 'insufficient funds',
    });
  });

  it('clears payment state', () => {
    expect(paymentsReducer({ ...initialPaymentsState, error: 'old' }, clearPayments())).toEqual(initialPaymentsState);
  });

  it('flags a name mismatch rejection so the payer can send anyway, and clears it on the next attempt', () => {
    const failed = paymentsReducer(
      initialPaymentsState,
      paymentFailed({ error: 'Name does not match', nameMismatch: true }),
    );
    expect(failed).toMatchObject({ error: 'Name does not match', nameMismatch: true });

    const retried = paymentsReducer(
      failed,
      submitPayment({ request: {} as never, idempotencyKey: 'unit-test-key-002' }),
    );
    expect(retried).toMatchObject({ error: null, nameMismatch: false, submitting: true });
  });
});
