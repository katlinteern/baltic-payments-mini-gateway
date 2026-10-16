# Security policy

This is a demonstration project, not a production banking system. Please still report suspected vulnerabilities
privately through GitHub's *Security → Report a vulnerability* instead of opening a public issue.

## Controls in place

| Area | Control |
|---|---|
| Authentication | BCrypt password hashes, JWT bearer tokens (HS256, 60 min), constant-time credential check against a decoy hash for unknown users |
| Brute force | Failed logins limited per client IP and username (5 per 15 min, `429` with `Retry-After`) |
| Authorisation | Role checks (`USER`, `AUDITOR`, `ADMIN`) and ownership checks on the source account of every payment |
| Money integrity | Single transaction, pessimistic locks in sorted order, `CHECK (balance >= 0)`, idempotency keys, immutable ledger |
| Audit | Append-only `audit_log` (database trigger) for logins, executed and rejected payments |
| Input validation | Bean Validation on every request field, IBAN checksum, ISO 4217 currency, bounded lengths |
| Transport edge | nginx sets CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; hides its version; no `/actuator` proxying |
| Client IP | Taken from `X-Forwarded-For` only when the request comes from a private-network proxy; nginx overwrites the header |
| Operations | Actuator on a separate management port (8081) that is not published; backend port bound to loopback in Compose |
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
