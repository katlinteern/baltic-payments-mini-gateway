# Security policy

This is a demonstration project, not a production banking system. Please still report suspected vulnerabilities
privately through GitHub's *Security → Report a vulnerability* instead of opening a public issue.

## Controls in place

| Area | Control |
|---|---|
| Authentication | BCrypt password hashes, JWT bearer tokens (HS256, 60 min), constant-time credential check against a decoy hash for unknown users |
| Brute force | Failed logins limited per IP + username (5 per 15 min), per IP across usernames (20) and per username across IPs (20); `429` with `Retry-After`. A successful login clears only the IP + username window |
| Authorisation | Role checks (`USER`, `AUDITOR`, `ADMIN`) and ownership checks on the source account of every payment |
| Money integrity | Single transaction, ownership checked before any row lock, pessimistic locks in sorted order with a 3 s lock timeout, `CHECK (balance >= 0)`, idempotency keys, immutable ledger |
| Audit | Append-only `audit_log` (database trigger) for logins, executed and rejected payments |
| Input validation | Bean Validation on every request field, IBAN checksum, ISO 4217 currency, bounded lengths |
| Transport edge | nginx sets CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; hides its version; no `/actuator` proxying |
| Client IP | Taken from `X-Forwarded-For` only when the request comes from a private-network proxy; nginx overwrites the header |
| Operations | Actuator on a separate management port (8081) that is not published; backend port bound to loopback in Compose; Prometheus metrics readable on that internal port only |
| Containers | Non-root users, read-only backend filesystem, all capabilities dropped, `no-new-privileges` |
| Data | Demo seed users exist only under the Liquibase contexts `local` and `test`; the default context is `prod` |
| Supply chain | Dependabot, CodeQL, Trivy image scan and a JaCoCo coverage gate in CI |

## Known limitations (deliberate for this scope)

- The JWT is kept in `localStorage` and cannot be revoked before it expires. A production design would use OIDC with
  PKCE, short-lived access tokens and `httpOnly` refresh cookies.
- Login throttling is in memory, so it is per instance.
- HS256 uses one shared secret supplied through `JWT_SECRET`; there is no key rotation.
- No strong customer authentication (SCA) step for payments, no transaction limits, no fraud scoring.
- Payments are internal transfers between accounts held in this system only.
- Two Spring Framework 6.2 advisories (CVE-2026-47884, CVE-2026-47890) are fixed only in Spring Framework 7, and 6.2 has
  no further open-source release. They are listed in `.trivyignore.yaml` with a justification and an expiry of
  2026-12-31, and the resource handler they may relate to is disabled. The planned fix is a migration to Spring Boot 4.
- Patch versions of Jackson, Tomcat and the PostgreSQL driver are pinned above the Spring Boot defaults in `pom.xml`
  to pick up CVE fixes; remove the overrides once the Boot release manages equal or newer versions.
