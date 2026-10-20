import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Account } from '@shared/models/account.model';
import { selectAccounts } from '@features/accounts/state';
import { PaymentIntent } from '@features/payments/state/payment-intent.model';
import { intentFixture } from '@features/payments/state/payment-intent.fixtures';
import {
  cancelIntent,
  clearPayments,
  createIntent,
  editPayment,
  startConfirmation,
  stopPolling,
} from '@features/payments/state/payments.actions';
import { selectIntent, selectPaymentBusy, selectPaymentError } from '@features/payments/state/payments.selectors';
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

function setup(initial: PaymentIntent | null = null) {
  const accounts = signal<Account[]>([
    { id: 'source', iban: SOURCE, currency: 'EUR', holderName: 'Alice Kask', balance: 1000 },
  ]);
  const intent: WritableSignal<PaymentIntent | null> = signal(initial);
  const busy = signal(false);
  const dispatch = vi.fn();
  const store = {
    dispatch,
    selectSignal: (selector: unknown) => {
      if (selector === selectAccounts) return accounts;
      if (selector === selectIntent) return intent;
      if (selector === selectPaymentBusy) return busy;
      if (selector === selectPaymentError) return signal(null);
      throw new Error('Unexpected selector');
    },
  };
  // The Smart-ID dialog is replaced by a stand-in whose closing the test controls.
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
  const element = fixture.nativeElement as HTMLElement;
  const text = () => element.textContent?.replace(/\s+/g, ' ') ?? '';
  const button = (label: string) =>
    Array.from(element.querySelectorAll('button')).find((candidate) => candidate.textContent?.includes(label));
  const dispatched = (type: string) =>
    dispatch.mock.calls.map(([action]) => action).filter((action) => action.type === type);
  return {
    fixture,
    component: fixture.componentInstance,
    intent,
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

    it('prepares the payment with the exact amount and a normalised IBAN, and sends nothing else', () => {
      const { component, dispatched } = setup();

      component.submit();

      expect(dispatched(createIntent.type)[0].request).toEqual({
        fromIban: SOURCE,
        beneficiaryName: 'Bob',
        toIban: 'EE382200221020145685',
        amount: 66,
        currency: 'EUR',
        reference: 'rent',
      });
      expect(dispatched(startConfirmation.type)).toEqual([]);
    });

    it('reads the amount as typed, whatever decimal separator was used', () => {
      const { component, dispatched } = setup();
      component.form.patchValue({ amount: '10,5' });

      component.submit();

      expect(dispatched(createIntent.type)[0].request.amount).toBe(10.5);
    });

    it('does not prepare an invalid form or a second payment while one is in progress', () => {
      const { component, busy, dispatched } = setup();
      component.form.patchValue({ toIban: '' });
      component.submit();
      component.form.patchValue({ toIban: 'EE382200221020145685' });
      busy.set(true);
      component.submit();

      expect(dispatched(createIntent.type)).toEqual([]);
    });

    it('shows the form while there is no prepared payment', () => {
      const { component, text } = setup();

      expect(component.view()).toBe('form');
      expect(text()).toContain('New payment');
    });
  });

  describe('the review', () => {
    it('shows what the payer is about to pay and the name check, then asks for confirmation', () => {
      const { fixture, intent, text, button, dispatched, component } = setup();
      intent.set(intentFixture());
      fixture.detectChanges();

      expect(component.view()).toBe('review');
      expect(text()).toContain('Review payment');
      expect(text()).toContain('Bob Tamm');
      expect(text()).toContain('10.00 EUR');
      expect(text()).toContain('The name matches the account holder.');

      button('Confirm with Smart-ID')?.click();

      expect(dispatched(startConfirmation.type)).toEqual([
        startConfirmation({ id: 'intent-1', acknowledgeNameMismatch: false }),
      ]);
    });

    it('shows the registered name for a close match so the payer can check it', () => {
      const { fixture, intent, text } = setup();
      intent.set(intentFixture({ nameCheck: 'CLOSE_MATCH', registeredName: 'Bob Tamm-Kask' }));
      fixture.detectChanges();

      expect(text()).toContain('registered as Bob Tamm-Kask');
    });

    it('does not let a mismatching name through until the payer acknowledges the risk', () => {
      const { fixture, component, intent, button, dispatched } = setup();
      intent.set(intentFixture({ nameCheck: 'NO_MATCH' }));
      fixture.detectChanges();

      expect(button('Confirm with Smart-ID')?.disabled).toBe(true);
      component.confirm();
      expect(dispatched(startConfirmation.type)).toEqual([]);

      component.acknowledged.set(true);
      fixture.detectChanges();
      expect(button('Confirm with Smart-ID')?.disabled).toBe(false);
      component.confirm();
      expect(dispatched(startConfirmation.type)).toEqual([
        startConfirmation({ id: 'intent-1', acknowledgeNameMismatch: true }),
      ]);
    });

    it('withdraws the prepared payment when the payer chooses to edit it', () => {
      const { fixture, intent, button, dispatched } = setup();
      intent.set(intentFixture());
      fixture.detectChanges();

      button('Edit')?.click();

      expect(dispatched(cancelIntent.type)).toEqual([cancelIntent({ id: 'intent-1' })]);
    });
  });

  describe('the confirmation', () => {
    const awaiting = () =>
      intentFixture({
        status: 'AWAITING_AUTHENTICATION',
        verificationCode: '4821',
        expiresAt: '2026-10-19T10:02:00Z',
      });

    it('keeps the review on the page and asks for the approval in a dialog that cannot be dismissed by accident', () => {
      const { fixture, intent, text, button, open, component } = setup();
      intent.set(awaiting());
      fixture.detectChanges();

      expect(component.view()).toBe('authenticating');
      expect(text()).toContain('Review payment');
      expect(button('Confirm with Smart-ID')?.disabled).toBe(true);
      expect(button('Edit')?.disabled).toBe(true);
      expect(open).toHaveBeenCalledTimes(1);
      expect(open).toHaveBeenCalledWith(SmartIdDialogComponent, expect.objectContaining({ disableClose: true }));
    });

    it('opens the dialog once, however often the payment is polled', () => {
      const { fixture, intent, open } = setup();
      intent.set(awaiting());
      fixture.detectChanges();
      intent.set({ ...awaiting() });
      fixture.detectChanges();

      expect(open).toHaveBeenCalledTimes(1);
    });

    it('withdraws the payment when the payer cancels in the dialog', () => {
      const { fixture, intent, dispatched, closed } = setup();
      intent.set(awaiting());
      fixture.detectChanges();

      closed.next(true);

      expect(dispatched(cancelIntent.type)).toEqual([cancelIntent({ id: 'intent-1' })]);
    });

    it('closes the dialog by itself, without cancelling, once the payment moves on', () => {
      const { fixture, intent, dispatched, closeDialog } = setup();
      intent.set(awaiting());
      fixture.detectChanges();

      intent.set(intentFixture({ status: 'COMPLETED' }));
      fixture.detectChanges();

      expect(closeDialog).toHaveBeenCalled();
      expect(dispatched(cancelIntent.type)).toEqual([]);
    });

    it('does not open the dialog for the form or the review', () => {
      const { fixture, intent, open } = setup();
      fixture.detectChanges();
      intent.set(intentFixture());
      fixture.detectChanges();

      expect(open).not.toHaveBeenCalled();
    });

    it('shows the payer which of the three steps they are on', () => {
      const { fixture, intent, element } = setup();
      const current = () => element.querySelector('[aria-current="step"]')?.textContent?.trim();
      expect(current()).toContain('Details');

      intent.set(intentFixture());
      fixture.detectChanges();
      expect(current()).toContain('Review');

      intent.set(awaiting());
      fixture.detectChanges();
      expect(current()).toContain('Confirm');
    });
  });

  describe('when the payment ended without money moving', () => {
    it.each([
      [
        intentFixture({ status: 'FAILED', failureMessage: 'Balance is too low for this payment' }),
        'Balance is too low',
      ],
      [intentFixture({ status: 'FAILED' }), 'The payment was not completed.'],
      [intentFixture({ status: 'EXPIRED' }), 'expired'],
      [intentFixture({ status: 'CANCELLED' }), 'cancelled'],
    ])('explains it and lets the payer edit the payment (%#)', (ended, expected) => {
      const { fixture, intent, text, button, dispatched, component } = setup();
      intent.set(ended);
      fixture.detectChanges();

      expect(component.view()).toBe('ended');
      expect(text()).toContain('Payment not completed');
      expect(text()).toContain(expected);

      button('Edit payment')?.click();
      expect(dispatched(editPayment.type)).toEqual([editPayment()]);
    });

    it('lets the payer try the same payment again without retyping it', () => {
      const { fixture, intent, button, dispatched } = setup();
      intent.set(intentFixture({ status: 'EXPIRED' }));
      fixture.detectChanges();

      button('Try again')?.click();

      expect(dispatched(createIntent.type)).toHaveLength(1);
      expect(dispatched(createIntent.type)[0].request).toMatchObject({ beneficiaryName: 'Bob', amount: 66 });
    });

    it('says a completed payment is being opened instead of offering to start again', () => {
      const { fixture, intent, text, button } = setup();
      intent.set(intentFixture({ status: 'COMPLETED' }));
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
    const { fixture, intent, closeDialog } = setup();
    intent.set(intentFixture({ status: 'AWAITING_AUTHENTICATION', verificationCode: '4821' }));
    fixture.detectChanges();

    fixture.destroy();

    expect(closeDialog).toHaveBeenCalled();
  });
});
