import { describe, expect, it } from 'vitest';
import { paymentFixture } from './payment.fixtures';
import {
  cancelPayment,
  clearPayments,
  createPayment,
  editPayment,
  loadPayment,
  paymentFailed,
  paymentUpdated,
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
    expect(paymentsReducer(initialPaymentsState, createPayment({ request })).busy).toBe(true);
    expect(
      paymentsReducer(initialPaymentsState, startConfirmation({ id: 'i', acknowledgeNameMismatch: false })).busy,
    ).toBe(true);
    expect(paymentsReducer(initialPaymentsState, cancelPayment({ id: 'i' })).busy).toBe(true);
    expect(paymentsReducer(initialPaymentsState, loadPayment({ id: 'i' })).busy).toBe(true);
  });

  it('keeps the latest state of the payment, including every poll', () => {
    const awaiting = paymentFixture({ status: 'AWAITING_AUTHENTICATION', verificationCode: '4821' });
    const busy = paymentsReducer(
      initialPaymentsState,
      startConfirmation({ id: 'payment-1', acknowledgeNameMismatch: false }),
    );

    const state = paymentsReducer(busy, paymentUpdated({ payment: awaiting }));

    expect(state).toEqual({ payment: awaiting, busy: false, error: null });
    expect(
      paymentsReducer(state, paymentUpdated({ payment: paymentFixture({ status: 'COMPLETED' }) })).payment?.status,
    ).toBe('COMPLETED');
  });

  it('starts a new payment without the previous one', () => {
    const withPayment = { ...initialPaymentsState, payment: paymentFixture() };

    expect(paymentsReducer(withPayment, createPayment({ request })).payment).toBeNull();
  });

  it('shows a failure without losing the payment being worked on', () => {
    const withPayment = { ...initialPaymentsState, payment: paymentFixture(), busy: true };

    expect(paymentsReducer(withPayment, paymentFailed({ error: 'boom' }))).toEqual({
      payment: withPayment.payment,
      busy: false,
      error: 'boom',
    });
  });

  it('returns to the form and clears everything on request', () => {
    const withPayment = { payment: paymentFixture(), busy: true, error: 'old' };

    expect(paymentsReducer(withPayment, editPayment())).toEqual(initialPaymentsState);
    expect(paymentsReducer(withPayment, clearPayments())).toEqual(initialPaymentsState);
  });
});
