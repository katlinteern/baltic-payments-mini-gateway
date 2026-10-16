import { describe, expect, it } from 'vitest';
import { Transaction } from '@shared/models/transaction.model';
import {
  clearPayments,
  editPayment,
  payeeVerificationFailed,
  payeeVerified,
  paymentCompleted,
  paymentFailed,
  submitPayment,
  verifyPayee,
} from './payments.actions';
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
    expect(paymentsReducer(submitting, paymentFailed({ error: 'insufficient funds' }))).toMatchObject({
      submitting: false,
      error: 'insufficient funds',
    });
  });

  it('clears payment state', () => {
    expect(paymentsReducer({ ...initialPaymentsState, error: 'old' }, clearPayments())).toEqual(initialPaymentsState);
  });

  it('shows the review step once the payee is verified and hides it again on edit', () => {
    const verifying = paymentsReducer(
      initialPaymentsState,
      verifyPayee({ toIban: 'EE382200221020145685', beneficiaryName: 'Bob' }),
    );
    expect(verifying.verifying).toBe(true);

    const verified = paymentsReducer(
      verifying,
      payeeVerified({ verification: { result: 'CLOSE_MATCH', registeredName: 'Bob Tamm' } }),
    );
    expect(verified).toMatchObject({
      verifying: false,
      verification: { result: 'CLOSE_MATCH', registeredName: 'Bob Tamm' },
    });

    expect(paymentsReducer(verified, editPayment()).verification).toBeNull();
  });

  it('keeps the review step open when the payment fails, so the payer can retry or edit', () => {
    const reviewing = { ...initialPaymentsState, verification: { result: 'MATCH' as const, registeredName: null } };

    const failed = paymentsReducer(reviewing, paymentFailed({ error: 'Balance is too low' }));

    expect(failed.verification).not.toBeNull();
    expect(failed.error).toBe('Balance is too low');
  });

  it('leaves the review step when the payment completes', () => {
    const reviewing = { ...initialPaymentsState, verification: { result: 'MATCH' as const, registeredName: null } };

    expect(
      paymentsReducer(reviewing, paymentCompleted({ transaction: { id: 'tx-2' } as Transaction })).verification,
    ).toBeNull();
  });

  it('reports a failed verification without opening the review step', () => {
    const failed = paymentsReducer(initialPaymentsState, payeeVerificationFailed({ error: 'Too many requests.' }));

    expect(failed).toMatchObject({ verifying: false, verification: null, error: 'Too many requests.' });
  });
});
