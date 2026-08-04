# Project KEYSTONE Delivery Plan

## Status

- `[x]` complete and verified
- `[ ]` not started or not yet verified
- `[!]` blocked by a recorded human decision

This file separates the completed planning baseline from future implementation. No application implementation item is complete at the time of this baseline.

## Planning Baseline (Complete)

- [x] Define engineering operating rules in `AGENTS.md`.
- [x] Separate brief requirements, locked decisions, assumptions, and open questions.
- [x] Define role/route authorization and resource scoping.
- [x] Define the exact work-order lifecycle and conflict semantics.
- [x] Define the REST API conventions and surface.
- [x] Define the Mermaid ERD and database constraints.
- [x] Define security, integrity, migration, UI, and deployment tests.
- [x] Record the required monorepo, stateless-JWT, service-layer-state-machine, Flyway-only, and in-app-notification ADRs; keep SLA and attachment choices in the assumptions register.
- [x] Run a cross-document specification-consistency review.

## Delivery Rules

- Work in milestone order. Do not begin later product functionality until the current milestone gate is green.
- Each day starts by reading the planning documents and current diff and ends with docs/task updates and verification evidence.
- Keep deployment foundations working from M1; day 19 is production packaging/deployment completion, not the first infrastructure check.
- Never mark a task complete from code inspection alone when its acceptance requires a command, automated test, or live check.
- Open questions in `docs/ASSUMPTIONS.md` must be decided before the dependent behavior is implemented. Unaffected work may continue.

## M1 - Foundation, Domain, Authentication (Days 1-5)

**Milestone outcome:** A clean checkout starts the frontend, backend, and PostgreSQL; Flyway creates/validates the schema; four-role stateless JWT authentication and server-side RBAC are proven.

### Day 1 - Runnable monorepo skeleton

- [ ] Resolve migration/security-critical portions of Q-009, Q-011, Q-013, Q-018, and Q-023 before fixing first-schema fields, currency, user credential creation, JWT runtime values, seed policy, or attachment-content storage.
- [x] Create `backend/` Spring Boot Java 21 Maven Wrapper project with approved dependencies and profiles.
- [x] Create `frontend/` React + TypeScript + Vite project with the approved frontend stack.
- [x] Add Docker Compose PostgreSQL with health check and non-secret environment placeholders.
- [x] Add backend/frontend Dockerfiles, formatting/lint configuration, `.editorconfig`, README starter, and health connectivity.
- [x] Add an initial Flyway connectivity migration without business controllers/entities.
- [ ] Verify Compose config, database health, backend tests/startup, frontend lint/test/build, and frontend-to-health call. All automated, packaging, and live HTTP checks passed on 2026-08-05; in-browser display confirmation remains manual because no browser runtime was available.

### Days 2-3 - Domain schema and seed foundation

- [ ] Implement enums/entities/repositories corresponding exactly to `docs/ERD.md`; expose no business HTTP API yet.
- [ ] Create ordered Flyway migrations for tables, indexes, foreign keys, unique constraints, checks, and seed/demo profile.
- [ ] Enforce customer-site-work-order coherence, unique normalized email, unique SKU/code, positive usage/time, non-negative stock/cost, and optimistic versioning.
- [ ] Model required attachment metadata, but create attachment-content storage only after Q-023 approves a backend; if A-027 is approved, keep PostgreSQL bytes separate from metadata. Use lazy/safe relationships.
- [ ] Seed multiple customers/sites/parts/orders and exactly one non-production demo login per role using BCrypt hashes.
- [ ] Verify clean PostgreSQL migrate, Flyway validate, Hibernate schema validate, repository constraints, and repeat startup.

### Days 4-5 - Authentication and RBAC

- [ ] Approve the M1 values from Q-009/Q-013: baseline manager user operations, initial credential delivery, token lifetime, issuer/audience, signing algorithm/key source, and active-user behavior.
- [ ] Implement login and current-user endpoints from `docs/API_CONTRACT.md`.
- [ ] Implement BCrypt verification, signed expiring bearer JWTs, stateless security, method security, CORS, and current-user abstraction.
- [ ] Enforce default-deny route rules with JSON `401`/`403` responses.
- [ ] Implement and test the approved manager user list/create/read/update/activation API without inventing password recovery or invitation flows.
- [ ] Test successful login for four roles plus wrong credentials, missing/malformed/tampered/expired JWT, and role mismatch.
- [ ] Confirm no plaintext passwords, secrets, tokens, or sensitive customer data in source, database fixtures, claims, logs, or errors.

### M1 gate - Day 5

- [ ] Run clean backend/frontend builds and tests, Docker Compose startup, fresh Flyway migration, Hibernate validation, login/RBAC smoke tests, and secret scan.
- [ ] Create `docs/reviews/M1_REVIEW.md` with PASS/FAIL evidence, risks, and manual demo steps.
- [ ] Do not begin customer/site/work-order features until the gate passes.

## M2 - Core Records and Open-Work Board (Days 6-10)

**Milestone outcome:** Authorized users manage customer/site data and role-scoped work orders through validated APIs and usable screens, with an open-work board and consistent list mechanics.

### Day 6 - Customers and sites

- [ ] Resolve Q-014 for customer/site deactivation before enabling activation-state mutations.
- [ ] Implement dispatcher/manager customer and site create/read/update; manager deactivation only.
- [ ] Implement customer-self organisation/site reads using safe DTOs.
- [ ] Enforce parent integrity, scoped queries, service ownership checks, validation, pagination/search/sort, and no destructive delete.
- [ ] Build login shell, role navigation, internal management screens, and customer organisation/site view with full UI states.
- [ ] Test route roles, cross-customer guessed IDs/filter attacks, customer-site integrity, and direct API bypass.

### Days 7-8 - Work-order CRUD

- [ ] Resolve Q-001, Q-002, Q-010, Q-022, and required data-dictionary parts of Q-011 before finalizing create/update DTOs or calculating `slaDueAt`.
- [ ] Implement role-scoped work-order create/list/detail/update without assignment or lifecycle commands.
- [ ] Generate concurrency-safe human-readable codes; set `NEW`, SLA due time, version, and initial `null -> NEW` history atomically.
- [ ] Enforce customer/site coherence, customer request field allowlist, customer-safe responses, terminal immutability, and optimistic conflicts.
- [ ] Implement list/detail/create/edit UI with server-side filters, URL state, pagination, and errors.
- [ ] Test IDOR, invalid parent combinations, terminal edits, code uniqueness, initial history, and mass-assignment attempts.

### Day 9 - Open-work board

- [ ] Implement server-scoped board data grouped by `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`.
- [ ] Exclude terminal work by default; include authorized priority, technician, customer/site, and SLA filters.
- [ ] Build accessible responsive cards and explicit actions without inventing drag-and-drop status behavior.
- [ ] Test grouping, customer exclusion, technician scope, internal-role scope, filter manipulation, and no forbidden card fields.

### Day 10 - List/search quality and M2 gate

- [ ] Enforce size bounds, allowlisted sorts/filters, deterministic tie-break ordering, free-text limits, and database indexes.
- [ ] Inspect and repair common N+1/unbounded-query risks.
- [ ] Verify frontend URL filter state, reset, no-results, loading, validation, and error behavior.
- [ ] Run backend/frontend suites and direct API role-scoping probes.
- [ ] Create `docs/reviews/M2_REVIEW.md`; do not implement lifecycle transitions until the gate passes.

## M3 - Lifecycle, Dispatch, Field Integrity, SLA (Days 11-15)

**Milestone outcome:** Work moves only through the approved state machine, assignment and technician ownership are enforced, parts/time are transactional, and SLA/in-app notifications are deterministic and idempotent.

### Days 11-12 - State machine and audit history

- [ ] Implement a dedicated transition policy/service using only `docs/STATE_MACHINE.md`.
- [ ] Implement the generic status command while excluding first assignment from it.
- [ ] Make status/version/history/side effects atomic; return stable `409` details for illegal or stale transitions.
- [ ] Return role-safe chronological history and server-authoritative allowed actions.
- [ ] Add exhaustive state-pair, actor, ownership, terminal, history-row, rollback, and concurrent-transition tests.

### Day 13 - Assignment and technician workflow

- [ ] Resolve Q-007 and Q-021 before finalizing completed-work reassignment, same-technician assignment, or a separate assignment-audit design.
- [ ] Implement technician selection and first assignment/reassignment commands for dispatcher/manager.
- [ ] Validate active technician targets; first assignment atomically creates `NEW -> ASSIGNED` status history, while reassignment preserves status and creates no fake same-state history. Notify the incoming technician atomically; any additional assignment audit awaits Q-021.
- [ ] Implement mobile-first technician list/detail and lifecycle controls, scoped server-side to the assignee.
- [ ] Test inactive/non-technician targets, first assignment, reassignment, cross-technician access, stale actions, and notification creation.

### Day 14 - Parts and time

- [ ] Resolve Q-006, Q-014, Q-020, and currency elements of Q-011 before final mutation, deactivation, and safe part-selection policy.
- [ ] Implement manager part inventory create/update/list/deactivate with unique SKU and non-negative values.
- [ ] Implement authorized part usage with stock locking/atomic decrement, historical cost snapshot, and roll-up totals.
- [ ] Implement authorized positive-minute time logs and labour-minute totals.
- [ ] Build manager inventory and technician job-detail forms/history states.
- [ ] Prove failure rollback, concurrent non-negative stock, ownership, terminal/eligibility rules, historical cost, positive minutes, and totals.

### Day 15 - SLA and notifications

- [ ] Resolve Q-003, Q-008, and Q-015 before final SLA state/recipient/scheduler logic; Q-002/Q-022 must already be resolved before work-order due-time calculation in M2.
- [ ] Implement typed SLA duration/risk/scheduler configuration and controllable clock.
- [ ] Implement bounded open-order scanning and idempotent at-risk/breach notifications.
- [ ] Implement notification list/mark-read endpoints and UI; show SLA badges in authorized surfaces.
- [ ] Add structured non-sensitive logs for assignment/status/SLA events.
- [ ] Test all priorities and boundaries, terminal behavior, recipient policy, deduplication, scheduler batching, and time control.
- [ ] Create `docs/reviews/M3_REVIEW.md` and repair every failed lifecycle/security/transaction/SLA criterion before M4.

## M4 - Insights, Customer Completion, Production (Days 16-20)

**Milestone outcome:** Managers have accurate reports, customers have a safe portal, proof photos are secure, the contract and deployment are verified, and final acceptance evidence is complete.

### Days 16-17 - Dashboard and reporting

- [ ] Resolve Q-012 before fixing report calculations.
- [ ] Complete the manager user-management UI for the approved M1 API and verify high-risk role/organisation/activation changes.
- [ ] Implement manager-only summary metrics and bounded technician/site breakdowns using aggregate projections.
- [ ] Apply date, customer/site, technician, and priority filters consistently.
- [ ] Build accessible cards/charts/tables and actionable SLA lists with synchronized filters and UI states.
- [ ] Test manager-only access, aggregate accuracy against deterministic data, empty sets, filter consistency, and query bounds.

### Day 18 - Customer portal, attachments, and responsive polish

- [ ] Deliver the required customer timeline now with statuses/timestamps. Resolve Q-004 only before enriching it with actor/note fields; resolve Q-005/Q-006/Q-019/Q-023/Q-024 before proof visibility, proof storage, or dispatcher nested-record reads. Proof remains optional.
- [ ] Complete customer request/list/detail/timeline views with organisation-only queries and customer-safe fields.
- [ ] Implement internal proof-image upload/list/download using the Q-023-approved content store, metadata-only collection responses, size/type/signature/filename validation, and authorized eligibility. If A-027 is approved, keep PostgreSQL metadata and bytes in separate tables.
- [ ] Apply the approved customer proof-image visibility policy, defaulting to denial until approved.
- [ ] Test guessed IDs, filter manipulation, metadata/content access, storage failure cleanup, MIME spoofing, oversize files, unsafe filenames, terminal/ownership rules, and customer field leakage.
- [ ] Verify keyboard access, labelled controls, status labels, error states, and common phone-width technician workflows.

### Day 19 - OpenAPI, integration, packaging, and deployment

- [ ] Resolve Q-013, Q-016, and Q-018 before production configuration/deployment.
- [ ] Generate and verify OpenAPI for every route, role, DTO, filter, page, status, error, and bearer scheme.
- [ ] Add high-value PostgreSQL integration and frontend component tests from `docs/TEST_MATRIX.md`.
- [ ] Create repeatable multi-stage images, environment-only production config, health/readiness, persistent storage, and safe migration procedure.
- [ ] Deploy the backend, frontend, and PostgreSQL; configure TLS/CORS/API URL and document rollback/troubleshooting in `docs/DEPLOYMENT.md`.
- [ ] Smoke-test live API/UI, all roles, Swagger, persistence after restart, and an end-to-end work-order flow.

### Day 20 - Final acceptance and demo package

- [ ] Create `docs/reviews/FINAL_ACCEPTANCE.md` mapping each acceptance criterion to implementation, automated test, manual step, result, and evidence.
- [ ] Run a clean-checkout simulation using only README instructions.
- [ ] Run all backend tests, frontend tests/lint/build, Docker builds, fresh migrations, schema validation, and live smoke tests.
- [ ] Audit secrets, authorization, lifecycle, transactions, SLA, accessibility/responsiveness, OpenAPI drift, and deployment configuration.
- [ ] Finalize README, `docs/DEMO_SCRIPT.md`, `docs/SUBMISSION_CHECKLIST.md`, release notes, screenshots, URLs, and version-tag recommendation.
- [ ] Separate PASS, FAIL, and MANUAL-ONLY checks; never mark an unverified criterion PASS.

## Cross-Milestone Quality Gates

Every milestone review must include:

- [ ] `git diff --check` and a review for unrelated changes/secrets.
- [ ] Backend unit and PostgreSQL integration tests relevant to the milestone.
- [ ] Frontend lint, unit/component tests, and production build.
- [ ] OpenAPI/API-contract drift check for implemented routes.
- [ ] Direct unauthenticated, wrong-role, cross-customer, and cross-technician probes where applicable.
- [ ] Fresh Flyway startup and Hibernate validation after schema changes.
- [ ] Documentation and exact setup/test commands updated.

## Dependency and Decision Watchlist

- M2 work-order DTOs depend on Q-001, Q-010, and parts of Q-011.
- M1 schema/security depends on the implementation-critical portions of Q-009, Q-011, Q-013, and Q-018.
- M2 deactivation and due-time calculation depend on Q-002, Q-014, and Q-022.
- M3 reassignment depends on Q-007 and Q-021.
- M3 parts/time/proof eligibility depends on Q-006; cost presentation depends on currency in Q-011.
- M3 part selection/deactivation depends on Q-014 and Q-020; SLA outcomes/recipients depend on Q-003 and Q-008.
- M4 reporting depends on Q-012.
- M4 customer history/attachments and dispatcher nested reads depend on Q-004, Q-005, Q-019, Q-023, and Q-024.
- Production authentication/deployment depends on the operational portion of Q-013 plus Q-016 and Q-018.

Unresolved decisions block only the affected behavior, not unrelated milestone preparation.
