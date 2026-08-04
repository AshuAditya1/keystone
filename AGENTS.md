# Project KEYSTONE Agent Guide

## Scope

These instructions apply to the entire `keystone` repository. Project KEYSTONE is a field-service management platform for Meridian Facilities Management. Keep changes aligned with the four required roles, the governed work-order lifecycle, and the M1-M4 delivery plan in `TASKS.md`.

The current repository phase is specification and planning. Do not add application code as part of the planning task. Later implementation work must be explicitly scoped to a task in `TASKS.md`.

## Read Before Changing Anything

Before each implementation task, read:

1. `AGENTS.md`
2. `TASKS.md`
3. `docs/PROJECT_SPEC.md`
4. `docs/ASSUMPTIONS.md`
5. `docs/ACCESS_MATRIX.md`
6. `docs/STATE_MACHINE.md`
7. `docs/API_CONTRACT.md`
8. `docs/ERD.md`
9. `docs/TEST_MATRIX.md`
10. `docs/DECISIONS/README.md` and the relevant accepted records in that directory
11. The current Git diff and the existing implementation

If documents disagree, do not silently choose one. Apply this precedence and record the conflict:

1. The explicit product brief and approved human decisions
2. `docs/STATE_MACHINE.md` for lifecycle rules and `docs/ACCESS_MATRIX.md` for authorization rules
3. Accepted architecture decision records
4. `docs/API_CONTRACT.md` and `docs/ERD.md`
5. Working assumptions in `docs/ASSUMPTIONS.md`

An unresolved question is not permission to invent a feature. Use the safest documented behavior that does not expand scope, or stop and request a human decision when implementation would otherwise diverge materially.

The reference playbook still contains the superseded Prompt 0 filenames `ADR-001` through `ADR-004` and a later attachment reference to the old `ADR-004`. Do not recreate or follow those obsolete filenames. `docs/DECISIONS/README.md` is the current ADR index, SLA policy stays in `docs/ASSUMPTIONS.md`/`docs/PROJECT_SPEC.md`, and attachment storage remains Q-023 rather than an accepted ADR.

## Locked Product Boundaries

- Roles are exactly `DISPATCHER`, `TECHNICIAN`, `MANAGER`, and `CUSTOMER`. Do not create a separate administrator role.
- The core hierarchy is Customer -> Site -> WorkOrder.
- Enforce the lifecycle in `docs/STATE_MACHINE.md` on the server. Never implement status changes as unrestricted CRUD updates.
- A customer user is confined to their own customer organisation. A technician is confined to work assigned to them wherever ownership is required.
- Authorization must be enforced by server-side route guards, scoped queries, and service-level checks. Hidden UI controls are not security controls.
- Do not implement invoicing, payments, GPS/live tracking, route optimisation, automatic scheduling, native mobile applications, ERP integrations, social login, or a microservices/event-streaming redesign.

## Locked Technology and Architecture

- Monorepo with `backend/` and `frontend/` applications.
- Java 21, Spring Boot 3, Maven Wrapper, Spring Web, Bean Validation, Spring Security, Spring Data JPA, PostgreSQL, Flyway, and springdoc-openapi.
- React, TypeScript, Vite, React Router, TanStack Query, Axios, React Hook Form, Zod, Material UI, and Recharts.
- Docker Compose for local PostgreSQL and deployable Docker images.
- Modular monolith with thin controllers, transaction-owning services, persistence repositories, and explicit DTOs at every HTTP boundary.
- Flyway is the only schema-management mechanism. Set Hibernate DDL behavior to `validate`; never `create`, `update`, or `create-drop` outside disposable test experiments.
- Stateless bearer access JWTs; no HTTP session and no OAuth2 login.
- In-app notifications.
- Avoid Lombok unless a later approved decision changes this rule.

## Implementation Guardrails

- Inspect before editing. Do not assume that a file, class, endpoint, dependency, or command exists.
- Work only on the active task and milestone. Do not pre-build later features.
- Keep controllers concerned with HTTP mapping and validation; keep authorization and business invariants in testable services/domain policies.
- Never expose JPA entities directly. Use request/response DTOs and explicit mapping, including separate customer-safe representations.
- Attachment storage is not a locked architecture decision. Follow A-027 through A-029 and resolve Q-023/Q-024 in `docs/ASSUMPTIONS.md` before creating attachment-content schema or implementation.
- Use lazy JPA relationships by default, avoid unsafe cascading, and avoid bidirectional collections unless justified.
- Use database constraints as a second line of defense for invariants identified in `docs/ERD.md`.
- Treat status history as append-only. Application code must not expose update or delete operations for history rows.
- Make a status change, its history row, and required side effects one transaction.
- Make stock validation, decrement, and part-usage creation one transaction. Concurrency must not allow negative stock.
- Use optimistic locking or an equally explicit strategy for concurrent work-order mutation.
- Use `Clock` injection for time-sensitive SLA behavior and deterministic tests.
- Normalize emails consistently, store password hashes only, and never log tokens, passwords, secrets, or attachment bytes.
- Do not hard-code secrets. Document environment variables in `.env.example` using non-secret placeholders.
- Preserve user work and unrelated changes. Avoid destructive Git commands and broad rewrites.

## API and Security Conventions

- Implement the versioned API and payload conventions in `docs/API_CONTRACT.md`.
- Return JSON errors consistently. Keep stable machine-readable error codes and correlation identifiers.
- Distinguish `401` unauthenticated/invalid token, `403` authenticated but route/action forbidden, `404` absent or deliberately concealed scoped resource, and `409` state/version/business conflicts.
- Every collection is scoped on the server before pagination. Never fetch broadly and filter in the browser.
- Pagination must be bounded and sorting must be deterministic.
- Validate UUIDs/identifiers, request fields, relationships, file size, declared MIME type, content signature where practical, and safe filenames.
- OpenAPI must match running behavior; contract changes require updates to `docs/API_CONTRACT.md`, tests, and frontend types/calls.

## Required Verification

For each implementation task, run the narrowest relevant checks and the milestone gate. Expected final equivalents include:

```text
docker compose config
docker compose up -d db
# Windows PowerShell
./backend/mvnw.cmd clean verify
# Unix-like shell
./backend/mvnw clean verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test -- --run
npm --prefix frontend run build
git diff --check
git status --short
```

Use PostgreSQL/Testcontainers for behavior that depends on transactions, constraints, locking, or Flyway. Do not replace database-integrity tests with an in-memory database.

Never make a check pass by disabling tests, weakening authorization or validation, loosening types, or replacing real behavior with a fake success path.

## Documentation and Handoff

- Update `TASKS.md` only for work actually completed and verified.
- Update affected specifications and ADRs when an approved decision or contract changes.
- End each task with: files changed, behavior delivered, commands and outcomes, remaining risks/questions, and a recommended commit message.
- Stop at the end of the active task.
