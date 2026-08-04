# Project KEYSTONE Product and Engineering Specification

**Status:** Planning baseline

**Product:** Field-service management platform for Meridian Facilities Management

**Delivery model:** Four-role web application and API
**Implementation status:** Day 1 runnable foundation implemented; no product/business API is implemented

## 1. How to Read This Specification

This document separates three kinds of statements:

- **Brief requirement:** explicitly required by the project brief supplied in the task.
- **Locked project decision:** already selected in the repository build playbook to remove implementation drift.
- **Working assumption:** a reversible implementation interpretation recorded in `ASSUMPTIONS.md`; it is not a new product requirement.

Unresolved product or policy questions are not resolved here. They are collected in `ASSUMPTIONS.md` and repeated in the final consistency review.

Supporting contracts:

- Authorization: `ACCESS_MATRIX.md`
- Lifecycle: `STATE_MACHINE.md`
- HTTP API: `API_CONTRACT.md`
- Data model: `ERD.md`
- Verification: `TEST_MATRIX.md`
- Architecture choices and index: `DECISIONS/README.md`

## 2. Product Objective

KEYSTONE will give Meridian one governed system for receiving service requests, dispatching technicians, recording field execution, controlling inventory usage, monitoring SLA exposure, closing work, and exposing a safe customer view of service history.

The product is a single Meridian-operated system. Customer organisations are security boundaries for customer users; this specification does not introduce a general-purpose multi-tenant SaaS model.

## 3. Explicit Brief Requirements

### 3.1 Required roles

| Role | Required capability |
|---|---|
| `DISPATCHER` | Create customers, sites, and work orders; assign and reassign technicians; see all Meridian-managed open work and the board. |
| `TECHNICIAN` | See only assigned work; start, hold, resume, and complete assigned jobs; log parts and time; upload proof photos. |
| `MANAGER` | All dispatcher capabilities, plus close/reopen jobs, manage users and parts, use the dashboard and reports, and perform the defined administrative actions. |
| `CUSTOMER` | Raise requests for sites belonging to their organisation; see only their organisation's work-order status and history; never receive internal fields. |

No fifth role is required.

### 3.2 Core domain

The required aggregate path is:

```text
Customer -> Sites -> WorkOrders
```

A work order has:

- a customer and a site;
- an optional assignee before first assignment;
- a governed current status and append-only status history;
- zero or more part usages and time logs;
- SLA information;
- zero or more notifications associated with relevant users/work orders; and
- optional proof-photo attachments.

The site on a work order must belong to the same customer as the work order.

### 3.3 Required lifecycle

The only lifecycle transitions are:

```text
NEW -> ASSIGNED
NEW -> CANCELLED
ASSIGNED -> IN_PROGRESS
ASSIGNED -> CANCELLED
IN_PROGRESS -> ON_HOLD
ON_HOLD -> IN_PROGRESS
IN_PROGRESS -> COMPLETED
COMPLETED -> IN_PROGRESS  (manager reopen)
COMPLETED -> CLOSED
```

The diagram above is illustrative; `STATE_MACHINE.md` is the exact transition matrix. `CLOSED` and `CANCELLED` are terminal.

### 3.4 Required technology

- Java 21 and Spring Boot 3
- Spring Security with stateless JWT authentication
- Spring Data JPA
- PostgreSQL
- Flyway
- React, TypeScript, and Vite
- OpenAPI/Swagger
- Docker and a deployed system

## 4. Locked Project Decisions

The supplied build playbook fixes these choices before implementation:

- Use a monorepo with backend and frontend applications.
- Use Maven Wrapper, Spring Web, Bean Validation, Spring Data JPA, Spring Security, PostgreSQL, Flyway, and springdoc-openapi on the backend.
- Use React Router, TanStack Query, Axios, React Hook Form, Zod, Material UI, and Recharts on the frontend.
- Use Docker Compose with PostgreSQL for local infrastructure.
- Let Flyway alone manage the schema and configure Hibernate with `ddl-auto=validate`.
- Use a layered modular monolith: thin controllers, services for business logic/transactions, repositories for persistence, and DTOs at every API boundary.
- Use bearer access JWTs, stateless sessions, and no OAuth2 login.
- Use in-app notifications rather than an external email provider.
- Create one append-only status-history row per successful transition and an initial `null -> NEW` row at creation.
- Avoid Lombok unless separately approved.

### 4.1 Recommended working assumptions

The playbook recommends initial SLA defaults of 4 hours for `CRITICAL`, 24 hours for `HIGH`, 48 hours for `MEDIUM`, and 72 hours for `LOW`, with a two-hour at-risk window. These are configurable working assumptions, not contractual brief values; Q-022 must approve or replace them before due-time behavior is accepted.

The playbook also recommends PostgreSQL storage for small proof-image bytes with metadata separated from content. The brief requires optional proof photos but does not select their storage backend, so this remains A-027 rather than an accepted architecture decision. Q-023 must approve or replace it before attachment-content schema or implementation is created; Q-024 governs formats, limits, processing, deletion, and retention.

## 5. Functional Scope

### F1. Authentication and identity

- Authenticate all product users by email/password and issue an expiring stateless access JWT.
- Expose the current user's safe identity and role.
- Enforce all authorization on the API even when a user bypasses the UI.
- Permit public access only to deliberately configured health/API-documentation endpoints and login. Customer request submission is authenticated.

### F2. Users and technicians

- Managers manage users and their active state within the four-role model.
- Dispatchers and managers can retrieve active technicians for assignment.
- A `CUSTOMER` user is associated with exactly one customer organisation; internal roles are not.
- A technician cannot obtain or mutate another technician's assigned work by altering an identifier or filter.

Exact user-administration operations remain a human policy question.

### F3. Customers and sites

- Dispatchers and managers create, read, and edit customers and sites.
- Managers may deactivate records without deleting historical relationships.
- Customer users may read only their own organisation and its sites through customer-safe responses.
- Lists support server-side search, filtering, sorting, and bounded pagination.

### F4. Work-order intake and visibility

- Dispatchers and managers create work orders for a valid customer/site pair.
- Authenticated customer users raise requests only for their organisation's sites.
- Work-order creation sets status `NEW`, calculates an SLA due time from the configured policy, and appends the initial history row.
- All list, board, detail, edit, history, and nested-resource access is role scoped before data is returned.
- The default internal board contains `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`; terminal work is available through explicit history/filter views.
- Customer responses omit internal notes, inventory/cost data, private user data, and all data belonging to other organisations.

### F5. Assignment and dispatch

- A dispatcher or manager first-assigns an active technician to a `NEW` work order.
- First assignment atomically sets the assignee, transitions `NEW -> ASSIGNED`, appends history, and creates the assignment notification.
- A dispatcher or manager may reassign eligible open work without changing its current status.
- Assignment and reassignment must reject inactive users and users who are not technicians.

The boundary of eligible reassignment for `COMPLETED` work requires confirmation; see `ASSUMPTIONS.md`.

### F6. Governed field execution

- Only the assigned technician can start, hold, resume, or complete a work order.
- Only a manager can reopen `COMPLETED -> IN_PROGRESS` or close `COMPLETED -> CLOSED`.
- A dispatcher or manager can cancel only `NEW` or `ASSIGNED` work.
- Illegal transitions return HTTP `409`; a forbidden actor returns `403`; missing or deliberately concealed resources return `404`.
- Status mutation, history insertion, and required side effects are atomic and concurrency safe.

### F7. Parts, time, and proof

- Managers create, update, list, and deactivate parts.
- Assigned technicians and managers may log part usage on an eligible non-terminal work order.
- Stock verification, decrement, and usage creation happen in one transaction; stock can never become negative, including under concurrency.
- Part usage captures the unit cost at the time of use.
- Assigned technicians and managers may log positive time minutes under the approved status policy.
- Assigned technicians and managers upload validated proof images to eligible work orders.

Exact eligible statuses, attachment storage, limits/types, and whether customers see proof images remain unresolved policy questions.

### F8. SLA and notifications

- Store the SLA due time calculated at work-order creation and derive a displayable SLA condition.
- Scan open work in bounded scheduled batches for at-risk and breached conditions.
- Create idempotent in-app notifications when an order first becomes at risk or breached.
- Create an in-app notification for assignment.
- Allow a user to list their notifications and mark them read.
- Show SLA information in authorized lists, board cards, details, and manager reporting.

Business-hours calendars, hold behavior, completion/closure semantics, and final alert recipients are unresolved.

### F9. Dashboard and reporting

- Managers alone receive operational dashboard/report data.
- Required measures include counts by status, open and overdue counts, SLA compliance, at-risk/breached work, total labour minutes, parts cost, and open workload by technician.
- Include at least one technician breakdown and one site breakdown.
- Apply relevant date-range, customer/site, technician, and priority filters consistently to all returned measures.
- Use bounded aggregate queries rather than loading all records.

The reporting clock, date basis, and SLA-compliance denominator require human approval.

## 6. Data Integrity and Audit Requirements

- Customer, site, work order, assignee, and nested records must remain referentially valid.
- User email and part SKU are normalized/unique according to the data contract.
- Work-order codes are unique and human readable.
- Stock and usage quantities are non-negative at rest; a logged usage quantity is positive.
- Time-log minutes are positive.
- Historical part cost does not change when a part's current cost changes.
- Status history is append-only and chronologically retrievable.
- Every work-order mutation uses explicit transaction boundaries and a concurrency strategy.
- Historical parent records are deactivated rather than destructively deleted where deletion would break auditability.
- Flyway creates every table, index, foreign key, unique key, and check constraint; Hibernate validates the result.

## 7. Security and Privacy Requirements

- Default deny every route not explicitly allowed in `ACCESS_MATRIX.md`.
- Authenticate bearer tokens by signature and expiry; reject missing, malformed, tampered, and expired tokens with `401`.
- Return `403` for an authenticated principal that lacks the route/action permission.
- Scope customer collections and direct lookups to the authenticated customer's organisation.
- Scope technician collections and ownership-sensitive direct lookups to the authenticated technician's assignments.
- Use `404` for scoped resources that are absent from the caller's authorized view so identifiers cannot be used as an existence oracle.
- Validate authorization again in the service layer before mutating a work order or nested resource.
- Use separate internal and customer-safe DTOs. Serialization must never be the only field-level security control.
- Never expose password hashes, secrets, tokens, internal notes to customers, attachment bytes in list payloads, filesystem paths, private technician details, stock balances, or unit costs to customer users.
- Upload handling must enforce a configured size limit, safe filename treatment, permitted image MIME types, and content-signature validation where practical.
- Logs and errors must not disclose secrets or sensitive payloads.

## 8. API and User-Experience Requirements

- Serve a conventional versioned JSON REST API, except binary attachment download and multipart upload.
- Publish OpenAPI with bearer security, role requirements, schemas, filters, pagination, errors, and status codes matching the implementation.
- Use consistent validation and Problem Details-style errors with stable application codes.
- Keep filters in frontend URLs and perform search/filter/sort/page operations on the server.
- Provide useful loading, empty, validation, unauthorized, forbidden, conflict, and unexpected-error states.
- Make the technician workflow usable at common phone widths.
- Use accessible labelled inputs, keyboard-operable actions, chart labels, and tabular report fallbacks where practical.
- Derive visible actions from server-authoritative capabilities; a hidden button never grants or removes permission.

## 9. Non-Functional Requirements

- A clean checkout must build and start from documented instructions.
- Local PostgreSQL starts through Docker Compose with a health check.
- Production configuration is supplied through environment variables; no secrets are committed.
- Production images use repeatable builds, health/readiness checks, persistent PostgreSQL storage, and documented Flyway/rollback procedures.
- Database-specific behavior is verified against PostgreSQL, preferably with Testcontainers.
- Common list/detail queries avoid unbounded results and material N+1 regressions.
- Time-sensitive behavior uses a controllable clock.
- Direct API security, state transitions, transactions, migrations, frontend guards, and deployment smoke paths are automated as specified in `TEST_MATRIX.md`.

## 10. Explicitly Out of Scope

- Invoicing or payments
- GPS or live technician tracking
- Route optimisation
- Automated scheduling
- Native mobile applications
- ERP integrations
- A microservices split or Kafka/event streaming
- OAuth2/social login
- A multi-tenant platform redesign beyond required customer-organisation isolation
- Optional design-system work that delays security, lifecycle, transaction, test, or deployment requirements

## 11. Delivery and Acceptance

Delivery follows `TASKS.md`:

- **M1, days 1-5:** runnable foundation, schema/domain, authentication, and server-side RBAC.
- **M2, days 6-10:** customers/sites, role-scoped work-order CRUD, board, and list quality.
- **M3, days 11-15:** lifecycle, assignment/field workflow, parts/time integrity, SLA, and notifications.
- **M4, days 16-20:** dashboard/reporting, customer portal/attachments, OpenAPI, deployment, and final acceptance evidence.

A milestone is complete only when its acceptance gate is green and evidence is recorded. Planning checkboxes do not imply implementation success.

## 12. Specification-Consistency Review

### Confirmed alignment

- The prompt, playbook, role model, lifecycle, access model, API plan, ERD, and test plan all use exactly four roles.
- `NEW -> ASSIGNED` is an assignment command, not a generic status update.
- The lifecycle contains only the nine supplied transitions; `CLOSED` and `CANCELLED` are terminal everywhere.
- The customer boundary and technician ownership rules apply to collection queries, direct identifiers, and nested mutations.
- The database model and API both preserve append-only status history and atomic stock usage.
- The selected stack and modular-monolith architecture are consistent across the ADRs and delivery plan.
- Proof attachments are optional and can never become an implicit completion precondition.
- Unresolved visibility/eligibility questions use explicit default-deny behavior rather than provisional access grants.
- The current human correction supersedes the reference playbook's obsolete Prompt 0 ADR filenames. The numbered ADR index is authoritative; SLA and attachment choices remain in `ASSUMPTIONS.md`.

### No unresolved product contradiction found

The product requirements do not directly contradict one another. The ADR filename/ownership mismatch between the older playbook and the current correction is resolved by the newer human instruction and does not change product behavior. Two product terms still need careful interpretation but can coexist:

- "All open work" includes `COMPLETED` work awaiting manager closure for board purposes; terminal work is `CLOSED` or `CANCELLED`.
- A manager has dispatcher capabilities, but technician-only lifecycle actions remain assigned-technician actions unless a human explicitly broadens them.

### Gaps requiring a human decision

This is the product-level summary. `ASSUMPTIONS.md` is the exhaustive unresolved-decision register. Accepted ADRs link back to that register instead of embedding separate product-policy decisions.

1. Approval/replacement of the recommended SLA durations/warning window, SLA calendar and timezone, whether `ON_HOLD` pauses the clock, exact threshold equality, what event measures attainment, and whether priority edits recalculate the due time.
2. SLA-compliance reporting denominator and treatment of cancelled or reopened work.
3. Whether customers select priority or receive a server default when raising a request.
4. Exact fields that customers may see in status history, including actor identity and notes.
5. Whether customers may list/download proof photos; the approved content-storage backend; file types, size/count limits, processing controls, deletion/replacement, and retention policy.
6. Exact work-order statuses eligible for part usage, time logging, and proof-photo upload.
7. Whether a `COMPLETED` work order can be reassigned before closure; until approved, completed-work reassignment is denied.
8. The exact manager set and any dispatcher/technician recipients for SLA alerts, plus whether an outgoing technician is notified on reassignment.
9. Exact manager user-administration actions, password recovery/reset flow, token lifetime/signing-key policy, and whether refresh tokens exist.
10. Editable work-order fields by status and customer role, including effects on audit/SLA data.
11. Customer/site/user/part field requirements, currency, and any data-retention obligations.
12. Deployment provider, domains, TLS ownership, backups, observability destination, and recovery objectives.
13. Whether dispatchers can read terminal work and nested parts/time/proof records beyond their explicit open-work view; current access is denied outside open work.
14. Whether technicians/dispatchers receive a safe active-part lookup needed to select part identifiers without gaining part-management access; current catalogue access is manager-only.
15. Whether assignment/reassignment needs its own append-only audit record and how a same-technician assignment behaves.
16. Deactivation effects for active customers/sites/users/technicians/parts, including self-deactivation and existing assignments.
17. Whether demo seed users/data are allowed in the deployed demonstration and how non-production credentials are distributed.
18. SLA scheduler frequency, batch/downtime recovery behavior, and the exact manager alert recipient set.
19. Attachment content storage, count/aggregate size, deletion/replacement, metadata stripping/scanning/pixel limits, retention, backup limits, and any future storage-migration threshold.
20. Browser token storage, account recovery/lockout, active-user token freshness, CORS origins, and production OpenAPI exposure.
21. Notification pagination/unread-count semantics, read-state reversibility, wording/localization, refresh mechanism, and refresh interval.

Until these are approved, they remain open questions in `ASSUMPTIONS.md`; implementation must not silently convert them into product features.
