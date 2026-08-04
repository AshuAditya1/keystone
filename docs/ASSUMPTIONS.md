# Project KEYSTONE Assumptions and Open Questions

## 1. Purpose

This register prevents implementation choices from being mistaken for product requirements. It records:

- decisions explicitly fixed by the brief or build playbook;
- reversible working assumptions needed to make planning artifacts precise; and
- unresolved questions that require a human decision before the affected behavior is implemented.

Status meanings:

- **LOCKED:** supplied decision; implementation must follow it.
- **WORKING:** proposed implementation default; may be changed without redefining the product.
- **OPEN:** no decision has been supplied; do not silently resolve it.

## 2. Supplied and Locked Decisions

| ID | Status | Decision | Source/rationale |
|---|---|---|---|
| D-001 | LOCKED | Roles are exactly dispatcher, technician, manager, and customer; there is no separate admin role. | Brief and build playbook. |
| D-002 | LOCKED | Use the lifecycle and actor rules in `STATE_MACHINE.md`; `CLOSED` and `CANCELLED` are terminal. | Brief and build playbook. |
| D-003 | LOCKED | Every successful transition writes exactly one append-only history record; creation writes `null -> NEW`. | Build playbook. |
| D-004 | LOCKED | Use Java 21/Spring Boot 3, stateless JWT security, JPA, PostgreSQL, Flyway, React/TypeScript/Vite, OpenAPI, Docker, and deployment. | Brief. |
| D-005 | LOCKED | Use a modular monolith with thin controllers, service-owned logic/transactions, repositories, and DTOs at HTTP boundaries. | Build playbook and ADRs 0001/0003. |
| D-006 | LOCKED | Flyway alone manages schema; Hibernate uses `ddl-auto=validate`. | Build playbook and ADR 0004. |
| D-007 | LOCKED | Access JWT is sent as `Authorization: Bearer <token>`; no server session and no OAuth2 login. | Build playbook and ADR 0002. |
| D-008 | LOCKED | Notifications are in-app. | Build playbook and ADR 0005. |
| D-011 | LOCKED | First assignment of `NEW` work atomically assigns an active technician, moves it to `ASSIGNED`, appends history, and notifies the incoming technician. Reassignment preserves status, writes no fake status-history row, and notifies the incoming technician. | Supplied build playbook assignment task. |
| D-012 | LOCKED | Manager manages parts; assigned technician or manager can log part usage/time and upload proof under the final eligible-status policy. | Supplied build playbook days 14 and 18. |
| D-013 | LOCKED | Do not add invoicing/payments, GPS, route optimisation, automated scheduling, native mobile apps, ERP integrations, or other scope-protection exclusions in `PROJECT_SPEC.md`. | Build playbook. |
| D-014 | LOCKED | Proof-photo attachments are optional; completion does not require a photo unless a future approved brief changes scope. | Explicit core-domain requirement. |
| D-015 | LOCKED | The ADR directory contains only its README plus 0001 monorepo, 0002 stateless JWT, 0003 service-layer state machine, 0004 Flyway-only schema management, and 0005 in-app notifications. SLA and attachment policy are not separate ADRs. | Explicit human correction to the Prompt 0 output; supersedes the older playbook filename list. |

## 3. Working Implementation Assumptions

These choices make the API, ERD, and tests concrete. They are not unrelated product features.

| ID | Status | Working assumption | Consequence / revisit trigger |
|---|---|---|---|
| A-001 | WORKING | Persisted identifiers are UUIDs and are represented as canonical UUID strings in JSON. | Revisit before the first migration if Meridian requires numeric IDs. Public work-order codes remain human readable. |
| A-002 | WORKING | HTTP routes are rooted at `/api/v1`; Swagger/OpenAPI documents that version. | A different versioning strategy requires coordinated backend/frontend/docs changes. |
| A-003 | WORKING | Timestamps are stored as timezone-aware UTC instants and serialized as ISO-8601 UTC strings. | Display converts to the configured business/user timezone once Q-002 is decided. |
| A-004 | WORKING | List defaults are `page=0`, `size=20`, with maximum `size=100`; repeated sort values use `field,asc` or `field,desc`. | Defined precisely in `API_CONTRACT.md`; server appends a unique-ID tie-breaker. |
| A-005 | WORKING | Database/API field names use `camelCase` in JSON and `snake_case` in PostgreSQL. | Keeps Java/TypeScript conventional without changing domain meaning. |
| A-006 | WORKING | Customer, site, user, and part records with historical references are deactivated, not hard deleted. | No DELETE route is planned for these resources. |
| A-007 | WORKING | "Open work" for the board means every non-terminal status: `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`. Reassignment eligibility is separately unresolved in Q-007. | Matches the supplied board definition without silently equating board visibility with reassignment eligibility. |
| A-008 | WORKING | Work orders carry an optimistic-lock version; stale mutations return `409 WORK_ORDER_VERSION_CONFLICT`. | Exact JPA implementation is deferred. |
| A-009 | WORKING | Part stock rows are locked for usage mutation (or use an equivalent atomic database strategy) inside one transaction. | Tests must prove rollback and concurrent non-negative stock. |
| A-010 | WORKING | An authenticated caller using an ID outside their customer/technician/self scope receives `404 RESOURCE_NOT_FOUND`; an authenticated caller who can see the resource but lacks the route/action receives `403 ACCESS_DENIED`. | Reduces IDOR existence leakage while preserving useful route-level errors. Coarse route denial occurs before resource lookup. |
| A-011 | WORKING | Error payloads follow RFC 9457 Problem Details semantics with stable KEYSTONE codes and a correlation/trace identifier. | Exact fields are fixed in `API_CONTRACT.md`. |
| A-012 | WORKING | Emails are trimmed and lower-cased for comparison/storage; uniqueness is case-insensitive. | The database must enforce the same normalization invariant. |
| A-013 | WORKING | Work-order codes follow a concurrency-safe shape such as `WO-YYYYMM-000001`; clients never create them. | Exact sequence mechanism is selected with the first schema migration. |
| A-014 | WORKING | Customer status history contains public status and timestamp; internal notes and private actor details are withheld until Q-004 is approved. | Safe minimum consistent with the brief. |
| A-015 | WORKING | Customers cannot access attachment list/download routes until Q-005 explicitly permits a safe proof-image view. | Deny by default does not remove a required brief capability. |
| A-016 | WORKING | Attachment list responses return metadata only; bytes require a separately authorized download route. | Avoids accidental large payloads and data leakage. |
| A-017 | WORKING | Money uses fixed-precision decimal values and part usage snapshots the then-current unit cost. | Currency is still OPEN in Q-011. |
| A-018 | WORKING | Initial creation and lifecycle events use the authenticated user as actor and server time as event time. | Backdated lifecycle history is not offered. |
| A-019 | WORKING | No destructive API exists for status history, part usage, or time logs during the required scope. | Correction/reversal policies, if later required, need explicit audit design. |
| A-020 | WORKING | Free-text search is trimmed, case-insensitive, bounded in length, and applied only to documented fields. | Avoids an unbounded generic query language. |
| A-021 | WORKING | A manager inherits dispatcher route capabilities, but not assigned-technician-only lifecycle actions. | Matches the role wording and supplied actor table. |
| A-022 | WORKING | Customer-provided fields outside the customer request DTO are rejected as validation errors rather than silently accepted. | Prevents mass assignment of internal fields. |
| A-023 | WORKING | Dispatcher work-order reads default to non-terminal/open work; manager reads include terminal history. | This is the least-privilege interpretation until Q-019 approves broader dispatcher history. |
| A-024 | WORKING | Part catalogue list/detail remains manager-only until Q-020 approves a separate safe lookup for technicians/dispatchers. | A technician part-entry UI is blocked on that decision; raw identifiers are never treated as authorization. |
| A-025 | WORKING | Dispatcher access to nested part usage, time logs, and proof attachments is denied until Q-019 approves a read-only operational view. | Prevents an assumption from becoming a binding data grant. |
| A-026 | WORKING | Initial configurable SLA defaults are critical 4h, high 24h, medium 48h, low 72h, with a 2h at-risk window. | The playbook recommends these as a project assumption, not a contractual brief value; Q-022 can approve or replace them. |
| A-027 | WORKING | For planning only, the playbook recommends storing small proof-image bytes in PostgreSQL with metadata separated from content. | The brief requires optional proof photos but does not select the storage backend. This is not a locked ADR; Q-023 must approve or replace it before the attachment-content migration or implementation. |
| A-028 | WORKING | Attachments are proof images rather than arbitrary files; upload handling uses configurable size/type allow-lists, signature/content checks where practical, safe display filenames, and no exposed storage paths. | Exact formats, limits, image-processing controls, and retention remain Q-024. |
| A-029 | WORKING | Attachment collection responses contain safe metadata only and content uses a separately authorized download operation. If PostgreSQL content storage is approved, metadata and bytes commit or roll back together. | Prevents binary loading/leakage while keeping storage selection explicitly conditional on Q-023. |
| A-030 | WORKING | After Q-001/Q-002/Q-022 are approved, a work order persists an SLA due instant at creation. Later configuration changes affect new orders only; an existing due instant is not recalculated without an approved audited rule. | Preserves the original commitment while leaving edit/reopen behavior open in Q-003. |
| A-031 | WORKING | The backend, using a controllable clock, is authoritative for derived SLA condition across list, board, detail, notifications, and reports. Scheduled scans are bounded and threshold notification creation is idempotent. | Keeps views and tests consistent; cadence/recovery and final boundary rules remain Q-015/Q-003. |
| A-032 | WORKING | The proposed SLA display vocabulary is `ON_TRACK`, `AT_RISK`, `BREACHED`, `MET`, and `NOT_APPLICABLE`. | `MET`/`NOT_APPLICABLE`, exact equality, cancellation, completion, hold, and reopen semantics are not approved until Q-003/Q-012 are resolved. |

## 4. Open Questions Requiring Human Decisions

No answer in this table should be inferred from example UI text or a DTO name.

| ID | Status | Decision needed | Why it matters / safe interim position |
|---|---|---|---|
| Q-001 | OPEN | Can a customer choose priority when raising a request, or does the server apply a fixed/default priority? | Changes the customer create DTO and SLA due time. Do not expose priority input until decided. |
| Q-002 | OPEN | Which business timezone and calendar drive SLA: elapsed hours or business hours, with what working days/holidays? | The proposed durations alone do not define due-time arithmetic. |
| Q-003 | OPEN | What are exact SLA threshold-equality rules; does `ON_HOLD` pause time; does completion or closure determine attainment; how are completed-but-open, cancellation, late work, reopen, and priority edits classified; and may any event recalculate/extend `dueAt`? | Required for deterministic SLA status, notification, audit, and report behavior. Keep the original due instant and do not add pause/recalculation logic until approved. |
| Q-004 | OPEN | Which status-history fields may a customer see: actor name/role, transition note, reopen/cancel reasons? | Internal notes/private technician data are forbidden. Interim DTO exposes only public status/timestamp. |
| Q-005 | OPEN | May customers list/download proof photos, and if so which images are customer-visible and how is that visibility selected? | Interim access is denied to customers so internal proof cannot leak. Technical format/limit/storage questions are separated into Q-023/Q-024. |
| Q-006 | OPEN | In which non-terminal statuses may parts, time, and proof photos be logged? Are backdated time entries permitted? | Route authorization is known, but lifecycle eligibility is not. Do not implement these mutations before approval. |
| Q-007 | OPEN | Is reassignment allowed while `COMPLETED` but not yet `CLOSED`? | The playbook says reassign open work and defines completed as open-board work; operational intent is unclear. |
| Q-008 | OPEN | Which managers, dispatchers, and technicians receive at-risk/breach notifications, and should the outgoing technician receive a reassignment notice? | Required for recipient selection and deduplication keys. Incoming-assignee notification is supplied; the rest is not. |
| Q-009 | OPEN | For required manager user management, are role changes, customer reassociation, self-deactivation, password reset, or credential invitation required beyond baseline list/create/read/edit/deactivate/reactivate? | No recovery/invitation or high-risk identity change should be invented. Initial credential delivery must be approved before user creation is usable. |
| Q-010 | OPEN | Which work-order fields can each role edit at each status, and must changes to priority/site/customer/title be audited? | Generic update of status/assignee is forbidden, but ordinary edit policy is not supplied. |
| Q-011 | OPEN | What required fields and validation apply to customer/site/user/part/work-order records, and what currency represents unit cost? | ERD fields are a minimum planning shape, not a signed-off data dictionary. |
| Q-012 | OPEN | What is the report date basis/timezone and exact SLA-compliance denominator, especially for cancellations and reopened work? | Required before report figures can be accepted. |
| Q-013 | OPEN | What are access-token lifetime, issuer/audience, signing algorithm/key rotation, logout expectations, and refresh-token policy? | The supplied decision fixes stateless access JWTs, not operational token policy. |
| Q-014 | OPEN | What should happen to active assignments and customer access when a user/site/customer is deactivated? | Affects mutation validation and operational recovery. Historical visibility must remain intact. |
| Q-015 | OPEN | What SLA scheduler frequency, bounded batch size, acceptable alert delay, and downtime catch-up/recovery behavior are required? | Needed before scheduler service-level and operational acceptance tests can be finalized. |
| Q-016 | OPEN | Which deployment provider, regions, hostnames, TLS/DNS ownership, backup schedule, observability destination, and RPO/RTO apply? | Required before day-19 production deployment and recovery documentation. |
| Q-017 | OPEN | What retention/privacy rules apply to status history, time logs, costs, notifications, and attachments? | These records currently have no deletion lifecycle in scope. |
| Q-018 | OPEN | Are demo seed users/data permitted in the deployed demonstration environment, and how are credentials distributed? | Production must never inherit accidental seed secrets. |
| Q-019 | OPEN | Should dispatchers read terminal work and nested part/time/proof records, or only open-work summaries? | Current policy defaults to open-work reads and denies nested/terminal visibility until approved. |
| Q-020 | OPEN | May technicians and dispatchers query a safe active-part catalogue to obtain part identifiers for usage, despite manager-only part management? | Without a safe lookup, the technician part-entry form has no specified discovery mechanism. |
| Q-021 | OPEN | Is a separate append-only assignment/reassignment audit required, and is assigning the current technician an idempotent success or a conflict? | Reassignment preserves status, so fake same-state status-history rows are forbidden. |
| Q-022 | OPEN | Approve or replace the recommended configurable SLA defaults (4h/24h/48h/72h and 2h warning window). | These values are a working project assumption rather than an explicit contractual duration. |
| Q-023 | OPEN | Which attachment-content storage backend is approved? The playbook recommends PostgreSQL for small proof images, but the brief does not require PostgreSQL binary storage. | Must be decided before the first attachment-content migration. Do not treat A-027 as accepted architecture. |
| Q-024 | OPEN | What image formats, per-file/count/aggregate limits, filename/metadata normalization, EXIF/GPS stripping, pixel/decompression bounds, malware scanning, deletion/replacement, retention, backup limits, and future storage-migration threshold apply? | Required for a safe, testable proof-photo implementation without inventing a general file service. |
| Q-025 | OPEN | What notification pagination/unread-count semantics, read-state reversibility, wording/localization, refresh mechanism, and refresh interval are required? | The in-app channel is fixed by ADR 0005; these user-experience and retention-adjacent details are not. |

## 5. Decision Process

When a human resolves an OPEN item:

1. Add the decision, approver, and date to this file.
2. Create or supersede an ADR if the choice has architectural consequences.
3. Update `PROJECT_SPEC.md`, `ACCESS_MATRIX.md`, `STATE_MACHINE.md`, `API_CONTRACT.md`, `ERD.md`, and `TEST_MATRIX.md` where affected.
4. Add/update the implementation task in `TASKS.md`.
5. Do not rewrite an already-applied Flyway migration; add a new migration once migrations are shared/deployed.
