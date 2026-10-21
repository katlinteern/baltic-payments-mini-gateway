import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Payment } from '@features/payments/state/payment.model';
import { paymentFixture } from '@features/payments/state/payment.fixtures';
import { PaymentReviewComponent } from './payment-review.component';

function setup(payment: Payment = paymentFixture()) {
  const fixture = TestBed.createComponent(PaymentReviewComponent);
  fixture.componentRef.setInput('payment', payment);
  const confirmed = vi.fn<(acknowledged: boolean) => void>();
  const edited = vi.fn();
  fixture.componentInstance.confirmed.subscribe(confirmed);
  fixture.componentInstance.edited.subscribe(edited);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  const text = () => element.textContent?.replace(/\s+/g, ' ') ?? '';
  const button = (label: string) =>
    Array.from(element.querySelectorAll('button')).find((candidate) => candidate.textContent?.includes(label));
  return { fixture, component: fixture.componentInstance, confirmed, edited, text, button, element };
}

describe('PaymentReviewComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows what the payer is about to pay and the name check', () => {
    const { text } = setup();

    expect(text()).toContain('Review payment');
    expect(text()).toContain('Bob Tamm');
    expect(text()).toContain('EE382200221020145685');
    expect(text()).toContain('10.00 EUR');
    expect(text()).toContain('rent');
    expect(text()).toContain('The name matches the account holder.');
  });

  it('asks for confirmation without an acknowledgement when the name matches', () => {
    const { button, confirmed } = setup();

    button('Confirm with Smart-ID')?.click();

    expect(confirmed).toHaveBeenCalledWith(false);
  });

  it('shows the registered name for a close match so the payer can check it', () => {
    const { text } = setup(paymentFixture({ nameCheck: 'CLOSE_MATCH', registeredName: 'Bob Tamm-Kask' }));

    expect(text()).toContain('registered as Bob Tamm-Kask');
  });

  it('says when the name could not be verified', () => {
    const { text } = setup(paymentFixture({ nameCheck: 'NOT_AVAILABLE' }));

    expect(text()).toContain('could not be verified');
  });

  it('does not let a mismatching name through until the payer acknowledges the risk', () => {
    const { fixture, component, button, confirmed, element } = setup(paymentFixture({ nameCheck: 'NO_MATCH' }));

    expect(button('Confirm with Smart-ID')?.disabled).toBe(true);
    component.confirm();
    expect(confirmed).not.toHaveBeenCalled();

    (element.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(button('Confirm with Smart-ID')?.disabled).toBe(false);
    component.confirm();

    expect(confirmed).toHaveBeenCalledWith(true);
  });

  it('asks the payer to start over, forgetting an earlier acknowledgement', () => {
    const { component, button, edited } = setup(paymentFixture({ nameCheck: 'NO_MATCH' }));
    component.acknowledged.set(true);

    button('Edit')?.click();

    expect(edited).toHaveBeenCalledTimes(1);
    expect(component.acknowledged()).toBe(false);
  });

  it('disables both actions while a request is in progress or the approval is awaited', () => {
    const { fixture, button, component, confirmed } = setup();

    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();
    expect(button('Edit')?.disabled).toBe(true);
    expect(button('Confirm with Smart-ID')?.disabled).toBe(true);
    component.confirm();
    expect(confirmed).not.toHaveBeenCalled();

    fixture.componentRef.setInput('busy', false);
    fixture.componentRef.setInput('waiting', true);
    fixture.detectChanges();
    expect(button('Edit')?.disabled).toBe(true);
    expect(button('Confirm with Smart-ID')?.disabled).toBe(true);
  });
});
