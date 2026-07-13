# Documentation Index

This index is the navigation and ownership map for project knowledge. Documents describe either **current** evidence or **planned** design; they must never imply that planned behavior is implemented.

## Reading Order

1. Root [RESUME.md](../RESUME.md) for current state and next work.
2. Root [README.md](../README.md) for scope, stack, and repository entry points.
3. This index to load only the documents triggered by the task.

## Canonical Owners

| Document | Owns | Read or update when |
| --- | --- | --- |
| [PRD.md](PRD.md) | Product scope, requirements, acceptance, non-goals | Product behavior or scope changes |
| [TRD.md](TRD.md) | Technical requirements, runtime, configuration, Supabase contract | Stack, deployment, security, or NFR changes |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Boundaries, components, dependencies, system flows | Component ownership or integration changes |
| [LLD.md](LLD.md) | Packages, classes, state machine, transactions, algorithms | Implementation shape or business rules change |
| [API_CONTRACT.md](API_CONTRACT.md) | Endpoints, fields, validation, responses, errors | Any HTTP behavior changes |
| [DATA_MODEL.md](DATA_MODEL.md) | Tables, columns, constraints, indexes, migration rules | Persistence changes |
| [TEST_STRATEGY.md](TEST_STRATEGY.md) | Test layers, requirement matrix, verification gates | Behavior or test tooling changes |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Ordered delivery slices and completion checks | Planning, progress, or handoff changes |
| [decisions/0001-tech-stack.md](decisions/0001-tech-stack.md) | Stack choice and rejected alternatives | Stack choice is reconsidered |
| [decisions/0002-supabase-and-concurrency.md](decisions/0002-supabase-and-concurrency.md) | Database provider, migration owner, race strategy | Data platform or write concurrency changes |
| [AI_USAGE.md](AI_USAGE.md) | AI assistance, issues, corrections, human verification | AI materially assists a task |

## Progressive Loading

- API task: PRD → API contract → LLD → test strategy.
- Database task: data model → Supabase/concurrency ADR → TRD.
- Scheduler or status task: PRD → LLD → concurrency ADR → test strategy.
- Stack/config task: tech-stack ADR → TRD → architecture.
- Delivery task: implementation plan → RESUME.

## Authority and Change Rules

- The user request overrides repository documents; record the resulting decision in the canonical owner.
- `PRD.md` owns *what* and `LLD.md` owns *how*. Do not duplicate full rules elsewhere.
- ADRs explain *why* a durable decision was made; they do not track progress.
- `RESUME.md` is the only volatile queue. Completed implementation evidence belongs in the plan and tests.
- Update all affected documents, contracts, and traceability rows in the same future change.
- Broken links, conflicting rules, or unlabelled planned behavior block implementation.
