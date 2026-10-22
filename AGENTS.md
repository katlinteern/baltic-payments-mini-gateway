# AGENTS.md

Guidance for AI coding assistants working in this repository. Humans: start with [README.md](README.md).

## What this is

A payment-gateway assignment: Spring Boot 3.5 / Java 25 backend (modular monolith, PostgreSQL, Liquibase) and an Angular 21
frontend (Material, NgRx), run with Docker Compose. Money correctness under concurrency, retries and wrong beneficiary
names is the point of the project. Deep dives: [docs/architecture.md](docs/architecture.md), [docs/adr](docs/adr),
[docs/operations.md](docs/operations.md).

## Commands

| Task | Command |
|---|---|
| Backend build and all tests | `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 25) SPRING_PROFILES_ACTIVE=local ./mvnw verify` (needs Docker) |
| Backend format (palantir-java-format via Spotless; `verify` fails on unformatted code) | `cd backend && ./mvnw spotless:apply` |
| Backend compile only | `cd backend && ./mvnw -q compile` |
| Frontend lint / format | `cd frontend && npm run lint && npm run format:check` |
| Frontend unit tests | `cd frontend && npm run test:coverage` |
| E2E | `cd frontend && npm run e2e` (isolated Compose project) |
| Run everything | `docker compose up --build` |

JDK 25 and Node 22 (>= 20.19) are required; check `node -v` before running Angular commands.

## Architecture rules (enforced by tests)

- Backend packages: `com.baltic.gateway.<feature>.<layer>.<concept>` with features `identity`, `accounts`, `payments`,
  `transactions`, `audit`, `platform` and layers `api`, `application`, `domain`, `infrastructure`. `ArchitectureTest` forbids cycles
  between features, `platform` depending on features, `domain` depending on other layers, `application` depending on
  `api`, `api` depending on `infrastructure`, and other features using a feature's `api` or `infrastructure`.
- Frontend: features are lazy-loaded and may depend on each other only through the public API of `accounts`; imports use
  `@core`, `@shared` and `@features`. ESLint boundary rules enforce it. `shared/ui` must not import features or NgRx.

## Invariants that must not break

- A payment executes in one transaction: ownership check without a lock, then account locks in sorted IBAN order, then
  debit and credit ledger entries. Never take locks in another order.
- Execution is idempotent through `(created_by, idempotency_key)`; the key for payments is `payment-{id}`.
- `ledger_entries`, `audit_log` and payment transactions are immutable; the `payments_app` role has no update or delete on
  them. Do not add code that updates or deletes them.
- Money is `BigDecimal` with two decimals and at most 12 integer digits. Never use `double` for amounts on the server.
- Existing Liquibase changesets are never edited after the first deployment; add a new file under `db/changelog/schema`.
  Demo data lives in `db/changelog/seed` with contexts `local,test`.
- Errors are RFC 7807 problems with a stable `ErrorCode`. New business failures get a new `ErrorCode`, not a new string.
- Every resource lookup is scoped to the authenticated user (ownership checks); never fetch by id alone.

## Conventions

- **No code comments.** Names, small methods and tests carry the meaning. Put rationale in an ADR or `docs/`, not in code.
  Functional directives (`eslint-disable`, `@ts-expect-error`) are the only exception.
- Java: 4 spaces, 120 columns, constructor injection via Lombok `@RequiredArgsConstructor`, Lombok `@Getter` /
  `@NoArgsConstructor(access = PROTECTED)` on JPA entities, `@Slf4j` for logging, records for DTOs and value objects.
  No `@Data`, `@Setter` or `@Builder` on entities; keep explicit constructors when they do more than assign fields.
  Frontend: Prettier
  (120 columns, single quotes), standalone components, OnPush, NgRx slices per feature.
- Tests: unit tests with Mockito, integration tests with Testcontainers PostgreSQL in `backend/src/test/java/.../integration`,
  frontend specs next to the component (`*.spec.ts`). Backend line coverage must stay above 80% (JaCoCo) and frontend
  coverage above the thresholds in `frontend/angular.json`.
- A behaviour change updates the tests and, if it changes the design, the matching doc or ADR in the same change.
- Commit messages follow Conventional Commits (`feat(backend): ...`, `fix: ...`, `docs: ...`, `refactor: ...`).
- Never commit secrets. `local` profile secrets are demo-only; other profiles read `JWT_SECRET` and `DB_PASSWORD`.
