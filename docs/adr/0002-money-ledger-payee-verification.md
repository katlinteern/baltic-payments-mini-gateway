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
   payment is simply tried again (the client polls, see ADR 0004). Other features see the locked accounts only as an immutable
   `AccountParty`, never as the `Account` entity.
2. **Ledger.** Every transfer writes a `DEBIT` and a `CREDIT` row in `ledger_entries` carrying `balance_after`. Rows
   are immutable (database trigger, and the application's database role has no UPDATE, DELETE or TRUNCATE on them).
   The account balance is a projection of the postings. `LedgerReconciliationService` proves it every five minutes:
   every transaction nets to zero, every posting continues its account's balance chain (ordered by the `seq` identity
   column, assigned under the account lock) and the latest posting equals the stored balance. Opening balances loaded
   outside the ledger (the demo seed) are covered from the first posting onward; a real system books them as postings.
3. **Idempotency.** `(created_by, idempotency_key)` is unique. Since ADR 0004 the key is derived from the payment
   intent (`intent-<id>`), not supplied by the client. A second execution with an identical payload replays the stored
   result; one with a different payload is rejected with 422 (a rule that now guards the service itself, not an HTTP
   contract). The key is looked up before any lock is taken, so a retry never waits behind a busy account, and again after
   the locks are acquired, because the same request may have committed meanwhile. When the same key races on two
   disjoint pairs of accounts no lock serialises them: the unique index rejects the loser, whose single retry finds the
   winner.
4. **`Money` value object.** Amount and ISO 4217 currency travel together; scale follows the currency's minor units and
   mixing currencies is an error, not a silent conversion.
5. **Verification of Payee is part of preparing a payment.** `PayeeNameMatcher` compares the beneficiary name typed by the
   payer with the account holder name, ignoring case, diacritics, punctuation and word order. When the payer prepares a
   payment (ADR 0004) the server stores the result with the payment intent: `MATCH`; `CLOSE_MATCH` (typo or partial name)
   together with the registered name so the payer can confirm it is the intended person; `NO_MATCH` (the registered name
   is not disclosed); `NOT_AVAILABLE`. The UI shows the outcome on the review of the payment before anything is
   confirmed. `NO_MATCH` can be confirmed only with an explicit acknowledgement, which the transaction records
   (`name_check = NO_MATCH`) and the audit entry marks as `mismatchAcknowledgedByPayer=true`. Because the check reveals
   whether a guessed name is right, preparing a payment is rate limited per user. Executing a payment recomputes the
   check itself, so it cannot be bypassed by a client.
6. **Rejections are audited.** Business-rule rejections (403/404/422) are written to the audit log in a separate
   transaction so they survive the rollback. Plain validation errors (400) are not recorded to avoid log flooding.
7. **Money is a `BigDecimal` on the server and a JSON number on the wire, with a bounded range.** All arithmetic and
   storage use `BigDecimal` / `NUMERIC` (never `double`), and `Money` fixes the scale to the currency. A browser parses
   a JSON number into a binary `double`, which is exact for any decimal of at most 14 significant digits (measured:
   no mismatch in two million random values up to 12 integer digits and two decimals) but not beyond it (about three
   quarters of random 15-digit amounts came back different). The request therefore allows at most 12 integer digits
   (`@Digits(integer = 12, fraction = 2)`; the earlier limit of 15 was too loose), and the UI validates the typed text
   before turning it into a number and adds balances in integer cents. Decimal strings (as PSD2 / Berlin Group APIs
   use) or integer minor units (as Stripe does) would remove the dependence on that bound, at the price of a contract
   and client code that are harder to read; they are the move if limits grow past 12 digits or a client does
   arithmetic on amounts.
8. **The transaction history selects payments in the database.** `GET /api/transactions` pages over displayed rows
   (`totalElements` is the number of rows): a transfer between the caller's own accounts is two rows (debit and credit)
   and counts as two, so a page never holds more than `size` rows. Only the first `page * size + size` payments are loaded. The text filter `q` matches names, IBANs and the reference with
   `lower(column) LIKE '%text%'`, served by `pg_trgm` GIN indexes; the holder-name match is a sub-select, not a list of ids.

## Consequences

- A rejected payment leaves no `transactions` row; the audit log is the record of attempts.
- The server stores the name check with the intent and a payment can only be executed from an intent, so the result the
  payer was shown is the result the payment is bound to (the "signed verification id" of an earlier draft is no longer
  needed). Strong customer authentication is in ADR 0004.
- Balances use `NUMERIC(19,4)` with a `CHECK (balance >= 0)` as a last line of defence behind the application checks.
