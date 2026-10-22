# ADR 0002: Money movement, ledger and payee verification

- Status: accepted
- Date: 2026-10-16

## Context

A payment changes two balances and must stay correct under concurrency, retries and malicious input.

## Decision

1. **One transaction, deterministic locking.** `TransferService.lockForTransfer` takes `PESSIMISTIC_WRITE` locks in sorted
   IBAN order, so opposite transfers cannot deadlock (`PaymentConcurrencyTest`). Ownership of the source account is
   checked first, without a lock, so a caller who does not own it can never make others wait. Locks give up after 3 s
   (`lock_timeout`); the API answers `409 Account busy` with `Retry-After` and the client polls again. Other features
   see locked accounts only as an immutable `AccountParty`.
2. **Ledger.** Every transfer writes a `DEBIT` and a `CREDIT` row in `ledger_entries` with `balance_after`. Rows are
   immutable (trigger, and no UPDATE, DELETE or TRUNCATE for the application role). The balance is a projection of the
   postings. `LedgerReconciliationService` verifies every five minutes that each transaction nets to zero, each
   posting continues its account's chain (ordered by the `seq` column assigned under the account lock) and the last
   posting equals the stored balance. The result is a metric and a log line; there is no endpoint, because a customer
   application has no back-office role. Opening balances in the demo seed are covered from the first posting onward.
3. **Idempotency.** `(created_by, idempotency_key)` is unique and the key is `payment-<id>` (ADR 0004). A repeat with the
   same payload replays the stored result; a different payload is rejected with 422. The key is looked up before any lock
   and again after the locks, because the same request may have committed meanwhile. If the same key races on two disjoint
   account pairs, the unique index rejects the loser, whose single retry finds the winner.
4. **`Money`.** Amount and ISO 4217 currency travel together, the scale follows the currency, and mixing currencies is an
   error.
5. **Verification of Payee.** `PayeeNameMatcher` compares the typed beneficiary name with the holder name, ignoring case,
   diacritics, punctuation and word order. The result is stored with the payment: `MATCH`; `CLOSE_MATCH` with the
   registered name; `NO_MATCH` (registered name not disclosed); `NOT_AVAILABLE`. A `NO_MATCH` can be confirmed only with
   an explicit acknowledgement, recorded on the transaction (`name_check = NO_MATCH`) and in the audit entry. Execution
   recomputes the check, so a client cannot bypass it. Because the check reveals whether a guessed name is right,
   preparing a payment is rate limited per user.
6. **Rejections are audited.** Business rejections (403/404/422) are written to the audit log in a separate transaction
   so they survive the rollback. Validation errors (400) are not, to avoid flooding.
7. **Amounts are `BigDecimal` on the server and a JSON number on the wire, at most 12 integer digits.** A browser parses
   a JSON number into a `double`, which is exact up to 14 significant digits (no mismatch in two million random values
   of 12 integer digits and two decimals; about three quarters of random 15-digit amounts came back different). The
   request allows `@Digits(integer = 12, fraction = 2)`, and the UI validates the typed text and adds balances in integer
   cents. Decimal strings (Berlin Group) or integer minor units (Stripe) remove the dependence on that bound at the price
   of a harder contract; they are the move if limits grow.
8. **`transactions` owns customer history and search.** `GET /api/transactions` pages over displayed rows: a transfer
   between a user's own accounts is two rows. The filter `q` matches names, IBANs and reference with
   `lower(column) LIKE '%text%'`, served by `pg_trgm` GIN indexes. A transaction stores both holder names as registered
   when booked (`from_name`, `to_name`), like a bank statement, so a rename does not rewrite history; balance-after values
   come from the ledger via `accounts`.

## Consequences

- A rejected payment leaves no `transactions` row; the audit log is the record of attempts.
- Balances are `NUMERIC(19,4)` with `CHECK (balance >= 0)` as a last line of defence.
