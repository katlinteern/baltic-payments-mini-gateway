import { uuid } from '@shared/utils/uuid';

/**
 * Hands out the Idempotency-Key for a payment. The key stays the same while the identical payment is retried
 * (so a retry after a timeout is replayed by the server instead of paid twice) and changes as soon as any
 * field changes (the server rejects a reused key with a different payload).
 */
export class IdempotencyKeys {
  private fingerprint: string | null = null;
  private key = '';

  keyFor(payment: object): string {
    const fingerprint = JSON.stringify(payment);
    if (fingerprint !== this.fingerprint) {
      this.fingerprint = fingerprint;
      this.key = uuid();
    }
    return this.key;
  }

  /** Call once a payment completed, so the next payment with identical details is a new payment. */
  renew(): void {
    this.fingerprint = null;
  }
}
