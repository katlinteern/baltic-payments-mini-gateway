# Security policy

This is a demonstration project, not a production banking system. Please still report suspected vulnerabilities
privately through GitHub's *Security → Report a vulnerability* instead of opening a public issue.

## Controls in place

| Area | Control |
|---|---|
| Authentication | BCrypt password hashes, JWT bearer tokens (HS256, 60 min), constant-time credential check against a decoy hash for unknown users |
| Brute force | Failed logins limited per IP + username (5 per 15 min), per IP across usernames (20) and per username across IPs (20); `429` with `Retry-After`. A successful login clears only the IP + username window |
| Authorisation | Every request is scoped to the signed-in customer: accounts, statements and the transaction history show only their own data, and the source account of every payment must be theirs. There is no back-office role in the customer API; the ledger reconciliation result is published only as metrics on the unpublished management port |
| Payment confirmation | A payment can only be executed from an intent that the payer confirmed with strong authentication; the code and the outcome come from the server, the reviewed details cannot be altered afterwards (not even by the application's database role), intents expire, and an approved intent is executed once. Without an authenticator payments cannot be confirmed (`503`) |
| Money integrity | Single transaction, ownership checked before any row lock, pessimistic locks in sorted order with a 3 s lock timeout, `CHECK (balance >= 0)`, idempotency keys, immutable ledger |
| Audit | Append-only `audit_log` (database trigger and no UPDATE/DELETE/TRUNCATE for the application role) for logins, executed and rejected payments, including payer-acknowledged name mismatches |
| Database access | In Compose the application connects as `payments_app` with only the rights it needs; Liquibase migrates as the schema owner. The role cannot rewrite or remove ledger, audit or transaction rows, nor drop the immutability triggers |
| Abuse control | Payee name checks (20/min) and payments (60/min) rate limited per user; the name check is an oracle for guessing account holder names |
| Input validation | Bean Validation on every request field, IBAN checksum, ISO 4217 currency, bounded lengths |
| Transport edge | nginx sets CSP (same-origin only: fonts are self-hosted, no third-party requests), `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; hides its version; no `/actuator` proxying |
| Client IP | Taken from `X-Forwarded-For` only when the request comes from a private-network proxy; nginx overwrites the header |
| Operations | Actuator on a separate management port (8081) that is not published; backend port bound to loopback in Compose; Prometheus metrics readable on that internal port only; the OpenAPI description and Swagger UI exist only under the `local` profile |
| Containers | Non-root users, read-only backend filesystem, all capabilities dropped, `no-new-privileges` |
| Data | Demo seed users exist only under the Liquibase contexts `local` and `test`; the default context is `prod` |
| Supply chain | Dependabot, CodeQL, Trivy image scan and a JaCoCo coverage gate in CI |

## Known limitations (deliberate for this scope)

- The JWT is kept in `localStorage` and cannot be revoked before it expires. A production design would use OIDC with
  PKCE, short-lived access tokens and `httpOnly` refresh cookies.
- Login throttling is in memory, so it is per instance.
- HS256 uses one shared secret supplied through `JWT_SECRET`; there is no key rotation.
- Strong authentication of payments is **simulated**: the local profile ships a stand-in for Smart-ID that approves every
  payment after a few seconds and verifies nothing (ADR 0004). A real provider adapter replaces it behind the same port.
  Signing in is still password plus JWT; there is no SCA at login, no transaction limits and no fraud scoring.
- Payments are internal transfers between accounts held in this system only.
- The payee name check reveals whether an IBAN exists (`NOT_AVAILABLE` for unknown accounts) and whether a guessed name
  is close; real Verification of Payee schemes accept this and rely on rate limits and authenticated callers, as here.
- Two Spring Framework 6.2 advisories (CVE-2026-47884, CVE-2026-47890) are fixed only in Spring Framework 7, and 6.2 has
  no further open-source release. They are listed in `.trivyignore.yaml` with a justification and an expiry of
  2026-12-31, and the resource handler they may relate to is disabled. Spring Boot 3.5 is out of open-source support, so
  the planned fix is a migration to Spring Boot 4 (README, *What I would do next*); until then each release is scanned by
  Trivy and the ignore entries must be reviewed before they expire.
- Patch versions of Jackson, Tomcat and the PostgreSQL driver are pinned above the Spring Boot defaults in `pom.xml`
  to pick up CVE fixes; remove the overrides once the Boot release manages equal or newer versions.
