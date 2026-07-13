# ADR 0002: Supabase PostgreSQL, Flyway Ownership, and Conditional Writes

- **Status:** Accepted and implemented
- **Date:** 2026-07-11
- **Owners:** Repository maintainers
- **Related:** [TRD](../TRD.md), [LLD](../LLD.md), [Data Model](../DATA_MODEL.md), [Test Strategy](../TEST_STRATEGY.md)

## Context

Supabase is available, while the Java service needs ordinary transactions,
migrations, and concurrency behavior that can be reproduced locally and in CI.
Cancellation and the five-minute processor both write from the precondition
`status = PENDING`; scheduler invocations may overlap across application
instances. The smallest safe design must prevent a cancelled row from being
revived without introducing distributed infrastructure.

## Database and Connection Decision

Use Supabase as managed/local **PostgreSQL**, accessed by Spring through the
PostgreSQL JDBC driver. Do not add the Supabase Data API/client: it would
duplicate the JDBC transaction boundary and reduce portability.

- A persistent JVM uses the managed direct endpoint when IPv6/connectivity
  permits. Use it for migrations as well.
- On an IPv4-only persistent runtime, use Supavisor **session mode**.
- Do not default to transaction mode; Supabase documents it for transient or
  serverless clients and it does not support prepared statements. Revisit only
  with deployment evidence and JDBC settings tested in that mode.
- Use HikariCP within the plan limits, TLS for managed connections, and
  environment credentials. The isolated assessment uses one datasource identity
  for startup Flyway and runtime access. A public or multi-replica deployment
  must split schema-owner and runtime identities as a deployment hardening step.

Flyway is the **only schema owner**. Versioned SQL in
`src/main/resources/db/migration/` creates and changes objects; Hibernate uses
`ddl-auto=validate`. Do not split ownership with Hibernate DDL, Supabase Dashboard
edits, or ad-hoc CLI migrations. An emergency manual change must be captured and
reconciled in a new migration before normal delivery resumes.

## E-commerce Pattern Fit

Established commerce platforms commonly grow into asynchronous workflows
because payment, inventory, fulfilment, and notification systems have separate
failure and retry boundaries. This assignment has none of those effects, so it
keeps the same domain safeguards at a smaller scale:

| Pattern | How larger commerce systems use it | Decision here |
| --- | --- | --- |
| Order aggregate | One consistency boundary owns order items and legal lifecycle changes | **Use:** parent and immutable items commit in one transaction. |
| Explicit state machine | Reject illegal, skipped, repeated, or terminal transitions | **Use:** one `OrderStatus` transition table. |
| Optimistic concurrency | Version/ETag or expected state prevents stale writers | **Use the narrower form:** expected-status compare-and-set; add a version only when independent mutable fields appear. |
| Idempotent worker | A retried/overlapping worker must not repeat an already-applied effect | **Use:** one set-based `PENDING -> PROCESSING` update; immediate retry affects zero. |
| Pessimistic locking / work claiming | `FOR UPDATE SKIP LOCKED` partitions expensive per-order work across workers | Defer until processing becomes per-row or long-running. |
| Saga/workflow + transactional outbox | Coordinate durable payment, inventory, fulfilment, and message side effects | Defer; require a new ADR before the first external side effect. |
| Create idempotency key | Cache/fingerprint the first mutation result so network retries cannot create duplicate orders | Defer until safe automatic create retries are a requirement. |

This is not a simplified imitation of a distributed workflow: it deliberately
stops at the single-database transaction boundary where every current invariant
can be enforced atomically.

## Concurrency Options

| Option | Result | Decision |
| --- | --- | --- |
| Read pending rows, then save entities | Race window, N+1 writes, stale rows can be acted on | Reject |
| One conditional bulk `UPDATE ... WHERE status = 'PENDING'` | PostgreSQL atomically updates only rows still eligible; repeat returns zero | **Choose** |
| Pessimistic row locks / `SKIP LOCKED` batches | Useful for expensive per-row work, but more transactions and failure states | Defer |
| ShedLock/distributed scheduler leader | Reduces duplicate triggers but does not solve cancel-versus-update correctness | Reject for v1 |
| External queue/workflow engine | Durable per-item effects and retries, but far beyond a status-only assignment | Defer |

## Write Contract

The processor performs one transactional conditional bulk update from
`PENDING` to `PROCESSING`, setting UTC `updated_at`.
Cancellation and manual advancement also mutate with order ID plus expected
status in the `WHERE` clause. An affected count of zero is resolved as not found
or conflict by a follow-up read; it is never treated as success.

PostgreSQL serializes conflicting row updates and rechecks the condition against
the current row. Therefore, when cancellation and processing race, exactly one
eligible transition commits and the losing predicate no longer matches. Two job
runs may execute, but each row changes at most once. This provides correctness
without leader election; logs and metrics may still show both invocations.

The bulk statement sees rows committed before its Read Committed statement
snapshot. An order committed after that statement begins is intentionally left
for the next tick; no catch-up loop extends the current transaction.

No optimistic-version column is required in v1: status is the only mutable
business field, and every write includes its exact expected status as the
compare-and-set predicate. Add versioning only if later requirements introduce
independent mutable fields or entity-save update paths.

## Consequences and Revisit Triggers

- Application code remains portable PostgreSQL/JDBC and does not require a
  Supabase SDK.
- Flyway migrations must be tested on empty Testcontainers PostgreSQL and local
  Supabase; H2 cannot verify this decision.
- Race tests use separate real transactions and assert the final state plus
  affected counts.
- Bulk mutations bypass the persistence context; the implementing repository
  must clear/refresh it before returning state.
- Add row locking/batching only if processing gains costly per-row work. Add an
  outbox/queue before emitting payments, inventory, notifications, or other
  external side effects. Consider ShedLock only for operational load reduction,
  never as the data-integrity mechanism.

## Verification Sources

- [Supabase database connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
- [Flyway schema history](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table)
- [PostgreSQL conditional `UPDATE`](https://www.postgresql.org/docs/current/sql-update.html)
- [PostgreSQL schema privileges](https://www.postgresql.org/docs/current/ddl-schemas.html#DDL-SCHEMAS-PRIV)
- [Spring Boot Flyway connection properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data-migration.spring.flyway.user)
- [Stripe idempotent request contract](https://docs.stripe.com/api/idempotent_requests)
