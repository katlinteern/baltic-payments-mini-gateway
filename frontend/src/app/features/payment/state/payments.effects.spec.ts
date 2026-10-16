import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { Transaction } from '@shared/models/transaction.model';
import { loadAccounts } from '@features/accounts/state';
import { PaymentRequest } from './payment-request.model';
import { PaymentsApiService } from './payments-api.service';
import {
  paymentCompleted,
  paymentFailed,
  payeeVerificationFailed,
  payeeVerified,
  submitPayment,
  verifyPayee,
} from './payments.actions';
import { PaymentsEffects } from './payments.effects';

const request: PaymentRequest = {
  fromIban: 'LT121000011101001000',
  toIban: 'EE382200221020145685',
  beneficiaryName: 'Bob Tamm',
  amount: 1,
  currency: 'EUR',
  reference: 'x',
};
const transaction = { id: 'tx-1', status: 'COMPLETED' } as Transaction;

function emitted(result: Observable<unknown>) {
  TestBed.configureTestingModule({
    providers: [
      PaymentsEffects,
      provideMockActions(() => of(submitPayment({ request, idempotencyKey: 'key-12345678' })) as Observable<never>),
      { provide: PaymentsApiService, useValue: { submit: () => result } },
    ],
  });
  const actions: unknown[] = [];
  TestBed.inject(PaymentsEffects).submitPayment$.subscribe((action) => actions.push(action));
  return actions;
}

describe('PaymentsEffects', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('emits paymentCompleted with the created transaction', () => {
    expect(emitted(of(transaction))).toEqual([paymentCompleted({ transaction })]);
  });

  it('refreshes the shared accounts after a completed payment', () => {
    TestBed.configureTestingModule({
      providers: [
        PaymentsEffects,
        provideMockActions(() => of(paymentCompleted({ transaction })) as Observable<never>),
        { provide: PaymentsApiService, useValue: {} },
      ],
    });
    const actions: unknown[] = [];

    TestBed.inject(PaymentsEffects).refreshAccounts$.subscribe((action) => actions.push(action));

    expect(actions).toEqual([loadAccounts()]);
  });

  it('surfaces the business rule detail returned by the API', () => {
    const detail = 'The beneficiary name does not match the holder of the destination account';

    expect(emitted(throwError(() => ({ error: { detail } })))).toEqual([paymentFailed({ error: detail })]);
  });

  it('joins field validation messages', () => {
    const errors = { toIban: 'Invalid IBAN', currency: 'Unknown currency code' };

    expect(emitted(throwError(() => ({ error: { errors } })))).toEqual([
      paymentFailed({ error: 'Invalid IBAN Unknown currency code' }),
    ]);
  });

  it('falls back to a generic message when the response has no body', () => {
    expect(emitted(throwError(() => ({ status: 0 })))).toEqual([
      paymentFailed({ error: 'Payment could not be completed.' }),
    ]);
  });

  describe('verifyPayee$', () => {
    function verification(result: Observable<unknown>) {
      TestBed.configureTestingModule({
        providers: [
          PaymentsEffects,
          provideMockActions(
            () => of(verifyPayee({ toIban: request.toIban, beneficiaryName: 'Bob' })) as Observable<never>,
          ),
          { provide: PaymentsApiService, useValue: { verifyPayee: () => result } },
        ],
      });
      const actions: unknown[] = [];
      TestBed.inject(PaymentsEffects).verifyPayee$.subscribe((action) => actions.push(action));
      return actions;
    }

    it('emits the verification outcome', () => {
      const outcome = { result: 'CLOSE_MATCH' as const, registeredName: 'Bob Tamm' };

      expect(verification(of(outcome))).toEqual([payeeVerified({ verification: outcome })]);
    });

    it('reports why the check failed, for example when it is rate limited', () => {
      const detail = 'Too many requests. Try again later.';

      expect(verification(throwError(() => ({ error: { detail } })))).toEqual([
        payeeVerificationFailed({ error: detail }),
      ]);
    });
  });
});
