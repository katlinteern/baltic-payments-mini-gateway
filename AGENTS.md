# AGENTS.md

Guidance for AI coding assistants; humans start with [README.md](README.md). A payment gateway: Spring Boot 3.5 / Java 25
backend (modular monolith, PostgreSQL, Liquibase) and Angular 21 frontend (Material, NgRx), run with Docker Compose. Money
correctness under concurrency, retries and wrong beneficiary names is the point. Read the code in this order:
`PaymentService` → `PaymentConfirmationService` → `PaymentExecutionService` → `TransferService.transfer`.

## Commands

| Task | Command |
|---|---|
| Backend build and all tests (needs Docker) | `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 25) SPRING_PROFILES_ACTIVE=local ./mvnw verify` |
| Backend format (`verify` fails on unformatted code) | `cd backend && ./mvnw spotless:apply` |
| Frontend lint / format | `cd frontend && npm run lint && npm run format:check` |
| Frontend unit tests | `cd frontend && npm run test:coverage` |
| E2E | `cd frontend && npm run e2e` (isolated Compose project) |
| Run everything | `docker compose up --build` |

JDK 25 and Node 22 (>= 20.19) are required; check `node -v` before running Angular commands.

## Architecture rules (enforced by tests)

- Backend: `com.baltic.gateway.<feature>.<layer>.<concept>`, features `identity`, `accounts`, `payments`, `transactions`,
  `audit`, `platform`, layers `api`, `application`, `domain`, `infrastructure`. `ArchitectureTest` forbids feature cycles,
  `platform` depending on features, `domain` depending on other layers, `application` depending on `api`, `api` depending on
  `infrastructure`, and features using another feature's `api` or `infrastructure`.
- Frontend: features are lazy-loaded and depend on each other only through the public API of `accounts`; imports use
  `@core`, `@shared` and `@features`. ESLint enforces it, and `shared/ui` must not import features or NgRx.

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

- **No comments in Java and TypeScript.** Names, small methods and tests carry the meaning; put rationale in the README.
  Functional directives (`eslint-disable`, `@ts-expect-error`) are the only exception.
- Java: constructor injection via Lombok `@RequiredArgsConstructor`, `@Getter` and `@NoArgsConstructor(access = PROTECTED)`
  on JPA entities, `@Slf4j`, records for DTOs and value objects. No `@Data`, `@Setter` or `@Builder` on entities; keep
  explicit constructors when they do more than assign fields.
- Frontend: standalone components, OnPush, one NgRx slice per feature.
- Tests: unit tests with Mockito, integration tests with Testcontainers PostgreSQL in
  `backend/src/test/java/.../integration`, frontend specs next to the component (`*.spec.ts`). Backend line coverage must
  stay above 80% (JaCoCo) and frontend coverage above the thresholds in `frontend/angular.json`.
- A behaviour change updates the tests and, if it changes the design, the README in the same change.
- Commit messages follow Conventional Commits (`feat(backend): ...`, `fix: ...`, `docs: ...`, `refactor: ...`).
- `local` profile secrets are demo-only; other profiles read `JWT_SECRET` and `DB_PASSWORD`. Never commit secrets.
