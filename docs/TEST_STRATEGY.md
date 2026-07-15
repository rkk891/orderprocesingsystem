# Test Strategy

| Field | Value |
| --- | --- |
| Status | **Complete**; clean build and local running-service smoke are green |
| Runtime under test | Java 21, Spring Boot 4.1.0, PostgreSQL |
| Canonical scope | [PRD](PRD.md), [TRD](TRD.md), [API Contract](API_CONTRACT.md), [LLD](LLD.md), [Data Model](DATA_MODEL.md) |

## 1. Goals and Test Shape

Tests must prove each product rule at the lowest useful layer and repeat critical
transactions across HTTP and real PostgreSQL. Domain tests stay free of Spring;
service tests isolate orchestration; MockMvc tests own the JSON contract; database
and race tests use Testcontainers PostgreSQL. **H2 is prohibited** because its
constraints, enum handling, locking, and conditional-update behavior are not the
Supabase PostgreSQL contract.

Use JUnit Jupiter, AssertJ, Mockito, MockMvc, Testcontainers, and JaCoCo. Name fast
tests `*Test` and PostgreSQL tests `*IT`. Keep fixtures in test builders rather
than shared mutable database state.

## 2. Requirement-to-Test Matrix

| Requirement | Domain/service proof | HTTP/operational proof | PostgreSQL/concurrency proof | Phase |
| --- | --- | --- | --- | ---: |
| PRD-FR-001 create | Defensive request snapshot; named creation methods with 1/100-item boundary proof; exact duplicate and Unicode code-point validation; atomic rollback | Multi-item `201`; every invalid/server-owned/unknown member case is `400` | Constraints, cascade, uniqueness, and fresh-transaction aggregate rollback | 2–3 |
| PRD-FR-002 retrieve | Complete detached aggregate; existing versus missing UUID | Detail `200`; malformed UUID `400`; absent UUID `404` | One aggregate fetch; no N+1 or lazy-session dependency | 3 |
| PRD-FR-003 advance | Complete transition matrix; shared stale-versus-missing classification | Legal `200`; every illegal/stale case `409`; stable Problem Details | Expected-status mutation; two contenders yield one update and one conflict | 2–3 |
| PRD-FR-004 list | Exact status and page-bound orchestration | Defaults/max/metadata; repeated documented parameter is `400`; undocumented `sort` is ignored | Fixed sort, filtered/unfiltered indexes, stable-dataset traversal without gaps/duplicates | 2–3 |
| PRD-FR-005 cancel | Only `PENDING -> CANCELLED`; missing versus ineligible | Success `200`; missing `404`; non-pending or non-empty body `409`/`400` as contracted | Conditional cancel; cancel-versus-job has exactly one valid winner | 3–4 |
| PRD-FR-006 processor | Handler returns affected count and never revives terminal states | No public endpoint; scheduler annotation/delegation test | One bulk update; all visible pending, post-snapshot create waits for next run, rerun zero, overlapping jobs safe | 4 |
| PRD-FR-007 disclosure | Review each material change against the AI record template | No secrets or raw sensitive prompts in the record | N/A | Every material phase |
| PRD-NFR-001 correctness | Aggregate invariant and transaction rollback tests | Error paths leave no partial mutation | Fresh PostgreSQL proves aggregate atomicity | 2–3 |
| PRD-NFR-002 concurrency | Conditional-write service classification | Stable conflict responses | Real PostgreSQL transition, cancel/job, and job/job races | 3–4 |
| PRD-NFR-003 security | Boundary validation and parameterized repository methods | Unknown-input tests; secret/log review; TLS configuration review | Fresh PostgreSQL uses only externalized credentials; production role split is a documented non-assessment gate | 1, 3, 5 |
| PRD-NFR-004 operability | Stateless handler with measured outcome | Health/readiness, structured-log, HTTP and scheduler metric smoke | Database readiness failure is observable without leaking credentials | 1, 4–5 |
| PRD-NFR-005 efficiency | Bounded list and set-based processor rules | Reject size above 100 | Query-count assertions, index-plan evidence, one bulk mutation | 2–5 |
| PRD-NFR-006 portability | Wrapper/profile configuration tests | Clean-clone startup guidance | Flyway on empty Testcontainers PostgreSQL and local Supabase smoke | 1–2, 5 |
| PRD-NFR-007 testability | Pure-Java domain suite and fixed/mutable test clock | MockMvc contract suite | Testcontainers PostgreSQL; no H2 or sleeps | 1–4 |
| PRD-NFR-008 maintainability | `ArchitectureRulesTest`; focused class responsibilities | DTO/application/persistence boundary checks | Dependency and schema-drift review | Every implementation phase |

## 3. Implemented Test Surfaces

- `order/domain/OrderStatusTest.java`: the complete pure-Java transition matrix.
- `order/persistence/OrderEntityTest.java`: fast tests for named creation methods,
  zero/101-item rejection before parent attachment, the accepted 100-item
  boundary, initial state/timestamps, positions, and parent links without Spring.
- `order/api/request/NewOrderRequestTest.java`: null-preserving defensive
  collection snapshots and immutable request access without Spring MVC.
- `order/application/OrderServiceTest.java`: commands, item validation, query
  orchestration, detached results, and shared 404/409 classification with a
  mocked repository/clock.
- `order/application/OrderServiceIT.java`: real service-transaction rollback
  after a PostgreSQL child constraint failure, verified from a fresh transaction.
- `order/api/OrderControllerMockMvcTest.java`: `@WebMvcTest` request/response,
  mapping, strict Jackson behavior, query semantics, tracing, and global errors.
- `config/OpenApiDocumentationMockMvcTest.java`: parses the checked-in OpenAPI
  contract, proves exact paths/methods/statuses and custom body/query/error rules,
  and verifies Swagger UI loads that artifact while runtime inference stays off.
- `config/OpenApiProductionExposureMockMvcTest.java`: proves the production
  profile does not serve the conditional OpenAPI contract route.
- `order/persistence/OrderRepositoryIT.java`: mappings, constraints, ordering,
  conditional mutations, and Flyway on an empty PostgreSQL container.
- `order/application/OrderProcessorTest.java`: processor delegation, one
  clock instant, and affected count.
- `order/application/OrderProcessorIT.java`: pending-only bulk behavior,
  exact counts, rerun idempotence, terminal-state preservation, and backward-clock
  monotonicity against PostgreSQL.
- `order/application/OrderProcessorSnapshotIT.java`: deterministic
  PostgreSQL statement-snapshot proof using distinct transactions and
  `pg_blocking_pids`, without sleeps.
- `order/persistence/OrderConcurrencyIT.java`: cancel-versus-job, update-versus-update,
  and overlapping-job races using separate transactions and barriers—not sleeps.
- `order/job/OrderSchedulerTest.java`: one focused cron/UTC wiring test;
  it verifies scheduler-to-processor delegation, success/failure metrics, and
  never waits five real minutes.
- `OrderProcessingApplicationIT.java`: clean Spring context, Flyway migration,
  and JPA validation against Testcontainers PostgreSQL.
- `DemoDataIT.java`: activates `test,demo` against Testcontainers PostgreSQL and
  proves the opt-in Flyway callback inserts every lifecycle state and all six
  expected items without changing the normal test profile.
- `DatabaseReadinessIT.java`: readiness is `UP` with PostgreSQL and becomes a
  sanitized `503 DOWN` response when the database stops.

Package paths are rooted at
`backend/ordersystem/src/test/java/com/rkk/orderprocessing/`.

## 4. Deterministic Time and Concurrency

Production code receives `java.time.Clock`; tests use `Clock.fixed(...)` or a
small mutable test clock. IDs and timestamps are asserted as UTC `Instant`s.
Concurrent tests coordinate executor threads with latches/barriers, start real
transactions together, then assert committed database state and affected-row
counts. Deterministic barriers avoid timing loops while each assertion remains
independent of which valid contender wins.

Validation coverage includes PostgreSQL-incompatible U+0000 product IDs, known
but never-manual status targets on missing IDs, 405/406/415 framework failures,
and backward-clock mutations proving `updatedAt` remains monotonic.

`ArchitectureRulesTest` is a small JDK/JUnit source check run by normal
`./mvnw test`. A `Files.walk` scan requires the semantic request/response and
command/result/exception packages to contain sources, keeps those carrier
packages dependency-clean, rejects `order.api -> order.persistence`, any
`order.job` dependency except `order.application`, framework/persistence imports
from `order.domain`, and any remaining `com.example.ordersystem` source. A
focused reflection assertion also requires final instance fields on scanned
Spring components. No ArchUnit dependency is justified for these few boundaries.

## 5. Commands and Evidence

Run from `backend/ordersystem/`:

```bash
./mvnw clean verify
```

The clean command passed 81 architecture/domain/entity/application/scheduler/
MockMvc tests and 27 Testcontainers PostgreSQL 17.6 tests (108 total) on Java
21.0.11, with zero failures/errors/skips. It covered empty-schema Flyway, JPA
validation, demo fixture reset/isolation, repository behavior, processor
visibility/idempotence, and the three core race classes. Configured merge thresholds are at least
80% line and branch coverage overall and 90% branch coverage for domain and
application packages, including application subpackages; the scenario matrix
remains mandatory even when metrics pass; all gates were met. Local Supabase startup/Flyway and the Newman
running-service smoke passed 12 requests with 12 assertions, including database
readiness. The opt-in local
scheduler-handler smoke passed one test and reported `affectedCount=1`.
