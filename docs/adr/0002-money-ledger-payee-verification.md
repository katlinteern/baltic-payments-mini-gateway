# ADR 0002: Money movement, ledger and payee verification

- Status: accepted
- Date: 2026-10-16

## Context

A payment changes two balances and must stay correct under concurrency, retries and malicious input.

## Decision

1. **One transaction, deterministic locking.** `AccountService.lockForTransfer` takes `PESSIMISTIC_WRITE` locks in sorted
   IBAN order, so opposite transfers cannot deadlock (covered by `PaymentConcurrencyTest`). Ownership of the source
   account is checked first, without a lock, so a caller who does not own it can never make others wait. Locks give up
   after 3 s (`lock_timeout` on every pooled connection); the API answers `409 Account busy` with `Retry-After` and the
   client retries with the same `Idempotency-Key`. Other features see the locked accounts only as an immutable
   `AccountParty`, never as the `Account` entity.
2. **Ledger.** Every transfer writes a `DEBIT` and a `CREDIT` row in `ledger_entries` carrying `balance_after`. Rows
   are immutable (database trigger, and the application's database role has no UPDATE, DELETE or TRUNCATE on them).
   The account balance is a projection of the postings. `LedgerReconciliationService` proves it every five minutes:
   every transaction nets to zero, every posting continues its account's balance chain (ordered by the `seq` identity
   column, assigned under the account lock) and the latest posting equals the stored balance. Opening balances loaded
   outside the ledger (the demo seed) are covered from the first posting onward; a real system books them as postings.
3. **Idempotency.** `(created_by, idempotency_key)` is unique. A retry with an identical payload replays the stored
   result (`Idempotent-Replay: true`); a retry with a different payload is rejected with 422. The key is looked up
   before any lock is taken, so a retry never waits behind a busy account, and again after the locks are acquired,
   because the same request may have committed meanwhile. When the same key races on two disjoint pairs of accounts no
   lock serialises them: the unique index rejects the loser, whose single retry finds the winner.
4. **`Money` value object.** Amount and ISO 4217 currency travel together; scale follows the currency's minor units and
   mixing currencies is an error, not a silent conversion.
5. **Verification of Payee is its own step.** `PayeeNameMatcher` compares the beneficiary name typed by the payer with
   the account holder name, ignoring case, diacritics, punctuation and word order. `POST /api/payments/verify-payee`
   returns the result before any money moves: `MATCH`; `CLOSE_MATCH` (typo or partial name) together with the registered
   name so the payer can confirm it is the intended person; `NO_MATCH` (the registered name is not disclosed);
   `NOT_AVAILABLE` (unknown account). The UI shows a review step with that result. In the payment itself `MATCH`,
   `CLOSE_MATCH` and `NOT_AVAILABLE` proceed; `NO_MATCH` is rejected with 422 unless the payer saw the warning and sent
   with `proceedDespiteNameMismatch`, in which case the transaction stores `NO_MATCH` and the audit entry records that the
   payer acknowledged it. Because the check reveals whether a guessed name is right, it is rate limited per user.
6. **Rejections are audited.** Business-rule rejections (403/404/422) are written to the audit log in a separate
   transaction so they survive the rollback. Plain validation errors (400) are not recorded to avoid log flooding.

## Consequences

- A rejected payment leaves no `transactions` row; the audit log is the record of attempts.
- The confirmation step is enforced by the UI, not by the API: a client that skips `verify-payee` still gets the
  `NO_MATCH` rejection, but a `CLOSE_MATCH` payment is accepted without proof that the payer saw the registered name.
  A production system would bind the verification result to the payment (a signed verification id) and add strong
  customer authentication on top.
- Balances use `NUMERIC(19,4)` with a `CHECK (balance >= 0)` as a last line of defence behind the application checks.
