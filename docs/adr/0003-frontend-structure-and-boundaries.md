# ADR 0003: Frontend structure, lazy features and enforced boundaries

- Status: accepted
- Date: 2026-10-16

## Context

The Angular client had folders named `features/<name>/feature` and `features/<name>/state`, relative imports such as
`../../../shared/...`, all NgRx slices registered eagerly, and a dependency cycle between `accounts` and `payment`.
A sign-out had to call a `clear*` action of every feature, so every new feature required a change in the app shell.

## Decision

```
src/app
├─ core/        auth service, guard, interceptor, session reset      (may use shared)
├─ features/    login · accounts · payment · transactions
│  └─ <name>/   <name>.routes.ts · pages/ (components) · state/ (NgRx slice + API service)
└─ shared/      models, validators, utils, ui/ components             (depends on nothing in the app)
```

- **Lazy features.** Each feature exposes `<name>.routes.ts` and is loaded with `loadChildren`. The route registers its
  own NgRx slice and effects (`provideState`, `provideEffects`), so the initial bundle only contains the shell.
  Measured effect: initial JavaScript fell from about 856 kB to about 540 kB (raw).
- **Aliases.** `@core/*`, `@shared/*` and `@features/*` replace parent-relative imports.
- **One public API between features.** `accounts` is the only feature others may use, through
  `@features/accounts/state` (`loadAccounts`, `selectAccounts`, `provideAccountsState`). Its slice is registered
  at application start because the payment form needs it regardless of which route is opened first. The reverse
  dependency was removed: `PaymentsEffects` dispatches `loadAccounts` once a payment is executed (`intentUpdated` with
  status `COMPLETED`).
- **Session reset.** A meta-reducer clears the whole store when `sessionEnded` is dispatched (sign-out, or a 401 from the
  API). Lazily registered slices are covered automatically and no feature knows about the others.
- **Enforced in lint.** `no-restricted-imports` rules in `eslint.config.js` are the frontend counterpart of the backend
  `ArchitectureTest`: features may not import each other (except the accounts public API), `core` may not import
  features, `shared` may not import `core` or features, and features may not use `../../` paths.

## Consequences

- Adding a feature means a folder, a routes file and one `loadChildren` entry; the shell stays untouched.
- `shared/ui` has no dependency on the rest of the app, so it can be moved into an Angular workspace library
  (`ng generate library`, alias `@baltic/ui`) without changing its code.
- The first visit to a page now costs one small extra request for its chunk.

## Alternatives considered

- **Rename `features` to `libs`.** Rejected: `libs` is Nx vocabulary for reusable libraries, whereas features are
  application-specific.
- **Nx workspace.** Rejected: its module-boundary tooling is excellent but heavy for an application of this size.
  Plain ESLint rules give the same guarantee here.
- **Keep every slice eager.** Rejected: simpler, but it defeats lazy loading and keeps the clear-everything coupling.
