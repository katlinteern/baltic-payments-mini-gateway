# Architecture

Modular monolith, package-by-feature, enforced by tests (`ArchitectureTest` on the backend, ESLint boundary rules on the
frontend). Decisions and trade-offs are in the [ADRs](adr).

## Structure

```text
backend/src/main/java/com/baltic/gateway
├─ platform/      money, errors, security (JWT), paging, rate limit, metrics
├─ identity/      login, users, strong authentication port
├─ accounts/      accounts, ledger, transfers, reconciliation
├─ payments/      prepare, confirm, execute, idempotency, payee name check
├─ transactions/  customer transaction history and search
└─ audit/         append-only audit trail

frontend/src/app
├─ core/          auth service, guard, interceptor, session reset
├─ features/      login · accounts · payments · transactions (lazy, own NgRx slice)
└─ shared/        models, validators, utilities, ui/
```

Each backend feature has the layers `api`, `application`, `domain` and `infrastructure`, with one folder per concept
inside a layer (`accounts/application/{account,ledger,transfer}`) and DTOs in `api/<concept>/dto`. Frontend imports use
`@core`, `@shared` and `@features`; features depend on each other only through the public API of `accounts`.

Schema: Liquibase plain SQL under `db/changelog/schema` (one baseline per module, later changes are new files) and demo
data under `db/changelog/seed` (contexts `local,test`). Hibernate only validates the schema.

## Payment flow

```mermaid
sequenceDiagram
    actor Payer
    participant UI as Angular + NgRx
    participant API as Spring Boot
    participant SCA as StrongAuthenticator (Smart-ID mock)
    participant DB as PostgreSQL
    Payer->>UI: details, Continue
    UI->>API: POST /payments
    API->>DB: ownership + advisory checks, name check, save payment (READY)
    API-->>UI: payment + name check result
    Payer->>UI: Confirm (acknowledge a NO_MATCH)
    UI->>API: POST /payments/{id}/confirmation
    API->>SCA: start
    API-->>UI: AWAITING_AUTHENTICATION + code to compare
    loop every second
        UI->>API: GET /payments/{id}
        API->>SCA: outcome
    end
    Note over API,DB: APPROVED: one transaction, locks in IBAN order,<br/>debit + credit ledger entries, key payment-{id}
    API-->>UI: COMPLETED + receipt data
```

Read in this order: `PaymentService` → `PaymentConfirmationService` → `PaymentExecutionService` →
`TransferService.transfer`.

## Design notes

- **Prepare, confirm, execute.** Preparing checks accounts and beneficiary name and moves no money; confirming starts
  strong authentication; polling `GET /api/payments/{id}` executes an approved payment; `DELETE` cancels. There is no
  other way to pay.
- **Concurrency.** Execution is one transaction: ownership check without a lock, then locks in sorted IBAN order, so
  opposite transfers cannot deadlock. Locks give up after 3 s (`409 Account busy`, the next poll retries).
- **Idempotency.** `(created_by, idempotency_key)` is unique and the key is `payment-{id}`, so concurrent polls pay once
  and a retry replays the stored result without taking a lock.
- **Ledger.** Every transfer writes a debit and a credit with `balance_after`. A reconciliation job checks that each
  transaction nets to zero, each account's postings form an unbroken chain and the last one equals the balance.
- **Verification of Payee.** `MATCH`, `CLOSE_MATCH`, `NO_MATCH` or `NOT_AVAILABLE`, stored with the payment. A
  `NO_MATCH` needs an explicit acknowledgement, and execution recomputes the check.
- **Money.** `BigDecimal` on the server, a JSON number on the wire, at most 12 integer digits and two decimals.
- **Database role.** `payments_app` cannot update, delete or truncate ledger, audit or transaction rows.
- **Errors.** RFC 7807 problems with a stable `ErrorCode`; a validated `X-Correlation-Id` is logged and returned.

## Limitations and next steps

An educational gateway; see [SECURITY.md](../SECURITY.md) for security limitations.

- Payments are internal transfers; the ledger has no internal accounts, fees or period closing, and the demo seed loads
  opening balances directly.
- A `GET` executes an approved payment because the provider outcome is polled. A provider callback would make the read
  side-effect free and execute an approval even when the browser is closed.
- Transaction history pages in memory (`offset + size` rows), so deep pages get slower; a statement read from
  `ledger_entries` would avoid that.
- Rate limits and the reconciliation job are per instance; more than one instance needs shared limits and a lock.
- Amounts should become strings on the wire (`"123.50"`, as in Berlin Group APIs).
- Logs are kept seven days in Loki and Alloy reads the Docker socket: fine locally, not for a bank.
- Next: a real Smart-ID adapter, Spring Boot 4, shared rate limiting and token revocation.
