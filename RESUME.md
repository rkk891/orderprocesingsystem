# Project Handoff

**Updated:** 2026-07-13  
**Phase:** Corrected documentation baseline awaiting user approval  
**Implementation status:** Order-processing features not started

## Verified Repository State

- Git is initialized on `main`; `origin` is `git@github.com:rkk891/orderprocesingsystem.git`.
- The user added a Spring Initializr scaffold at `backend/ordersystem/`.
- `pom.xml` pins Java 21 and Spring Boot 4.1.0 with only the base and test starters.
- The active local runtime is Eclipse Temurin JDK 21.0.11, and the Maven Wrapper uses the same runtime.
- The generated package is `com.example.ordersystem.ordersystem`; target package `com.rkk.orderprocessing` is planned only.
- Supabase CLI 2.53.6 is installed. It reports 2.109.1 is available.
- Docker 29.4.0 is running. Local Supabase is not running because no project
  configuration/container exists yet.
- No order domain, API, persistence, migration, scheduler, or feature test exists.

## Locked Design Decisions

- Modular monolith with package-by-feature organization.
- Supabase provides PostgreSQL; Spring uses JDBC/JPA, not PostgREST or a Supabase client SDK.
- Flyway is the sole schema migration owner; dashboard DDL and Supabase migrations are not mixed with it.
- The assessment keeps one externalized datasource identity for startup Flyway
  and runtime access. A separate production migrator/runtime-role design is
  documented as a pre-public-deployment hardening step, not assessment scope.
- Items contain only `productId` and `quantity`; pricing/catalog concerns are out of scope.
- Status flow is `PENDING → PROCESSING → SHIPPED → DELIVERED`, plus `PENDING → CANCELLED`.
- Every UTC five-minute tick promotes all orders still `PENDING`; this is not a five-minute age threshold.
- Status changes use atomic conditional database updates so scheduler/cancellation races have one winner.
- Necessary patterns only: stateless Spring-managed singleton components, named
  aggregate creation methods, application service/repository/mapper boundaries,
  and the enum state machine. Strategy and Observer remain deferred until a real
  second policy or consumer exists.
- API code communicates through immutable application commands/results and never
  imports persistence; the transactional pending processor belongs to
  `order.application`, while `order.job` contains only the scheduler adapter.
- The singular Java feature package is `order`; plural `orders` is reserved for
  HTTP resources and database table names. `ClockConfiguration` is the canonical
  clock configuration class.
- JSON bodies are strict while undocumented query parameters are harmlessly
  ignored; error responses have one fully specified RFC 9457 shape,
  `productId` limits use Unicode code points, and the application `Clock` alone
  owns timestamps.

## Current Queue

1. User reviews and explicitly approves the corrected PRD, TRD, architecture,
   LLD, data model, API contract, test matrix, and implementation plan.
2. Only after that approval, begin Phase 1 of `docs/IMPLEMENTATION_PLAN.md`.

## Known Environment Issues

- GitHub SSH remote inspection is blocked until the GitHub host key is trusted.
- `supabase status` reports no `supabase_db_order_system` container;
  initialization remains an implementation-phase action.
- Supabase CLI should be upgraded before it becomes part of the verified workflow.

## Verification Performed

- Inspected the repository tree, Maven descriptor, generated application, test, and properties.
- Confirmed `java`, `javac`, and the Maven Wrapper use Eclipse Temurin 21.0.11.
- Ran `./mvnw test` and `./mvnw verify`; the one generated context-load test passed.
- Verified Docker 29.4.0; confirmed no local Supabase database container exists yet.
- Confirmed Java/Spring versions against current official Spring documentation.
- Confirmed Supabase direct/session connection guidance and PostgreSQL conditional-update semantics.
- Reviewed classic-pattern alternatives and reconciled the LLD/architecture
  dependency boundary, singleton thread-safety rules, and processor ownership.
- Compared distributed-commerce patterns with the assignment boundary and kept
  the order aggregate, explicit state machine, conditional writes, and
  idempotent worker while deferring saga/outbox and create idempotency until a
  real external-effect or retry requirement exists.
- Reconciled singular package/class names, timestamp authority, strict API/error
  semantics, per-requirement delivery/test traceability, and the simple
  assessment datasource boundary across the canonical documents.
- Re-ran `./mvnw test` on 2026-07-13 after the docs-only correction; the one
  generated context test still passes. This remains scaffold evidence only.
- No scaffold file or application code was changed during this documentation phase.
