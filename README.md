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

Stop with `docker compose down`; add `-v` to delete database data. After pulling a version with new migrations, reset
the volume (`docker compose down -v`) because the seed changeset context changed.

## Architecture

Modular monolith, package-by-feature, enforced by tests. Details and trade-offs are in
[ADR 0001](docs/adr/0001-modular-monolith.md) and [ADR 0002](docs/adr/0002-money-ledger-payee-verification.md).

```
backend/src/main/java/lt/baltic/gateway
├─ platform/   money · web (errors, correlation id, paging) · security (JWT, roles) · validation
├─ identity/   login, users, login rate limiter
├─ accounts/   accounts, ledger entries, transfers
├─ payments/   payment orchestration, idempotency, payee name check, transaction search
└─ audit/      append-only audit trail
   (each feature: api · application · domain · infrastructure)
```

```
frontend/src/app
├─ core/       auth service, guard, HTTP interceptor
├─ features/   accounts · login · payment · transactions   (feature/ UI, state/ NgRx + API service)
└─ shared/     models, validators, utilities
```

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
- Liquibase owns the schema; Hibernate only validates it.

## Known limitations

This is an educational mini-gateway, not a production banking system. Not implemented: strong customer
authentication for payments, transaction limits, OIDC/refresh tokens, key rotation, external payment rails,
distributed rate limiting. Several of these are listed with their rationale in [SECURITY.md](SECURITY.md).
