import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Account } from '../../../shared/models/account.model';
import { selectAccounts } from '../../accounts/state/accounts.selectors';
import { submitPayment } from '../state/payments.actions';
import { selectCompletedTransaction, selectPaymentError, selectPaymentSubmitting } from '../state/payments.selectors';
import { PaymentComponent } from './payment.component';

describe('PaymentComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('dispatches the exact entered amount in the payment request', async () => {
    const accounts = signal<Account[]>([
      { id: 'source', iban: 'LT121000011101001000', currency: 'EUR', balance: 1000 },
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
    fixture.componentInstance.form.setValue({
      fromIban: 'LT121000011101001000',
      toIban: 'EE382200221020145685',
      amount: 66,
      reference: 'amount-regression',
    });
    fixture.componentInstance.submit();

    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({
      type: submitPayment.type,
      request: expect.objectContaining({ amount: 66, currency: 'EUR' }),
    }));
  });
});