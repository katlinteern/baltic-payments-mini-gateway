import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Account } from '@shared/models/account.model';
import { Transaction } from '@shared/models/transaction.model';
import { selectAccounts } from '@features/accounts/state';
import { PayeeVerification } from '../state/payee-verification.model';
import { editPayment, submitPayment, verifyPayee } from '../state/payments.actions';
import {
  selectCompletedTransaction,
  selectPayeeVerification,
  selectPayeeVerifying,
  selectPaymentError,
  selectPaymentSubmitting,
} from '../state/payments.selectors';
import { PaymentComponent } from './payment.component';

const SOURCE = 'LT121000011101001000';
const filledForm = {
  fromIban: SOURCE,
  beneficiaryName: 'Bob',
  toIban: 'EE38 2200 2210 2014 5685',
  amount: 66,
  reference: 'amount-regression',
};

async function createComponent() {
  const accounts = signal<Account[]>([
    { id: 'source', iban: SOURCE, currency: 'EUR', holderName: 'Alice Kask', balance: 1000 },
  ]);
  const verification: WritableSignal<PayeeVerification | null> = signal(null);
  const completedTransaction: WritableSignal<Transaction | null> = signal(null);
  const dispatch = vi.fn();
  const navigate = vi.fn().mockResolvedValue(true);
  const store = {
    dispatch,
    selectSignal: (selector: unknown) => {
      if (selector === selectAccounts) return accounts;
      if (selector === selectPayeeVerification) return verification;
      if (selector === selectPayeeVerifying) return signal(false);
      if (selector === selectPaymentSubmitting) return signal(false);
      if (selector === selectPaymentError) return signal(null);
      if (selector === selectCompletedTransaction) return completedTransaction;
      throw new Error('Unexpected selector');
    },
  };

  await TestBed.configureTestingModule({
    imports: [PaymentComponent],
    providers: [
      { provide: Store, useValue: store },
      { provide: Router, useValue: { navigate } },
    ],
  })
    .overrideComponent(PaymentComponent, { set: { template: '' } })
    .compileComponents();

  const fixture = TestBed.createComponent(PaymentComponent);
  fixture.detectChanges();
  fixture.componentInstance.form.setValue(filledForm);
  const dispatched = (type: string) =>
    dispatch.mock.calls.map(([action]) => action).filter((action) => action.type === type);
  return { component: fixture.componentInstance, verification, completedTransaction, navigate, fixture, dispatched };
}

describe('PaymentComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('verifies the payee before anything is sent, with a normalised IBAN', async () => {
    const { component, dispatched } = await createComponent();

    component.review();

    expect(dispatched(verifyPayee.type)).toEqual([
      verifyPayee({ toIban: 'EE382200221020145685', beneficiaryName: 'Bob' }),
    ]);
    expect(dispatched(submitPayment.type)).toEqual([]);
  });

  it('sends the exact reviewed amount once the payer confirms', async () => {
    const { component, verification, dispatched } = await createComponent();
    component.review();
    verification.set({ result: 'MATCH', registeredName: null });

    component.confirm();

    expect(dispatched(submitPayment.type)[0].request).toMatchObject({
      amount: 66,
      currency: 'EUR',
      toIban: 'EE382200221020145685',
    });
    expect(dispatched(submitPayment.type)[0].request.proceedDespiteNameMismatch).toBeUndefined();
  });

  it('does not send anything before a verification result is available', async () => {
    const { component, dispatched } = await createComponent();
    component.review();

    component.confirm();

    expect(dispatched(submitPayment.type)).toEqual([]);
  });

  it('acknowledges a name mismatch explicitly when the payer sends anyway', async () => {
    const { component, verification, dispatched } = await createComponent();
    component.review();
    verification.set({ result: 'NO_MATCH', registeredName: null });

    component.confirm();

    expect(dispatched(submitPayment.type)[0].request.proceedDespiteNameMismatch).toBe(true);
  });

  it('lets the payer go back and edit the details', async () => {
    const { component, dispatched } = await createComponent();

    component.edit();

    expect(dispatched(editPayment.type)).toHaveLength(1);
  });

  it('redirects to the accounts page after a payment completes', async () => {
    const { completedTransaction, navigate, fixture } = await createComponent();

    completedTransaction.set({
      id: 'transaction-1',
      fromIban: SOURCE,
      toIban: 'EE382200221020145685',
      beneficiaryName: 'Bob',
      nameCheck: 'MATCH',
      amount: 66,
      currency: 'EUR',
      reference: 'amount-regression',
      status: 'COMPLETED',
      createdAt: '2026-10-19T07:23:00Z',
    });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/accounts']);
  });

  it('retries an unchanged payment with the same idempotency key', async () => {
    const { component, verification, dispatched } = await createComponent();
    component.review();
    verification.set({ result: 'MATCH', registeredName: null });

    component.confirm();
    component.confirm();

    const [first, second] = dispatched(submitPayment.type).map((action) => action.idempotencyKey);
    expect(first).toBe(second);
  });

  it('uses a new idempotency key when the payer edits and re-reviews the payment', async () => {
    const { component, verification, dispatched } = await createComponent();
    component.review();
    verification.set({ result: 'MATCH', registeredName: null });
    component.confirm();

    component.form.patchValue({ amount: 67 });
    component.review();
    component.confirm();

    const [first, second] = dispatched(submitPayment.type).map((action) => action.idempotencyKey);
    expect(first).not.toBe(second);
  });

  it('uses a new idempotency key when the payer decides to send despite a mismatch', async () => {
    const { component, verification, dispatched } = await createComponent();
    component.review();
    verification.set({ result: 'MATCH', registeredName: null });
    component.confirm();
    verification.set({ result: 'NO_MATCH', registeredName: null });

    component.confirm();

    const [first, second] = dispatched(submitPayment.type).map((action) => action.idempotencyKey);
    expect(first).not.toBe(second);
  });
});
