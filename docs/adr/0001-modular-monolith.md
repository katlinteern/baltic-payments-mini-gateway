# ADR 0001: Modular monolith with package-by-feature

- Status: accepted
- Date: 2026-10-16

## Context

Payments must never reach into persistence details of accounts, and cross-cutting concerns (security, errors, money) must
not depend on business features. A layout grouped by technical type (`controllers`, `services`, `entities`) hides these
boundaries.

## Decision

One deployable, organised package-by-feature (`identity`, `accounts`, `payments`, `transactions`, `audit`, `platform`),
with the layers `api`, `application`, `domain` and `infrastructure` inside each feature (see
[architecture](../architecture.md)). Files are grouped by concept, never by Java type; a feature with a single concept
stays flat.

The style is layered, deliberately not hexagonal: `application` uses Spring Data repositories directly and JPA entities
live in `domain`. Ports pay off when a capability has several adapters, so the only port is `StrongAuthenticator`
(ADR 0004). The price is domain code coupled to JPA annotations.

Dependency rules are executable (`ArchitectureTest`, ArchUnit): no cycles between features, `platform` depends on no
feature, `domain` depends on no other layer, `application` never on `api`, `api` never on `infrastructure`, and other
features use a feature only through its `application` and `domain`.

## Consequences

- Only `accounts` changes balances (`TransferService`); `payments` asks it to transfer.
- Money movement stays in one database transaction, which keeps the overdraft and idempotency guarantees simple.
- Splitting a feature into a service later is mostly mechanical.

## Alternatives considered

- **Microservices:** payments and accounts must commit atomically; splitting needs sagas for no benefit at this size.
- **A message broker:** nothing consumes events yet. The first consumer would be fed from an outbox table written in the
  payment's transaction.
- **Micro-frontends:** one small SPA does not justify Module Federation.
- **Spring Modulith:** compatible; ArchUnit needs no runtime dependency.
