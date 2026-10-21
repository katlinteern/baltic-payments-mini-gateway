import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Account } from '@shared/models/account.model';
import { selectAccounts } from '@features/accounts/state';
import { Payment } from '@features/payments/state/payment.model';
import { paymentFixture } from '@features/payments/state/payment.fixtures';
import {
  cancelPayment,
  clearPayments,
  createPayment,
  editPayment,
  startConfirmation,
  stopPolling,
} from '@features/payments/state/payments.actions';
import { selectPayment, selectPaymentBusy, selectPaymentError } from '@features/payments/state/payments.selectors';
import { SmartIdDialogComponent } from '@features/payments/pages/smart-id-dialog/smart-id-dialog.component';
import { PaymentComponent } from './payment.component';

const SOURCE = 'LT121000011101001000';
const filledForm = {
  fromIban: SOURCE,
  beneficiaryName: 'Bob',
  toIban: 'EE38 2200 2210 2014 5685',
  amount: '66.00',
  reference: 'rent',
};

function setup(initial: Payment | null = null) {
  const accounts = signal<Account[]>([
    { id: 'source', iban: SOURCE, currency: 'EUR', holderName: 'Alice Kask', balance: 1000 },
  ]);
  const payment: WritableSignal<Payment | null> = signal(initial);
  const busy = signal(false);
  const dispatch = vi.fn();
  const store = {
    dispatch,
    selectSignal: (selector: unknown) => {
      if (selector === selectAccounts) return accounts;
      if (selector === selectPayment) return payment;
      if (selector === selectPaymentBusy) return busy;
      if (selector === selectPaymentError) return signal(null);
      throw new Error('Unexpected selector');
    },
  };
  const closed = new Subject<boolean | undefined>();
  const closeDialog = vi.fn(() => closed.next(undefined));
  const open = vi.fn(() => ({ close: closeDialog, afterClosed: () => closed.asObservable() }));
  TestBed.configureTestingModule({
    imports: [PaymentComponent],
    providers: [
      { provide: Store, useValue: store },
      { provide: MatDialog, useValue: { open } },
    ],
  });
  const fixture = TestBed.createComponent(PaymentComponent);
  fixture.detectChanges();
  fixture.componentInstance.form.setValue(filledForm);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  const text = () => element.textContent?.replace(/\s+/g, ' ') ?? '';
  const button = (label: string) =>
    Array.from(element.querySelectorAll('button')).find((candidate) => candidate.textContent?.includes(label));
  const dispatched = (type: string) =>
    dispatch.mock.calls.map(([action]) => action).filter((action) => action.type === type);
  return {
    fixture,
    component: fixture.componentInstance,
    payment,
    busy,
    text,
    button,
    dispatched,
    element,
    open,
    closeDialog,
    closed,
  };
}

describe('PaymentComponent', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] }));
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  describe('the form', () => {
    it('starts fresh and asks for the accounts', () => {
      const { dispatched } = setup();

      expect(dispatched(clearPayments.type)).toHaveLength(1);
      expect(dispatched('[Accounts] Load')).toBeDefined();
    });

    it('shows the form while there is no prepared payment', () => {
      const { component, element } = setup();

      expect(component.view()).toBe('form');
      expect(element.querySelector('app-payment-form')).not.toBeNull();
    });

    it('prepares the payment the form hands over, and sends nothing else', () => {
      const { button, dispatched } = setup();

      button('Continue')?.click();

      expect(dispatched(createPayment.type)[0].request).toMatchObject({ beneficiaryName: 'Bob', amount: 66 });
      expect(dispatched(startConfirmation.type)).toEqual([]);
    });
  });

  describe('the review', () => {
    it('shows the review for a prepared payment and asks for confirmation', () => {
      const { fixture, payment, element, button, dispatched, component } = setup();
      payment.set(paymentFixture());
      fixture.detectChanges();

      expect(component.view()).toBe('review');
      expect(element.querySelector('app-payment-review')).not.toBeNull();

      button('Confirm with Smart-ID')?.click();

      expect(dispatched(startConfirmation.type)).toEqual([
        startConfirmation({ id: 'payment-1', acknowledgeNameMismatch: false }),
      ]);
    });

    it('passes the risk acknowledgement of a mismatching name on to the confirmation', () => {
      const { fixture, element, payment, button, dispatched } = setup();
      payment.set(paymentFixture({ nameCheck: 'NO_MATCH' }));
      fixture.detectChanges();

      (element.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
      fixture.detectChanges();
      button('Confirm with Smart-ID')?.click();

      expect(dispatched(startConfirmation.type)).toEqual([
        startConfirmation({ id: 'payment-1', acknowledgeNameMismatch: true }),
      ]);
    });

    it('withdraws the prepared payment when the payer chooses to edit it', () => {
      const { fixture, payment, button, dispatched } = setup();
      payment.set(paymentFixture());
      fixture.detectChanges();

      button('Edit')?.click();

      expect(dispatched(cancelPayment.type)).toEqual([cancelPayment({ id: 'payment-1' })]);
    });

    it('keeps what the payer typed when they go back to the form', () => {
      const { fixture, payment, component, element } = setup();
      payment.set(paymentFixture());
      fixture.detectChanges();

      payment.set(null);
      fixture.detectChanges();

      expect(component.view()).toBe('form');
      expect((element.querySelector('input[formcontrolname="beneficiaryName"]') as HTMLInputElement).value).toBe('Bob');
    });
  });

  describe('the confirmation', () => {
    const awaiting = () =>
      paymentFixture({
        status: 'AWAITING_AUTHENTICATION',
        verificationCode: '4821',
        expiresAt: '2026-10-19T10:02:00Z',
      });

    it('keeps the review on the page and asks for the approval in a dialog that cannot be dismissed by accident', () => {
      const { fixture, payment, element, button, open, component } = setup();
      payment.set(awaiting());
      fixture.detectChanges();

      expect(component.view()).toBe('authenticating');
      expect(element.querySelector('app-payment-review')).not.toBeNull();
      expect(button('Confirm with Smart-ID')?.disabled).toBe(true);
      expect(button('Edit')?.disabled).toBe(true);
      expect(open).toHaveBeenCalledTimes(1);
      expect(open).toHaveBeenCalledWith(SmartIdDialogComponent, expect.objectContaining({ disableClose: true }));
    });

    it('opens the dialog once, however often the payment is polled', () => {
      const { fixture, payment, open } = setup();
      payment.set(awaiting());
      fixture.detectChanges();
      payment.set({ ...awaiting() });
      fixture.detectChanges();

      expect(open).toHaveBeenCalledTimes(1);
    });

    it('withdraws the payment when the payer cancels in the dialog', () => {
      const { fixture, payment, dispatched, closed } = setup();
      payment.set(awaiting());
      fixture.detectChanges();

      closed.next(true);

      expect(dispatched(cancelPayment.type)).toEqual([cancelPayment({ id: 'payment-1' })]);
    });

    it('closes the dialog by itself, without cancelling, once the payment moves on', () => {
      const { fixture, payment, dispatched, closeDialog } = setup();
      payment.set(awaiting());
      fixture.detectChanges();

      payment.set(paymentFixture({ status: 'COMPLETED' }));
      fixture.detectChanges();

      expect(closeDialog).toHaveBeenCalled();
      expect(dispatched(cancelPayment.type)).toEqual([]);
    });

    it('does not open the dialog for the form or the review', () => {
      const { fixture, payment, open } = setup();
      fixture.detectChanges();
      payment.set(paymentFixture());
      fixture.detectChanges();

      expect(open).not.toHaveBeenCalled();
    });

    it('shows the payer which of the three steps they are on', () => {
      const { fixture, payment, element } = setup();
      const current = () => element.querySelector('[aria-current="step"]')?.textContent?.trim();
      expect(current()).toContain('Details');

      payment.set(paymentFixture());
      fixture.detectChanges();
      expect(current()).toContain('Review');

      payment.set(awaiting());
      fixture.detectChanges();
      expect(current()).toContain('Confirm');
    });
  });

  describe('when the payment ended without money moving', () => {
    it.each([
      [
        paymentFixture({ status: 'FAILED', failureMessage: 'Balance is too low for this payment' }),
        'Balance is too low',
      ],
      [paymentFixture({ status: 'FAILED' }), 'The payment was not completed.'],
      [paymentFixture({ status: 'EXPIRED' }), 'expired'],
      [paymentFixture({ status: 'CANCELLED' }), 'cancelled'],
    ])('explains it and lets the payer edit the payment (%#)', (ended, expected) => {
      const { fixture, payment, text, button, dispatched, component } = setup();
      payment.set(ended);
      fixture.detectChanges();

      expect(component.view()).toBe('ended');
      expect(text()).toContain('Payment not completed');
      expect(text()).toContain(expected);

      button('Edit payment')?.click();
      expect(dispatched(editPayment.type)).toEqual([editPayment()]);
    });

    it('lets the payer try the same payment again without retyping it', () => {
      const { fixture, payment, button, dispatched } = setup();
      payment.set(paymentFixture({ status: 'EXPIRED' }));
      fixture.detectChanges();

      button('Try again')?.click();

      expect(dispatched(createPayment.type)).toHaveLength(1);
      expect(dispatched(createPayment.type)[0].request).toMatchObject({ beneficiaryName: 'Bob', amount: 66 });
    });

    it('does not try again while another request is in progress', () => {
      const { fixture, payment, busy, component, dispatched } = setup();
      payment.set(paymentFixture({ status: 'EXPIRED' }));
      busy.set(true);
      fixture.detectChanges();

      component.retry();

      expect(dispatched(createPayment.type)).toEqual([]);
    });

    it('says a completed payment is being opened instead of offering to start again', () => {
      const { fixture, payment, text, button } = setup();
      payment.set(paymentFixture({ status: 'COMPLETED' }));
      fixture.detectChanges();

      expect(text()).toContain('Opening your receipt');
      expect(button('Try again')).toBeUndefined();
      expect(button('Edit payment')).toBeUndefined();
    });
  });

  it('stops asking the server when the payer leaves the page', () => {
    const { fixture, dispatched } = setup();

    fixture.destroy();

    expect(dispatched(stopPolling.type)).toHaveLength(1);
  });

  it('closes the approval dialog when the payer leaves the page', () => {
    const { fixture, payment, closeDialog } = setup();
    payment.set(paymentFixture({ status: 'AWAITING_AUTHENTICATION', verificationCode: '4821' }));
    fixture.detectChanges();

    fixture.destroy();

    expect(closeDialog).toHaveBeenCalled();
  });
});
