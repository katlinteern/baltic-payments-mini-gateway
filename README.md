# Baltic Payments Mini-Gateway

A runnable payment-gateway assignment: Java 25, Spring Boot 3.5, PostgreSQL, Liquibase, Angular 21 (Material, NgRx) and
Docker Compose.

- **Payments** between accounts with idempotency keys, pessimistic locking, an immutable double-entry **ledger** and
  **Verification of Payee** (beneficiary name vs account holder).
- **Security**: JWT, roles, login rate limiting, audit trail, hardened containers and nginx edge. See [SECURITY.md](SECURITY.md).
- **Quality gates**: unit, architecture (ArchUnit), Testcontainers integration and Playwright E2E tests; JaCoCo coverage
  gate; ESLint/Prettier; CodeQL, Trivy and Dependabot.

## Run

Requires Docker Desktop with Compose v2.

```sh
docker compose up --build
```

- App: <http://localhost:4200>
- API docs (loopback only): <http://localhost:8080/swagger-ui/index.html>

The browser talks to the API only through nginx. The backend port is published on `127.0.0.1` for Swagger, and
Actuator runs on an internal management port (8081) used by the container health check.

Demo accounts (seeded **only** under the `local` profile; all passwords are test-only):

| Username | Password | Role | Accounts / holder |
|---|---|---|---|
| alice | Alice123! | USER | 2 · Alice Kask |
| bob | Bob12345! | USER | 2 · Bob Tamm |
| auditor | Auditor123! | AUDITOR | read-only audit access |
| admin | Admin123! | ADMIN | 1 · Baltic Treasury |

Stop with `docker compose down`; add `-v` to delete database data. The migration history was squashed into one baseline
per module before the first deployment, so an existing local database must be reset once with `docker compose down -v`.

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
├─ schema/     001-identity · 002-accounts · 003-payments · 004-ledger · 005-audit   (plain SQL, one file per module)
└─ seed/       001-demo-data.sql   (contexts local,test only)
```

The backend is a *layered* modular monolith, not hexagonal; see ADR 0001 for why.

```
frontend/src/app
├─ core/       auth service, guard, HTTP interceptor, session reset
├─ features/   login · accounts · payment · transactions
│  └─ <name>/  <name>.routes.ts (lazy) · pages/ (components) · state/ (NgRx slice + API service)
└─ shared/     models, validators, utilities, ui/ (ui-page, ui-alert, ui-section-head, ui-empty-state, ui-status-pill)
```

Features are lazy-loaded and register their own NgRx slice with their route. Imports use the `@core`, `@shared` and
`@features` aliases, and ESLint boundary rules (the frontend counterpart of the ArchUnit tests) forbid features from
depending on each other except through the public API of `accounts`. Rationale in
[ADR 0003](docs/adr/0003-frontend-structure-and-boundaries.md).

`shared/ui` holds the presentational building blocks (prefix `ui-`, OnPush, styled only with the semantic design
tokens in `styles.scss`). It is deliberately free of feature and NgRx imports so it can later be extracted into a
standalone library.

**Installable web app (PWA).** The production build ships a web manifest, icons and an Angular service worker that
caches only the static app shell. API and Actuator URLs are excluded from the worker, and nginx serves `ngsw.json`
and `ngsw-worker.js` with `no-cache` so new releases reach clients. Open the app on a phone and use *Add to Home
Screen*; it starts in standalone mode. Offline use of account data is intentionally not supported.

## Configuration and environments

| Setting | Meaning |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` enables demo seed data, a development JWT key and a development DB password. Any other profile requires `JWT_SECRET` and `DB_PASSWORD`. |
| `LIQUIBASE_CONTEXTS` | Defaults to `prod` (schema only). Demo data is tagged `local,test`. |
| `JWT_SECRET` | At least 32 bytes. |
| `app.login-rate-limit.*` | Failed-login limit (default 5 per 15 minutes). |

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

- Payments run in one database transaction. Accounts are locked in sorted IBAN order, so opposite transfers cannot
  deadlock and overdrafts are impossible. A `CHECK (balance >= 0)` backs this up.
- `(created_by, idempotency_key)` is unique. A retry with the same payload replays the original result; a changed
  payload is rejected with 422.
- Every transfer writes a debit and a credit to `ledger_entries` with `balance_after`; the table is immutable.
- A beneficiary name that does not match the destination account holder is rejected; a close match is allowed and
  recorded as `CLOSE_MATCH`.
- API errors use RFC 7807 `application/problem+json`; a validated `X-Correlation-Id` is propagated to logs (MDC) and
  returned in the response.
- Liquibase owns the schema (plain SQL, one baseline file per module); Hibernate only validates it. Until the first
  deployment the history may be squashed; afterwards existing files must never be edited, only new ones added.

## Monitoring (optional)

```sh
docker compose --profile observability up --build
```

| | URL | Notes |
|---|---|---|
| Grafana | <http://localhost:3000> | user `admin`, password `grafana-dev-only` (override with `GRAFANA_PASSWORD`); dashboard *Baltic Payments Gateway* is provisioned from code |
| Prometheus | <http://localhost:9090> | five alert rules in `observability/prometheus/alerts.yml` |

Both are bound to `127.0.0.1`. The application exposes metrics on the internal management port (`backend:8081/actuator/prometheus`),
which is not published and not proxied by nginx; a test asserts that the public port does not serve it.

Business metrics (all with bounded tag values): `payments_total{outcome,currency,reason}` (completed, replayed, rejected with
reason), `payments_value_total`, `payments_processing_seconds` (histogram), `auth_logins_total{outcome}` (success, failed,
blocked), plus the standard HTTP, JVM and connection-pool metrics. The dashboard has panels for throughput, latency
percentiles, rejection reasons, sign-in outcomes, HTTP status, JVM heap and the database pool.

Alerts: gateway down, payment rejection rate above 30%, a burst of failed or blocked sign-ins, payment p95 above one second,
and any 5xx responses. Series start at zero on boot so `increase()` and `rate()` see the first event. Alert routing
(email, Slack) is intentionally not configured.

## Known limitations

This is an educational mini-gateway, not a production banking system. Not implemented: strong customer
authentication for payments, transaction limits, OIDC/refresh tokens, key rotation, external payment rails,
distributed rate limiting. Several of these are listed with their rationale in [SECURITY.md](SECURITY.md).
