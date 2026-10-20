# ADR 0001: Modular monolith with package-by-feature

- Status: accepted
- Date: 2026-10-16

## Context

The gateway is small (accounts, payments, identity, audit) but models a domain where boundaries matter: payments must
never reach into persistence details of accounts, and cross-cutting concerns (security, error format, money) must not
depend on business features. The original layout grouped code by technical type (`controllers`, `services`,
`entities`, `repositories`), which hides those boundaries: every feature can import every other feature's internals.

## Decision

Keep **one deployable** and organise it as a modular monolith, package-by-feature:

```
com.baltic.gateway
├─ platform/   money, error, web (error mapping, correlation id), paging, openapi, ratelimit, security (JWT), time, validation, metrics
├─ identity/   login, users, login rate limiting
├─ accounts/   accounts, ledger entries, fund transfer between locked accounts
├─ payments/   payment orchestration, idempotency, payee name check, transaction search
└─ audit/      append-only audit trail
   each feature: api (controllers, DTOs) · application (services) · domain (entities, rules) · infrastructure (repositories)
```

Inside each layer, files are grouped by **concept**, with the same folders in every layer where the concept appears:

```
accounts/   api, application, domain, infrastructure  →  account/ · ledger/ · transfer/ (application only)
payments/   api, application, domain, infrastructure  →  payment/ · transaction/ · payee/
identity/   api, application, domain, infrastructure  →  auth/ · user/ · strongauth/ (application and infrastructure only)
```

A feature with a single concept (`audit`) stays flat: a folder is added only when a feature holds two or more
concepts. Folders never group by Java type (no `entities/` or `enums/`); the one exception is `api/<concept>/dto/`,
which keeps the request and response shapes apart from the controller.

The style is a **layered modular monolith**: vertical slices (features) that are layered inside. It is deliberately not
hexagonal (ports and adapters): `application` uses the Spring Data repositories directly and entities are JPA entities, so
there is no dependency inversion between application and infrastructure. Introducing ports pays off when one capability
has several interchangeable adapters (for example a SEPA rail, a Smart-ID provider and a mock); until then it would only
add interfaces and mappers.

Dependency rules are executable (`ArchitectureTest`, ArchUnit) and run in CI:

- features are free of cycles (`payments → accounts, audit`; `identity → audit`);
- `platform` depends on no feature;
- `domain` depends on neither `api`, `application` nor `infrastructure`; `application` never depends on `api`;
- controllers never touch repositories; other features use a feature only through its `application` and `domain`.

JPA entities live in `domain` and are not wrapped in a second persistence model. That is a conscious trade-off: it
keeps the code small at the price of coupling the domain to JPA annotations.

## Consequences

- Splitting a feature into a service later is mostly mechanical: its only entry points are `application` classes.
- The ledger sits in `accounts`, so only that module changes balances (through `TransferService`); `payments` asks it to `transfer`.
- Money movement stays in one database transaction, which is what makes the overdraft and idempotency guarantees simple.

## Alternatives considered

- **Microservices.** Rejected: payments and accounts must commit atomically, so splitting them would require sagas or
  distributed transactions for no benefit at this size. The first candidate for extraction is an asynchronous consumer
  (notifications, fraud screening) fed through an outbox table; nothing in the current structure prevents it.
- **RabbitMQ (or another broker) now.** Rejected: nothing consumes events yet, so a broker would be infrastructure
  without a user. When the first consumer appears, `payments` writes a `payment.completed` row to an outbox table in
  the payment's own transaction and a relay publishes it, which keeps the money movement atomic and the publish
  at-least-once.
- **Micro-frontends.** Rejected for the same reason: one small SPA does not justify Module Federation. A shared
  component library inside the Angular workspace is the intended next step.
- **Spring Modulith.** Compatible with this layout; ArchUnit was chosen because it needs no runtime dependency.
