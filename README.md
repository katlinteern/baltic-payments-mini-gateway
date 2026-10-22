# Baltic Payments Mini-Gateway

A payment gateway in the way a bank does it: prepare a payment, review it with Verification of Payee, confirm it with a
simulated Smart-ID, get a receipt. Underneath are idempotent execution, pessimistic locking and an immutable
double-entry ledger, so a payment stays correct under concurrency, retries and a wrong beneficiary name.

<table>
  <tr>
    <td width="50%"><img src="screenshots/login.png" alt="Sign in"></td>
    <td width="50%"><img src="screenshots/accounts.png" alt="Accounts"></td>
  </tr>
  <tr>
    <td width="50%"><img src="screenshots/payments.png" alt="Payments"></td>
    <td width="50%"><img src="screenshots/transactions.png" alt="Transactions"></td>
  </tr>
</table>

## Getting started

Requires Docker.

```sh
docker compose up --build
docker compose down        # stop; add -v to delete the data
```

<table>
  <tr><td><b>Web app</b></td><td><a href="http://localhost:4200">localhost:4200</a></td><td><code>alice</code> / <code>Alice123!</code> or <code>bob</code> / <code>Bob12345!</code></td></tr>
  <tr><td>API docs (Swagger UI)</td><td><a href="http://localhost:8080/swagger-ui/index.html">localhost:8080/swagger-ui</a></td><td>same users: <code>POST /api/auth/login</code>, then <i>Authorize</i></td></tr>
  <tr><td>Grafana metrics</td><td><a href="http://localhost:3000/d/baltic-gateway">localhost:3000/d/baltic-gateway</a></td><td><code>admin</code> / <code>grafana-dev-only</code></td></tr>
  <tr><td>Grafana logs</td><td><a href="http://localhost:3000/d/baltic-logs">localhost:3000/d/baltic-logs</a></td><td>same as above</td></tr>
  <tr><td>Prometheus (metrics, alerts)</td><td><a href="http://localhost:9090">localhost:9090</a></td><td>none</td></tr>
  <tr><td>PostgreSQL</td><td><code>localhost:5432</code>, database <code>payments</code></td><td><code>payments</code> / <code>payments-dev-only</code> (owner), <code>payments_app</code> / <code>payments-app-dev-only</code> (application)</td></tr>
</table>

**Try it:** sign in as `alice`, open Payments and pay Bob (`EE382200221020145685`). The name "Bob Tamm" matches, "Bob" is a
close match and "Mallory" is a mismatch that must be acknowledged. The simulated Smart-ID approves after a few seconds.

## Tech stack

<table>
  <tr><td><b>Backend</b></td><td>Java 25, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA</td></tr>
  <tr><td><b>Frontend</b></td><td>Angular 21, Angular Material, NgRx, served by nginx</td></tr>
  <tr><td><b>Database</b></td><td>PostgreSQL 16, Liquibase migrations</td></tr>
  <tr><td><b>Infrastructure</b></td><td>Docker Compose, nginx, Prometheus, Grafana, Loki, Alloy</td></tr>
  <tr><td><b>Quality</b></td><td>JUnit, Mockito, ArchUnit, Testcontainers, Vitest, Playwright, JaCoCo, ESLint, Prettier</td></tr>
  <tr><td><b>CI</b></td><td>GitHub Actions: build, all tests, coverage gates, E2E, CodeQL, Trivy image scan</td></tr>
</table>

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
├── screenshots/        images used in this README
├── .github/workflows/  CI, CodeQL, image scan
└── docker-compose.yml
```

## Features

<table>
  <tr><td rowspan="2"><b>Payments</b></td><td>Flow</td><td>prepare → review → confirm with Smart-ID (simulated) → receipt</td></tr>
  <tr><td>Verification of Payee</td><td><code>MATCH</code>, <code>CLOSE_MATCH</code>, <code>NO_MATCH</code>, <code>NOT_AVAILABLE</code>; a mismatch must be acknowledged</td></tr>
  <tr><td rowspan="3"><b>Money correctness</b></td><td>Concurrency</td><td>one transaction, locks in sorted IBAN order, idempotency key <code>payment-{id}</code></td></tr>
  <tr><td>Ledger</td><td>immutable double-entry, scheduled reconciliation</td></tr>
  <tr><td>Amounts</td><td><code>BigDecimal</code>, at most 12 integer digits and 2 decimals</td></tr>
  <tr><td rowspan="2"><b>Security</b></td><td>Access</td><td>JWT, ownership checks on every lookup, rate limiting</td></tr>
  <tr><td>Protection</td><td>audit trail, restricted database role, nginx security headers</td></tr>
  <tr><td><b>Operations</b></td><td>Observability</td><td>Prometheus alerts, Grafana dashboards, Loki logs</td></tr>
  <tr><td rowspan="2"><b>Quality</b></td><td>Architecture</td><td>modular monolith checked by ArchUnit and ESLint</td></tr>
  <tr><td>Testing</td><td>unit, integration and E2E tests, coverage gates, CodeQL, Trivy</td></tr>
</table>

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

Grafana has a metrics dashboard and a logs dashboard (API requests, backend events); Prometheus has seven alert rules in
`observability/prometheus/alerts.yml` (no alert routing). Search the logs for the correlation id of an error response to
see everything that request logged.

<table>
  <tr>
    <td width="50%"><img src="screenshots/grafana.png" alt="Grafana metrics dashboard"></td>
    <td width="50%"><img src="screenshots/grafana-logs.png" alt="Grafana logs dashboard"></td>
  </tr>
</table>

## Security

Report vulnerabilities privately via GitHub's *Security → Report a vulnerability*. This is a demo, not a production bank.

<table>
  <tr><td><b>JWT</b></td><td>kept in <code>localStorage</code> and cannot be revoked; production would use OIDC with <code>httpOnly</code> refresh cookies</td></tr>
  <tr><td><b>Smart-ID</b></td><td>simulated, verifies nothing; sign-in has no strong authentication, no limits, no fraud scoring</td></tr>
  <tr><td><b>Rate limits</b></td><td>in memory, so per instance</td></tr>
  <tr><td><b>Dependencies</b></td><td>two Spring Framework CVEs accepted in <code>.trivyignore.yaml</code> until 2026-12-31 (fix: Spring Boot 4); some patch versions pinned in <code>pom.xml</code></td></tr>
</table>
