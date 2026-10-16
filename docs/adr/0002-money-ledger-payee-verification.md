# ADR 0002: Money movement, ledger and payee verification

- Status: accepted
- Date: 2026-10-16

## Context

A payment changes two balances and must stay correct under concurrency, retries and malicious input.

## Decision

1. **One transaction, deterministic locking.** `AccountService.lockForUpdate` takes `PESSIMISTIC_WRITE` locks in sorted
   IBAN order, so opposite transfers cannot deadlock (covered by `PaymentConcurrencyTest`). Ownership of the source
   account is checked first, without a lock, so a caller who does not own it can never make others wait. Locks give up
   after 3 s (`lock_timeout` on every pooled connection); the API answers `409 Account busy` with `Retry-After` and the
   client retries with the same `Idempotency-Key`.
2. **Ledger.** Every transfer writes a `DEBIT` and a `CREDIT` row in `ledger_entries` carrying `balance_after`. Rows
   are immutable (database trigger). The account balance is a projection that can be reconciled from the entries.
3. **Idempotency.** `(created_by, idempotency_key)` is unique. A retry with an identical payload replays the stored
   result (`Idempotent-Replay: true`); a retry with a different payload is rejected with 422.
4. **`Money` value object.** Amount and ISO 4217 currency travel together; scale follows the currency's minor units and
   mixing currencies is an error, not a silent conversion.
5. **Verification of Payee.** `PayeeNameMatcher` compares the beneficiary name typed by the payer with the account
   holder name, ignoring case, diacritics, punctuation and word order:
   `MATCH` and `CLOSE_MATCH` (typo or partial name) proceed and the result is stored on the transaction;
   `NO_MATCH` is rejected with 422; `NOT_AVAILABLE` (holder unknown) proceeds.
6. **Rejections are audited.** Business-rule rejections (403/404/422) are written to the audit log in a separate
   transaction so they survive the rollback. Plain validation errors (400) are not recorded to avoid log flooding.

## Consequences

- A rejected payment leaves no `transactions` row; the audit log is the record of attempts.
- `CLOSE_MATCH` is allowed without an explicit payer confirmation step. A production system would show the payer the
  registered name and ask for confirmation before sending.
- Balances use `NUMERIC(19,4)` with a `CHECK (balance >= 0)` as a last line of defence behind the application checks.
