# Product Requirements Document

| Field | Value |
| --- | --- |
| Product | E-commerce Order Processing API |
| Status | **Planned**; no product capability is implemented |
| Planning baseline | 2026-07-11 |
| Scope | Backend-only coding assignment |

## 1. Purpose

Build a small, correct backend that lets API clients create orders, inspect and
list them, advance their lifecycle, and cancel them while cancellation is still
allowed. A scheduled process must advance every order that is still `PENDING`
at each UTC five-minute boundary. The assessment prioritizes understandable
design, transactional correctness, tests, and a clear account of AI assistance.

## 2. Document Map and Authority

This document is the source of truth for product scope and acceptance. Technical
choices belong in [TRD](TRD.md); structure and behavior belong in
[Architecture](ARCHITECTURE.md) and [LLD](LLD.md). Exact HTTP and persistence
contracts belong in [API Contract](API_CONTRACT.md) and
[Data Model](DATA_MODEL.md). Verification and sequencing belong in
[Test Strategy](TEST_STRATEGY.md) and [Implementation Plan](IMPLEMENTATION_PLAN.md).
Use [Documentation Index](INDEX.md) for reading order and
[tech-stack](decisions/0001-tech-stack.md) and
[Supabase/concurrency](decisions/0002-supabase-and-concurrency.md) decisions for
trade-offs. If documents conflict, this PRD controls *what* the product must do;
the narrower technical document controls *how* it does it.

## 3. Users and Outcomes

- **Customer/API client:** creates an order, retrieves its details and status,
  lists orders, and cancels an eligible order.
- **Operations/API client:** advances an order through fulfilment states.
- **Scheduler:** advances all currently pending orders without reviving orders
  that were concurrently cancelled.
- **Evaluator/developer:** can reproduce the build and tests and audit how AI
  was used, what it got wrong, and how the result was verified.

## 4. Domain Scope

An order has a server-generated ID, status, UTC creation/update timestamps, and
one or more items. An item contains only an opaque `productId` and a positive
integer `quantity`. Price, currency, tax, discounts, totals, product snapshots,
and catalog lookup are intentionally absent.

The lifecycle is:

`PENDING -> PROCESSING -> SHIPPED -> DELIVERED`

`CANCELLED` is terminal and is reachable only from `PENDING` through the cancel
operation. Cancellation changes status; it never deletes an order. A generic
status update cannot set `CANCELLED`, skip a state, move backward, or repeat the
current state.

## 5. Functional Requirements and Acceptance Criteria

### PRD-FR-001 — Create an order

The API shall accept one order containing one or more items.

- **AC-001.1:** A valid multi-item request returns the persisted order with a
  server-generated ID, `PENDING` status, items, and UTC timestamps.
- **AC-001.2:** Empty or missing items, blank `productId`, non-positive quantity,
  duplicate `productId` entries, unknown fields, or malformed input are rejected
  without persisting a partial order.
- **AC-001.3:** IDs, status, and timestamps supplied by a caller cannot override
  server-owned values.

### PRD-FR-002 — Retrieve an order

The API shall retrieve one complete order by ID.

- **AC-002.1:** An existing ID returns its status, timestamps, and all items.
- **AC-002.2:** A syntactically invalid ID is rejected; an absent valid ID
  returns a not-found response.

### PRD-FR-003 — Update order status

The API shall permit only the next transition in the defined fulfilment path.

- **AC-003.1:** Each legal next transition persists and returns the new state.
- **AC-003.2:** Skipped, backward, repeated, post-terminal, or generic
  `CANCELLED` transitions are rejected as conflicts without changing data.
- **AC-003.3:** Concurrent updates cannot produce a transition from a stale
  state; one contender may succeed and stale contenders fail predictably.

### PRD-FR-004 — List orders

The API shall make all orders traversable through bounded pages, optionally
filtered by one exact status. `page` is zero-based and defaults to `0`; `size`
defaults to `20` and cannot exceed `100`. Results are ordered by `createdAt DESC`
and then `id DESC`.

- **AC-004.1:** With defaults and no filter, the first page contains at most 20
  orders plus page metadata in the defined deterministic order.
- **AC-004.2:** Across a stable dataset, traversing every page exposes every
  persisted order. With a valid filter, only matching orders are exposed; no
  matches produces an empty page.
- **AC-004.3:** An unsupported status, negative page, non-positive size, or size
  above 100 is rejected as invalid input.

### PRD-FR-005 — Cancel an order

The API shall cancel an order only while its persisted state is `PENDING`.

- **AC-005.1:** Cancelling a pending order atomically changes it to `CANCELLED`
  and returns the updated order.
- **AC-005.2:** Missing orders return not found; all non-pending orders,
  including already-cancelled orders, return conflict and remain unchanged.
- **AC-005.3:** A cancellation racing the scheduler cannot be overwritten: the
  database commits exactly one valid outcome.

### PRD-FR-006 — Process pending orders every five minutes

The system shall run at second zero of every UTC minute divisible by five and
process every order whose committed status is then `PENDING`. There is no age or
“pending for five minutes” threshold.

- **AC-006.1:** One run atomically changes all rows still matching `PENDING` to
  `PROCESSING` and reports the affected count.
- **AC-006.2:** Re-running the handler immediately is safe and affects zero rows.
- **AC-006.3:** Overlapping application instances cannot double-process or
  revive a cancelled row.
- **AC-006.4:** Tests invoke the handler directly with a controlled clock; they
  do not wait for wall-clock scheduling.

### PRD-FR-007 — Disclose AI assistance

The repository shall maintain `docs/AI_USAGE.md` with the task, useful prompt or
technique, issues found in AI output, corrections made, and human verification.
No secrets, credentials, or sensitive data may be recorded.

## 6. Non-Functional Requirements

| ID | Requirement |
| --- | --- |
| PRD-NFR-001 | **Correctness:** order plus items commit as one transaction; failed commands leave no partial mutation. |
| PRD-NFR-002 | **Concurrency:** status changes use the persisted current state as a precondition and remain safe across threads and replicas. |
| PRD-NFR-003 | **Security:** validate at the HTTP boundary, parameterize database access, keep credentials outside source control, use TLS for managed PostgreSQL, and avoid logging request bodies or secrets. |
| PRD-NFR-004 | **Operability:** expose health information and structured logs for request outcome and scheduled-run count/duration without sensitive payloads. |
| PRD-NFR-005 | **Efficiency:** bound list responses to 100 orders, load orders and items without N+1 queries, and process pending rows with a set-based database operation rather than one transaction per row. No volume or latency SLA is asserted by the assignment. |
| PRD-NFR-006 | **Portability:** the Maven Wrapper and versioned Flyway migrations reproduce the application against local or managed Supabase PostgreSQL. |
| PRD-NFR-007 | **Testability:** domain rules are framework-independent; integration tests use real PostgreSQL semantics through Testcontainers. |
| PRD-NFR-008 | **Maintainability:** use a modular monolith, explicit domain names, small layers, and no speculative extension points or distributed infrastructure. |

## 7. Assumptions and Constraints

- This is a JSON REST API; a UI is not part of the assignment.
- Customer identity, ownership, authentication, authorization, and tenancy were
  not specified. The v1 data model therefore has no customer/account field.
- `productId` is an opaque non-blank identifier measured as 1–100 Unicode code
  points. It is preserved exactly: no trimming, normalization, or case folding;
  trimming is used only to decide whether it is blank. No external catalog is
  queried.
- Order IDs are UUIDs generated by the service; time is represented as UTC
  instants.
- “List all” means every order can be traversed through bounded offset pages; it
  does not mean returning the complete table in one response. Cursor pagination
  is deferred until workload evidence justifies the added contract complexity.
- PostgreSQL is supplied by local Supabase for development and may be supplied
  by managed Supabase for deployment. Supabase-specific Auth, Data API,
  Realtime, Storage, and Edge Functions are not required.

## 8. Non-Goals

Product catalog, pricing/totals, inventory reservation, checkout, payment,
shipping-provider integration, notifications, returns/refunds, customer
accounts, authentication/authorization, multi-tenancy, a frontend, event
streaming, microservices, status-history audit trails, and create-request
idempotency keys are outside v1.

## 9. Success Checks and Status

Planning is complete when the indexed documents resolve every requirement to an
API, data rule, component, test, and implementation step without contradiction.
Implementation is complete only when all acceptance criteria have automated
coverage, the Maven verification build passes, migrations succeed against a
fresh PostgreSQL instance, and a local API/scheduler smoke test is recorded.

As of this baseline, only a generated Spring project scaffold exists. Every
requirement and success check in this document is **planned, not implemented**.
