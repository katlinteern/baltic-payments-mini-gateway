import { describe, expect, it } from 'vitest';
import { IdempotencyKeys } from './idempotency-keys';

describe('IdempotencyKeys', () => {
  const payment = { toIban: 'EE382200221020145685', amount: 10 };

  it('reuses the key while the same payment is retried', () => {
    const keys = new IdempotencyKeys();

    expect(keys.keyFor(payment)).toBe(keys.keyFor({ ...payment }));
  });

  it('issues a new key as soon as the payment changes', () => {
    const keys = new IdempotencyKeys();

    expect(keys.keyFor(payment)).not.toBe(keys.keyFor({ ...payment, amount: 11 }));
  });

  it('issues a new key after renew, so an identical follow-up payment is not mistaken for a retry', () => {
    const keys = new IdempotencyKeys();
    const first = keys.keyFor(payment);

    keys.renew();

    expect(keys.keyFor(payment)).not.toBe(first);
  });

  it('produces keys the API accepts (8-64 letters, digits, underscores or hyphens)', () => {
    expect(new IdempotencyKeys().keyFor(payment)).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
  });
});
