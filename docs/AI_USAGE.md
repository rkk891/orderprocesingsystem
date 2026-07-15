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

## 2026-07-14 — One-command local launcher

- Goal and scope: replace the multi-command local datasource setup with a safe
  repository-root command that boots the existing application stack.
- AI/tool assistance: parallel reviews compared a committed environment file,
  Make indirection, Docker Compose, and a thin Bash launcher. The launcher was
  selected as the smallest option that preserves the existing architecture.
- Files or decisions influenced: added `./dev`; updated README onboarding, the
  TRD configuration contract, and the volatile handoff.
- Issue, uncertainty, or incorrect suggestion: committing local credentials or
  hard-coding the configured PostgreSQL port would have made startup shorter but
  contradicted the security and Supabase connection contracts.
- Correction made and why: the launcher reads only `DB_URL` from `supabase
  status -o env`, parses it without `source` or `eval`, and passes credentials to
  the Maven child process without printing or persisting them.
- Automated verification: `bash -n dev`, help/status/error paths, a cold launch
  from outside the repository, warm reuse of pre-existing Supabase, readiness
  `200`, Newman 12/12, terminal interrupt and direct `TERM` cleanup, and
  `./mvnw clean verify` (97 tests) all passed.
- Human verification/review: independent reviews covered credential handling,
  signal cleanup, existing Supabase ownership, path spaces, and portability.
- Official sources consulted: none; the committed Supabase configuration and
  locally installed CLI behavior were authoritative for this repository change.
- Residual risk or follow-up: the launcher assumes the local CLI reports the
  documented PostgreSQL URL shape and does not install missing prerequisites.
- Sensitive-data check: passed; no datasource credential is printed, persisted,
  or added to Git.

## 2026-07-14 — Guided project story

- Goal and scope: create one standalone, movie-like visual guide that lets a
  first-time reader understand the implemented V1 behavior, code path, data
  model, concurrency, runtime, verification evidence, and public-deployment
  boundary without first learning the codebase vocabulary.
- AI/tool assistance (summary only): the main agent traced current source and
  canonical documents; three read-only reviewers independently audited runtime
  flows, factual coverage, beginner comprehension, information design,
  responsiveness, and accessibility. The in-app browser exercised the rendered
  page at desktop and narrow widths.
- Files or decisions influenced: added `order-system-atlas.html` and linked it
  from `README.md`. The atlas is explicitly derived; canonical documents and
  current source remain authoritative.
- Issue, uncertainty, or incorrect suggestion: the existing Graphify output was
  stale and still described the removed generated package. The first accurate
  atlas was also rejected by the user because five lenses, step grids, matrices,
  and permanent detail panels made the project harder—not easier—to understand.
- Correction made and why: replaced the reference-dashboard model with one
  anchored order story across 12 scenes. Each scene presents one plain-language
  idea, the same order/request token carries the audience through the flow, Play
  advances staged visual beats, and Show code swaps in exact details only on
  demand. Lifecycle is introduced before scheduling/races, and narrow-screen
  playback controls appear before the longer visual stage. The final sequence
  still covers all six capabilities, transaction boundaries, lifecycle,
  scheduling, concurrency, architecture, proof, and the public-deployment
  boundary.
- Automated verification: JavaScript parsing and fragment-contract checks passed;
  in-app-browser checks exercised story/code switching, timed playback, scene
  progression, the architecture reveal, and the final boundary. Desktop and
  320-pixel layouts had no horizontal overflow, and browser console checks had no
  warnings or errors. `./mvnw -q
  -Dtest=ArchitectureRulesTest test` passed; current reports still total 71 fast
  plus 26 PostgreSQL tests with zero failures/errors/skips; `git diff --check`
  passed.
- Human verification/review: source-backed adversarial reviews were reconciled;
  the user then rejected the initial information density, directly shaping the
  cinematic redesign. Final user visual acceptance remains the handoff gate.
- Official sources consulted: none; current repository source and canonical
  documentation were authoritative for this derived explainer.
- Residual risk or follow-up: update or regenerate the atlas whenever behavior,
  API, architecture, schema, tests, configuration, or deployment status changes.
- Sensitive-data check: passed; no credentials, connection strings, request
  payloads, or raw sensitive prompts were embedded.

## 2026-07-14 — Guided API theatre

- Goal and scope: add a separate beginner-first walkthrough of all five V1 HTTP
  endpoints so a reader can watch request validation, translation, business
  decisions, persistence, PostgreSQL effects, responses, and refusal paths one
  step at a time.
- AI/tool assistance (summary only): the main agent read the canonical API
  documents and current controller/service/repository/error code; three
  read-only reviewers independently audited exact endpoint behavior,
  traceability, beginner comprehension, accessibility, and mobile presentation.
  The in-app browser exercised the rendered walkthrough in a separate tab.
- Files or decisions influenced: added `order-api-theater.html` and linked it
  from `README.md`. The page is a derived explainer and sends no live request.
- Issue, uncertainty, or incorrect suggestion: the existing Graphify result was
  documentation-oriented and marked earlier project-wide visual paths as dead
  ends. The first rail also merged API/application and
  persistence/PostgreSQL boundaries, placed unknown PATCH status parsing too
  early, overstated database validation, and pushed mobile scene content below
  the visible viewport.
- Correction made and why: the final theatre uses explicit Caller, HTTP/API,
  Application, Persistence, PostgreSQL, and Response lanes; maps unknown status
  parsing to `OrderService`; distinguishes Java-only aggregate/Unicode rules
  from database defense in depth; accepts absent or zero-byte cancel bodies; and
  replaces the full mobile rail with a current-layer cue. One live summary
  announces step, layer, title, and caption.
- Automated verification: fragment-only, JavaScript-parse, no-network,
  button-type, single-live-region, endpoint-count, and size checks passed. The
  in-app browser traversed all five seven-step chapters, verified code/story and
  timed-playback controls, and reported no horizontal overflow at 320 pixels;
  the current Maven reports remain 71 fast plus 26 PostgreSQL tests with zero
  failures/errors/skips.
- Human verification/review: source-backed factual, flow, and UX findings were
  reconciled; final user visual acceptance remains the handoff gate.
- Official sources consulted: none; current repository source and canonical
  documentation were authoritative.
- Residual risk or follow-up: regenerate both derived explainers whenever the
  API, architecture, schema, tests, or deployment boundary changes.
- Sensitive-data check: passed; only synthetic IDs, product names, quantities,
  and timestamps appear in the explainer.

## 2026-07-15 — Package navigation and boundary hardening

- Goal and scope: simplify code navigation by separating HTTP requests from
  responses and application commands/results/errors, while preserving every
  documented HTTP, schema, lifecycle, scheduling, and concurrency behavior.
- AI/tool assistance (summary only): the main agent traced imports and canonical
  documents; focused reviewers audited package moves, model invariants, and
  documentation impact. Graphify was used only to cross-check the documented
  package-by-feature boundaries; current source and tests remained authoritative.
- Files or decisions influenced: reorganized API carriers into `request` and
  `response` packages; reorganized application carriers/errors into `command`,
  `result`, and `exception`; kept use-case services and mappers at their layer
  roots to avoid speculative abstractions.
- Issue, uncertainty, or incorrect suggestion: the flat packages mixed unrelated
  navigation concerns, `NewOrderRequest` retained its caller-owned mutable
  list, and direct aggregate construction did not repeat the documented 1–100
  item cardinality. Review also found that an exact JaCoCo application include
  would omit the new subpackages and that package tests could otherwise pass
  vacuously if a directory were empty.
- Correction made and why: moved types without changing their contracts,
  defensively snapshotted request items while preserving nulls for Jakarta
  Validation, added the aggregate cardinality guard before child attachment,
  made semantic-package checks require Java sources, and included application
  subpackages in the 90% coverage rule.
- Automated verification: the focused 71-test command
  `./mvnw -q -Dtest=ArchitectureRulesTest,NewOrderRequestTest,OrderEntityTest,OrderServiceTest,OrderControllerMockMvcTest test`
  passed. `./mvnw clean verify` then passed 77 fast/unit/MockMvc tests and 26
  PostgreSQL integration tests (103 total) with all JaCoCo gates met. The stale
  package-path scan returned no matches and `git diff --check` passed.
- Human verification/review: the user explicitly approved the refactor;
  independent read-only reviews checked the move surface, invariants, test
  non-vacuity, coverage scope, and canonical-document updates.
- Official sources consulted: none; repository source, canonical documents, and
  executable tests were authoritative.
- Residual risk or follow-up: the derived explainers retain valid class/layer
  names; only the atlas's verification counters changed from 71/97 to 77/103.
  Public deployment hardening remains outside the assessment boundary.
- Sensitive-data check: passed; no credentials, connection strings, or raw
  sensitive prompts were added.

## 2026-07-15 — Contract-tested Swagger UI integration

- Goal and scope: make the implemented V1 HTTP API easy to discover and try
  locally without changing endpoint behavior or expanding the public-deployment
  boundary.
- AI/tool assistance (summary only): the main agent traced controllers, error
  handling, configuration, and canonical API documents; an independent reviewer
  checked Spring Boot 4/Springdoc compatibility and compared generated output
  with the actual contract. Graphify supplied a secondary architecture view;
  repository source and tests remained authoritative.
- Files or decisions influenced: added Springdoc Swagger UI 3.0.3, a checked-in
  OpenAPI 3.1 specification, conditional local specification serving, production
  disablement, focused MockMvc contract/exposure tests, and canonical-document
  updates.
- Issue, uncertainty, or incorrect suggestion: default runtime inference
  described create as 200 instead of 201 with `Location`, flattened list query
  parameters incorrectly, gave cancellation a request body, and omitted exact
  problem responses.
- Correction made and why: disabled the inferred default document and configured
  Swagger UI to load a contract-tested static specification that mirrors
  `API_CONTRACT.md`; kept the UI and specification unavailable in `prod`.
- Automated verification: focused Swagger tests passed; Redocly validated the
  specification with one missing-license warning; `./mvnw clean verify` passed
  81 fast/unit/MockMvc tests and 26 PostgreSQL integration tests (107 total) with
  all JaCoCo gates met.
- Human verification/review: an independent reviewer challenged compatibility
  and contract accuracy. The main agent opened the live UI on an isolated local
  port, confirmed all five operations and schemas, and found no browser-console
  warnings or errors.
- Official sources consulted: Springdoc's Spring Boot 4 documentation and 3.0.3
  release notes.
- Residual risk or follow-up: the lint warning remains until the repository has
  an explicitly declared license; future API changes must update the prose
  contract, static specification, and contract tests together.
- Sensitive-data check: passed; no credentials, connection strings, or raw
  sensitive prompts were added.

## 2026-07-15 — Plain-language Swagger operation guidance

- Goal and scope: make all five Swagger operations explain their purpose, when
  to use them, inputs, successful result, and common failures without changing
  HTTP behavior.
- AI/tool assistance (summary only): the main agent traced the checked-in OpenAPI
  document to the controller and canonical API contract; an independent reviewer
  checked the proposed copy for accuracy and jargon. Graphify was used as a
  secondary contract-navigation aid.
- Files or decisions influenced: rewrote operation summaries and descriptions in
  `openapi/openapi.yaml`, added matching usage context to `API_CONTRACT.md`, and
  added focused assertions that every operation retains structured reader-facing
  guidance.
- Issue, uncertainty, or incorrect suggestion: the original descriptions led
  with sorting, duplicate-parameter, body-byte, and transition edge cases, so a
  reader had to infer why an endpoint existed and what a successful call returned.
- Correction made and why: each operation now uses the same short Markdown
  structure—`Use this when`, `What it does`, and `What to expect`—with purpose
  before protocol details.
- Automated verification: `./mvnw -q
  -Dtest=OpenApiDocumentationMockMvcTest,OpenApiProductionExposureMockMvcTest test`
  passed. Redocly confirmed the OpenAPI document is valid with the existing
  missing-license warning. The restarted local UI rendered all three guidance
  sections for all five operations with no browser warnings or errors.
- Human verification/review: the user identified the readability gap directly in
  Swagger UI; an independent review checked all five operations against the
  implemented contract.
- Official sources consulted: none; repository source, canonical documents, and
  executable tests were authoritative.
- Residual risk or follow-up: exact schema constraints remain visible because
  they support machine validation; their adjacent descriptions remain the
  plain-language explanation.
- Sensitive-data check: passed; no credentials, connection strings, or raw
  sensitive prompts were added.

## 2026-07-15 — Recruiter fixtures and executable Swagger examples

- Goal and scope: make the assessment demonstrable from a fresh local database
  so Swagger returns meaningful orders without manual setup.
- AI/tool assistance (summary only): the main agent traced the launcher, Flyway
  locations, live API, static OpenAPI contract, and Newman workflow; a parallel
  reviewer compared permanent seed data, API-driven setup, and an isolated demo
  profile. Graphify provided a secondary view that was verified against source.
- Files or decisions influenced: the default `./dev` assessment command activates
  a `demo` profile, which adds a Flyway callback containing five fixed lifecycle
  fixtures and disables scheduling; `./dev start` keeps scheduler behavior without
  loading or resetting fixtures. OpenAPI operations now include status-specific
  success and error examples.
- Issue, uncertainty, or incorrect suggestion: the live database contained eight
  Newman-created rows, which initially looked like seed data but would disappear
  on a fresh database. Schema-only OpenAPI definitions also left Swagger's
  example values unhelpful before execution.
- Correction made and why: added repeatable, profile-isolated fixtures instead of
  treating incidental smoke data as a demo contract or adding always-on Java
  bootstrap code. Restarting the demo restores only its fixed IDs, and the
  existing hardened profile remains unchanged.
- Automated verification: focused OpenAPI and Testcontainers demo-profile tests
  passed; `./mvnw clean verify` passed 81 fast tests and 27 PostgreSQL integration
  tests (108 total), with all coverage gates met.
- Human verification/review: the live API on isolated port 8081 returned all five
  fixed states and seeded detail; Swagger and the checked-in examples were
  reachable. Independent review found and prompted correction of shared examples
  that had shown the wrong success/error status for several operations.
- Official sources consulted: none; repository configuration, executable tests,
  and the live local service were the source of truth.
- Residual risk or follow-up: demo IDs are intentionally fixed and must never be
  presented as application-generated customer data outside this assessment.
- Sensitive-data check: passed; fixtures contain no credentials or personal data.

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
