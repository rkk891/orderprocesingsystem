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
dev                    One-command local launcher
docs/                  Source-of-truth product and engineering documents
postman/               Newman running-service smoke collection
supabase/               Local Supabase configuration
```

The service pins Java 21 and Spring Boot 4.1.0 and is rooted at
`com.rkk.orderprocessing`. Flyway owns the PostgreSQL schema; Hibernate validates
it at startup.

## Quick Start

With Java 21, Docker (Docker Desktop on macOS), and the Supabase CLI installed,
run this from the repository root:

```bash
./dev
```

On macOS, the launcher selects an installed Java 21 runtime and can start Docker
Desktop. On other platforms, Java 21 and Docker must already be available. It
starts the repository-isolated Supabase project when needed, reads the
CLI-provided database credentials in memory, runs Flyway, and starts the API at
`http://127.0.0.1:8080`. It never writes or prints the database password.

Use `Ctrl+C` to stop the API. If the launcher started Supabase, it also stops
that project while preserving its data. `./dev stop` stops Supabase only. Useful
commands are:

```bash
./dev status
./dev stop
ORDERS_SCHEDULER_ENABLED=false ./dev
```

With the application running, exercise every V1 route from a second shell:

```bash
npx --yes newman@6.2.1 run postman/order-processing-smoke.postman_collection.json --reporters cli
```

## Verification

From `backend/ordersystem/`, the complete verification command is:

```bash
./mvnw clean verify
```

The suite covers domain and API behavior, Flyway migrations, JPA validation,
transaction rollback, database-backed readiness, repository constraints,
processor visibility, and scheduler/cancellation races against PostgreSQL.
JaCoCo coverage gates run as part of `verify`.

Never commit database credentials. Use environment variables and the connection
guidance in [docs/TRD.md](docs/TRD.md).
