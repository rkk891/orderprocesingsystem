# E-commerce Order Processing System

Documentation-first design for a backend that creates multi-item orders, retrieves and lists them, advances their status, cancels pending orders, and promotes pending orders every five minutes.

> **Phase:** Architecture and contract design. A minimal Spring Initializr scaffold exists, but no order feature is implemented. Planned behavior is not runnable behavior.

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

The target is a package-by-feature modular monolith. Microservices, messaging, CQRS, payment, inventory, catalog pricing, authentication, and shipping integrations are intentionally outside the assignment scope.

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
5. [docs/LLD.md](docs/LLD.md) — planned packages, classes, state machine, and transactions.
6. [docs/API_CONTRACT.md](docs/API_CONTRACT.md) — HTTP contract.

## Repository Layout

```text
backend/ordersystem/   Spring Boot scaffold and future implementation
docs/                  Source-of-truth product and engineering documents
AGENTS.md              Agent execution contract
RESUME.md              Volatile project handoff and queue
```

The current scaffold pins Java 21 and Spring Boot 4.1.0. Its placeholder package `com.example.ordersystem.ordersystem` is planned to become `com.rkk.orderprocessing`; that rename has not been implemented.

## Scaffold Command

The generated scaffold's test command is:

```bash
cd backend/ordersystem
./mvnw test
```

Supabase and application commands remain planned until their configuration exists. Never commit database credentials; use environment variables and the connection guidance in [docs/TRD.md](docs/TRD.md).

This command is verified with Eclipse Temurin JDK 21.0.11: the generated
context-load test passes. This is scaffold verification only; no order feature
is implemented yet.

## Documentation Contract

Each decision has one canonical document listed in the index. Update that owner and its affected tests/contracts in the same future change. Record material AI assistance, mistakes, and corrections in [docs/AI_USAGE.md](docs/AI_USAGE.md).
