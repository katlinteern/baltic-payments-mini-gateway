import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadAccounts } from '@features/accounts/state';
import { PaymentIntent, PaymentRequest } from './payment.model';
import { intentFixture } from './payment.fixtures';
import { PaymentsApiService } from './payments-api.service';
import {
  cancelIntent,
  createIntent,
  editPayment,
  intentFailed,
  intentUpdated,
  loadIntent,
  startConfirmation,
  stopPolling,
} from './payments.actions';
import { PaymentsEffects, POLL_INTERVAL_MS } from './payments.effects';

const request: PaymentRequest = {
  fromIban: 'LT121000011101001000',
  toIban: 'EE382200221020145685',
  beneficiaryName: 'Bob Tamm',
  amount: 10,
  currency: 'EUR',
  reference: 'rent',
};
const ready = intentFixture();
const awaiting = intentFixture({ status: 'AWAITING_AUTHENTICATION', verificationCode: '4821' });
const completed = intentFixture({ status: 'COMPLETED', transactionId: 'tx-1' });

function setup(api: Partial<Record<keyof PaymentsApiService, unknown>> = {}, url = '/payments') {
  const actions$ = new Subject<unknown>();
  const navigate = vi.fn().mockResolvedValue(true);
  TestBed.configureTestingModule({
    providers: [
      PaymentsEffects,
      provideMockActions(() => actions$ as unknown as Observable<never>),
      { provide: PaymentsApiService, useValue: api },
      { provide: Router, useValue: { navigate, url } },
    ],
  });
  const effects = TestBed.inject(PaymentsEffects);
  const collect = (source: Observable<unknown>) => {
    const emitted: unknown[] = [];
    source.subscribe((action) => emitted.push(action));
    return emitted;
  };
  return { actions$, effects, navigate, collect };
}

describe('PaymentsEffects', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('prepares a payment and publishes the intent the server returned', () => {
    const createIntentApi = vi.fn().mockReturnValue(of(ready));
    const { actions$, effects, collect } = setup({ createIntent: createIntentApi });
    const emitted = collect(effects.createIntent$);

    actions$.next(createIntent({ request }));

    expect(createIntentApi).toHaveBeenCalledWith(request);
    expect(emitted).toEqual([intentUpdated({ intent: ready })]);
  });

  it('shows the reason the server gave when a payment cannot be prepared', () => {
    const detail = 'Balance is too low for this payment';
    const { actions$, effects, collect } = setup({
      createIntent: () => throwError(() => ({ error: { detail } })),
    });
    const emitted = collect(effects.createIntent$);

    actions$.next(createIntent({ request }));

    expect(emitted).toEqual([intentFailed({ error: detail })]);
  });

  it('asks for confirmation, passing on whether a name mismatch was acknowledged', () => {
    const startConfirmationApi = vi.fn().mockReturnValue(of(awaiting));
    const { actions$, effects, collect } = setup({ startConfirmation: startConfirmationApi });
    const emitted = collect(effects.startConfirmation$);

    actions$.next(startConfirmation({ id: 'intent-1', acknowledgeNameMismatch: true }));

    expect(startConfirmationApi).toHaveBeenCalledWith('intent-1', true);
    expect(emitted).toEqual([intentUpdated({ intent: awaiting })]);
  });

  it('returns to the form after a payment was cancelled', () => {
    const { actions$, effects, collect } = setup({ cancelIntent: () => of({ ...ready, status: 'CANCELLED' }) });
    const emitted = collect(effects.cancelIntent$);

    actions$.next(cancelIntent({ id: 'intent-1' }));

    expect(emitted).toEqual([editPayment()]);
  });

  it('reads the real state when a payment cannot be cancelled because it was already executed', () => {
    const { actions$, effects, collect } = setup({ cancelIntent: () => throwError(() => ({ status: 409 })) });
    const emitted = collect(effects.cancelIntent$);

    actions$.next(cancelIntent({ id: 'intent-1' }));

    expect(emitted).toEqual([loadIntent({ id: 'intent-1' })]);
  });

  describe('polling', () => {
    it('asks the server again every second while the payer has not approved, and stops once it is executed', () => {
      const getIntent = vi.fn().mockReturnValueOnce(of(awaiting)).mockReturnValueOnce(of(completed));
      const { actions$, effects, collect } = setup({ getIntent });
      const emitted = collect(effects.poll$);

      actions$.next(intentUpdated({ intent: awaiting }));
      expect(getIntent).not.toHaveBeenCalled();
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      expect(emitted).toEqual([intentUpdated({ intent: awaiting })]);

      actions$.next(intentUpdated({ intent: awaiting }));
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      expect(emitted).toEqual([intentUpdated({ intent: awaiting }), intentUpdated({ intent: completed })]);

      actions$.next(intentUpdated({ intent: completed }));
      vi.advanceTimersByTime(5 * POLL_INTERVAL_MS);
      expect(getIntent).toHaveBeenCalledTimes(2);
    });

    it.each([
      ['the payer leaves the page', stopPolling()],
      ['the payer cancels', cancelIntent({ id: 'intent-1' })],
      ['the payer goes back to the form', editPayment()],
    ])('stops when %s', (_reason, action) => {
      const getIntent = vi.fn().mockReturnValue(of(awaiting));
      const { actions$, effects, collect } = setup({ getIntent });
      collect(effects.poll$);

      actions$.next(intentUpdated({ intent: awaiting }));
      actions$.next(action);
      vi.advanceTimersByTime(3 * POLL_INTERVAL_MS);

      expect(getIntent).not.toHaveBeenCalled();
    });

    it('does not poll a payment that is not waiting for approval', () => {
      const getIntent = vi.fn();
      const { actions$, effects, collect } = setup({ getIntent });
      collect(effects.poll$);

      actions$.next(intentUpdated({ intent: ready }));
      vi.advanceTimersByTime(3 * POLL_INTERVAL_MS);

      expect(getIntent).not.toHaveBeenCalled();
    });

    it('survives a brief network failure and reports a lasting one', () => {
      let requests = 0;
      const failing = new Observable<never>((subscriber) => {
        requests++;
        subscriber.error({ status: 0 });
      });
      const { actions$, effects, collect } = setup({ getIntent: () => failing });
      const emitted = collect(effects.poll$);

      actions$.next(intentUpdated({ intent: awaiting }));
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      expect(emitted).toEqual([]);
      vi.advanceTimersByTime(3 * POLL_INTERVAL_MS);

      expect(requests).toBe(4);
      expect(emitted).toEqual([intentFailed({ error: 'The payment status could not be read.' })]);
    });
  });

  describe('when the payment is executed', () => {
    const run = (intent: PaymentIntent, url?: string) => {
      const { actions$, effects, navigate, collect } = setup({}, url);
      const refreshed = collect(effects.refreshAccounts$);
      collect(effects.showReceipt$);
      actions$.next(intentUpdated({ intent }));
      return { refreshed, navigate };
    };

    it('refreshes the accounts and opens the receipt', () => {
      const { refreshed, navigate } = run(completed);

      expect(refreshed).toEqual([loadAccounts()]);
      expect(navigate).toHaveBeenCalledWith(['/payments', 'intent-1', 'receipt']);
    });

    it('does not navigate again when the receipt itself is already open', () => {
      expect(run(completed, '/payments/intent-1/receipt').navigate).not.toHaveBeenCalled();
    });

    it.each([ready, awaiting, intentFixture({ status: 'FAILED' })])(
      'does nothing for a %#th unfinished payment',
      (intent) => {
        const { refreshed, navigate } = run(intent);

        expect(refreshed).toEqual([]);
        expect(navigate).not.toHaveBeenCalled();
      },
    );
  });
});
