# E-commerce Order Processing System

Java backend that creates multi-item orders, retrieves and lists them, advances
their status, cancels pending orders, and promotes pending orders every five
minutes.

> **Phase:** Assessment implementation and Phase 5 verification are complete.
> Public deployment still requires the security/role hardening documented in the
> TRD because authentication is intentionally outside V1.

## Chosen Stack

| Area | Decision |
| --- | --- |
| Runtime | Java 21 LTS |
| Framework | Spring Boot 4.1.0, Spring MVC |
| Build | Maven Wrapper |
| Persistence | Spring Data JPA with Supabase PostgreSQL over JDBC |
| Schema | Flyway is the only migration owner |
| Scheduling | Spring `@Scheduled` with a UTC five-minute cron |
| Testing | JUnit 5, MockMvc, Testcontainers PostgreSQL |

The implementation is a package-by-feature modular monolith. Microservices,
messaging, CQRS, payment, inventory, catalog pricing, authentication, and
shipping integrations are intentionally outside the assignment scope.

## Assessment Focus

The design demonstrates a small set of patterns with real responsibilities:

- an order aggregate with named creation methods;
- an enum state machine for legal transitions;
- an application service/facade and repository transaction boundary;
- immutable API commands/results with explicit mappers;
- constructor injection with a deterministic `Clock`;
- a thin scheduler adapter over an idempotent, set-based processor;
- expected-status conditional writes and real-PostgreSQL race tests.

Strategy hierarchies, GoF State classes, events, queues, saga/outbox,
microservices, distributed locks, and create-idempotency storage are deferred
until a requirement gives them work to do. Detailed rationale and revisit
triggers remain in [docs/LLD.md](docs/LLD.md) and
[ADR 0002](docs/decisions/0002-supabase-and-concurrency.md).

## Start Here

1. [RESUME.md](RESUME.md) — current phase, verified state, and next action.
2. [docs/INDEX.md](docs/INDEX.md) — canonical documentation map and reading triggers.
3. [docs/PRD.md](docs/PRD.md) — product requirements and acceptance criteria.
4. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — system boundaries and component flows.
5. [docs/LLD.md](docs/LLD.md) — packages, classes, state machine, and transactions.
6. [docs/API_CONTRACT.md](docs/API_CONTRACT.md) — HTTP contract.

## Repository Layout

```text
backend/ordersystem/   Spring Boot order-processing service
docs/                  Source-of-truth product and engineering documents
AGENTS.md              Agent execution contract
RESUME.md              Volatile project handoff and queue
```

The service pins Java 21 and Spring Boot 4.1.0 and is rooted at
`com.rkk.orderprocessing`. Flyway owns the PostgreSQL schema; Hibernate validates
it at startup.

## Verification

From `backend/ordersystem/`, the complete verification command is:

```bash
./mvnw clean verify
```

On Java 21.0.11 this passed 71 fast/unit/MockMvc tests and 26 Testcontainers
PostgreSQL integration tests. It proved Flyway on an empty PostgreSQL 17.6
database, JPA validation, aggregate rollback, database-backed readiness,
repository constraints/queries, pending-processor visibility, and the required
races. JaCoCo's 80% bundle line/branch and 90% domain/application branch gates
passed.

Local Supabase startup and Flyway also succeeded, Newman completed 12 requests
with 12 passing assertions, and the opt-in scheduler-handler smoke passed one
test while promoting exactly its one pending row. Never commit database
credentials; use environment variables and the connection guidance in
[docs/TRD.md](docs/TRD.md).

### Reproduce the local smoke

Start the repository-isolated Supabase database from the repository root:

```bash
supabase start --workdir .
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:54332/postgres'
export SPRING_DATASOURCE_USERNAME='postgres'
read -s SPRING_DATASOURCE_PASSWORD
export SPRING_DATASOURCE_PASSWORD
```

Enter the local database password printed by `supabase start`; do not save it in
shell history or repository files. In that shell, start the API from
`backend/ordersystem/` with automatic scheduling disabled so the HTTP smoke is
deterministic:

```bash
ORDERS_SCHEDULER_ENABLED=false ./mvnw spring-boot:run
```

In a second shell at the repository root, exercise the running API:

```bash
npx --yes newman@6.2.1 run postman/order-processing-smoke.postman_collection.json --reporters cli
```

After stopping the API, export the same datasource variables in
`backend/ordersystem/` and explicitly run one real scheduler-handler smoke:

```bash
./mvnw -Dtest=LocalSupabaseSchedulerSmoke test
```

Then stop the isolated local project from the repository root:

```bash
supabase stop --workdir .
```

The handler intentionally promotes every `PENDING` row, matching the production
contract, and cleans up only the row it creates. Use an otherwise idle local
project for this smoke.

## Documentation Contract

Each decision has one canonical document listed in the index. Update that owner
and its affected tests/contracts in the same change. Record material AI
assistance, mistakes, and corrections in [docs/AI_USAGE.md](docs/AI_USAGE.md).
