import { describe, expect, it } from 'vitest';
import { intentFixture } from './payment-intent.fixtures';
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
import { initialPaymentsState, paymentsReducer } from './payments.reducer';

const request = {
  fromIban: 'LT121000011101001000',
  toIban: 'EE382200221020145685',
  beneficiaryName: 'Bob Tamm',
  amount: 10,
  currency: 'EUR',
  reference: 'rent',
};

describe('paymentsReducer', () => {
  it('is busy while a payment is prepared, confirmed, cancelled or loaded', () => {
    expect(paymentsReducer(initialPaymentsState, createIntent({ request })).busy).toBe(true);
    expect(
      paymentsReducer(initialPaymentsState, startConfirmation({ id: 'i', acknowledgeNameMismatch: false })).busy,
    ).toBe(true);
    expect(paymentsReducer(initialPaymentsState, cancelIntent({ id: 'i' })).busy).toBe(true);
    expect(paymentsReducer(initialPaymentsState, loadIntent({ id: 'i' })).busy).toBe(true);
  });

  it('keeps the latest state of the intent, including every poll', () => {
    const awaiting = intentFixture({ status: 'AWAITING_AUTHENTICATION', verificationCode: '4821' });
    const busy = paymentsReducer(
      initialPaymentsState,
      startConfirmation({ id: 'intent-1', acknowledgeNameMismatch: false }),
    );

    const state = paymentsReducer(busy, intentUpdated({ intent: awaiting }));

    expect(state).toEqual({ intent: awaiting, busy: false, error: null });
    expect(
      paymentsReducer(state, intentUpdated({ intent: intentFixture({ status: 'COMPLETED' }) })).intent?.status,
    ).toBe('COMPLETED');
  });

  it('starts a new payment without the previous one', () => {
    const withIntent = { ...initialPaymentsState, intent: intentFixture() };

    expect(paymentsReducer(withIntent, createIntent({ request })).intent).toBeNull();
  });

  it('shows a failure without losing the intent being worked on', () => {
    const withIntent = { ...initialPaymentsState, intent: intentFixture(), busy: true };

    expect(paymentsReducer(withIntent, intentFailed({ error: 'boom' }))).toEqual({
      intent: withIntent.intent,
      busy: false,
      error: 'boom',
    });
  });

  it('returns to the form and clears everything on request', () => {
    const withIntent = { intent: intentFixture(), busy: true, error: 'old' };

    expect(paymentsReducer(withIntent, editPayment())).toEqual(initialPaymentsState);
    expect(paymentsReducer(withIntent, clearPayments())).toEqual(initialPaymentsState);
  });
});
