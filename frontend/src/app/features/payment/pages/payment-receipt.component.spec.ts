import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PaymentIntent } from '../state/payment.model';
import { intentFixture } from '../state/payment.fixtures';
import { loadIntent } from '../state/payments.actions';
import { selectIntent, selectPaymentBusy, selectPaymentError } from '../state/payments.selectors';
import { PaymentReceiptComponent } from './payment-receipt.component';

function render(intent: PaymentIntent | null, error: string | null = null) {
  const dispatch = vi.fn();
  const values = new Map<unknown, unknown>([
    [selectIntent, signal(intent)],
    [selectPaymentBusy, signal(false)],
    [selectPaymentError, signal(error)],
  ]);
  TestBed.configureTestingModule({
    imports: [PaymentReceiptComponent],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', 'intent-1']]) } } },
      { provide: Store, useValue: { dispatch, selectSignal: (selector: unknown) => values.get(selector) } },
    ],
  });
  const fixture = TestBed.createComponent(PaymentReceiptComponent);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  return { dispatch, text: element.textContent?.replace(/\s+/g, ' ') ?? '', element };
}

describe('PaymentReceiptComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('loads the payment named in the address, so a refresh or a saved link still works', () => {
    expect(render(null).dispatch).toHaveBeenCalledWith(loadIntent({ id: 'intent-1' }));
  });

  it('shows the receipt of an executed payment', () => {
    const { text, element } = render(
      intentFixture({
        status: 'COMPLETED',
        transactionId: 'tx-123',
        completedAt: '2026-10-19T10:00:00Z',
        balanceAfter: 990,
      }),
    );

    expect(text).toContain('Payment completed');
    expect(text).toContain('10.00 EUR');
    expect(text).toContain('Bob Tamm');
    expect(text).toContain('EE382200221020145685');
    expect(text).toContain('rent');
    expect(text).toContain('990.00 EUR');
    expect(text).not.toContain('Payment ID');
    expect(text).not.toContain('tx-123');
    expect(text).toContain('19.10.2026 13:00');
    const actions = [...element.querySelectorAll('.actions a')].map((link) => link.textContent?.trim());
    expect(actions).toEqual(['Accounts', 'Activity', 'New payment']);
  });

  it('does not claim a payment that did not complete', () => {
    const { text } = render(intentFixture({ status: 'FAILED' }));

    expect(text).toContain('has not been completed');
    expect(text).not.toContain('Payment completed');
  });

  it('shows why the receipt could not be loaded', () => {
    expect(render(null, 'Unknown payment').text).toContain('Unknown payment');
  });
});
