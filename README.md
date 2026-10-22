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

## At a glance

| | |
|---|---|
| **Payments** | Accounts and balances, guided payment flow (details → review → Smart-ID → receipt), searchable transaction history |
| **Verification of Payee** | `MATCH`, `CLOSE_MATCH`, `NO_MATCH`, `NOT_AVAILABLE`; a mismatch must be acknowledged explicitly |
| **Money correctness** | Idempotent execution, locks in sorted IBAN order, no overdraft, immutable double-entry ledger with reconciliation |
| **Security** | JWT sign-in, ownership checks on every lookup, rate limiting, audit trail, least-privilege database role, security headers |
| **Observability** | Prometheus metrics and alerts, Grafana dashboards, one structured log line per request collected by Loki and Alloy |
| **Backend** | Java 25, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Liquibase, PostgreSQL 16 |
| **Frontend** | Angular 21, Angular Material, NgRx, served by nginx |
| **Quality** | JUnit, Mockito, ArchUnit, Testcontainers, Vitest, Playwright, JaCoCo, ESLint, Prettier |
| **CI** | GitHub Actions: build, all tests, coverage gates, E2E, CodeQL, Trivy image scan |

### Services and credentials

`docker compose up --build` starts everything below. All ports are bound to `127.0.0.1` and can be changed through
`FRONTEND_PORT`, `BACKEND_PORT`, `GRAFANA_PORT` and `PROMETHEUS_PORT`.

| Service | URL / port | Username | Password |
|---|---|---|---|
| Web app (Angular, nginx) | <http://localhost:4200> | `alice` or `bob` | `Alice123!` / `Bob12345!` |
| Backend API and Swagger UI | <http://localhost:8080/swagger-ui/index.html> | `alice` or `bob` via `POST /api/auth/login`, then *Authorize* | same as above |
| Grafana | <http://localhost:3000> | `admin` | `grafana-dev-only` |
| Prometheus | <http://localhost:9090> | none | none |
| PostgreSQL (database `payments`) | `localhost:5432` | `payments` (owner) / `payments_app` (application) | `payments-dev-only` / `payments-app-dev-only` |
| Loki, Alloy, actuator (`8081`) | internal to the Compose network, not published | | |

The credentials are demo-only and exist under the `local` profile, the Compose default. In Grafana, open the dashboards
*Baltic Payments Gateway* (metrics) and *Baltic Payments Logs* (request logs from Loki), see [Monitoring](#monitoring).

## Getting started

Requires Docker Desktop with Compose v2.

```sh
docker compose up --build
```

Open the app at <http://localhost:4200>; every service and login is listed in [Services and credentials](#services-and-credentials).

The default Compose stack serves the production-built frontend through nginx, including the configured security headers.
For live frontend source updates, run `docker compose --profile dev up --build db backend frontend-dev` instead.

Demo users: `alice` (Alice Kask) and `bob` (Bob Tamm), two accounts each, seeded only under the `local` profile. Sign in as `alice`, open Payments and pay Bob (`EE382200221020145685`, name "Bob Tamm"). Try the name "Bob" (a close
match) or "Mallory" (a mismatch that must be acknowledged). The simulated Smart-ID approves by itself after a few
seconds. There is one kind of user, a customer; back-office access (audit, reconciliation) is deliberately not part of the
application ([operations](docs/operations.md)).

Stop with `docker compose down`; add `-v` to delete the database data.

## Features

### Payments

- Accounts and balances, payments between accounts, searchable transaction history
- Guided flow: details → review → Smart-ID confirmation → receipt
- Verification of Payee (`MATCH`, `CLOSE_MATCH`, `NO_MATCH`, `NOT_AVAILABLE`); a mismatch must be acknowledged explicitly
- Simulated Smart-ID strong authentication behind a `StrongAuthenticator` port

### Money correctness

- No double payment: idempotent execution through `(created_by, idempotency_key)`
- No deadlock: account locks always taken in sorted IBAN order, one transaction per payment
- No overdraft: balance checked under lock
- Immutable double-entry ledger with scheduled reconciliation; `BigDecimal` amounts, never `double`

### Security

- JWT sign-in, ownership checks on every lookup, rate limiting
- Audit trail; the application database role has no update or delete on ledger, audit and transactions
- Security headers in nginx, RFC 7807 errors with stable error codes

### Engineering quality

- Modular monolith with package boundaries enforced by ArchUnit; frontend boundaries enforced by ESLint
- Unit, architecture, Testcontainers integration (concurrency, ledger immutability) and Playwright E2E tests
- Coverage gates: backend above 80% line coverage (JaCoCo), frontend thresholds in `angular.json`
- Liquibase migrations, versioned and never edited after deployment; ADRs for the key decisions

### CI/CD (GitHub Actions)

- `ci.yml` on every push and pull request: backend `verify` (unit, architecture, integration, coverage), frontend lint,
  format check, unit tests with coverage and production build, then Playwright E2E against the full Compose stack
- Backend and frontend container images are built and scanned with Trivy (HIGH/CRITICAL with a fix), also weekly
  through `image-scan.yml`
- `codeql.yml`: CodeQL static analysis on every push and pull request, also weekly
- Dependabot keeps dependencies current; coverage and E2E diagnostics are kept as build artifacts

### Observability

- Metrics, Prometheus alerts and a Grafana dashboard, started together with the stack
- One structured log line per API request, collected by Loki and searchable in Grafana

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

More in [docs/architecture.md](docs/architecture.md): locking, idempotency, ledger and design notes.

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
├── docs/               architecture, operations, limitations, ADRs
├── .github/workflows/  CI, CodeQL, image scan
└── docker-compose.yml
```

## Monitoring

Prometheus and Grafana start with the stack.

| | URL | Sign-in |
|---|---|---|
| Grafana dashboard *Baltic Payments Gateway* | <http://localhost:3000/d/baltic-gateway> | user `admin`, password `grafana-dev-only` |
| Grafana logs dashboard *Baltic Payments Logs* | <http://localhost:3000/d/baltic-logs> | same as Grafana above |
| Prometheus alert rules | <http://localhost:9090/alerts> | none |
| Prometheus metrics browser | <http://localhost:9090/graph> | none |

Logs without Grafana: `docker compose logs -f backend`. Metrics, alerts, logs and ports:
[docs/operations.md](docs/operations.md#monitoring).

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

CI runs all of the above on every push and pull request, see [CI/CD](#features).

## Documentation

| Document | Contents |
|---|---|
| [Architecture](docs/architecture.md) | Module layout, payment flow, concurrency, ledger and design notes |
| [Operations](docs/operations.md) | Configuration, reconciliation, audit trail, monitoring and alerts |
| [Limitations](docs/limitations.md) | What is left out on purpose and what comes next |
| [ADRs](docs/adr) | Modular monolith, money and ledger, frontend boundaries, payments and strong authentication |
| [SECURITY.md](SECURITY.md) | Security controls and vulnerability reporting |
| [AGENTS.md](AGENTS.md) | Conventions for AI coding assistants |

Start reading at [ADR 0004](docs/adr/0004-payments-and-strong-authentication.md) and `PaymentExecutionService`.
