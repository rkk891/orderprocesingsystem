# Test Strategy

| Field | Value |
| --- | --- |
| Status | **Planned**; only the generated context-load test exists |
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
| PRD-FR-001 create | Named creation methods; exact duplicate and Unicode code-point validation; atomic rollback | Multi-item `201`; every invalid/server-owned/unknown member case is `400` | Constraints, cascade, uniqueness; simultaneous creates remain independent | 2–3 |
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

## 3. Planned Test Surfaces

- `order/domain/OrderStatusTest.java`: the complete pure-Java transition matrix.
- `order/persistence/OrderEntityTest.java`: fast tests for named creation methods,
  initial state/timestamps, item positions, and parent links without Spring.
- `order/application/OrderServiceTest.java`: commands, item validation, query
  orchestration, detached results, and shared 404/409 classification with a
  mocked repository/clock.
- `order/api/OrderApiMapperTest.java`: request/command and result/response mapping;
  no mapper method accepts a persistence type.
- `order/api/OrderControllerTest.java`: `@WebMvcTest` request/response contract
  and global error mapping.
- `order/persistence/OrderRepositoryIT.java`: mappings, constraints, ordering,
  conditional mutations, and Flyway on an empty PostgreSQL container.
- `order/application/PendingOrderProcessorIT.java`: processor behavior and affected counts.
- `order/application/OrderConcurrencyIT.java`: cancel-versus-job, update-versus-update,
  and overlapping-job races using separate transactions and barriers—not sleeps.
- `order/job/PendingOrderSchedulerTest.java`: one focused cron/UTC wiring test;
  it verifies scheduler-to-processor delegation and never waits five real minutes.

Package paths are rooted at
`backend/ordersystem/src/test/java/com/rkk/orderprocessing/` after the planned
package rename.

## 4. Deterministic Time and Concurrency

Production code receives `java.time.Clock`; tests use `Clock.fixed(...)` or a
small mutable test clock. IDs and timestamps are asserted as UTC `Instant`s.
Concurrent tests coordinate executor threads with latches/barriers, start real
transactions together, then assert committed database state and affected-row
counts. Repeat race scenarios enough to detect non-determinism while keeping the
assertion independent of which valid contender wins.

`ArchitectureRulesTest` is a small JDK/JUnit source check run by normal
`./mvnw test`. A `Files.walk` scan rejects
`order.api -> order.persistence`, any `order.job` dependency except
`order.application`, framework/persistence imports from `order.domain`, and any
remaining `com.example.ordersystem` source. Stateless singleton design stays a
focused code-review and unit-test rule rather than requiring reflection
infrastructure. No ArchUnit dependency is justified for these few boundaries.

## 5. Planned Commands and Gates

Run from `backend/ordersystem/` after the implementation phases add the planned
plugins and dependencies:

```bash
./mvnw test
./mvnw -Dtest=OrderServiceTest test
./mvnw -Dit.test=OrderConcurrencyIT verify
./mvnw verify
```

`test` runs fast `*Test` suites. `verify` must run unit, MockMvc, `*IT`, Flyway,
and JaCoCo gates with Docker available. The planned merge thresholds are at least
80% line and branch coverage overall and 90% branch coverage for domain and
application packages; the scenario matrix remains mandatory even when metrics
pass. A local Supabase smoke test follows the green Testcontainers build.

The generated context-load test passes through both `./mvnw test` and
`./mvnw verify` on Eclipse Temurin 21.0.11. This proves only the scaffold and
Java toolchain. Docker 29.4.0 is available, but no PostgreSQL/Testcontainers
test or order feature exists yet, so no such result is claimed. See the
[Implementation Plan](IMPLEMENTATION_PLAN.md) for the remaining gates.
