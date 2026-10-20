# Configuration and operations

## Configuration

| Setting | Meaning |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` enables demo seed data, Swagger UI, a development JWT key and a development DB password. Any other profile requires `JWT_SECRET` and `DB_PASSWORD`. |
| `LIQUIBASE_CONTEXTS` | Defaults to `prod` (schema only). Demo data is tagged `local,test`. The `prod` context refuses to start when the database role `payments_app` does not exist. |
| `JWT_SECRET` | At least 32 bytes. |
| `STRONG_AUTH_MOCK_DELAY` | Compose variable for the simulated Smart-ID (`app.strong-auth.mock.delay`, default `PT3S`): how long it waits before approving. The simulator exists only when `app.strong-auth.mock.enabled=true` (the `local` profile). Any other profile starts, but payments cannot be confirmed (`503`) until a real authenticator is provided ([ADR 0004](adr/0004-payment-intents-and-strong-authentication.md)). |
| `STRONG_AUTH_MOCK_OUTCOME` | Local mock result after the delay: `APPROVED` (default) or `REJECTED`. Use `STRONG_AUTH_MOCK_OUTCOME=REJECTED docker compose up --build` to exercise the rejected-authentication flow. |
| `app.payments.*` | `ttl` (10 min): how long a prepared payment may wait for the payer; `authentication-ttl` (2 min): how long the payer has to approve it. |
| `app.login-rate-limit.*` | Failed-login limits per window (15 min): 5 per IP + username, 20 per IP, 20 per username. |
| `app.rate-limit.*` | Per user and minute: 60 payments, 20 payee verifications. |

Both the app and the backend port are published on `127.0.0.1` only. The browser talks to the API through nginx, and
Actuator runs on an internal management port (8081) that is never published or proxied.

## Local database

The migration history is one baseline per module until the first deployment, and the database provisions an
unprivileged application role on first start. After pulling schema changes, reset a local database once with
`docker compose down -v`.

## Ledger reconciliation

An operations tool, not a customer feature, so it has no endpoint. It runs every five minutes and publishes metrics on
the management port: `ledger_reconciliation_discrepancies` (total), `ledger_reconciliation_findings{check=...}`
(`unbalanced_transactions`, `balance_mismatches`, `broken_balance_chains`) and
`ledger_reconciliation_last_run_timestamp_seconds`. A failing run is also logged as an error.

```sh
docker compose exec backend curl -s http://localhost:8081/actuator/prometheus | grep ledger_reconciliation
```

## Audit trail

Written by the application into the append-only `audit_log` table and not readable through the API. Operations staff read
it with a database tool; in a real bank a log pipeline would ship it to a SIEM.

## Monitoring (optional)

```sh
docker compose --profile observability up --build
```

| | URL | Notes |
|---|---|---|
| Grafana | <http://localhost:3000> (change with `GRAFANA_PORT`) | user `admin`, password `grafana-dev-only` (override with `GRAFANA_PASSWORD`); the *Baltic Payments Gateway* dashboard is provisioned from code and opens directly at <http://localhost:3000/d/baltic-gateway> (or *Dashboards → Baltic Payments*) |
| Prometheus | <http://localhost:9090> | raw metrics at `/graph`, the seven alert rules at `/alerts` (`observability/prometheus/alerts.yml`) |

Both are bound to `127.0.0.1`. A test asserts that the public port does not serve metrics.

Business metrics (bounded tag values): `payments_total{outcome,currency,reason}`, `payments_value_total`,
`payments_processing_seconds` (histogram) and `auth_logins_total{outcome}`, plus the standard HTTP, JVM and
connection-pool metrics. `payments_value_total` is exported but not charted: a counter cannot give an exact amount for a
time window, so exact amounts come from the ledger.

Alerts: gateway down, payment rejection rate above 30%, a burst of failed or blocked sign-ins, payment p95 above one
second, any 5xx responses, a ledger discrepancy and a stale reconciliation job. Alert routing (email, Slack) is
intentionally not configured.

## Dependency updates

Dependabot runs monthly. Major upgrades (Spring Boot 4 with springdoc 3, Angular 22 with TypeScript 6, newer JDK and
Node lines) are excluded on purpose: each is one planned migration, not a bot bump (`.github/dependabot.yml`).
