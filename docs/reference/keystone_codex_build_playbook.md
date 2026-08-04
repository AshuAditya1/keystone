# Project KEYSTONE — Codex Build Playbook

This playbook converts the Project KEYSTONE brief into a controlled, milestone-based Codex workflow. Use the prompts in order. Do not ask Codex to build the entire application in one prompt.

## 1. Locked project decisions

These choices keep the implementation consistent and reduce agent drift. Change them only before coding starts.

- Repository: monorepo named `keystone`.
- Back end: Java 21, Spring Boot 3, Maven Wrapper, Spring Web, Validation, Spring Data JPA, Spring Security, JWT, PostgreSQL, Flyway, springdoc-openapi.
- Front end: React + TypeScript + Vite, React Router, TanStack Query, Axios, React Hook Form + Zod, Material UI, Recharts.
- Local infrastructure: Docker Compose with PostgreSQL.
- Database schema: managed only by Flyway; `spring.jpa.hibernate.ddl-auto=validate`.
- Architecture: thin controllers, business logic in services, repositories for persistence, DTOs at every API boundary.
- Roles: `DISPATCHER`, `TECHNICIAN`, `MANAGER`, `CUSTOMER`. Do not add a separate ADMIN role unless the mentor explicitly requests it.
- Authentication: stateless access JWT sent as `Authorization: Bearer <token>`. No server session and no OAuth2 login for this project.
- Notifications: in-app notifications, because the brief allows email or in-app and does not require an external email provider.
- Attachments: small work-order proof images stored in PostgreSQL for the seed-scale project, with strict type and size validation. Store metadata separately from bytes.
- No Lombok unless explicitly approved. Prefer explicit Java code for easier explanation during review.
- Do not implement invoicing, payments, GPS, route optimisation, native mobile applications, automated scheduling, or ERP integrations.

### Recommended priority and SLA policy

The brief requires SLA deadlines but does not define exact durations. Record this as a project assumption and make it configurable:

- `CRITICAL`: 4 hours
- `HIGH`: 24 hours
- `MEDIUM`: 48 hours
- `LOW`: 72 hours
- `AT_RISK`: an open order whose due time is inside a configurable warning window, initially 2 hours.
- `BREACHED`: an open order whose SLA due time has passed.

### Recommended transition and permission policy

The PDF diagram defines these transitions:

- `NEW -> ASSIGNED`: dispatcher or manager assigns a technician.
- `NEW -> CANCELLED`: dispatcher or manager.
- `ASSIGNED -> IN_PROGRESS`: assigned technician.
- `ASSIGNED -> CANCELLED`: dispatcher or manager.
- `IN_PROGRESS -> ON_HOLD`: assigned technician.
- `ON_HOLD -> IN_PROGRESS`: assigned technician.
- `IN_PROGRESS -> COMPLETED`: assigned technician.
- `COMPLETED -> IN_PROGRESS`: manager reopens.
- `COMPLETED -> CLOSED`: manager closes.
- `CLOSED` and `CANCELLED`: terminal.

Every transition must write one append-only history record containing old status, new status, actor, timestamp, and optional note. The initial creation should also write a `null -> NEW` history record.

## 2. Before opening Codex

Install or confirm:

1. Git and a GitHub account.
2. Java 21.
3. Node.js LTS and npm.
4. Docker Desktop with Docker Compose.
5. Codex app, IDE extension, or CLI signed in.

Create an empty GitHub repository named `keystone`, clone it, and open the repository root in Codex. Keep all work under version control from the first file.

## 3. Reusable task contract

Paste this block at the beginning of every implementation prompt after Prompt 0.

```text
You are working inside the Project KEYSTONE repository.

Operating rules:
1. Read AGENTS.md, TASKS.md, docs/PROJECT_SPEC.md, docs/ASSUMPTIONS.md, docs/ACCESS_MATRIX.md, docs/STATE_MACHINE.md, docs/API_CONTRACT.md, and the current git diff before changing code.
2. Inspect the existing implementation. Do not assume a file, class, endpoint, command, or dependency exists.
3. Work only on the task described below. Do not start later milestones.
4. Before editing, give a short plan listing the files or areas you expect to change and the tests you will run.
5. Preserve all working behavior outside the task. Do not rewrite unrelated modules.
6. Never weaken authentication, authorisation, validation, transactions, database constraints, or tests to make a build pass.
7. Do not expose JPA entities from controllers. Use request/response DTOs and explicit mapping.
8. Do not hard-code secrets. Add documented placeholders to .env.example and configuration properties.
9. Do not create fake success paths, empty stubs, disabled tests, TODO-only implementations, or catch-and-ignore blocks.
10. Do not use destructive git commands, force push, or reset unrelated user work.
11. Run the relevant formatter, linter, unit tests, integration tests, production builds, and docker/config checks. Do not claim success without showing the command results.
12. If blocked, stop and report the exact error, likely cause, and smallest action required. Do not guess around the blocker.
13. Update TASKS.md and any affected design/API documentation.
14. At the end, report: files changed, behavior implemented, commands run with results, remaining risks, and the recommended commit message.
15. Stop after this task. Do not continue to the next prompt.
```

## 4. Prompt 0 — Specification, decisions, and guardrails

Use this before any application code is generated.

```text
Act as the lead engineer for Project KEYSTONE, a field-service management platform for Meridian Facilities Management.

The required product is a four-role system:
- Dispatcher: creates customers, sites, and work orders; assigns/reassigns technicians; sees all open work and the board.
- Technician: sees only assigned work; starts, holds, resumes, and completes jobs; logs parts and time; uploads proof photos.
- Manager: dispatcher capabilities plus closing/reopening jobs, user and part management, dashboard, reporting, and administrative actions.
- Customer: raises requests for their organisation's sites and sees only their organisation's work-order status and history, without internal fields.

Core domain:
Customer -> Sites -> WorkOrders. WorkOrders have an assignee, status history, part usages, time logs, SLA information, notifications, and optional attachments.

Required lifecycle:
NEW -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED
IN_PROGRESS -> ON_HOLD -> IN_PROGRESS
COMPLETED -> IN_PROGRESS for manager reopen
NEW or ASSIGNED -> CANCELLED
CLOSED and CANCELLED are terminal.

Required stack:
Java 21, Spring Boot 3, Spring Security with stateless JWT, Spring Data JPA, PostgreSQL, Flyway, React + TypeScript + Vite, OpenAPI/Swagger, Docker, and deployment.

Do not write application code yet.

Create the following planning files:
- AGENTS.md
- TASKS.md
- docs/PROJECT_SPEC.md
- docs/ASSUMPTIONS.md
- docs/ACCESS_MATRIX.md
- docs/STATE_MACHINE.md
- docs/API_CONTRACT.md
- docs/ERD.md using Mermaid
- docs/TEST_MATRIX.md
- docs/DECISIONS/ADR-001-architecture.md
- docs/DECISIONS/ADR-002-authentication.md
- docs/DECISIONS/ADR-003-sla-policy.md
- docs/DECISIONS/ADR-004-notifications-and-attachments.md

Requirements for the documents:
1. Separate explicit brief requirements from implementation assumptions.
2. Include a route-by-role authorisation matrix.
3. Include the exact state-transition matrix, actor rules, terminal-state rules, audit-history behavior, and expected HTTP 409 behavior for illegal transitions.
4. Define a conventional REST API surface for auth, users/technicians, customers, sites, work orders, assignment, status transitions, history, parts, part usage, time logs, attachments, notifications, and reports.
5. Define consistent pagination, sorting, filtering, and error-response conventions.
6. Define the full entity relationship model and important constraints, but do not generate JPA code yet.
7. Define test cases for IDOR/cross-customer access, technician ownership, direct API attacks, invalid/expired JWTs, illegal transitions, terminal states, transactional stock decrement, negative stock prevention, and clean Flyway startup.
8. Build TASKS.md around milestones M1 through M4 and the 20-day plan.
9. Record unresolved specification questions. Resolve only the decisions already supplied in this prompt; do not invent unrelated features.
10. End with a specification-consistency review and list any contradictions or gaps that still need a human decision.
```

Review every generated document before moving on. Correct disagreements now; later prompts treat these files as the source of truth.

## 5. Prompt 1 — Day 1: repository and runnable skeleton

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: create the Day 1 runnable monorepo skeleton.

Implement:
- Root folders: backend, frontend, docs, scripts if needed.
- Spring Boot Maven project under backend using Java 21 and Maven Wrapper.
- Dependencies for web, validation, JPA, security, OAuth2 resource-server/JWT support if used only as the JWT implementation, PostgreSQL, Flyway, Actuator, springdoc-openapi, and testing.
- React + TypeScript + Vite under frontend.
- Front-end dependencies locked to the approved stack in AGENTS.md.
- Root docker-compose.yml with PostgreSQL and a health check.
- .gitignore, .editorconfig, .env.example, root README starter, and consistent formatting/lint scripts.
- Backend health endpoint through Actuator and a minimal front-end page that calls it through a typed API module.
- Configuration profiles for local, test, and production. No credentials committed.
- CORS origins configured from environment.
- A first Flyway migration proving the database connection, even if it only creates a schema metadata table.
- Dockerfiles for backend and frontend or clearly documented placeholders that produce valid images now.

Verification:
- docker compose config
- start PostgreSQL
- backend tests
- backend application startup and health check
- npm lint/test if configured
- npm production build
- front end successfully calls the backend health endpoint

Do not create business entities or authentication yet. Update TASKS.md and README with exact commands for Windows PowerShell and Unix-like shells where commands differ.
```

Commit after green verification: `chore: scaffold keystone monorepo and local environment`.

## 6. Prompt 2 — Days 2–3: domain model, migrations, and seed data

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement the M1 domain foundation and Flyway schema without exposing any business API yet.

Create enums and entities for:
- Role
- WorkOrderStatus
- Priority
- SlaStatus
- NotificationType
- Customer
- Site
- User, with an optional customer organisation reference for CUSTOMER users
- WorkOrder
- WorkOrderStatusHistory
- Part
- PartUsage
- TimeLog
- Notification
- WorkOrderAttachment metadata and binary content design approved in ADR-004

Required constraints:
- Site must belong to Customer.
- WorkOrder must belong to Customer and Site; the site must belong to the same customer.
- User email unique and normalized.
- WorkOrder code unique and human-readable.
- Status history append-only.
- Quantity and stock non-negative.
- Time-log minutes positive.
- Part SKU unique.
- Correct createdAt/updatedAt audit timestamps.
- Use lazy relationships by default and avoid unsafe cascading.
- Avoid bidirectional collections unless they are necessary.

Flyway:
- Replace the proof migration if needed with a clean ordered migration history.
- Create all tables, indexes, foreign keys, unique constraints, and checks explicitly.
- Add seed data for multiple customers, sites, parts, work orders, and exactly one demo login for each role.
- Seed passwords only as BCrypt hashes. Document demo credentials as non-production values.
- Configure Hibernate schema validation; never auto-create/update schema.

Add repository interfaces and repository-focused tests, including fresh PostgreSQL startup with migrations. Prefer Testcontainers for database integration tests.

Do not add controllers or authentication. Update ERD, assumptions, and test matrix when implementation details become concrete.
```

Commit: `feat: add keystone domain model and flyway schema`.

## 7. Prompt 3 — Days 4–5: authentication and server-side RBAC

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: complete M1 authentication and role-based access control.

Implement:
- POST /api/auth/login accepting validated email and password.
- A response containing accessToken, tokenType, expiresAt, and a safe current-user summary.
- GET /api/auth/me.
- BCrypt password verification.
- Signed, expiring stateless JWTs using a secret supplied only through environment/configuration.
- JWT claims for user id, email, and role. Do not place sensitive customer data in the token.
- Spring Security filter chain with stateless sessions, clear 401 and 403 JSON responses, CORS, and method security enabled.
- A CurrentUser abstraction so business services do not parse tokens themselves.
- Role guard utilities or method-level @PreAuthorize expressions that remain readable and testable.
- No public endpoints other than health/docs as deliberately configured and login. Customer request submission remains authenticated for this implementation.

Security tests must cover:
- successful login for all four roles
- incorrect credentials
- missing token
- malformed token
- tampered token
- expired token
- role mismatch returning 403
- protected endpoint never reached without valid authentication
- BCrypt hashes in the database, not plaintext

Add a minimal secured probe endpoint only if needed for tests, then remove it or keep it internal/documented. Do not implement feature controllers yet.
```

Commit: `feat: implement jwt authentication and role security`.

## 8. Prompt 4 — M1 audit gate

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: audit M1 without adding new product features.

Review the repository against these M1 requirements:
- clean layered structure
- Java 21/Spring Boot application and React application start from a clean checkout
- PostgreSQL starts with Docker Compose
- Flyway creates and seeds a fresh database
- Hibernate validates rather than creates schema
- entities and constraints match the ERD
- JWT login works for four roles
- server-side RBAC returns correct 401/403 responses
- no secrets are committed
- README setup works exactly

Run all tests and builds from a clean state. Inspect git-tracked files for credentials. Add or repair missing tests and documentation only where needed for M1. Produce docs/reviews/M1_REVIEW.md with a pass/fail checklist, command evidence, remaining risks, and manual demo steps.

Do not begin customer/site or work-order features.
```

Commit: `test: complete m1 foundation audit`.

## 9. Prompt 5 — Day 6: customers and sites, API and UI

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement customer and site management end to end.

Back end:
- Dispatcher and manager can create, read, and edit customers and sites.
- Manager may deactivate records; avoid destructive deletion that would break historical work orders.
- Site creation verifies the parent customer.
- Searchable, sortable, paginated customer and site list endpoints.
- Customer users can read only their own organisation and sites.
- Separate public/internal response DTOs so customer users never receive internal fields.
- Central validation and consistent error responses.
- Service-level ownership checks in addition to repository scoping.

Front end:
- Authenticated application shell with route guards and role-aware navigation.
- Login screen.
- Dispatcher/manager customer list, create/edit form, customer detail, and site management.
- Customer role organisation/site view.
- Loading, empty, validation, 401, 403, and generic error states.

Tests:
- role permissions
- pagination/search
- site/customer integrity
- a customer changing an id cannot see another organisation
- direct API calls remain protected even when UI routes are bypassed

Update API contract and README demo steps.
```

Commit: `feat: add customer and site management`.

## 10. Prompt 6 — Days 7–8: work-order CRUD

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement validated work-order creation, retrieval, editing, and listing. Do not implement assignment or lifecycle actions yet.

Back end:
- POST /api/work-orders for dispatcher/manager and for authenticated customers creating a request for one of their own sites.
- GET /api/work-orders and GET /api/work-orders/{id}, always role-scoped.
- PUT /api/work-orders/{id} while the work order is open.
- Closed or cancelled work orders are immutable and return a clear conflict response.
- Generate a concurrency-safe unique human-readable code such as WO-YYYYMM-000001.
- Validate title, description, priority, customer/site relationship, and role-specific writable fields.
- Customer-created requests ignore or reject internal fields such as assignee, internal notes, costs, and status.
- On creation set status NEW, calculate SLA due time from configurable priority policy, and append initial history `null -> NEW`.
- Return a detailed DTO with safe role-specific views.
- List endpoint supports page, size, sort, free-text search, status, priority, customer, site, assignee, SLA status, and overdue filters where authorised.

Front end:
- Work-order create form whose fields change safely by role.
- Work-order list with filters, pagination, loading, empty, and error states.
- Work-order detail showing allowed information and history.
- Edit action only when server and UI rules allow it.

Tests must include cross-customer IDOR attempts, invalid site/customer combinations, terminal immutability, code uniqueness, pagination, and initial history creation.
```

Commit: `feat: implement role-scoped work order crud`.

## 11. Prompt 7 — Day 9: Kanban work-order board

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement the open-work Kanban board without inventing drag-and-drop behavior.

Requirements:
- Board groups open work orders by NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, and COMPLETED.
- CANCELLED and CLOSED are excluded from the default open board but available through filters/history pages.
- Dispatcher and manager see authorised organisation-wide work.
- Technician sees only assigned work.
- Customer does not use the internal board.
- Cards show code, title, priority, site, assignee, SLA badge, and due time without leaking forbidden fields.
- Board data comes from role-scoped backend queries, not client-side security filtering.
- Provide filters for priority, technician, customer/site where authorised, and SLA condition.
- Use accessible buttons/actions; do not add drag-and-drop until status and assignment commands exist.
- Responsive layout with useful loading, empty, and error states.
- Add component and API tests for grouping and role scoping.
```

Commit: `feat: add role-scoped work order board`.

## 12. Prompt 8 — Day 10: M2 search, pagination, and quality gate

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: finish and audit M2.

Improve and verify:
- server-side pagination, sorting, filtering, and free-text search for customers, sites, and work orders
- bounded maximum page size
- stable sort with deterministic secondary ordering
- database indexes for common filters
- URL query parameters reflected in the front-end views
- filter reset and no-result states
- validation and safe error responses
- no N+1 regressions in common list/detail queries

Run backend and frontend tests/builds. Add docs/reviews/M2_REVIEW.md against the milestone: validated work-order CRUD, usable board, and proper list/filter/search/pagination. Include direct API test evidence for role scoping.

Do not implement status transitions yet.
```

Commit: `test: complete m2 core work order audit`.

## 13. Prompt 9 — Days 11–12: governed lifecycle and audit history

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement the work-order state machine as the central business rule.

Implement POST /api/work-orders/{id}/status with a request containing targetStatus and optional note.

Allowed transitions:
- NEW -> CANCELLED
- ASSIGNED -> IN_PROGRESS
- ASSIGNED -> CANCELLED
- IN_PROGRESS -> ON_HOLD
- ON_HOLD -> IN_PROGRESS
- IN_PROGRESS -> COMPLETED
- COMPLETED -> IN_PROGRESS
- COMPLETED -> CLOSED

Assignment itself performs NEW -> ASSIGNED in the next task; the generic status endpoint must not be used to assign.

Actor rules:
- assigned technician: start, hold, resume, complete only on their assigned work order
- manager: reopen COMPLETED and close COMPLETED
- dispatcher or manager: cancel NEW or ASSIGNED
- no user can transition CLOSED or CANCELLED

Requirements:
- A dedicated state-machine/domain service owns the transition table and actor rules.
- Controllers do not contain lifecycle logic.
- Transition, work-order update, status-history insert, and side effects happen in one transaction.
- Every successful transition writes exactly one append-only history row.
- Invalid transition returns 409 with a stable error code and current/attempted status.
- Forbidden actor returns 403; missing order returns 404.
- Concurrent transitions cannot silently overwrite each other; use optimistic locking/versioning or another explicit strategy.
- Expose history in chronological order with actor-safe information.
- UI displays only currently available actions returned or derived from a server-authoritative transition capability response.

Write exhaustive parameterized tests for every allowed and disallowed state pair, every actor category, terminal states, technician ownership, history rows, and concurrency behavior.
```

Commit: `feat: enforce work order lifecycle and audit history`.

## 14. Prompt 10 — Day 13: dispatch, assignment, and technician field view

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement assignment/reassignment and the technician mobile workflow.

Back end:
- GET /api/users/technicians for dispatcher/manager selection.
- POST /api/work-orders/{id}/assign with technicianId and optional note.
- First assignment allowed only from NEW and performs NEW -> ASSIGNED atomically with history and notification.
- Reassignment allowed only while open; preserve current status unless the specification documents otherwise.
- Only dispatcher/manager can assign or reassign.
- Validate target user is an active technician.
- Create an in-app assignment notification atomically.
- Technician list/detail endpoints return only jobs assigned to the current technician.
- Technicians cannot access, mutate, or log against unassigned jobs by changing ids.

Front end:
- Dispatcher/manager assignment and reassignment dialog.
- Notification badge/list for the affected technician.
- Technician mobile-first page with assigned job cards and detail view.
- Start, hold, resume, and complete actions wired to the state endpoint.
- Clear server error feedback for stale/illegal actions.

Tests include first assignment transition, reassignment, inactive/non-technician target, notification creation, cross-technician access, and responsive UI checks.
```

Commit: `feat: add dispatch assignment and technician workflow`.

## 15. Prompt 11 — Day 14: transactional parts and time logging

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement part inventory, part usage, and time logging with strong integrity.

Part management:
- Manager-only create/edit/deactivate/list part endpoints.
- Unique SKU, non-negative stock, non-negative unit cost.

Part usage:
- POST /api/work-orders/{id}/parts with partId, quantity, and optional note.
- Assigned technician or manager may log usage on an eligible non-terminal job.
- Lock the selected part row or use another correct concurrency strategy.
- In one transaction: validate ownership/status, verify stock, decrement stock, insert PartUsage, and update/derive totals.
- Stock must never go negative, including concurrent requests.
- Capture unit cost at usage time so historical cost does not change when the part price changes.

Time logging:
- POST /api/work-orders/{id}/time with positive minutes and optional note.
- Assigned technician or manager only, with documented status rules.
- Calculate total labour minutes on the work-order response.

Front end:
- Manager inventory screen.
- Technician part/time forms on the job detail.
- Usage and time history plus roll-up totals.
- Proper loading, validation, conflict, and insufficient-stock messages.

Tests must prove rollback on failure, negative-stock prevention, concurrent stock safety, role/ownership checks, captured historical price, positive minutes, and correct totals.
```

Commit: `feat: add transactional parts and time tracking`.

## 16. Prompt 12 — Day 15: SLA tracking and in-app notifications

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: complete SLA calculation, scheduled breach checks, and in-app notifications.

Implement:
- Typed configuration properties for priority durations, risk window, and scheduler cron/fixed delay.
- SLA due date set at creation and recalculated only under a clearly documented allowed edit rule.
- Derived SLA status: ON_TRACK, AT_RISK, BREACHED, MET, or NOT_APPLICABLE as appropriate.
- Scheduled job scans only open orders in bounded batches.
- Create notifications when an order first enters AT_RISK and when it first becomes BREACHED; prevent duplicate notifications on each scheduler run.
- Managers see all relevant SLA alerts; assigned technician and dispatcher may receive role-appropriate alerts according to the documented decision.
- GET/PATCH notification endpoints to list and mark read.
- SLA badges on list, board, detail, and dashboard-ready API data.
- Structured logs for assignment, status change, at-risk, and breach events without secrets or token contents.

Use a controllable Clock in business logic so tests do not depend on wall-clock timing. Test all priority calculations, boundary times, scheduler idempotency, terminal orders, and notification recipients.

Complete docs/reviews/M3_REVIEW.md covering lifecycle, direct server-side authorisation, transactional parts/time, and SLA logic. M3 carries the most weight, so repair every failed requirement before continuing.
```

Commit: `feat: add sla monitoring and notifications`.

## 17. Prompt 13 — Days 16–17: dashboard and reporting

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: implement manager dashboard and reporting using current, filter-aware data.

Back end:
- GET /api/reports/summary, manager-only.
- Metrics: counts by status, open/overdue count, SLA compliance, at-risk and breached orders, total labour minutes, parts cost, and open workload by technician.
- At least one breakdown by technician and one by site.
- Date range, customer/site, technician, and priority filters where appropriate.
- Clearly define SLA compliance denominator and treatment of cancelled orders in docs.
- Use projections/aggregate queries and bounded result sets rather than loading all entities.

Front end:
- Dashboard cards for essential operational questions.
- Status distribution and SLA compliance charts.
- Workload-by-technician or site chart/table.
- Overdue/at-risk actionable list linking to work-order detail.
- Filters reflected in all metrics.
- Accessible chart labels and tabular fallback where practical.
- Loading skeletons, empty state, and error state.

Add aggregate-query and role-security tests. Verify displayed figures against deterministic seeded test data.
```

Commit: `feat: add manager dashboard and reporting`.

## 18. Prompt 14 — Day 18: customer portal, attachments, and responsive polish

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: complete the customer portal and proof-photo attachments.

Customer portal:
- Customer can create a request only for a site belonging to their organisation.
- Customer sees only their own organisation's work orders and safe status history.
- Exclude internal notes, stock details, unit costs, technician private data, and other customers' data.
- Provide status timeline, SLA/due information appropriate for the customer, and clear empty/error states.
- Add explicit tests for guessed ids and query-filter manipulation.

Attachments:
- Assigned technician and manager can upload proof images to eligible jobs.
- Validate allowed MIME types, actual content signature where practical, filename safety, and maximum size.
- Store metadata separately from bytes; never expose filesystem paths.
- Authorised users can list/download attachments; customer visibility must be explicitly documented and limited to safe proof images.
- Prevent arbitrary file upload and path traversal.

Polish:
- Ensure technician pages are usable at common phone widths.
- Keyboard-accessible actions and labelled inputs.
- Consistent status, priority, and SLA labels.
- No security decision depends on hidden buttons.
```

Commit: `feat: complete customer portal and work order attachments`.

## 19. Prompt 15 — Day 19: OpenAPI, integration tests, production packaging, and deployment

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: productionise the application and deploy it.

OpenAPI:
- Document every endpoint, role requirement, request/response DTO, pagination field, filter, status code, and error shape.
- Add bearer JWT security scheme and endpoint annotations/configuration.
- Ensure Swagger UI is reachable at the documented URL.
- Verify the generated contract against actual endpoint behavior.

Testing:
- Add high-value integration tests for login, cross-customer access, technician ownership, lifecycle transitions, terminal immutability, assignment, stock transactions, SLA scheduler idempotency, and manager reports.
- Run tests against PostgreSQL through Testcontainers where database behavior matters.
- Add frontend unit/component tests for auth guards, role navigation, lifecycle actions, and important forms.

Production:
- Multi-stage Docker builds.
- Production configuration entirely through environment variables.
- Health/readiness endpoints.
- Database migrations run safely on startup or as a documented release step.
- No seed credentials accidentally used as production secrets; demo deployment may intentionally include documented seed users.
- Deploy backend, frontend, and PostgreSQL to the selected host.
- Configure CORS, API base URL, TLS host URLs, and persistent storage.
- Add docs/DEPLOYMENT.md and a rollback/troubleshooting section.

Verify live API, live front end, login for all roles, Swagger UI, persistence across restart, and a complete work-order flow.
```

Commit: `chore: document test and deploy keystone`.

## 20. Prompt 16 — Day 20: final acceptance audit and demo package

```text
[PASTE THE REUSABLE TASK CONTRACT]

Task: perform final QA against every acceptance criterion and prepare the submission. Do not add optional features unless all required checks pass.

Create docs/reviews/FINAL_ACCEPTANCE.md with a traceability table containing:
- requirement/acceptance criterion
- implementation location
- automated test location
- manual verification step
- result
- evidence or screenshot filename

Audit:
- F1 authentication and four roles
- F2 customers and sites
- F3 work-order management
- F4 dispatch and assignment
- F5 technician field view
- F6 parts and time
- F7 SLA and notifications
- F8 dashboard and reporting
- F9 customer portal
- lifecycle and append-only history
- direct API security and cross-customer attacks
- consistent errors and pagination
- OpenAPI completeness
- clean checkout
- production deployment
- no secrets
- responsive technician UI

Run a clean-clone simulation in a new temporary directory using only README instructions. Run all backend tests, frontend tests, lint, production builds, Docker build, migration startup, and live smoke tests. Repair only verified defects.

Prepare:
- final README with overview, architecture, stack, setup, environment variables, migrations/seed, role credentials, test commands, API docs URL, deployment URLs, and screenshots
- docs/DEMO_SCRIPT.md for a 3–5 minute video covering login and one end-to-end lifecycle through all four roles
- docs/SUBMISSION_CHECKLIST.md with repository, live API, live front end, Swagger UI, seed logins, demo video placeholder, and submission form placeholder
- release notes and a version tag recommendation

At the end, clearly separate PASS, FAIL, and MANUAL-ONLY checks. Never mark an unverified item as passed.
```

Commit: `docs: finalize acceptance evidence and submission package`.

## 21. Final adversarial review prompt

Run this in a fresh Codex thread after all work is complete.

```text
Act as a hostile senior reviewer. Do not modify code initially.

Read the complete repository, AGENTS.md, project docs, tests, OpenAPI output, and deployment configuration. Try to disprove that this submission meets Project KEYSTONE.

Focus on:
- IDOR and cross-customer data leakage
- technician acting on unassigned jobs
- role checks existing only in the UI
- illegal or concurrent lifecycle transitions
- history rows that can be edited/deleted or are missing
- closed/cancelled work orders still mutable
- stock races and partial transactions
- negative stock
- incorrect SLA boundary calculations and duplicate alerts
- JWT expiry/tampering/secret handling
- entity exposure and recursion
- unbounded queries and N+1 behavior
- unsafe attachment upload
- OpenAPI drift
- clean-checkout failures
- deployment-only configuration mistakes
- seed data or credentials that break startup
- missing acceptance criteria

Produce docs/reviews/ADVERSARIAL_REVIEW.md with severity, reproduction steps, evidence, and exact suggested fix. Do not invent findings. After presenting the report, wait for approval before editing.
```

Then fix findings one at a time, starting with critical security and data-integrity issues.

## 22. Recovery prompts

### Build is failing

```text
Inspect the current failing command and its complete output. Reproduce it before editing. Identify the first root-cause error rather than downstream errors. Make the smallest correct fix, preserving architecture and behavior. Run the original command again, then the nearest affected test suite and production build. Do not disable checks, delete tests, loosen types, or replace real logic with mocks. Report root cause, patch, and evidence.
```

### Codex changed too much

```text
Review the current git diff. Separate changes required for the active task from unrelated changes. Revert only the unrelated edits without discarding valid user work. Keep the smallest cohesive patch. Re-run affected tests and report exactly what was removed and why.
```

### Backend and frontend disagree

```text
Treat docs/API_CONTRACT.md and the running OpenAPI document as the contract. Compare backend DTOs/status codes with frontend types and API calls. List every mismatch before editing. Correct the side that violates the approved contract; do not silently change both. Add a regression test for each mismatch and run backend and frontend builds.
```

### Security review

```text
For the active endpoint, test access as unauthenticated, every wrong role, the correct role, a customer from another organisation, and a technician not assigned to the work order. Verify both collection queries and direct-by-id access. Fix checks in the service/server layer, not only the UI. Add regression tests and show 401/403/404 behavior.
```

### Migration failure

```text
Start from a brand-new PostgreSQL database and reproduce the Flyway failure. Never edit an already-released migration that may have been applied; create a new migration unless the project is still before the first shared/deployed baseline. Verify migrate, validate, seed, Hibernate schema validation, and application startup. Document the migration decision.
```

## 23. Daily operating procedure

For each prompt:

1. Pull the latest `main` and ensure the working tree is clean.
2. Create one branch, for example `feat/m2-work-order-crud`.
3. Paste the reusable task contract and exactly one task prompt.
4. Approve Codex's plan only when it matches the active milestone.
5. Review the diff before accepting completion.
6. Run the commands yourself when practical.
7. Test one happy path and at least one forbidden/invalid path manually.
8. Commit only after green checks.
9. Merge, pull, and start the next prompt from the updated repository.
10. Keep deployment working from Week 1 onward; do not postpone all deployment until Day 19.

## 24. Minimum command gate

Codex should adapt commands to the generated repository, but the final project should provide equivalents of:

```bash
# infrastructure
docker compose config
docker compose up -d db

# backend
cd backend
./mvnw clean verify
./mvnw spring-boot:run

# frontend
cd frontend
npm ci
npm run lint
npm test -- --run
npm run build

# repository
git diff --check
git status --short
```

For Windows PowerShell, use `mvnw.cmd` where required.

## 25. Scope protection

Do not allow Codex to add these before all required features pass:

- payment or invoicing engine
- GPS or live tracking
- route optimisation
- automatic scheduling
- native mobile app
- third-party ERP integration
- microservices split
- Kafka or event streaming
- OAuth2 social login
- multi-tenant platform redesign beyond the required customer isolation
- elaborate design-system work that delays security, lifecycle, transactions, tests, or deployment

The highest-value work is the lifecycle, server-side access control, transactional integrity, SLA behavior, clean deployment, and reproducible submission.
