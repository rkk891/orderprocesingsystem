# E-commerce Order Processing System

Java backend that creates multi-item orders, retrieves and lists them, advances
their status, cancels pending orders, and promotes pending orders every five
minutes.

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

The service is organized as a package-by-feature modular monolith focused on the
core order lifecycle.

## Design

Core design choices include:

- an order aggregate with named creation methods;
- an enum state machine for legal transitions;
- an application service/facade and repository transaction boundary;
- immutable API commands/results with explicit mappers;
- constructor injection with a deterministic `Clock`;
- a thin scheduler adapter over an idempotent, set-based processor;
- expected-status conditional writes and real-PostgreSQL race tests.

Detailed design and concurrency rationale are documented in
[docs/LLD.md](docs/LLD.md) and
[ADR 0002](docs/decisions/0002-supabase-and-concurrency.md).

## Documentation

1. [docs/API_CONTRACT.md](docs/API_CONTRACT.md) — HTTP endpoints, requests, and responses.
2. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — system boundaries and component flows.
3. [docs/LLD.md](docs/LLD.md) — packages, classes, state machine, and transactions.
4. [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — PostgreSQL schema and constraints.
5. [docs/INDEX.md](docs/INDEX.md) — complete documentation map.

## Repository Layout

```text
backend/ordersystem/   Spring Boot order-processing service
docs/                  Source-of-truth product and engineering documents
postman/               Newman running-service smoke collection
supabase/               Local Supabase configuration
```

The service pins Java 21 and Spring Boot 4.1.0 and is rooted at
`com.rkk.orderprocessing`. Flyway owns the PostgreSQL schema; Hibernate validates
it at startup.

## Verification

From `backend/ordersystem/`, the complete verification command is:

```bash
./mvnw clean verify
```

The suite covers domain and API behavior, Flyway migrations, JPA validation,
transaction rollback, database-backed readiness, repository constraints,
processor visibility, and scheduler/cancellation races against PostgreSQL.
JaCoCo coverage gates run as part of `verify`.

### Local development and smoke test

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

Never commit database credentials. Use environment variables and the connection
guidance in [docs/TRD.md](docs/TRD.md).
