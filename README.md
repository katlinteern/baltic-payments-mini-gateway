# Baltic Payments Mini-Gateway

A runnable payment-gateway assignment: Java 25, Spring Boot 3.5, PostgreSQL, Liquibase, Angular 21 (Material, NgRx) and
Docker Compose.

- **Payments** the way a bank does them: prepare, review (with **Verification of Payee**), confirm with a simulated
  **Smart-ID**, receipt. Underneath: idempotent execution, pessimistic locking and an immutable double-entry **ledger**.
- **Security**: JWT, roles, login rate limiting, audit trail, hardened containers and nginx edge. See [SECURITY.md](SECURITY.md).
- **Quality gates**: unit, architecture (ArchUnit), Testcontainers integration and Playwright E2E tests; JaCoCo coverage
  gate; ESLint/Prettier; CodeQL, Trivy and Dependabot.
- **Start reading at** [ADR 0004](docs/adr/0004-payment-intents-and-strong-authentication.md) (the payment flow),
  [ADR 0002](docs/adr/0002-money-ledger-payee-verification.md) and `PaymentService`: the interesting part is how a payment
  stays correct under concurrency, retries and a wrong beneficiary name.

## Run

Requires Docker Desktop with Compose v2.

```sh
docker compose up --build
```

- App: <http://localhost:4200>
- API docs (only under the `local` profile, which Compose uses by default): <http://localhost:8080/swagger-ui/index.html>

The browser talks to the API only through nginx. Both the app and the backend port are published on `127.0.0.1` only (the
`local` profile ships a development JWT key and demo passwords), and Actuator runs on an internal management port (8081) used by the container health check.

To try a payment: sign in as `alice`, open **Payments**, pay Bob (`EE382200221020145685`, name "Bob Tamm"), press
**Continue**, check the review, press **Confirm with Smart-ID** and wait a few seconds: the simulated Smart-ID approves by
itself and the app opens the receipt. Try the name "Bob" (a close match, the registered name is shown) or "Mallory"
(a mismatch that must be acknowledged).

Demo accounts (seeded **only** under the `local` profile; all passwords are test-only):

| Username | Password | Role | Accounts / holder |
|---|---|---|---|
| alice | Alice123! | USER | 2 · Alice Kask |
| bob | Bob12345! | USER | 2 · Bob Tamm |
| auditor | Auditor123! | AUDITOR | read-only access to all accounts, transactions, the audit log and the ledger reconciliation |

Stop with `docker compose down`; add `-v` to delete database data. The migration history is one baseline per module
until the first deployment, and the database provisions an unprivileged application role on first start, so an
existing local database must be reset once with `docker compose down -v`.

## Architecture

Modular monolith, package-by-feature, enforced by tests. Details and trade-offs are in
[ADR 0001](docs/adr/0001-modular-monolith.md) and [ADR 0002](docs/adr/0002-money-ledger-payee-verification.md).
The frontend layout is described in the next block and in ADR 0003.

```
backend/src/main/java/com/baltic/gateway
├─ platform/   money · web (errors, correlation id, paging) · security (JWT, roles) · validation · metrics
├─ identity/   login, users, login rate limiter
├─ accounts/   accounts, ledger entries, transfers
├─ payments/   payment orchestration, idempotency, payee name check, transaction search
└─ audit/      append-only audit trail
   (each feature: api · application · domain · infrastructure)

backend/src/main/resources/db/changelog
├─ db.changelog-master.xml
├─ schema/     001-identity … 010-payment-intents   (plain SQL, one baseline per module, then additions)
└─ seed/       001-demo-data.sql   (contexts local,test only)
```

The backend is a *layered* modular monolith, not hexagonal; see ADR 0001 for why.

```
frontend/src/app
├─ core/       auth service, guard, HTTP interceptor, session reset
├─ features/   login · accounts · payment · transactions
│  └─ <name>/  <name>.routes.ts (lazy) · pages/ (components) · state/ (NgRx slice + API service)
└─ shared/     models, validators, utilities, ui/ (ui-page, ui-alert, ui-section-head, ui-empty-state, ui-status-pill, ui-stepper)
```

Features are lazy-loaded and register their own NgRx slice with their route. Imports use the `@core`, `@shared` and
`@features` aliases, and ESLint boundary rules (the frontend counterpart of the ArchUnit tests) forbid features from
depending on each other except through the public API of `accounts`. Rationale in
[ADR 0003](docs/adr/0003-frontend-structure-and-boundaries.md).

`shared/ui` holds the presentational building blocks (prefix `ui-`, OnPush, styled only with the semantic design
tokens in `styles.scss`). It is deliberately free of feature and NgRx imports so it can later be extracted into a
standalone library.

## Configuration and environments

| Setting | Meaning |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` enables demo seed data, the API description (Swagger UI), a development JWT key and a development DB password. Any other profile requires `JWT_SECRET` and `DB_PASSWORD`. |
| `LIQUIBASE_CONTEXTS` | Defaults to `prod` (schema only). Demo data is tagged `local,test`. The `prod` context refuses to start when the database role `payments_app` does not exist. |
| `JWT_SECRET` | At least 32 bytes. |
| `STRONG_AUTH_MOCK_DELAY` | Compose variable for the simulated Smart-ID (`app.strong-auth.mock.delay`, default `PT3S`): how long it "waits" before approving. The simulator exists only when `app.strong-auth.mock.enabled=true` (the `local` profile). Any other profile starts, but payments cannot be confirmed (`503`) until a real authenticator is provided; see [ADR 0004](docs/adr/0004-payment-intents-and-strong-authentication.md). |
| `app.payments.*` | `intent-ttl` (10 min): how long a prepared payment may wait for the payer; `authentication-ttl` (2 min): how long the payer has to approve it. |
| `app.login-rate-limit.*` | Failed-login limits per window (default 15 minutes): `max-failures` 5 per IP + username, `max-failures-per-ip` 20, `max-failures-per-username` 20. |

## Tests

```sh
cd backend && SPRING_PROFILES_ACTIVE=local mvn verify   # needs JDK 25 and a running Docker daemon
```

`verify` runs Mockito unit tests, ArchUnit architecture rules, PostgreSQL Testcontainers integration tests (including
concurrency, ledger immutability and seed isolation) and fails below 80% line coverage. The report is in
`backend/target/site/jacoco/`.

```sh
cd frontend                       # needs Node 22 (>= 20.19)
npm ci
npm run lint && npm run format:check
npm test                          # Angular unit-test builder with Vitest
npx playwright install chromium
npm run e2e                       # isolated Compose project "baltic-payments-e2e" on ports 14200/18080
```

CI (`.github/workflows/ci.yml`) runs all of the above plus container image builds with a Trivy scan; `codeql.yml`
adds static analysis.

## Design notes

- A payment is **prepared, confirmed and executed** in separate steps ([ADR 0004](docs/adr/0004-payment-intents-and-strong-authentication.md)):
  `POST /api/payments/intents` (checks the accounts and the beneficiary name, moves no money),
  `POST /api/payments/intents/{id}/confirmation` (starts the strong authentication and returns the code to compare),
  `GET /api/payments/intents/{id}` (polled; an approved payment is executed when it is read) and `DELETE` to cancel. There is
  no other way to pay. The details the payer reviewed cannot be changed afterwards, not even by the application's database
  role.
- Execution runs in one database transaction. Ownership of the source account is checked first, without a lock; then
  accounts are locked in sorted IBAN order, so opposite transfers cannot deadlock and overdrafts are impossible. Locks
  give up after 3 s (`409 Account busy`, the next poll tries again). A `CHECK (balance >= 0)` backs this up.
- Execution is idempotent: `(created_by, idempotency_key)` is unique and the key is derived from the intent id, so
  concurrent polls of an approved payment pay once and a retry replays the original result without taking any account lock.
- `GET /api/transactions` takes `q` (name, IBAN or reference), `from` (inclusive) and `to` (exclusive) as instants, so the
  client decides where a day starts in its own time zone. A user sees payments they made or received; an auditor sees
  all of them. The payments are selected in the database and paging counts rows as displayed: a transfer between a user's own
  accounts is two rows (debit and credit) and counts as two.
- Money is a `BigDecimal` on the server and a JSON number on the wire. A payment may have at most 12 integer digits and
  two decimals, so every amount is exact in a browser's `double`; see
  [ADR 0002](docs/adr/0002-money-ledger-payee-verification.md) for the reasoning and when to switch to strings.
- Every transfer writes a debit and a credit to `ledger_entries` with `balance_after`; the table is immutable. A
  reconciliation job (every 5 minutes, and `GET /api/ledger/reconciliation` for auditors) proves that every transaction
  nets to zero, that each account's postings form an unbroken balance chain and that the latest posting equals the
  stored balance; discrepancies raise a metric and an alert. `GET /api/accounts/{id}/ledger` is the account statement.
- Verification of Payee: preparing a payment returns `MATCH`, `CLOSE_MATCH` (with the registered name), `NO_MATCH` or
  `NOT_AVAILABLE`, and the review shows it before anything is confirmed. A `NO_MATCH` payment can be confirmed only with an
  explicit acknowledgement (the UI asks for a tick box); the mismatch is stored on the transaction and in the audit log.
  Executing the payment recomputes the check, so a client cannot bypass it. Preparing payments is rate limited per user
  (20 per minute), because it is also a name check.
- In Docker Compose the application connects as `payments_app`, which cannot update, delete or truncate the ledger,
  the audit log or transactions; Liquibase migrates as the schema owner (`RestrictedDatabaseRoleIntegrationTest`).
- API errors use RFC 7807 `application/problem+json` with a stable machine-readable `code` (for example
  `INSUFFICIENT_FUNDS`, see `ErrorCode`), which is also the `reason` tag of rejected-payment metrics. A validated
  `X-Correlation-Id` is propagated to logs (MDC) and returned in the response.
- Liquibase owns the schema (plain SQL, one baseline file per module); Hibernate only validates it. Until the first
  deployment the history may be squashed; afterwards existing files must never be edited, only new ones added.

## Monitoring (optional)

```sh
docker compose --profile observability up --build
```

| | URL | Notes |
|---|---|---|
| Grafana | <http://localhost:3000> (change with `GRAFANA_PORT`) | user `admin`, password `grafana-dev-only` (override with `GRAFANA_PASSWORD`); dashboard *Baltic Payments Gateway* is provisioned from code |
| Prometheus | <http://localhost:9090> | seven alert rules in `observability/prometheus/alerts.yml` |

Both are bound to `127.0.0.1`. The application exposes metrics on the internal management port (`backend:8081/actuator/prometheus`),
which is not published and not proxied by nginx; a test asserts that the public port does not serve it.

Business metrics (all with bounded tag values): `payments_total{outcome,currency,reason}` (completed, replayed, rejected with
reason), `payments_value_total`, `payments_processing_seconds` (histogram), `auth_logins_total{outcome}` (success, failed,
blocked), plus the standard HTTP, JVM and connection-pool metrics. The dashboard has panels for throughput, latency
percentiles, rejection reasons, sign-in outcomes, HTTP status, JVM heap and the database pool.

Alerts: gateway down, payment rejection rate above 30%, a burst of failed or blocked sign-ins, payment p95 above one second,
any 5xx responses, a ledger discrepancy (`ledger_reconciliation_discrepancies`) and a stale reconciliation job. Series start
at zero on boot so `increase()` and `rate()` see the first event. Alert routing (email, Slack) is intentionally not configured.

## Known limitations

This is an educational mini-gateway, not a production banking system. Not implemented: a real Smart-ID or
Mobile-ID provider (payment confirmation is simulated, see ADR 0004), strong authentication at sign-in, transaction
limits, OIDC/refresh tokens, key rotation, external payment rails, distributed rate limiting. Several of these are listed with their rationale in [SECURITY.md](SECURITY.md).
