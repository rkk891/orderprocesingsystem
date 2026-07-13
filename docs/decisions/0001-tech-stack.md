# ADR 0001: Java 21 and Spring Boot 4.1 Backend

- **Status:** Accepted and implemented
- **Date:** 2026-07-11
- **Owners:** Repository maintainers
- **Related:** [PRD](../PRD.md), [TRD](../TRD.md), [Architecture](../ARCHITECTURE.md), [Implementation Plan](../IMPLEMENTATION_PLAN.md)

## Context

The assignment permits Java, .NET, or another backend stack and explicitly
values design, correctness, and testing. The repository already contains
`backend/ordersystem/`, a Maven Wrapper scaffold targeting Java 21 with Spring
Boot 4.1.0. It has only the base starter and test starter; choosing the stack
does not mean any order feature exists.

The service needs synchronous JSON APIs, transactions, validation, scheduled
work, PostgreSQL, migrations, and understandable concurrency behavior. A small
modular monolith is sufficient.

## Options Considered

| Option | Strengths | Costs/risks |
| --- | --- | --- |
| Java + Spring Boot | Mature MVC, validation, transactions, JPA/JDBC, scheduling, and test ecosystem; matches scaffold and assessment preference | More framework conventions and ceremony; JPA bulk/update behavior needs explicit tests |
| .NET + ASP.NET Core | Strong typed APIs, EF Core, hosted services, and testing | Replaces the existing scaffold and adds no requirement-level advantage |
| Go + standard HTTP/database packages | Small binaries, direct control, simple concurrency primitives | More manual validation/transaction/error structure; fewer existing project assets to reuse |

For Java, version 25 is a newer LTS release from most vendors and Spring Boot
4.1 supports it. Java 21 is also LTS, is already the scaffold target, has broader
established tool/runtime availability, and supplies everything this design uses.
Moving to 25 would add change without product value.

## Decision

Use Java 21, Spring Boot 4.1.0, and the Maven Wrapper. Build one executable
modular monolith under target package `com.rkk.orderprocessing`, with the order
feature separated into API, application, domain, persistence, and job adapters.

Use Spring MVC, Jakarta Validation, Spring Data JPA plus explicit JDBC/JPQL
conditional mutations, PostgreSQL JDBC, Flyway, Spring scheduling, Actuator,
JUnit Jupiter, MockMvc, and Testcontainers PostgreSQL. Versions come from Spring
Boot dependency management unless a verified incompatibility is documented.

## Consequences

- Existing scaffold and wrapper are retained; the placeholder package is renamed
  once before feature work.
- Domain rules must not depend on MVC, scheduling, or Supabase APIs.
- Framework conveniences do not replace explicit transaction, validation, and
  race tests.
- Runtime OpenAPI generation is deferred until a Spring Boot 4.1/Jackson 3
  compatible version is verified; `docs/API_CONTRACT.md` owns the initial contract.
- Developers and CI must use Java 21 and verify the Maven Wrapper without
  lowering the target to match a machine-local runtime.
- Reconsider Java 25 only for a demonstrated runtime/support need. Reconsider
  another stack only if ownership or deployment constraints materially change.

## Verification Sources

- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [OpenJDK 21 release](https://openjdk.org/projects/jdk/21/)
- [OpenJDK 25 release](https://openjdk.org/projects/jdk/25/)
