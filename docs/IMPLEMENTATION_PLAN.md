# Implementation Plan

| Field | Value |
| --- | --- |
| Status | **Complete**; Phases 0–5 verified on 2026-07-13 |
| Working root | `backend/ordersystem/` |
| Target package | `com.rkk.orderprocessing` |
| Sources of truth | [Documentation Index](INDEX.md), [PRD](PRD.md), [TRD](TRD.md), [Architecture](ARCHITECTURE.md), [LLD](LLD.md), [API Contract](API_CONTRACT.md), [Data Model](DATA_MODEL.md) |

## 1. Baseline, Assumptions, and Success Checks

The repository contains a Java 21/Spring Boot 4.1.0 modular monolith rooted at
`com.rkk.orderprocessing`. Phases 1–4 delivered configuration, migration,
domain/persistence, the synchronous API, conditional races, the five-minute
scheduler, safe logging/tracing, and scheduler telemetry. The clean combined
build and local running-service evidence are green.

Assume one backend, one aggregate, PostgreSQL supplied locally or by Supabase,
no authentication/customer/catalog/pricing, and bounded offset pagination
(`page=0`, `size=20`, maximum 100). Success
means all PRD acceptance criteria map to one contract, implementation surface,
and automated test; Flyway rebuilds a fresh database; races preserve valid
state; `./mvnw verify` and a local Supabase smoke test pass.

## 2. Delivery Phases

### Phase 0 — Approve the documentation baseline (complete)

Review `README.md`, `docs/INDEX.md`, PRD, TRD, architecture, LLD, API contract,
data model, test strategy, ADRs, and AI record as one consistent design. Resolve
all broken links, duplicated ownership, open decisions, and requirement-matrix
gaps before code.

**Check:** each PRD ID resolves to API, class/flow, database rule, test, and phase.
**Risk:** contradictory documents become competing specifications; fix the
canonical owner rather than adding an exception elsewhere.

**Evidence:** the user explicitly approved implementation on 2026-07-13. The
baseline was captured in commit `a00f286` after the generated scaffold test
passed on Java 21.

### Phase 1 — Normalize and complete the scaffold (complete)

- Rename sources/tests once to `com.rkk.orderprocessing` and
  `OrderProcessingApplication`.
- Extend `pom.xml` with Spring MVC, validation, Data JPA, PostgreSQL JDBC,
  Flyway PostgreSQL, Actuator, Testcontainers, Failsafe, and JaCoCo using Spring
  dependency management; add Boot test-slice starters only if a slice is used.
- Add externalized base configuration plus credential-free `test` and `prod` profiles;
  set `ddl-auto=validate`, disable Open EntityManager in View, and commit no URLs
  or credentials.
- Add `config/ClockConfiguration.java` for `Clock` and
  `config/SchedulingConfiguration.java` for UTC scheduling.
- Add one small JDK/JUnit `ArchitectureRulesTest` for the package/import
  boundaries defined in the test strategy, including a focused reflection check
  that Spring component instance fields remain final.

Phase 1 and the Phase 2 migration/test harness are delivered as one foundation
batch so JPA validation is never committed without the schema it validates.

**Check:** Java 21 is active; wrapper compile and container-backed context test
pass; package scan finds no `com.example`; startup without secrets fails clearly,
not insecurely.
**Evidence:** architecture/package tests pass under Java 21; the container-backed
application context starts after Flyway and JPA validation.

### Phase 2 — Establish schema and domain (complete)

- Add `src/main/resources/db/migration/V1__create_orders.sql` with `orders` and
  `order_items`, FK/cascade rules, status and quantity checks, unique
  `(order_id, product_id)`, deterministic-list/status indexes, and UTC timestamps.
- Add `order/domain/OrderStatus.java` with the framework-independent transition rule.
- Add `order/persistence/OrderEntity.java`, `OrderItemEntity.java`, and
  `OrderRepository.java`; use named creation methods to establish the pending
  aggregate and keep item validation in the application boundary and database
  constraints rather than adding a factory hierarchy or duplicate models.

**Check:** Flyway migrates empty Testcontainers PostgreSQL; Hibernate validates;
domain and repository tests cover every invariant. **Risks:** JPA cascade/fetch
behavior can cause partial writes or N+1 queries; PostgreSQL constraints must
agree exactly with Java validation.

**Evidence:** domain/entity tests and 16 repository integration tests pass against
PostgreSQL 17.6; V1 migrates an empty schema before Hibernate validation.

### Phase 3 — Deliver HTTP vertical slices (complete)

1. **Create + retrieve:** add `order/application/OrderService.java`, command and
   result records, `order/api/OrderController.java`, request/response DTOs, and
   `OrderApiMapper`. Persist aggregate creation in one transaction; API code must
   not import persistence types.
2. **List:** add optional exact `OrderStatus`, validated page/size, response
   metadata, and fixed `createdAt DESC, id DESC` ordering. Prove every order is
   traversable across stable pages without duplicates or omissions.
3. **Advance + cancel:** add expected-status conditional repository operations,
   domain exception types, and `shared/api/ApiExceptionHandler.java` returning
   stable problem details.

**Check per slice:** domain/service tests, MockMvc contract tests, PostgreSQL IT,
then a curl smoke matching [API Contract](API_CONTRACT.md). **Risks:** accepting
server-owned fields, exposing entities, lazy-load failures, or classifying an
affected-row count of zero incorrectly as 404 instead of 409.

**Evidence:** 35 service tests and 18 MockMvc tests pass, covering create/detail/
list/advance/cancel, strict JSON, documented query behavior, stable Problem
Details, and sanitized failures. Newman then verified the running endpoints.

### Phase 4 — Add the five-minute processor (complete)

- Add repository bulk mutation `PENDING -> PROCESSING` with one conditional SQL
  statement that also updates UTC time and returns affected count.
- Add `order/application/PendingOrderProcessor.java` as the directly testable
  transactional use case and `order/job/PendingOrderScheduler.java` as the thin UTC cron
  adapter (`0 */5 * * * *`).
- Record post-commit scheduler metrics/logs for outcome, affected rows, and
  duration without request/item/customer data or raw throwable details.

**Check:** all-pending bulk run, immediate rerun zero, cancelled rows untouched,
cancel-versus-run and run-versus-run PostgreSQL races, and one cron wiring test.
**Risks:** select-then-save revives rows; per-row work creates N+1 transactions;
adding ShedLock masks rather than fixes the write race. Follow
[ADR 0002](decisions/0002-supabase-and-concurrency.md).

**Evidence:** processor/scheduler tests, processor snapshot visibility, and three
PostgreSQL concurrency tests pass; telemetry records success only after the
transactional processor proxy returns, and failure paths do not record affected
rows.

### Phase 5 — Harden, verify, and hand off (complete)

- Complete validation/error consistency, unknown-field rejection, query-count
  checks, TLS/least-privilege configuration guidance, health/readiness, and
  scheduler metrics. Strict Jackson handling, safe logging, health configuration,
  and scheduler metrics are implemented.
- Run the full [Test Strategy](TEST_STRATEGY.md), start against local Supabase,
  exercise every endpoint and one handler run, then update README, this plan,
  `RESUME.md`, and [AI Usage](AI_USAGE.md) with exact evidence.
- Review for secrets, generated files, dependency vulnerabilities, concurrency
  assumptions, docs drift, and only then prepare focused commits/PR.

**Smoke evidence:** local Supabase startup and Flyway passed; Newman passed 12
requests with 12 assertions, including readiness; the opt-in scheduler-handler smoke passed one test
and reported `affectedCount=1`.

**Final evidence:** `./mvnw clean verify` passed 71 fast/unit/MockMvc and 26
PostgreSQL integration tests (97 total), including all JaCoCo gates. Local
Supabase/Flyway and Newman passed 12 requests with 12 assertions, and the local
scheduler-handler smoke passed. Every PRD row has evidence.

**Deployment boundary:** local tooling differs from managed Supabase, and public
deployment still requires the authentication/TLS/role/operating controls kept
outside this assessment.

## 3. Change Discipline and Stop Conditions

Implement one vertical slice at a time; avoid generic ports, events, distributed
locks, alternate pagination schemes, auth, pricing, or idempotency keys until requirements demand
them. Stop and update the canonical docs if an API field, transition, transaction
boundary, schema rule, connection mode, or concurrency strategy must change.
Targeted commands did **not** replace the clean combined Phase 5 gate.

Each batch follows the same gate: implement, run targeted tests, ask a reviewer
subagent to challenge assumptions and check hallucinated APIs, standards,
security/concurrency, and production readiness, correct valid findings, rerun
tests, and only then commit.

## 4. Requirement-to-Delivery Traceability

Detailed behavioral proof is owned by the [Test Strategy](TEST_STRATEGY.md).
This table makes the Phase 0 gate auditable without duplicating test cases.

| Requirement | Contract/design owner | Delivery phase |
| --- | --- | --- |
| PRD-FR-001 create | API §4; LLD create flow; data-model aggregate constraints | 2–3 |
| PRD-FR-002 retrieve | API §5; LLD detail flow | 3 |
| PRD-FR-003 advance | API §7; LLD lifecycle/conditional mutation | 2–3 |
| PRD-FR-004 list | API §6; LLD list flow; data-model indexes | 2–3 |
| PRD-FR-005 cancel | API §8; LLD cancellation/concurrency | 3–4 |
| PRD-FR-006 processor | LLD pending processor; ADR 0002 | 4 |
| PRD-FR-007 AI disclosure | AI Usage | Every material phase |
| PRD-NFR-001 correctness | LLD transaction boundaries; test matrix | 2–3 |
| PRD-NFR-002 concurrency | ADR 0002; test matrix | 3–4 |
| PRD-NFR-003 security | TRD configuration/security; API validation; production role split explicitly deferred | 1, 3, 5 |
| PRD-NFR-004 operability | TRD observability; processor flow | 1, 4–5 |
| PRD-NFR-005 efficiency | Data-model indexes; LLD query/bulk flows | 2–5 |
| PRD-NFR-006 portability | TRD runtime/migrations | 1–2, 5 |
| PRD-NFR-007 testability | LLD boundaries; test strategy | 1–4 |
| PRD-NFR-008 maintainability | Architecture/LLD package and pattern rules | Every implementation phase |
