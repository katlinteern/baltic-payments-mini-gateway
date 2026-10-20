# ADR 0004: Payments and strong authentication

- Status: accepted
- Date: 2026-10-19

## Context

Until now a payment was one request: the client posted it and the money moved. A bank does it in steps. The payer
prepares a payment, sees where the money goes (including whether the beneficiary name matches the account holder),
confirms it with strong customer authentication (SCA, in the Baltics usually Smart-ID or Mobile-ID) and only then is it
executed. A single "pay" endpoint also cannot enforce SCA: whoever has the access token could skip it.

## Decision

1. **A payment is prepared first.** `POST /api/payments` checks the accounts (ownership,
   currency, advisory balance check) and the beneficiary name, stores a `payments` row (`READY`) and moves no
   money. The result of the name check is stored with the payment, so the server, not the client, decides what the payer
   was shown. The earlier single-step endpoint that moved money and the separate `verify-payee` endpoint are gone;
   a payment becomes a `transactions` row only after strong authentication.
2. **Confirming starts strong authentication.** `POST /api/payments/{id}/confirmation` asks the authenticator
   to approve the payment and returns a four-digit verification code (`AWAITING_AUTHENTICATION`). The payer compares
   it with the code in the authentication app, as with Smart-ID. A name mismatch can be confirmed only with
   `acknowledgeNameMismatch: true`. The details the payer reviewed are fixed when the payment is created: they are not
   updatable in the entity, and the application's database role has no UPDATE on those columns, only on the lifecycle
   columns (`RestrictedDatabaseRoleIntegrationTest`). What is authenticated is therefore exactly what was reviewed. (PSD2 calls this *dynamic linking*: the authentication must
   be bound to the amount and the payee. The mock shows only a code; a real Smart-ID adapter would also display the amount and
   the beneficiary from the payment in the authentication app.)
3. **The outcome comes from the provider, and the server executes.** `GET /api/payments/{id}` asks the
   `StrongAuthenticator` for the outcome. When it is approved, the same call executes the payment through the existing
   `PaymentExecutionService` and returns `COMPLETED` with the transaction id and the balance after. The browser only polls; it
   cannot declare a payment approved. Rejection ends the payment as `FAILED`; a payment that outlives its time ends as
   `EXPIRED` (`app.payments.ttl` for the review, `app.payments.authentication-ttl` for the approval); the payer
   can `DELETE` it (`CANCELLED`).
4. **Idempotency moved from the client to the payment.** The payment is executed under the key `payment-<payment id>`.
   Several polls reading an approved payment at once therefore pay once: the others get the stored result (the
   idempotency of ADR 0002, item 3, is what makes this safe), and a poll that failed halfway simply repeats. There is no
   client-supplied `Idempotency-Key` any more. If a payment cannot be executed (for example the balance dropped in the
   meantime) the payment ends as `FAILED` with the reason; money that has moved always wins over a cancellation that
   raced with it.
5. **The authenticator is a port.** `StrongAuthenticator` (`identity.application`) has two operations, `start` and
   `outcome`. The only implementation, `MockSmartIdAuthenticator`, is a stand-in: it shows a random four-digit code and
   approves every authentication after `app.strong-auth.mock.delay` (3 s). It verifies nothing and keeps no state (an
   approval is "the delay has passed"), so it also works after a restart. It exists only when
   `app.strong-auth.mock.enabled=true`, which the `local` profile sets. Without any authenticator the application
   starts, but confirming a payment answers `503 STRONG_AUTHENTICATION_UNAVAILABLE`: a deployment cannot silently run
   without strong authentication. A real Smart-ID (or Mobile-ID) adapter would implement the same interface; the
   provider's demo environment is the natural next step.
6. **One page for the payer.** Form, review and confirmation are one screen: the form turns into a review of what will be
   paid and the name check, then into the code with a countdown, and the app moves to a receipt page by itself when the
   payment is executed (`/payments/:id/receipt`, loaded by id, so it survives a refresh). Polling and navigation live in
   NgRx effects, and leaving the page stops the polling.
7. **Preparing a payment is a name check, so it is rate limited like one** (20 per user and minute); confirming is
   limited like a payment (60 per minute).

## Consequences

- The payment is executed when somebody reads it after the approval, not at the moment of approval. If the payer
  approves and closes the browser, nothing is executed and the payment expires. A real provider would call the bank back
  instead, which the same service could handle; polling is the simple, stateless choice here.
- Every payment now makes at least four requests (prepare, confirm, one or more polls) and writes a `payments` row.
  Payments that are never finished stay in the table; a real system would archive or purge them.
- The mock approves unconditionally, so the demo shows the flow, not the security of SCA. Strong authentication of
  *signing in* is out of scope (see SECURITY.md); a login adapter could reuse the same port.
- The audit log records `PAYMENT_CREATED`, `PAYMENT_AUTHENTICATION_STARTED`,
  `PAYMENT_AUTHENTICATION_REJECTED`, `PAYMENT_CANCELLED` and, as before, `PAYMENT_EXECUTED` and
  `PAYMENT_REJECTED`.

## Alternatives considered

- **Keep one endpoint and add an `authenticated: true` flag.** Rejected: the client would assert its own authentication.
- **Push the approval to the browser (WebSocket or SSE).** Rejected for now: polling every second is simple, works through
  nginx without extra configuration and is easy to test. It can be replaced without changing the API's state model.
- **Require re-entering the password.** Rejected: it is not strong authentication and does not model the Smart-ID flow.
