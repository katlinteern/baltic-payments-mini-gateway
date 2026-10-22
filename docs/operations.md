# Operations

## Configuration

| Setting | Meaning |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` enables demo data, Swagger UI, development secrets and the Smart-ID simulator. Any other profile requires `JWT_SECRET` (at least 32 bytes) and `DB_PASSWORD`, and payments cannot be confirmed (`503`) until a real authenticator exists ([ADR 0004](adr/0004-payments-and-strong-authentication.md)). |
| `LIQUIBASE_CONTEXTS` | Defaults to `prod` (schema only); demo data is tagged `local,test`. `prod` refuses to start without the `payments_app` database role. |
| `STRONG_AUTH_MOCK_DELAY` / `STRONG_AUTH_MOCK_OUTCOME` | Simulator delay (default `PT3S`) and result (`APPROVED` or `REJECTED`), for example `STRONG_AUTH_MOCK_OUTCOME=REJECTED docker compose up --build`. |
| `app.payments.*` | `ttl` (10 min) for the review, `authentication-ttl` (2 min) for the approval. |
| `app.login-rate-limit.*`, `app.rate-limit.*` | Limits listed in [SECURITY.md](../SECURITY.md). |

Ports are published on `127.0.0.1` only; Actuator runs on an internal management port (8081) that is never published or
proxied. After pulling schema changes, reset a local database with `docker compose down -v`.

## Ledger reconciliation and audit

Reconciliation runs every five minutes and has no endpoint. It publishes `ledger_reconciliation_discrepancies`,
`ledger_reconciliation_findings{check=...}` and `ledger_reconciliation_last_run_timestamp_seconds` on the management
port, and logs failures as errors.

```sh
docker compose exec backend curl -s http://localhost:8081/actuator/prometheus | grep ledger_reconciliation
```

The audit trail is the append-only `audit_log` table, read with a database tool; it has no API.

## Monitoring

Prometheus, Loki, Alloy and Grafana start with `docker compose up --build`, bound to `127.0.0.1`.

| | URL | Notes |
|---|---|---|
| Grafana | <http://localhost:3000/d/baltic-gateway> | `admin` / `grafana-dev-only` (`GRAFANA_PASSWORD`); dashboard provisioned from `observability/grafana` |
| Grafana logs | <http://localhost:3000/d/baltic-logs> | request, backend and all-service logs |
| Prometheus | <http://localhost:9090> | raw metrics and the seven alert rules in `observability/prometheus/alerts.yml` |

Business metrics: `payments_total{outcome,currency,reason}`, `payments_value_total`, `payments_processing_seconds` and
`auth_logins_total{outcome}`, next to the standard HTTP, JVM and connection-pool metrics.

Alerts: gateway down, payment rejection rate above 30%, a burst of failed or blocked sign-ins, payment p95 above one
second, any 5xx, a ledger discrepancy and a stale reconciliation job. Alert routing is not configured.

## Logs

The backend writes one JSON line per API request (method, path, status, duration, correlation id; never query strings or
bodies). Alloy tails the container output through the Docker socket (read-only, local stack only) and pushes it to Loki.
Search the logs dashboard for the correlation id of an error response to find everything that request logged. Without
Grafana: `docker compose logs -f backend`.
