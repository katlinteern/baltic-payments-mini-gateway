import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PaymentIntent } from '../state/payment.model';
import { intentFixture } from '../state/payment.fixtures';
import { selectIntent } from '../state/payments.selectors';
import { SmartIdDialogComponent } from './smart-id-dialog.component';

function render(initial: PaymentIntent | null) {
  const intent = signal<PaymentIntent | null>(initial);
  const close = vi.fn();
  TestBed.configureTestingModule({
    imports: [SmartIdDialogComponent],
    providers: [
      {
        provide: Store,
        useValue: { selectSignal: (selector: unknown) => (selector === selectIntent ? intent : null) },
      },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const fixture = TestBed.createComponent(SmartIdDialogComponent);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  const text = () => element.textContent?.replace(/\s+/g, ' ') ?? '';
  return { fixture, intent, close, element, text };
}

const awaiting = () =>
  intentFixture({
    status: 'AWAITING_AUTHENTICATION',
    verificationCode: '4821',
    amount: 25.5,
    beneficiaryName: 'Bob Tamm',
    expiresAt: '2026-10-19T10:02:00Z',
  });

describe('SmartIdDialogComponent', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] }));
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('shows what is being paid and the code the payer must find in the app', () => {
    vi.setSystemTime(new Date('2026-10-19T10:00:30Z'));
    const { text } = render(awaiting());

    expect(text()).toContain('Confirm with Smart-ID');
    expect(text()).toContain('Paying 25.50 EUR to Bob Tamm');
    expect(text()).toContain('Verification code 4821');
  });

  it('counts down the time left, once a second', () => {
    vi.setSystemTime(new Date('2026-10-19T10:00:30Z'));
    const { fixture, text } = render(awaiting());
    expect(text()).toContain('Time left 1:30');

    vi.advanceTimersByTime(1000);
    fixture.detectChanges();

    expect(text()).toContain('Time left 1:29');
  });

  it('never shows a negative time', () => {
    vi.setSystemTime(new Date('2026-10-19T10:05:00Z'));
    const { text } = render(awaiting());

    expect(text()).toContain('Time left 0:00');
  });

  it('closes with true when the payer cancels', () => {
    const { element, close } = render(awaiting());

    element.querySelector<HTMLButtonElement>('button')?.click();

    expect(close).toHaveBeenCalledWith(true);
  });

  it('stops its timer when it is closed', () => {
    const { fixture } = render(awaiting());

    fixture.destroy();

    expect(vi.getTimerCount()).toBe(0);
  });
});
