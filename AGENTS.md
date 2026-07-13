# Repository Guidelines

Codex contract for this order-processing assignment. Keep work scoped, readable, and traceable to the indexed docs.

## Identity

- Backend: Java 21, Spring Boot 4.1.0, Maven Wrapper, Spring MVC, JPA, Validation, Scheduling.
- Database: Supabase PostgreSQL over JDBC; Flyway alone owns schema migrations.
- Shape: package-by-feature modular monolith under `backend/ordersystem/`.
- Current phase: documentation and LLD only. Do not implement until the user approves the plan.

## First Reads and Source of Truth

Always read `RESUME.md`, then `README.md`, then `docs/INDEX.md`. Load other docs only when the index trigger matches. Product behavior lives in `docs/PRD.md`; implementation design in `docs/LLD.md`; HTTP behavior in `docs/API_CONTRACT.md`; schema in `docs/DATA_MODEL.md`.

If behavior, API, schema, architecture, configuration, tests, or delivery status changes, update its canonical document in the same change. Never present planned behavior as implemented.

## Engineering Gate

For non-trivial work:

- State assumptions, ambiguity, tradeoffs, and success checks before editing.
- Trace the real flow and fix the shared root cause, not one symptom.
- Stop at the first sufficient solution: reuse repository code, JDK/Spring/PostgreSQL features, then installed dependencies; otherwise add minimum code.
- Match local style; avoid speculative features, abstractions, dependencies, and unrelated cleanup.
- Leave the smallest runnable proof. Never reduce validation, security, data-loss handling, concurrency safety, accessibility, or required tests.

## Project Map

- Scaffold: `backend/ordersystem/`.
- Target package: `backend/ordersystem/src/main/java/com/rkk/orderprocessing/`.
- Tests mirror target packages under `backend/ordersystem/src/test/java/`.
- Migrations: `backend/ordersystem/src/main/resources/db/migration/`.
- Source-of-truth docs: `docs/`; current queue: `RESUME.md`.

The generated `com.example.ordersystem.ordersystem` package is current scaffold evidence, not the target design.

## Commands

From `backend/ordersystem/`, `./mvnw test` and `./mvnw verify` are verified for the generated context test only. `./mvnw spring-boot:run` becomes the development entry point after runtime configuration exists. Supabase local development uses `supabase start`/`supabase stop` only after Docker and `supabase/config.toml` exist. State exactly which scope each command verified.

## Hard Domain Rules

- Items are immutable `productId` plus positive `quantity`; pricing/catalog is out of scope.
- Store status as enum names and timestamps as UTC `Instant` values.
- Legal flow: `PENDING → PROCESSING → SHIPPED → DELIVERED`; only `PENDING → CANCELLED`.
- Cancellation is a state change, never deletion.
- Every UTC five-minute tick promotes all rows still `PENDING`.
- Manual transitions, cancellation, and the job use atomic conditional updates; row count determines success.
- Tests must cover the full transition matrix and scheduler/cancellation races using PostgreSQL, not H2 or sleeps.

## Supabase and Security

Use Spring JDBC/JPA only—no parallel PostgREST client. Use direct connection for persistent IPv6-capable deployments and migrations; use Supavisor session mode when IPv4 requires it. Keep credentials in environment variables, never logs or Git. Do not change schema in the Supabase dashboard.

## AI Evidence

Record material AI use, issues found, corrections, and human verification in `docs/AI_USAGE.md`. Never store secrets or raw sensitive prompts.
