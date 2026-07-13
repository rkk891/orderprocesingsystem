# AI Assistance Record

This file records material AI assistance required by PRD-FR-007. It stores
summaries and verification evidence—not raw prompts, credentials, connection
strings, personal data, or proprietary inputs. Canonical project context begins
at the [Documentation Index](INDEX.md); product and technical claims are owned by
the [PRD](PRD.md), [TRD](TRD.md), [LLD](LLD.md), and
[architecture decisions](decisions/).

## 2026-07-11 — Project discovery and docs-first design

### Goal and assistance used

Codex/ChatGPT coordinated focused document work to compare platforms, define the
product boundary, design the modular-monolith flows, specify API/data/test
contracts, and order implementation phases. Subagents separated PRD/TRD,
architecture/LLD, and governance reviews; the main agent owns consistency and
final verification. No application code was requested or produced in this
design phase.

NotebookLM was used for comparative technology research. Official vendor
documentation was then used as the authoritative source for version support,
scheduling, PostgreSQL connections, migrations, and tests. Graphify queried the
existing repository graph as a baseline check.

### Evidence, issues, and corrections

| Observation or issue | Correction and human-verifiable result |
| --- | --- |
| The Graphify baseline returned 20 nodes sourced only from `AGENTS.md`; it did not represent the newly generated Spring scaffold. | Treated the graph as stale, not as repository truth. Direct `rg --files` and `pom.xml` inspection established the actual baseline: Boot 4.1.0, Java 21 target, Maven Wrapper, starter + test only, placeholder package, no order behavior. A future graph rebuild may follow docs approval. |
| NotebookLM CLI polling hit a DNS/network resolution error even though the server-side research task later reported completion. | Did not infer findings from the polling failure. Used the completed research only as a discovery aid and independently checked decisions against official sources below. |
| Installed Supabase CLI is `2.53.6`; it reported `2.109.1` available. | Did not generate or trust version-sensitive local configuration. Record the version, review release notes, then upgrade/pin and re-run local initialization during implementation. Flyway remains the schema owner. |
| Docker was initially unavailable; the user later started it. | Verified Docker 29.4.0. `supabase status` still reports no local project database container, and no PostgreSQL/Testcontainers feature test exists, so no database result is claimed. H2 is not an acceptable substitute. |
| The first runtime check reported Oracle JDK 19 while `pom.xml` targets Java 21. During this phase, user-owned environment state selected an existing Temurin 21 installation; delegated docs agents confirmed they did not make that change. | Preserved the user-owned selection after audit. Independently verified Java 21.0.11, `./mvnw test`, and `./mvnw verify`; both commands pass the single generated context test. No order-feature or PostgreSQL result is implied. |
| AI suggestions could add pricing, auth, events, or distributed locking typical of production systems. | Removed speculative scope. v1 uses product ID + quantity items, no customer/auth/pricing, bounded offset pagination with a fixed order, one atomic PostgreSQL update, and documented revisit triggers. |
| A classic-pattern request could produce unused Factory, Strategy, State, and Observer hierarchies. Parallel LLD reviews also found that the original response mapper implied an API-to-JPA leak and placed transactional processing in the scheduler adapter package. | Kept only patterns with present responsibilities: stateless Spring-managed singletons, named entity creation methods, application service/repository/mapper boundaries, an enum state machine, and compare-and-set writes. Added persistence-free application commands/results, moved the processor design to `order.application`, and deferred Strategy/Observer behind explicit triggers. No application code was written. |

### Official sources checked

- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html): Java 17–26 compatibility and Maven baseline.
- [OpenJDK 21](https://openjdk.org/projects/jdk/21/) and
  [OpenJDK 25](https://openjdk.org/projects/jdk/25/): release/LTS comparison.
- [Eclipse Temurin 21 releases](https://adoptium.net/temurin/releases/?version=21):
  current macOS ARM JDK distribution used for the local runtime.
- [Spring task execution and scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): UTC cron wiring and directly testable scheduler separation.
- [Supabase PostgreSQL connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres): direct, session-pooler, and transaction-pooler constraints.
- [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations) and
  [schema history](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table): one versioned migration history.
- [PostgreSQL `UPDATE`](https://www.postgresql.org/docs/current/sql-update.html): conditional set-based mutation semantics.
- [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/) and
  [JUnit 5 integration](https://java.testcontainers.org/test_framework_integration/junit_5/): real-engine integration-test plan.

### Human review still required

Confirm the API/domain assumptions, managed Supabase networking, and final
dependency compatibility. Before implementation, review all indexed
documents for contradictions. During implementation, a human must inspect diffs,
run the documented commands, exercise the API/job, and record exact outcomes.

## 2026-07-13 — Assessment-fit pattern and readiness pass

### Goal and assistance used

Codex reviewed the full documentation graph and used parallel reviewers to
identify inconsistent names, incomplete contracts, and unnecessary
production-scale patterns. The user clarified that this is an assessment: the
design should visibly demonstrate sound Java, API, transaction, concurrency,
and testing skills without becoming a production commerce platform. No
application code was requested or produced.

A NotebookLM research run was started for commerce-pattern discovery before the
user asked to pause that tool. It was stopped and its broad recommendations were
not treated as design authority. The repository requirements and minimum-change
assessment scope control the decisions below.

### Evidence, issues, and corrections

| Observation or issue | Correction and human-verifiable result |
| --- | --- |
| The LLD used singular `order`, while the delivery/test docs used plural `orders`; the clock configuration name also drifted. | Standardized Java packages on singular `order`, kept plural `orders` only for HTTP/table names, and standardized `ClockConfiguration`. |
| Generic production-commerce advice proposed version columns, row-claiming workers, queues, outbox/saga, create-idempotency storage, and distributed locks. | Kept only one order aggregate, an enum state machine, application service/repository/mapper boundaries, expected-status conditional writes, and the set-based idempotent processor. Added explicit revisit triggers instead of code. |
| A production migration/runtime-role split would add provisioning and test machinery unrelated to the assessment. | Kept one externalized datasource identity for isolated local/Testcontainers/evaluation use and documented role separation only as pre-public-deployment hardening. |
| API error/query behavior, timestamp authority, Unicode length semantics, requirement traceability, and scheduler snapshot behavior were not fully explicit. | Resolved each in its canonical contract and test/delivery matrix so implementation does not need to invent behavior. |

### Verification and remaining human gate

Documentation verification checked internal links, table structure, stale
names, and presence of every PRD ID in both the test and delivery matrices; all
checks passed. `./mvnw test` also passed the one generated context test after the
docs-only change. Graphify supplied the cross-document map used during review;
its final semantic-refresh worker did not return a valid graph fragment, so the
direct canonical-document checks above—not the graph—are the final source of
truth. At that review point implementation had not started; this historical
status is superseded by the implementation evidence recorded below.

## 2026-07-13 — Implementation approval and adversarial batch design

- Goal and scope: begin the complete implementation from the approved docs and
  keep each commit meaningful and green.
- AI/tool assistance: three read-only subagents independently mapped the
  API/domain contract, PostgreSQL/testing requirements, and production-readiness
  risks; the main agent owns implementation and verification.
- Issue found: the saved Graphify graph only described the generated context
  test, and the plan could have committed JPA validation before its migration.
- Correction: treated the graph as stale, combined database configuration with
  its migration/test harness, and established an implement/test/adversarial
  review/improve/commit loop for every batch.
- Additional corrections: kept status strings across the API/application
  boundary, rejected U+0000 product IDs, made mutation timestamps monotonic, and
  closed 405/406/415 error behavior before controller code.
- Automated verification at approval time: `./mvnw test` passed the generated
  baseline test on Java 21 before commit `a00f286`; current feature evidence is
  recorded in the next entry.
- Sensitive-data check: passed; graph artifacts, `.env` files, and OS metadata
  are ignored.

## 2026-07-13 — Phases 1–5 implementation and adversarial correction loop

- Goal and scope: implement the Java/PostgreSQL foundation, synchronous order
  API, conditional concurrency, and five-minute processor in reviewable batches.
- AI/tool assistance: the main coding agent integrated parallel implementation,
  contract, production-readiness, hallucination, and simplification reviews.
  Graphify was a navigation aid; direct source, Spring Boot metadata, tests, and
  PostgreSQL behavior remained authoritative.
- Query-contract drift: the first controller draft rejected undocumented query
  parameters although the canonical API says to ignore them. The controller and
  MockMvc test were corrected to ignore `sort` while still rejecting repeated
  documented parameters.
- Jackson 3 correction: the initial duplicate-key setting used the obsolete
  `spring.jackson.parser` namespace, and adversarial tests proved duplicate JSON
  members still returned success. Local Boot 4.1 metadata identified
  `spring.jackson.read.strict-duplicate-detection`; the property was corrected
  and duplicate-member tests now pass.
- Numeric coercion correction: Jackson could otherwise coerce `1.0` or `1e0`
  into an integer quantity. Float-to-integer and scalar coercion were disabled,
  with focused HTTP tests for both representations.
- Safe telemetry/logging correction: unexpected HTTP and scheduler failures no
  longer log raw throwable messages or stack traces from application code. The
  transactional processor no longer logs success before its proxy can commit;
  the scheduler records duration, rows, outcome, and failure only after the
  processor returns or throws.
- Atomicity-evidence correction: the original constraint test failed inside a
  test-managed transaction and could not prove committed absence. A real
  `OrderService` transaction now fails on the second child insert and a fresh
  transaction proves that neither the parent nor any child committed.
- Readiness correction: Boot's default readiness group did not include the
  datasource despite the documentation claim. The group now explicitly includes
  `db`; tests prove `200 UP` while PostgreSQL is available and sanitized
  `503 DOWN` after database loss, and Newman covers the ready endpoint locally.
- Simplification/reuse correction: global Jackson strictness replaced repeated
  per-record unknown-member handlers, trace-ID creation was centralized, unused
  exception state was removed, and API/application/persistence boundaries stayed
  separate where they protect the contract.
- Final automated verification: `./mvnw clean verify` passed 71 architecture/
  domain/entity/application/scheduler/MockMvc tests and 26 PostgreSQL integration
  tests (97 total). The PostgreSQL suite covered empty-schema Flyway, JPA
  validation, real service-transaction rollback, database-loss readiness,
  repository constraints/queries, processor idempotence, deterministic
  post-snapshot visibility, and the three core races. All JaCoCo gates passed.
- Local smoke: local Supabase startup and Flyway succeeded; the final Newman run
  passed 12 requests with 12 assertions, including database readiness. The opt-in
  scheduler-handler smoke passed one test and safely reported `affectedCount=1`.
- Final review: dependency scope, secrets/generated files, documentation truth,
  and simplification/reuse were checked. Public-deployment controls remain the
  documented non-assessment boundary, not an unreported verification gap.
- Sensitive-data check: no credentials or raw sensitive prompts were added.

## Reusable Entry Template

Copy this section for each material AI-assisted change. Summarize techniques;
do not paste raw prompts or sensitive values.

```markdown
## YYYY-MM-DD — Short task name

- Goal and scope:
- AI/tool assistance (summary only):
- Files or decisions influenced:
- Issue, uncertainty, or incorrect suggestion:
- Correction made and why:
- Automated verification (exact command + result):
- Human verification/review:
- Official sources consulted:
- Residual risk or follow-up:
- Sensitive-data check: passed / action required
```
