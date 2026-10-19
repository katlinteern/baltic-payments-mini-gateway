import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Account } from '@shared/models/account.model';
import { Transaction } from '@shared/models/transaction.model';
import { selectAccounts } from '@features/accounts/state';
import { submitPayment } from '../state/payments.actions';
import {
  selectCompletedTransaction,
  selectNameMismatch,
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
  const nameMismatch = signal(false);
  const completedTransaction: WritableSignal<Transaction | null> = signal(null);
  const dispatch = vi.fn();
  const navigate = vi.fn().mockResolvedValue(true);
  const open = vi.fn().mockReturnValue({ afterClosed: () => of(true) });
  const store = {
    dispatch,
    selectSignal: (selector: unknown) => {
      if (selector === selectAccounts) return accounts;
      if (selector === selectNameMismatch) return nameMismatch;
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
      { provide: MatDialog, useValue: { open } },
    ],
  })
    .overrideComponent(PaymentComponent, { set: { template: '' } })
    .compileComponents();

  const fixture = TestBed.createComponent(PaymentComponent);
  fixture.detectChanges();
  fixture.componentInstance.form.setValue(filledForm);
  const dispatched = (type: string) =>
    dispatch.mock.calls.map(([action]) => action).filter((action) => action.type === type);
  return {
    component: fixture.componentInstance,
    completedTransaction,
    nameMismatch,
    navigate,
    open,
    fixture,
    dispatched,
  };
}

describe('PaymentComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('sends the exact amount with a normalised IBAN, without a name mismatch acknowledgement', async () => {
    const { component, dispatched } = await createComponent();

    component.submit();

    expect(dispatched(submitPayment.type)[0].request).toMatchObject({
      amount: 66,
      currency: 'EUR',
      toIban: 'EE382200221020145685',
    });
    expect(dispatched(submitPayment.type)[0].request.proceedDespiteNameMismatch).toBeUndefined();
  });

  it('does not send an invalid form', async () => {
    const { component, dispatched } = await createComponent();
    component.form.patchValue({ toIban: '' });

    component.submit();

    expect(dispatched(submitPayment.type)).toEqual([]);
  });

  it('sends the same payment again with an explicit acknowledgement after a name mismatch', async () => {
    const { component, dispatched } = await createComponent();
    component.submit();

    component.sendAnyway();

    const [first, second] = dispatched(submitPayment.type);
    expect(second.request).toEqual({ ...first.request, proceedDespiteNameMismatch: true });
    expect(second.idempotencyKey).not.toBe(first.idempotencyKey);
  });

  it('asks the payer in a dialog after a name mismatch and sends anyway once confirmed', async () => {
    const { component, nameMismatch, open, fixture, dispatched } = await createComponent();
    component.submit();

    nameMismatch.set(true);
    fixture.detectChanges();

    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][1].data).toMatchObject({ beneficiaryName: 'Bob', toIban: 'EE382200221020145685' });
    expect(dispatched(submitPayment.type)[1].request.proceedDespiteNameMismatch).toBe(true);
  });

  it('does not send the payment when the payer cancels the dialog', async () => {
    const { component, nameMismatch, open, fixture, dispatched } = await createComponent();
    open.mockReturnValue({ afterClosed: () => of(false) });
    component.submit();

    nameMismatch.set(true);
    fixture.detectChanges();

    expect(dispatched(submitPayment.type)).toHaveLength(1);
  });

  it('does nothing on send anyway before a payment was submitted', async () => {
    const { component, dispatched } = await createComponent();

    component.sendAnyway();

    expect(dispatched(submitPayment.type)).toEqual([]);
  });

  it('retries an unchanged payment with the same idempotency key', async () => {
    const { component, dispatched } = await createComponent();

    component.submit();
    component.submit();

    const [first, second] = dispatched(submitPayment.type).map((action) => action.idempotencyKey);
    expect(first).toBe(second);
  });

  it('uses a new idempotency key when the payer edits the payment', async () => {
    const { component, dispatched } = await createComponent();
    component.submit();

    component.form.patchValue({ amount: 67 });
    component.submit();

    const [first, second] = dispatched(submitPayment.type).map((action) => action.idempotencyKey);
    expect(first).not.toBe(second);
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
});
