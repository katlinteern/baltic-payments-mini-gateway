# Baltic Payments Mini-Gateway

A payment gateway in the way a bank does it: prepare a payment, review it with Verification of Payee, confirm it with a
simulated Smart-ID, get a receipt. Underneath are idempotent execution, pessimistic locking and an immutable
double-entry ledger, so a payment stays correct under concurrency, retries and a wrong beneficiary name.

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/login.png" alt="Sign in"></td>
    <td width="50%"><img src="docs/screenshots/accounts.png" alt="Accounts"></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/payments.png" alt="Payments"></td>
    <td width="50%"><img src="docs/screenshots/transactions.png" alt="Transactions"></td>
  </tr>
</table>

## Getting started

Requires Docker Desktop with Compose v2.

```sh
docker compose up --build
```

| Open | URL | Sign in |
|---|---|---|
| **Web app** | <http://localhost:4200> | `alice` / `Alice123!` or `bob` / `Bob12345!` |
| API docs (Swagger UI) | <http://localhost:8080/swagger-ui/index.html> | same users: `POST /api/auth/login`, then *Authorize* |
| Grafana metrics | <http://localhost:3000/d/baltic-gateway> | `admin` / `grafana-dev-only` |
| Grafana logs | <http://localhost:3000/d/baltic-logs> | same as above |
| Prometheus (metrics, alerts) | <http://localhost:9090> | none |
| PostgreSQL | `localhost:5432`, database `payments` | `payments` / `payments-dev-only` (owner), `payments_app` / `payments-app-dev-only` (application) |

Credentials are demo-only (`local` profile).

**Try it:** sign in as `alice`, open Payments and pay Bob (`EE382200221020145685`). The name "Bob Tamm" matches, "Bob" is a
close match and "Mallory" is a mismatch that must be acknowledged. The simulated Smart-ID approves after a few seconds;
`STRONG_AUTH_MOCK_OUTCOME=REJECTED docker compose up --build` makes it reject instead.

```sh
docker compose down                                                # stop; add -v to delete the data
docker compose --profile dev up --build db backend frontend-dev    # frontend with live reload
```

## Tech stack

| | |
|---|---|
| **Backend** | Java 25, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA |
| **Frontend** | Angular 21, Angular Material, NgRx, served by nginx |
| **Database** | PostgreSQL 16, Liquibase migrations |
| **Infrastructure** | Docker Compose, nginx, Prometheus, Grafana, Loki, Alloy |
| **Quality** | JUnit, Mockito, ArchUnit, Testcontainers, Vitest, Playwright, JaCoCo, ESLint, Prettier |
| **CI** | GitHub Actions: build, all tests, coverage gates, E2E, CodeQL, Trivy image scan |

## Project structure

```text
baltic-payments-mini-gateway/
├── backend/            Spring Boot modular monolith
│   └── src/main/java/com/baltic/gateway/
│       ├── identity/       sign-in, users, login rate limiting
│       ├── accounts/       accounts, transfers, ledger, reconciliation
│       ├── payments/       payment flow, Verification of Payee, strong authentication
│       ├── transactions/   transaction history
│       ├── audit/          audit trail
│       └── platform/       money, errors, security, paging, metrics, rate limit
├── frontend/           Angular app
│   └── src/app/
│       ├── core/           auth service, guard, interceptor
│       ├── features/       login · accounts · payments · transactions
│       └── shared/         reusable UI and utilities
├── db/                 database role bootstrap
├── observability/      Prometheus rules, Loki and Alloy config, Grafana dashboards
├── docs/               screenshots
├── .github/workflows/  CI, CodeQL, image scan
└── docker-compose.yml
```

## Features

- **Payments:** accounts and balances, guided flow (details → review → Smart-ID → receipt), searchable transaction history
- **Verification of Payee:** `MATCH`, `CLOSE_MATCH`, `NO_MATCH`, `NOT_AVAILABLE`; a mismatch must be acknowledged explicitly
- **Money correctness:** idempotent execution (`created_by` + idempotency key), locks in sorted IBAN order, balance checked
  under lock, immutable double-entry ledger with scheduled reconciliation, `BigDecimal` amounts
- **Security:** JWT sign-in, ownership checks on every lookup, rate limiting, audit trail, a database role without update or
  delete on ledger, audit and transactions, nginx security headers, RFC 7807 errors with stable codes
- **Observability:** Prometheus metrics and alerts, Grafana dashboards for metrics and logs, one structured log line per
  request collected by Alloy into Loki ([details](#monitoring))
- **Engineering quality:** modular monolith with boundaries enforced by ArchUnit and ESLint, unit, integration
  (Testcontainers) and Playwright E2E tests, coverage gates, Liquibase migrations
- **CI/CD:** GitHub Actions run build, all tests and E2E on every push and pull request, CodeQL and Trivy image scans
  (also weekly), Dependabot

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

## Design decisions

To read the code, start at `PaymentService` → `PaymentConfirmationService` → `PaymentExecutionService` →
`TransferService.transfer`.

- **Modular monolith, not microservices:** payments and accounts must commit atomically. Feature boundaries are enforced
  by ArchUnit on the backend and ESLint on the frontend.
- **Prepare → confirm → execute:** a single "pay" endpoint could skip strong authentication. The reviewed details are
  immutable, the server executes only after the provider approves, and the browser just polls.
- **One transaction per payment:** ownership is checked without a lock, then accounts are locked in sorted IBAN order, so
  opposite transfers cannot deadlock. The idempotency key is `payment-{id}`, so concurrent polls pay once.
- **Immutable ledger:** every transfer writes a debit and a credit; the application's database role cannot update or delete
  them, and a scheduled job reconciles balances against postings.
- **Amounts:** `BigDecimal` on the server and a JSON number on the wire, limited to 12 integer digits so every amount is
  exact in a browser `double`. Decimal strings would remove that bound.
- **Frontend:** lazy-loaded features with their own NgRx slice; `accounts` is the only feature others may import.

## Testing

```sh
cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw verify   # JDK 25 and a running Docker daemon
```

Unit, ArchUnit architecture and PostgreSQL Testcontainers integration tests (concurrency, ledger immutability, seed
isolation); fails below 80% line coverage. On macOS: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw verify`.

```sh
cd frontend                       # Node 22 (>= 20.19); `nvm use` reads .nvmrc
npm ci
npm run lint && npm run format:check
npm run test:coverage             # Vitest with a coverage gate, report in frontend/coverage/
npx playwright install chromium
npm run e2e                       # isolated Compose project on ports 14200/18080
npm run e2e:ui                    # same, in Playwright UI Mode (or e2e:headed for a visible browser)
```

CI runs all of the above on every push and pull request.

## Monitoring

Prometheus, Loki, Alloy and Grafana start with `docker compose up --build`. Grafana has a metrics dashboard (payments,
latency, rejection reasons, sign-ins, ledger checks) and a logs dashboard (API requests and backend events). Prometheus
(<http://localhost:9090>) has seven alert rules in `observability/prometheus/alerts.yml`: gateway down, rejection rate above
30%, failed or blocked sign-ins, payment p95 above one second, any 5xx, a ledger discrepancy and a stale reconciliation
job. Alert routing is not configured.

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/grafana.png" alt="Grafana metrics dashboard"></td>
    <td width="50%"><img src="docs/screenshots/grafana-logs.png" alt="Grafana logs dashboard"></td>
  </tr>
</table>

The ledger reconciliation runs every five minutes and publishes `ledger_reconciliation_*` metrics on the management port:

```sh
docker compose exec backend curl -s http://localhost:8081/actuator/prometheus | grep ledger_reconciliation
```

The backend writes one JSON line per API request (method, path, status, duration, correlation id; never query strings or
bodies). Search the logs dashboard for the correlation id of an error response to find everything that request logged.
Without Grafana: `docker compose logs -f backend`.

## Security

Report suspected vulnerabilities privately through GitHub's *Security → Report a vulnerability*. This is a demonstration
project, not a production banking system. Known limitations:

- The JWT lives in `localStorage` and cannot be revoked before it expires; a production design would use OIDC with PKCE
  and `httpOnly` refresh cookies.
- Strong authentication of payments is **simulated** and verifies nothing; sign-in has none, and there are no transaction
  limits or fraud scoring.
- Rate limits are in memory, so per instance.
- CVE-2026-47884 and CVE-2026-47890 are fixed only in Spring Framework 7 and are accepted in `.trivyignore.yaml` until
  2026-12-31; the planned fix is Spring Boot 4. Jackson, Tomcat and the PostgreSQL driver are pinned above the Spring Boot
  defaults in `pom.xml` for CVE fixes.
