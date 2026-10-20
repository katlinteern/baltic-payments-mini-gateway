# Baltic Payments Mini-Gateway

A payment gateway in the way a bank does it: prepare a payment, review it with **Verification of Payee**, confirm it with a
simulated **Smart-ID**, get a receipt. Underneath are idempotent execution, pessimistic locking and an immutable
double-entry ledger, so a payment stays correct under concurrency, retries and a wrong beneficiary name.

![Accounts](docs/screenshots/accounts.png)

## Features

- Accounts and balances, payments between accounts, searchable transaction history
- Payments: details → review → Smart-ID confirmation → receipt
- Verification of Payee (`MATCH`, `CLOSE_MATCH`, `NO_MATCH`, `NOT_AVAILABLE`) with explicit acknowledgement of a mismatch
- No double payment, no deadlock, no overdraft; an immutable ledger with scheduled reconciliation
- JWT sign-in, ownership checks, rate limiting, audit trail, restricted database role
- Metrics, Prometheus alerts and a Grafana dashboard

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

| Review with Verification of Payee | Smart-ID confirmation |
|---|---|
| ![Review](docs/screenshots/payment-review.png) | ![Smart-ID](docs/screenshots/smart-id.png) |

More in [docs/architecture.md](docs/architecture.md): locking, idempotency, ledger and design notes.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Liquibase |
| Frontend | Angular 21, Angular Material, NgRx |
| Database | PostgreSQL 16 |
| Infrastructure | Docker Compose, nginx, Prometheus, Grafana |
| Quality | JUnit, Mockito, ArchUnit, Testcontainers, Vitest, Playwright, JaCoCo, ESLint, Prettier, CodeQL, Trivy |

## Project structure

```
├─ backend/        Spring Boot modular monolith: identity · accounts · payments · audit · platform
├─ frontend/       Angular app: core · features (login, accounts, payments, transactions) · shared
├─ db/             database role bootstrap
├─ observability/  Prometheus rules and Grafana dashboards
├─ docs/           architecture, operations, limitations and ADRs
└─ docker-compose.yml
```

## Getting started

Requires Docker Desktop with Compose v2.

```sh
docker compose up --build
```

- App: <http://localhost:4200>
- API docs: <http://localhost:8080/swagger-ui/index.html> (`local` profile, the Compose default)
- Grafana and Prometheus: see [Monitoring](#monitoring)

Frontend source changes are picked up automatically while Compose is running.

Demo accounts (seeded only under the `local` profile, passwords are test-only):

| Username | Password | Accounts | Holder |
|---|---|---|---|
| `alice` | `Alice123!` | 2 | Alice Kask |
| `bob` | `Bob12345!` | 2 | Bob Tamm |

Sign in as `alice`, open **Payments** and pay Bob (`EE382200221020145685`, name "Bob Tamm"). Try the name "Bob" (a close
match) or "Mallory" (a mismatch that must be acknowledged). The simulated Smart-ID approves by itself after a few
seconds. There is one kind of user, a customer; back-office access (audit, reconciliation) is deliberately not part of the
application ([operations](docs/operations.md)).

Stop with `docker compose down`; add `-v` to delete the database data.

## Monitoring

Grafana and Prometheus start with the rest of the stack using `docker compose up --build`.

| Tool | URL | Login | What you see |
|---|---|---|---|
| Grafana | <http://localhost:3000> | `admin` / `grafana-dev-only` | Dashboard **Baltic Payments Gateway**: <http://localhost:3000/d/baltic-gateway> |
| Prometheus | <http://localhost:9090> | none | Raw metrics at `/graph`, the seven alert rules at `/alerts` |

In Grafana the dashboard is also under *Dashboards → Baltic Payments → Baltic Payments Gateway*. It is provisioned from
code (`observability/grafana`), refreshes every 10 s and shows the last 30 minutes. Make a few payments, a wrong sign-in
and a payment to "Mallory" in the app to see the panels move.

![Grafana](docs/screenshots/grafana.png)

| Area | Panels |
|---|---|
| Status | gateway up, completed and rejected payments (1 h), ledger discrepancies |
| Payments | per minute by outcome, processing latency (p50, p95, p99), rejection reasons |
| Security | sign-ins per minute: success, failed, blocked |
| Platform | HTTP requests and p95 latency, JVM heap, database connection pool |

Ports are bound to `127.0.0.1`. If port 3000 is taken, start with `GRAFANA_PORT=3001`; change the password with
`GRAFANA_PASSWORD`. Metrics and alerts in detail: [docs/operations.md](docs/operations.md#monitoring-optional).

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
```

CI (`.github/workflows/ci.yml`) runs all of the above plus container image builds with a Trivy scan; `codeql.yml` adds
static analysis.

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
