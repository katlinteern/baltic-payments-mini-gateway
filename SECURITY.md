# Security policy

This is a demonstration project, not a production banking system. Report suspected vulnerabilities privately through
GitHub's *Security → Report a vulnerability*, not a public issue.

## Controls

| Area | Control |
|---|---|
| Authentication | BCrypt hashes, JWT bearer tokens (HS256, 60 min), constant-time check against a decoy hash for unknown users |
| Brute force | Failed logins limited per IP + username (5 per 15 min), per IP (20) and per username (20); `429` with `Retry-After` |
| Authorisation | Every lookup is scoped to the signed-in customer; the source account of a payment must be theirs. No back-office role in the customer API |
| Payment confirmation | Executed only after strong authentication; code and outcome come from the server, reviewed details are immutable (also for the application's database role), payments expire and execute once. Without an authenticator confirmation fails with `503` |
| Money integrity | One transaction, ownership checked before any lock, locks in sorted order with a 3 s timeout, `CHECK (balance >= 0)`, idempotency keys, immutable ledger |
| Audit | Append-only `audit_log` (trigger, no UPDATE/DELETE/TRUNCATE for the application role) for logins, payments, rejections and acknowledged name mismatches; every entry carries the correlation id |
| Database access | The application connects as `payments_app` with minimal rights; Liquibase migrates as the schema owner |
| Abuse control | Payee name checks (20/min) and payments (60/min) rate limited per user |
| Input validation | Bean Validation on every field, IBAN checksum, ISO 4217 currency, bounded lengths |
| Edge | nginx sets CSP (same origin only), `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; `X-Forwarded-For` is trusted only from private-network proxies |
| Operations | Actuator on an unpublished management port (8081); Swagger UI only under the `local` profile |
| Containers | Non-root, read-only backend filesystem, all capabilities dropped, `no-new-privileges` |
| Data | Demo users exist only under the Liquibase contexts `local` and `test`; the default context is `prod` |
| Supply chain | Dependabot, CodeQL, Trivy image scan and a JaCoCo coverage gate in CI |

## Known limitations

- The JWT lives in `localStorage` and cannot be revoked before it expires. A production design would use OIDC with PKCE
  and `httpOnly` refresh cookies. HS256 uses one shared `JWT_SECRET` without rotation.
- Login throttling is in memory, so it is per instance.
- Strong authentication of payments is **simulated** and verifies nothing ([ADR 0004](docs/adr/0004-payments-and-strong-authentication.md)).
  Sign-in has no strong authentication, there are no transaction limits and no fraud scoring.
- The payee name check reveals whether an IBAN exists and whether a guessed name is close, as real Verification of Payee
  does; rate limits and authenticated callers are the mitigation.
- CVE-2026-47884 and CVE-2026-47890 are fixed only in Spring Framework 7, and 6.2 has no further open-source release.
  They are accepted in `.trivyignore.yaml` with a justification and an expiry of 2026-12-31. The planned fix is Spring
  Boot 4.
- Jackson, Tomcat and the PostgreSQL driver are pinned above the Spring Boot defaults in `pom.xml` for CVE fixes; remove
  the overrides once Boot manages equal or newer versions.
