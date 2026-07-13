# Technical Requirements Document

| Field | Value |
| --- | --- |
| System | E-commerce Order Processing API |
| Status | **Target design**; planned, not implemented |
| Runtime | Java 21, Spring Boot 4.1.0 |
| Architecture | Modular monolith |
| Database | Supabase PostgreSQL through JDBC |

## 1. Purpose and Authority

This document defines the technical baseline that satisfies the
[PRD](PRD.md). It records platform choices, runtime contracts, transaction and
deployment rules, and technical constraints. Detailed component ownership is in
[Architecture](ARCHITECTURE.md) and [LLD](LLD.md); exact interfaces and storage
are in [API Contract](API_CONTRACT.md) and [Data Model](DATA_MODEL.md); tests and
delivery order are in [Test Strategy](TEST_STRATEGY.md) and
[Implementation Plan](IMPLEMENTATION_PLAN.md). See [Documentation Index](INDEX.md)
and the [tech-stack](decisions/0001-tech-stack.md) and
[Supabase/concurrency](decisions/0002-supabase-and-concurrency.md) decisions for
navigation and trade-offs.

## 2. Current State Versus Target

### Current scaffold (observed 2026-07-11)

- `backend/ordersystem/` contains a Maven Wrapper project using Java 21 and the
  Spring Boot `4.1.0` parent.
- Its only dependencies are `spring-boot-starter` and
  `spring-boot-starter-test`; it has no web, persistence, validation, migration,
  PostgreSQL, scheduling, or observability implementation.
- The local machine selects Eclipse Temurin 21.0.11. Both `./mvnw test` and
  `./mvnw verify` pass for the generated context-load test only.
- The generated package `com.example.ordersystem.ordersystem` and the generated
  context-load test are placeholders. No product requirement is implemented.

### Target baseline

- Rename the base package once, before feature work, to
  `com.rkk.orderprocessing` and organize one singular `order` module by feature
  layer, matching the canonical LLD package map.
- Produce one executable Spring Boot service. Keep domain rules independent of
  MVC, JPA, scheduling, and Supabase details.
- Use the Maven Wrapper only; do not require a machine-global Maven version.

## 3. Technology Stack

| Concern | Target choice | Reason |
| --- | --- | --- |
| Language/runtime | Java 21 | LTS runtime with records, sealed types, and mature tooling. |
| Framework | Spring Boot 4.1.0 | Matches the scaffold and provides supported composition without custom infrastructure. |
| HTTP | Spring MVC via `spring-boot-starter-webmvc` | Synchronous REST fits a small transaction-oriented API; reactive complexity adds no value here. |
| Validation | Jakarta Validation via `spring-boot-starter-validation` | Declarative request-boundary constraints with consistent error handling. |
| Persistence | Spring Data JPA/Hibernate and PostgreSQL JDBC | Familiar repository/unit-of-work model; custom conditional SQL remains available for races and bulk work. |
| Schema | Flyway plus its PostgreSQL module | Versioned, repeatable schema ownership; no implicit schema mutation. |
| Scheduling | Spring Framework scheduling | Native cron support is sufficient; Quartz or an external queue is unnecessary for one idempotent set-based job. |
| Tests | JUnit, AssertJ/Mockito, MockMvc, and Testcontainers PostgreSQL | Fast domain tests plus real HTTP and PostgreSQL behavior; do not substitute H2 for integration tests. |
| Operations | Spring Boot Actuator and Micrometer | Standard health and metrics surface with minimal custom code. |
| Build | Maven Wrapper | Reproducible local and CI entry point. |

Dependency versions shall come from the Spring Boot dependency-management BOM
unless a documented compatibility issue requires a pinned override. The target
dependencies are planned additions; they are not present in the scaffold today.
The planned Maven set is `spring-boot-starter-webmvc`,
`spring-boot-starter-validation`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-flyway`, Flyway's PostgreSQL database module, the PostgreSQL
JDBC driver at runtime, and `spring-boot-starter-actuator`; retain
`spring-boot-starter-test`, add Boot 4's `spring-boot-starter-webmvc-test` and
`spring-boot-starter-data-jpa-test`, and add the Testcontainers PostgreSQL module
in test scope. Confirm the resolved dependency tree before implementation and
add no overlapping web or database client stack.

Do not add a runtime OpenAPI generator in the initial scaffold. The indexed
[API Contract](API_CONTRACT.md) is canonical until Spring Boot 4.1/Jackson 3
compatibility is verified for a specific library version; a machine-readable
OpenAPI artifact can then be generated without changing behavior.

## 4. Chosen Architecture and Alternatives

Use a **modular monolith** with one `order` vertical module and explicit HTTP,
application, domain, and persistence/scheduling adapters. A classic package-by-
layer layout was rejected because it scatters one feature; microservices were
rejected because the scope has one aggregate and no independent deployment need.

Java/Spring Boot was selected over an equally viable .NET implementation because
it is the preferred assessment stack and the repository already contains a Java
21/Spring Boot scaffold. Replacing that scaffold would add migration work without
improving any stated requirement.

Use PostgreSQL through JDBC/JPA. The Supabase Data API was rejected because the
backend needs transactions, conditional updates, Flyway, and ordinary relational
tests; adding a Supabase client would duplicate the persistence abstraction.

Use a Spring cron trigger and one conditional bulk database update. Polling rows
and saving them one by one was rejected due to N+1 work and race windows. A
distributed lock was not selected because `UPDATE ... WHERE status = 'PENDING'`
is itself atomic and idempotent for this status-only side effect. If the job later
emits external effects, record a new decision for an outbox or durable job model.

## 5. Configuration Contract

Configuration is externalized; secrets are never committed. Fixed product rules
(valid transitions, UTC zone, and five-minute cadence) are not environment knobs.

| Environment/property | Required | Contract |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Yes | JDBC PostgreSQL URL for local Supabase, managed direct connection, or managed session pooler. Managed URLs require TLS. |
| `SPRING_DATASOURCE_USERNAME` | Yes | Externalized assessment database identity; production uses the hardened runtime role described below. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Secret supplied by environment/secret manager; never logged. |
| `SPRING_PROFILES_ACTIVE` | No | Selects `local`, `test`, or `prod`; no profile may contain credentials. |
| `ORDERS_SCHEDULER_ENABLED` | No | Defaults to `true`; set `false` only for tests, migrations, or a deliberately non-worker process. |
| `SERVER_PORT` | No | Standard Spring override; defaults to `8080`. |

Target fixed settings are `spring.jpa.hibernate.ddl-auto=validate`,
`spring.jpa.open-in-view=false`, Flyway enabled, and the scheduler expression
`0 */5 * * * *` resolved in `UTC`. Pool size and timeouts must be set below the
selected Supabase plan's connection limits; do not copy an unverified universal
pool size into configuration.

For this assessment, local Supabase, Testcontainers, and a single evaluation
instance use the same externally supplied datasource identity for Flyway and
runtime access. This keeps the runnable proof small and is acceptable only in an
isolated assessment environment. Before any public or multi-replica deployment,
split a schema-owning Flyway release identity from a non-owning runtime identity;
Spring Boot already supports independent `spring.flyway` credentials, so that
hardening does not require an application-architecture change.

## 6. Supabase PostgreSQL Connection Modes

- **Local development:** connect by JDBC to the PostgreSQL endpoint and
  credentials reported by the local Supabase CLI. Do not hard-code its port.
- **Managed, preferred:** a long-lived JVM uses the direct connection when its
  network supports the endpoint. This is also the preferred migration route.
- **Managed, IPv4-only:** use Supavisor session mode for the persistent service.
- **Not the default:** transaction-pooler mode targets transient/serverless
  clients and does not support prepared statements. Do not use it unless a
  documented deployment constraint also supplies and tests the necessary JDBC
  settings.

Production connections require TLS with certificate and hostname verification.
Local and managed environments run the same Flyway migrations. Supabase is used
as managed/local PostgreSQL only; application correctness must not depend on its
Auth, REST/GraphQL Data API, Realtime, Storage, or Edge Functions.

## 7. Persistence and Transaction Semantics

- Flyway is the **sole schema owner**. Migrations use
  `V<version>__<description>.sql`; Hibernate validates mappings and must never
  create, update, or drop the schema.
- Creating an order and all its items is one transaction. Any validation or
  persistence failure rolls back the aggregate.
- Reads use read-only service transactions. HTTP controllers do not depend on
  lazy loading because Open EntityManager in View is disabled.
- Manual status advancement and cancellation are conditional mutations against
  both order ID and expected current status. An affected-row count of zero is
  resolved to not-found or conflict; stale state is never silently overwritten.
- The scheduler runs one set-based transaction that changes every committed
  `PENDING` row to `PROCESSING` and updates its UTC modification timestamp. A
  concurrent cancel or transition wins according to PostgreSQL row locking; the
  loser no longer matches its status precondition. No cancelled row is revived.
  Each statement uses `GREATEST(updated_at, :clockInstant)` so a backward clock
  cannot invalidate an otherwise legal mutation or an entire bulk tick.
- Database constraints enforce allowed status values, positive quantities,
  non-blank products, uniqueness of `(order_id, product_id)`, and foreign-key
  integrity. The application enforces at least one item before the atomic create;
  a cross-table trigger is not justified for this assignment. Boundary validation
  supplies clear client errors and database constraints remain the last line of
  defence.
- Listing uses zero-based offset pages (`20` by default, `100` maximum) ordered
  by `created_at DESC, id DESC`. Database indexes must support this ordering with
  and without the optional exact status predicate.
- Time is stored as timezone-aware PostgreSQL values and represented as Java
  `Instant`. The injected application `Clock` is the sole timestamp authority;
  migrations define no timestamp default or trigger. IDs are service-generated
  UUIDs.

## 8. Runtime and Deployment

The deployment unit is one executable JAR or container containing the API and
scheduler. The process is stateless apart from PostgreSQL. Local development
uses local Supabase; CI uses a disposable Testcontainers PostgreSQL instance;
deployment may use managed Supabase.

For local development, tests, and the single-instance assessment, Flyway runs
during startup before JPA validation. A future multi-replica production release
must run migrations once as a release step, then start application replicas
without schema-owner credentials against the validated schema.
Every replica may trigger the scheduled update because the conditional database
operation is safe; logs/metrics distinguish total invocations from affected
rows. Liveness checks process health; readiness requires database connectivity
and successful schema validation.

## 9. API and Error Requirements

Controllers expose versioned JSON endpoints defined only in
[API Contract](API_CONTRACT.md). DTOs are not JPA entities. Unknown request
fields and invalid enum/UUID/quantity values are rejected consistently. Errors
use one stable problem-details shape with a machine-readable code; malformed
input maps to 400, absent resources to 404, and illegal or stale state changes
to 409. Stack traces and database details never cross the HTTP boundary.

## 10. Observability and Security

- Emit structured logs for request ID, operation, order ID where available,
  outcome, duration, and scheduled affected-row count. Never log credentials,
  connection URLs, authorization headers, or complete request/response bodies.
- Expose health/readiness and metrics for HTTP latency/errors, database pool
  health, scheduler run success/failure/duration, and rows advanced. Keep
  privileged Actuator endpoints disabled or access-controlled.
- Validate before application logic, use parameterized persistence operations,
  enforce the documented request/list bounds, and apply privileges appropriate
  to the environment. The shared schema-owner identity is limited to the
  isolated assessment; public deployment requires the documented role split.
- Authentication and authorization are outside the assignment. Therefore an
  unauthenticated build is suitable only for local evaluation or behind an
  approved access-control boundary; it must not be exposed directly to the
  public Internet.

## 11. Verification Gates

The target is not considered implemented until the Maven Wrapper compile,
unit tests, MockMvc contract tests, PostgreSQL integration
tests, Flyway-on-empty-database test, concurrency tests, package-name scan, and
local Supabase smoke test all pass. Scheduler tests call the handler directly;
only a focused scheduling-wiring test checks the cron contract. See
[Test Strategy](TEST_STRATEGY.md) for the authoritative matrix.

## 12. Requirement Traceability

| Requirement | Primary technical mechanism | Detailed source |
| --- | --- | --- |
| PRD-FR-001 | Validated command, order aggregate, one JPA transaction | [LLD](LLD.md), [Data Model](DATA_MODEL.md), [API](API_CONTRACT.md) |
| PRD-FR-002 | Read-only query returning an explicit response projection | [LLD](LLD.md), [API](API_CONTRACT.md) |
| PRD-FR-003 | Domain transition policy plus expected-status conditional update | [LLD](LLD.md), [Data Model](DATA_MODEL.md) |
| PRD-FR-004 | Bounded repository page with optional exact enum filter and deterministic ordering | [API](API_CONTRACT.md), [LLD](LLD.md), [Data Model](DATA_MODEL.md) |
| PRD-FR-005 | `PENDING`-preconditioned cancellation mutation | [LLD](LLD.md), [Test Strategy](TEST_STRATEGY.md) |
| PRD-FR-006 | UTC Spring cron adapter plus atomic set-based PostgreSQL update | [Architecture](ARCHITECTURE.md), [LLD](LLD.md), [Test Strategy](TEST_STRATEGY.md) |
| PRD-FR-007 | Versioned `docs/AI_USAGE.md` evidence | [Documentation Index](INDEX.md) |
| PRD-NFR-001 | Aggregate transaction and rollback rules | [LLD](LLD.md), [Test Strategy](TEST_STRATEGY.md) |
| PRD-NFR-002 | Expected-status writes and PostgreSQL race model | [LLD](LLD.md), [ADR 0002](decisions/0002-supabase-and-concurrency.md) |
| PRD-NFR-003 | Strict validation, external secrets, TLS, and documented production hardening boundary | This document, [API](API_CONTRACT.md), [Test Strategy](TEST_STRATEGY.md) |
| PRD-NFR-004 | Health/readiness, structured logs, and scheduler metrics | This document, [Test Strategy](TEST_STRATEGY.md) |
| PRD-NFR-005 | Bounded pages, indexed reads, no N+1, set-based processor | [LLD](LLD.md), [Data Model](DATA_MODEL.md) |
| PRD-NFR-006 | Wrapper, Flyway, Testcontainers, local/managed PostgreSQL contract | This document, [Test Strategy](TEST_STRATEGY.md) |
| PRD-NFR-007 | Framework-free domain policy and deterministic real-PostgreSQL tests | [LLD](LLD.md), [Test Strategy](TEST_STRATEGY.md) |
| PRD-NFR-008 | Modular-monolith boundaries and minimum-pattern rule | [Architecture](ARCHITECTURE.md), [LLD](LLD.md), [Test Strategy](TEST_STRATEGY.md) |

## 13. Constraints and Deferred Decisions

The first release has no customer ownership, auth, catalog referential check,
prices/totals, cursor pagination, status history, create idempotency key,
external events, or availability/latency target. Before any public or
high-volume use, define authorization, retention, rate limits, recovery targets,
load targets, and whether offset pagination remains adequate, then record any
changes and Supabase plan/pool sizing in new approved decisions.

## 14. Primary References

- [Spring Boot 4.1 build systems and starters](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Spring Boot SQL and JPA guidance](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [Spring scheduling reference](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Supabase PostgreSQL connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/)
