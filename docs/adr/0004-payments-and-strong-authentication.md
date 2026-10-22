# ADR 0004: Payments and strong authentication

- Status: accepted
- Date: 2026-10-19

## Context

A bank handles a payment in steps: the payer prepares it, sees where the money goes (including whether the beneficiary
name matches), confirms it with strong customer authentication (SCA, in the Baltics usually Smart-ID or Mobile-ID) and
only then is it executed. A single "pay" endpoint cannot enforce SCA: whoever holds the access token could skip it.

## Decision

1. **Prepare first.** `POST /api/payments` checks ownership, currency and (advisory) balance and the beneficiary name,
   stores a `payments` row (`READY`) and moves no money. The server stores the name check, so it, not the client,
   decides what the payer was shown. A payment becomes a `transactions` row only after strong authentication.
2. **Confirming starts strong authentication.** `POST /api/payments/{id}/confirmation` asks the authenticator to approve
   and returns a four-digit code (`AWAITING_AUTHENTICATION`) that the payer compares with the authentication app. A
   name mismatch needs `acknowledgeNameMismatch: true`. The reviewed details are fixed at creation: not updatable in the
   entity, and the application's database role has no UPDATE on those columns (`RestrictedDatabaseRoleIntegrationTest`).
   What is authenticated is exactly what was reviewed (PSD2 *dynamic linking*; a real adapter would show the amount and
   payee in the app too).
3. **The provider decides, the server executes.** `GET /api/payments/{id}` asks `StrongAuthenticator` for the outcome;
   when approved, the same call executes the payment through `PaymentExecutionService` and returns `COMPLETED` with the
   transaction id and balance after. The browser only polls. Rejection ends as `FAILED`, a timeout as `EXPIRED`
   (`app.payments.ttl`, `app.payments.authentication-ttl`), and the payer can `DELETE` it (`CANCELLED`). If execution
   fails (for example the balance dropped), the payment ends as `FAILED` with the reason; money that has moved always
   wins over a racing cancellation.
4. **Idempotency belongs to the payment.** The key is `payment-<id>` (ADR 0002), so concurrent polls of an approved
   payment pay once and a poll that failed halfway simply repeats. There is no client-supplied `Idempotency-Key`.
5. **The authenticator is a port.** `StrongAuthenticator` (`identity.application`) has `start` and `outcome`. The only
   implementation, `MockSmartIdAuthenticator`, shows a random code and approves after `app.strong-auth.mock.delay` (3 s),
   verifies nothing and keeps no state. It exists only when `app.strong-auth.mock.enabled=true` (the `local` profile).
   Without an authenticator the application starts but confirmation answers `503 STRONG_AUTHENTICATION_UNAVAILABLE`, so
   a deployment cannot silently run without SCA.
6. **One page for the payer.** Form, review and code with a countdown are one screen; the app then moves to
   `/payments/:id/receipt`, loaded by id so it survives a refresh. Polling and navigation live in NgRx effects.
7. **Rate limits.** Preparing is a name check and limited as one (20 per user and minute); confirming is limited like a
   payment (60 per minute).

## Consequences

- Execution happens when somebody reads the payment after the approval. If the payer approves and closes the browser,
  nothing executes and the payment expires. A provider callback would fix that.
- A payment makes at least four requests and writes a `payments` row; unfinished payments stay until archived.
- The mock approves everything, so the demo shows the flow, not the security of SCA. SCA at sign-in is out of scope.
- The audit log records `PAYMENT_CREATED`, `PAYMENT_AUTHENTICATION_STARTED`, `PAYMENT_AUTHENTICATION_REJECTED`,
  `PAYMENT_CANCELLED`, `PAYMENT_EXECUTED` and `PAYMENT_REJECTED`.

## Alternatives considered

- **One endpoint with an `authenticated: true` flag:** the client would assert its own authentication.
- **WebSocket or SSE push:** polling is simpler, works through nginx and can be replaced without changing the state model.
- **Re-entering the password:** not strong authentication and does not model Smart-ID.
