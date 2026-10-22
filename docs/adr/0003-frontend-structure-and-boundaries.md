# ADR 0003: Frontend structure, lazy features and enforced boundaries

- Status: accepted
- Date: 2026-10-16

## Context

Eagerly registered NgRx slices, parent-relative imports and a dependency cycle between `accounts` and `payments` meant a
sign-out had to call a `clear*` action of every feature, and each new feature required a change in the app shell.

## Decision

Layout: `core/` (auth, guard, interceptor, session reset), `features/<name>/` (`<name>.routes.ts`, `pages/<page>/` with
`.ts .html .scss .spec.ts`, `state/` with the NgRx slice and API service) and `shared/` (models, validators, `ui/`). A
feature folder is named like its route, API and backend package.

- **Lazy features.** Each feature is loaded with `loadChildren` and registers its own slice and effects in its routes,
  which cut the initial JavaScript from about 856 kB to about 540 kB.
- **Aliases.** `@core/*`, `@shared/*` and `@features/*` replace parent-relative imports.
- **One public API between features.** `accounts` is the only feature others may use, through `@features/accounts/state`.
  Its slice is registered at application start because the payment form needs it. `PaymentsEffects` dispatches
  `loadAccounts` after a payment completes, so `accounts` does not depend on `payments`.
- **Session reset.** A meta-reducer clears the store on `sessionEnded` (sign-out or a 401), so lazy slices are covered and
  no feature knows about the others.
- **Enforced in lint.** `no-restricted-imports` in `eslint.config.js` mirrors the backend `ArchitectureTest`: features
  may not import each other (except the accounts public API), `core` not features, `shared` neither `core` nor features,
  and no `../` paths.

## Consequences

- A new feature is a folder, a routes file and one `loadChildren` entry.
- `shared/ui` depends on nothing in the app, so it can become a workspace library later.
- The first visit to a page costs one small extra request for its chunk.

## Alternatives considered

- **Nx workspace:** excellent boundary tooling, but heavy for this size; plain ESLint rules give the same guarantee.
- **Keeping every slice eager:** simpler, but defeats lazy loading and keeps the clear-everything coupling.
