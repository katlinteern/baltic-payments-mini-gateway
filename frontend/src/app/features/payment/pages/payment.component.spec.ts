import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Account } from '@shared/models/account.model';
import { selectAccounts } from '@features/accounts/state';
import { submitPayment } from '../state/payments.actions';
import { selectCompletedTransaction, selectPaymentError, selectPaymentSubmitting } from '../state/payments.selectors';
import { PaymentComponent } from './payment.component';

const SOURCE = 'LT121000011101001000';
const filledForm = {
  fromIban: SOURCE,
  beneficiaryName: 'Bob',
  toIban: 'EE382200221020145685',
  amount: 66,
  reference: 'amount-regression',
};

async function createComponent() {
  const accounts = signal<Account[]>([
    { id: 'source', iban: SOURCE, currency: 'EUR', holderName: 'Alice Kask', balance: 1000 },
  ]);
  const dispatch = vi.fn();
  const store = {
    dispatch,
    selectSignal: (selector: unknown) => {
      if (selector === selectAccounts) return accounts;
      if (selector === selectPaymentSubmitting) return signal(false);
      if (selector === selectPaymentError) return signal(null);
      if (selector === selectCompletedTransaction) return signal(null);
      throw new Error('Unexpected selector');
    },
  };

  await TestBed.configureTestingModule({
    imports: [PaymentComponent],
    providers: [{ provide: Store, useValue: store }],
  })
    .overrideComponent(PaymentComponent, { set: { template: '' } })
    .compileComponents();

  const fixture = TestBed.createComponent(PaymentComponent);
  fixture.detectChanges();
  fixture.componentInstance.form.setValue(filledForm);
  const submittedKeys = () =>
    dispatch.mock.calls
      .map(([action]) => action)
      .filter((action) => action.type === submitPayment.type)
      .map((action) => action.idempotencyKey as string);
  return { component: fixture.componentInstance, dispatch, submittedKeys };
}

describe('PaymentComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('dispatches the exact entered amount in the payment request', async () => {
    const { component, dispatch } = await createComponent();

    component.submit();

    expect(dispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: submitPayment.type,
        request: expect.objectContaining({ amount: 66, currency: 'EUR' }),
      }),
    );
  });

  it('retries an unchanged payment with the same idempotency key', async () => {
    const { component, submittedKeys } = await createComponent();

    component.submit();
    component.submit();

    const [first, second] = submittedKeys();
    expect(first).toBe(second);
  });

  it('uses a new idempotency key when the user edits the payment after a failed attempt', async () => {
    const { component, submittedKeys } = await createComponent();

    component.submit();
    component.form.patchValue({ amount: 67 });
    component.submit();

    const [first, second] = submittedKeys();
    expect(first).not.toBe(second);
  });
});
