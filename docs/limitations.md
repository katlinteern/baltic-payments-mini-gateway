# Known limitations and next steps

An educational mini-gateway, not a production banking system. Left out on purpose, with what a real bank would do.

## Limitations

- **Back-office roles.** Auditors, compliance and operations staff use internal tools with single sign-on, need-to-know
  access and logged queries, never the customer application. Here the reconciliation result is a metric and the audit log
  is a table.
- **Ledger.** Double-entry per transfer between customer accounts. A bank also has internal accounts (settlement, fees), a
  chart of accounts, period closing and reversal postings; opening balances would be postings too (the demo seed loads
  them directly).
- **Smart-ID.** Confirmation is simulated behind an interface; the simulator approves every request after a delay and
  verifies nothing. A real provider is an adapter.
- **A read that executes a payment.** `GET /api/payments/{id}` executes an approved payment because the
  provider's outcome is polled, not pushed. Concurrent polls pay once and the response is not cacheable, but a plain `GET`
  should be safe: with a provider callback or a background poller the read would be side-effect free.
- **Access tokens.** A JWT is valid until it expires (60 minutes) even if the user is disabled meanwhile; there is no
  logout, `jti` or revocation list, and the filter does not query the database.
- **Payee name check.** A `CLOSE_MATCH` returns the registered holder name, as Verification of Payee does; guessing is
  limited by the per-user rate limit, not prevented.
- **Transaction history.** Paging reads `offset + size` transactions and pages in memory, so deep pages get slower.
- **Single instance.** Rate limits are in memory per instance and the reconciliation job runs on every instance without a
  distributed lock.
- **Local profile by default.** Compose starts the `local` profile (demo data, Swagger UI, development secrets, simulated
  Smart-ID) and publishes ports on `127.0.0.1` only.
- **Not implemented:** strong authentication at sign-in, transaction limits, OIDC/refresh tokens, key rotation, external
  payment rails and distributed rate limiting. See also [SECURITY.md](../SECURITY.md).

## What I would do next

1. A real Smart-ID/Mobile-ID adapter with a callback instead of polling, so an approval is executed even when the browser
   is closed ([ADR 0004](adr/0004-payments-and-strong-authentication.md)).
2. Amounts as strings on the wire (`"123.50"`, as in Berlin Group APIs) instead of JSON numbers
   ([ADR 0002](adr/0002-money-ledger-payee-verification.md)).
3. Spring Boot 4, which removes the two accepted Spring Framework advisories listed in `.trivyignore.yaml`.
4. A statement read from `ledger_entries` joined to the caller's accounts, which avoids the in-memory paging in
   `TransactionHistoryService`.
5. Shared rate limiting and token revocation (Redis or an API gateway) once there is more than one instance.
