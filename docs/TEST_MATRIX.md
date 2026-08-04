# Project KEYSTONE Test Matrix

- **Status:** Planning baseline; no application tests have been executed yet
- **Last updated:** 2026-08-04
- **Purpose:** Trace each security, lifecycle, integrity, migration, client, contract, and deployment obligation to an objective automated or manual check.

## 1. Test principles and traceability

This document defines acceptance tests; it does not claim that any test currently passes. Test implementation must use the final routes and error codes in `docs/API_CONTRACT.md`, permissions in `docs/ACCESS_MATRIX.md`, lifecycle rules in `docs/STATE_MACHINE.md`, constraints in `docs/ERD.md`, and decisions in the ADRs.

### Requirement tags

| Tag | Requirement source and acceptance statement |
|---|---|
| `R-AUTH` | Brief, access matrix, ADR 0002: stateless bearer access JWT; four roles; invalid authentication is rejected before feature code. |
| `R-RBAC` | Brief and access matrix: route capability is enforced by the server for all four roles. |
| `R-CUST-ISO` | Brief: a customer sees and creates work only within their organisation and never receives internal fields. |
| `R-TECH-OWN` | Brief: a technician sees and mutates only work assigned to that technician. |
| `R-LIFE` | Brief and state-machine document: only the nine supplied lifecycle transitions are possible with their exact actors/command routes. |
| `R-HIST` | Playbook/state-machine document: creation and each successful transition append exactly one immutable history record; failures append none. |
| `R-TERM` | Brief: `CLOSED` and `CANCELLED` are terminal and immutable. |
| `R-CONC` | Playbook: concurrent lifecycle commands cannot silently overwrite one another. |
| `R-STOCK` | Brief/playbook: part usage and stock decrement are atomic and stock never becomes negative, including under concurrency. |
| `R-SLA` | `ASSUMPTIONS.md` A-026/A-030 through A-032: configurable priority deadlines and risk window use a controllable clock; Q-002/Q-003/Q-012/Q-015/Q-022 block final policy expectations. |
| `R-ATT` | Brief, API contract, and `ASSUMPTIONS.md` A-015/A-016/A-027 through A-029: proof-image access/validation is scoped; content storage remains blocked on Q-023. |
| `R-NOTIF` | ADR 0005: in-app notifications are recipient-scoped, read-state controlled, and duplicate-safe where required. |
| `R-DATA` | ERD/API contract: customer/site integrity, validation, unique keys, positive quantities/minutes, and safe DTOs. |
| `R-LIST` | API contract: consistent bounded pagination, stable sorting, authorised filters, and search. |
| `R-ERR` | API contract: stable JSON errors and correct `400`/`401`/`403`/`404`/`409` semantics. |
| `R-MIG` | ADR 0004/playbook: a fresh PostgreSQL database migrates and validates cleanly through Flyway; Hibernate does not create schema. |
| `R-UI` | Brief/playbook: role-aware navigation and route/action guards are correct, while the server remains authoritative. |
| `R-OAPI` | Brief/API contract: generated OpenAPI describes implemented routes, DTOs, bearer security, parameters, and responses. |
| `R-DEPLOY` | Brief/playbook: reproducible Docker packaging and a verified deployment with configuration, health checks, migrations, and persistence. |

### Test levels and evidence

| Level | Intended use | Required evidence |
|---|---|---|
| Unit | State table, actor policy, SLA calculation, DTO mapping, client components | Named deterministic test and assertion output |
| Back-end integration | HTTP security, scoped queries, transactions, locking, migrations | Spring integration test; use real PostgreSQL/Testcontainers where PostgreSQL semantics matter |
| Front-end component/integration | Route guards, navigation, forms, response handling | Automated front-end test with role/auth state and mocked or test API boundary |
| Contract | Generated OpenAPI versus planned/implemented routes and standard error/page schemas | Spec validation plus route/schema assertions |
| Packaging | Docker Compose, images, environment and clean startup | Command output and health/readiness response |
| Manual acceptance | Responsive layout, live TLS/CORS, deployment smoke, browser/Swagger flow | Dated checklist, URL/environment, result, and screenshot/log reference |

Security tests must call the HTTP API directly; clicking or hiding a UI control is not proof of authorisation. Collection tests and direct-id tests are both required. Database race tests must use independent transactions/connections and real PostgreSQL—not an in-memory database.

### Response convention used by this matrix

Unless the final API contract explicitly says otherwise:

- missing, malformed, tampered, or expired authentication returns `401`;
- a valid principal lacking a route capability or actor predicate for an otherwise visible resource returns `403 ACCESS_DENIED`;
- a direct read or command for a real but out-of-scope customer/technician resource returns the same `404 RESOURCE_NOT_FOUND` shape as a nonexistent id, preventing existence disclosure;
- invalid request syntax/validation returns `400`;
- an authenticated, in-scope command that violates an assignment, lifecycle, terminal-state, optimistic-version, stock, or active-resource precondition returns `409` with the applicable stable API code (including `ASSIGNMENT_NOT_ALLOWED`, `ILLEGAL_WORK_ORDER_TRANSITION`, `TERMINAL_WORK_ORDER_STATE`, `WORK_ORDER_VERSION_CONFLICT`, or `INSUFFICIENT_STOCK`); and
- every error uses the common problem shape and contains no stack trace, SQL, token, secret, or cross-scope resource detail.

Problem bodies contain `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`, and `traceId`, with optional safe `parameters` and `fieldErrors`. They never include hidden-resource details.

## 2. Authentication and baseline route authorisation

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `AUTH-001` | M1 | `R-AUTH` | Integration | Log in once with valid seeded credentials for each of `DISPATCHER`, `TECHNICIAN`, `MANAGER`, and `CUSTOMER`. | Each returns `200`, `tokenType=Bearer`, a nonblank signed access token, an expiry instant, and only the safe matching user summary. |
| `AUTH-002` | M1 | `R-AUTH`, `R-ERR` | Integration | Submit a known email with a wrong password, an unknown email, and invalid login fields. | Wrong/unknown credentials have the same non-enumerating `401 INVALID_CREDENTIALS`; invalid fields use `400 VALIDATION_FAILED`. No token is issued. |
| `AUTH-003` | M1 | `R-AUTH` | Integration/database | Inspect seeded and newly created user credential records after login tests. | Password values are BCrypt hashes, never plaintext; password/hash fields never appear in API responses or logs. |
| `AUTH-004` | M1 | `R-AUTH`, `R-ERR` | Integration | Call representative protected list, detail, and command routes without `Authorization`. | `401 AUTHENTICATION_REQUIRED`; controller/service action is not invoked and no state changes. |
| `AUTH-005` | M1 | `R-AUTH`, `R-ERR` | Integration | Send malformed headers/tokens: missing token after `Bearer`, wrong scheme, wrong segment count, invalid base64/JSON, unsupported algorithm. | Missing/wrong-scheme credentials use `401 AUTHENTICATION_REQUIRED`; a supplied malformed/unsupported token uses `401 INVALID_TOKEN`. No feature execution. |
| `AUTH-006` | M1 | `R-AUTH`, `R-ERR` | Integration | Modify payload claims in a valid token without re-signing, modify the signature, and sign with an unrelated key. | `401 INVALID_TOKEN`; forged id/role/customer claims have no effect. |
| `AUTH-007` | M1 | `R-AUTH`, `R-ERR` | Integration | Use a correctly signed token whose `exp` is in the past, with a controlled clock around the configured skew. | `401 TOKEN_EXPIRED`; no protected response or mutation. Exact equality follows the Q-013 values adopted under ADR 0002. |
| `AUTH-008` | M1 | `R-AUTH` | Integration | Use a valid token for a disabled/deactivated user after the approved active-user freshness policy is implemented. | Behavior matches the Q-013 policy adopted under ADR 0002 and is consistent across all routes; this case is blocked until that policy is decided. |
| `AUTH-009` | M1 | `R-AUTH`, `R-RBAC` | Integration | Call `/api/v1/auth/me` using each valid role token. | `200` with the token's effective user only; no password hash or unauthorised organisation/internal data. |
| `AUTHZ-001` | M1–M4 | `R-RBAC`, `R-ERR` | Parameterized integration | For every row in `docs/ACCESS_MATRIX.md`, call the route as unauthenticated and as each of the four roles, including routes the UI does not render. | Every allowed cell succeeds subject to input/domain rules; missing auth is `401`; every denied role is `403` or the documented scoped `404`. No denied call changes data. |
| `AUTHZ-002` | M1–M4 | `R-RBAC` | Integration | Send a valid low-privilege token while adding role-like headers, query parameters, form fields, or JSON fields such as `role=MANAGER`. | Server uses only the authenticated principal and persisted scope; privilege remains unchanged and forbidden action is denied. |
| `AUTHZ-003` | M1–M4 | `R-RBAC`, `R-ERR` | Integration | Replay representative UI requests by raw HTTP, alter ids and bodies, and call hidden action endpoints directly. | Results match server capability/scope rules; no authorization decision depends on the UI. |
| `AUTHZ-004` | M2–M4 | `R-RBAC`, `R-DATA` | Integration | Over-post server-controlled or internal fields on create/update requests (`status`, `assignee`, `customerId` where fixed by scope, costs, stock, internal notes, audit fields, role). | Request is rejected with the documented `400` field/unknown-property problem; no supplied internal value takes effect or appears in a response. |
| `AUTHZ-005` | M2–M4 | `R-RBAC`, `R-DATA` | Integration | Put SQL metacharacters/wildcards and very long input into search/filter fields and identifiers. | Inputs are bounded/validated and parameterized; no query error, scope broadening, data leakage, or mutation occurs. |

## 3. Customer isolation, technician ownership, and field safety

Use fixtures with at least customers `A` and `B`, multiple sites per customer, customer users `A1` and `B1`, technicians `T1` and `T2`, and work orders assigned/unassigned across both organisations. A test passes only if the response body, totals, page metadata, nested objects, history, attachments, and notifications are all non-leaking.

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `IDOR-C-001` | M2 | `R-CUST-ISO` | Integration | As customer `A1`, list customers, sites, and work orders with no filter. | Only organisation A's permitted records are present; totals/page counts do not include B; internal-only fields are absent. |
| `IDOR-C-002` | M2 | `R-CUST-ISO`, `R-ERR` | Integration | As `A1`, request B's customer, site, work-order detail, or work-order history using each real id. Repeat with nonexistent ids. | Each is the same non-disclosing `404` response shape; no existence signal in body. |
| `IDOR-C-003` | M2 | `R-CUST-ISO` | Integration | As `A1`, pass B's `customerId`, `siteId`, `assigneeId`, status, search term, or combinations in list/filter query parameters. | Query cannot widen the principal's organisation scope; only A results/counts are returned or a documented validation error is returned. |
| `IDOR-C-004` | M2 | `R-CUST-ISO`, `R-DATA` | Integration | As `A1`, create a request with B's site id, an A site paired with B customer id, or internal assignment/status/cost fields. | Cross-customer combinations are denied without creating a work order/history; internal fields have no effect according to the contract. |
| `IDOR-C-005` | M2 | `R-CUST-ISO`, `R-TERM` | Integration | As `A1`, call dispatcher/manager edit, assignment, status, parts, time, user, inventory, or report routes—including with A-owned ids. | `403` because customer role lacks those capabilities; no changes. Customer request creation remains the only supplied customer command. |
| `IDOR-C-006` | M4 | `R-CUST-ISO`, `R-ATT` | Integration | As `A1`, call attachment list/download routes with own, another organisation's, and nonexistent work-order/attachment ids while customer attachment access remains unapproved. | Every call is rejected at the coarse route guard with the same `403 ACCESS_DENIED`; no target existence, metadata, filename, media type, length, or bytes leak. If customer `OWN` access is later approved, cross-scope cases change to scoped `404`. |
| `IDOR-C-007` | M4 | `R-CUST-ISO`, `R-DATA` | DTO unit/integration | Compare manager/dispatcher work-order and history responses with the customer-safe response for the same A order. | Customer DTO omits internal notes, stock details, unit costs, technician-private data, administrative audit data, and every field classified internal in the API contract. |
| `IDOR-C-008` | M4 | `R-CUST-ISO` | Integration | As `A1`, follow ids exposed by an authorized notification/history response after the underlying order becomes out of scope. | Resource is re-authorized and denied without stale-data leakage. |
| `OWN-T-001` | M2/M3 | `R-TECH-OWN` | Integration | As `T1`, list and board work orders while T1 and T2 have assignments. | Only work currently assigned to T1 appears; totals, group counts, filters, and pagination exclude all other jobs. |
| `OWN-T-002` | M2/M3 | `R-TECH-OWN`, `R-ERR` | Integration | As `T1`, directly fetch T2's or an unassigned work order/history by known id; compare with nonexistent id. | Same non-disclosing `404`; no order or assignee existence detail. |
| `OWN-T-003` | M3 | `R-TECH-OWN`, `R-LIFE` | Integration | As `T1`, invoke start/hold/resume/complete on a work order assigned to T2 or unassigned. | `404 RESOURCE_NOT_FOUND`, identical to a nonexistent id; state, version, history, SLA state, and notifications are unchanged. |
| `OWN-T-004` | M3 | `R-TECH-OWN`, `R-STOCK` | Integration | As `T1`, post part usage or time to T2's/unassigned order. | `404 RESOURCE_NOT_FOUND`; stock, usage rows, time logs, totals, and history are unchanged. |
| `OWN-T-005` | M4 | `R-TECH-OWN`, `R-ATT` | Integration | As `T1`, upload/list/download proof for T2's/unassigned job using guessed ids or mismatched parent/attachment ids. | `404 RESOURCE_NOT_FOUND` without existence or byte leakage; no attachment metadata/content row is created. |
| `OWN-T-006` | M3 | `R-TECH-OWN` | Integration | Reassign an order from T1 to T2, then reuse T1's previously loaded URL/request. | T1 immediately loses collection/detail/command access according to the approved token/resource policy; T2 gains only the documented access. |
| `OWN-T-007` | M3 | `R-TECH-OWN`, `R-RBAC` | Integration | As a technician, call assignment/reassignment, customer/site administration, user, inventory management, close/reopen/cancel, and reports directly. | `403`; no target existence or data change is exposed beyond the documented scoped-read policy. |

## 4. Exhaustive work-order lifecycle matrix

### Matrix legend

- `A D/M`: allowed only through `POST /api/v1/work-orders/{workOrderId}/assign` by dispatcher or manager; atomically creates `NEW -> ASSIGNED` history and the required assignment notification.
- `S T*`: allowed through the status command only for the currently assigned technician.
- `S D/M`: allowed through the status command for dispatcher or manager.
- `S M`: allowed through the status command for manager only.
- `409-I`: illegal state pair; for an authenticated in-scope test actor, return `409 ILLEGAL_WORK_ORDER_TRANSITION` with safe `currentStatus` and `attemptedStatus` parameters.
- `409-T`: source is terminal; return `409 TERMINAL_WORK_ORDER_STATE` with safe `currentStatus` and `attemptedStatus` parameters.

Rows are current state; columns are requested target state. Every one of the 49 cells is a required parameterized case.

| From \ To | `NEW` | `ASSIGNED` | `IN_PROGRESS` | `ON_HOLD` | `COMPLETED` | `CLOSED` | `CANCELLED` |
|---|---|---|---|---|---|---|---|
| `NEW` | `409-I` | `A D/M` | `409-I` | `409-I` | `409-I` | `409-I` | `S D/M` |
| `ASSIGNED` | `409-I` | `409-I` | `S T*` | `409-I` | `409-I` | `409-I` | `S D/M` |
| `IN_PROGRESS` | `409-I` | `409-I` | `409-I` | `S T*` | `S T*` | `409-I` | `409-I` |
| `ON_HOLD` | `409-I` | `409-I` | `S T*` | `409-I` | `409-I` | `409-I` | `409-I` |
| `COMPLETED` | `409-I` | `409-I` | `S M` | `409-I` | `409-I` | `S M` | `409-I` |
| `CLOSED` | `409-T` | `409-T` | `409-T` | `409-T` | `409-T` | `409-T` | `409-T` |
| `CANCELLED` | `409-T` | `409-T` | `409-T` | `409-T` | `409-T` | `409-T` | `409-T` |

`NEW -> ASSIGNED` is a domain transition but is not accepted by the generic status route; its successful test uses the assignment command. Reassignment is not a state transition and is tested separately. For a legal pair attempted by the wrong actor, the state pair is valid but the actor test below expects `403`. Missing/out-of-scope resources remain `404` before disclosing state.

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `LIFE-001` | M3 | `R-LIFE` | Parameterized unit | Evaluate the state policy for all 49 source/target cells above. | Exactly the nine marked pairs are legal; the other 40 are illegal. No implicit wildcard, backwards transition, or same-state transition exists. |
| `LIFE-002` | M3 | `R-LIFE`, `R-ERR` | Parameterized integration | Execute each legal pair through its designated command with the correct actor and valid version/input. | Command succeeds, persisted status/version changes once, and the response matches the target state. |
| `LIFE-003` | M3 | `R-LIFE`, `R-ERR` | Parameterized integration | For every `409-I` cell, submit that target against an authenticated, in-scope order and snapshot all related rows first. | `409` illegal-transition problem containing safe current/attempted statuses; no work-order, history, notification, stock, time, attachment, or audit side effect. |
| `LIFE-004` | M3 | `R-LIFE` | Integration | Submit `targetStatus=ASSIGNED` to the generic status endpoint for a `NEW` order. | `409`; only the assignment command can perform first assignment. |
| `LIFE-005` | M3 | `R-LIFE`, `R-RBAC` | Parameterized integration | For each legal technician transition, try a technician outside the order's assignment scope, dispatcher, manager, and an in-scope customer; then use the assigned technician. | Out-of-scope technician receives non-disclosing `404`; visible callers lacking the technician action receive `403 ACCESS_DENIED`; only assigned technician succeeds. Manager does not silently inherit technician actions. |
| `LIFE-006` | M3 | `R-LIFE`, `R-RBAC` | Parameterized integration | Try close and reopen as dispatcher, technician, customer, and manager on a valid `COMPLETED` order. | Only manager succeeds; all other actors receive `403` with no mutation. |
| `LIFE-007` | M3 | `R-LIFE`, `R-RBAC` | Parameterized integration | Try cancel on `NEW` and `ASSIGNED` as dispatcher, manager, technician, and customer. | Dispatcher and manager succeed; technician and customer receive `403`. |
| `LIFE-008` | M3 | `R-LIFE`, `R-RBAC` | Integration | Call a legal command with a valid actor against a nonexistent id and a real out-of-scope id. | Both use the documented non-disclosing `404`; no current status is disclosed. |
| `LIFE-009` | M3 | `R-LIFE`, `R-ERR` | Integration | Submit absent, null, unknown/case-invalid target state, malformed JSON, oversized/invalid note, or fields not in the command DTO. | Documented `400`; no transition or history. |
| `LIFE-010` | M3 | `R-LIFE` | Integration | First-assign `NEW`, reassign separately in `ASSIGNED`, `IN_PROGRESS`, and `ON_HOLD`, then attempt reassignment in `COMPLETED`. | First assignment alone changes `NEW -> ASSIGNED`; eligible reassignments preserve status and create no fake transition; `COMPLETED` returns `409 ASSIGNMENT_NOT_ALLOWED` until Q-007 is approved. |

## 5. Terminal immutability, history, and concurrency

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `TERM-001` | M3 | `R-TERM`, `R-LIFE` | Parameterized integration | For both `CLOSED` and `CANCELLED`, request every one of the seven target states through the status command. | All 14 attempts return terminal-state `409`; state/version/history are unchanged. |
| `TERM-002` | M2/M3/M4 | `R-TERM` | Parameterized integration | For both terminal states, attempt work-order edit, assign/reassign, part usage, time log, attachment upload, and every status mutation as the strongest otherwise-capable actor. | Each mutation is rejected with `409 TERMINAL_WORK_ORDER_STATE`; no parent or child data changes. Existing authorized reads/downloads remain read-only. |
| `TERM-003` | M3 | `R-TERM` | Integration | Read terminal order detail and history as each otherwise-authorised role. | Read-only history remains available within role/scope; no response advertises a mutation action. |
| `TERM-004` | M3 | `R-TERM`, `R-RBAC` | Integration | As dispatcher, read/update/assign/status a known terminal order and a nonexistent id; repeat the terminal mutation as manager. | Dispatcher receives the same open-scoped `404 RESOURCE_NOT_FOUND` for terminal and nonexistent ids; manager sees the order but every mutation returns `409 TERMINAL_WORK_ORDER_STATE`. |
| `HIST-001` | M2 | `R-HIST` | Integration | Create a work order as dispatcher/manager and as a customer using a controlled clock. | Exactly one history row `null -> NEW` exists with actor, timestamp, and the documented optional-note behavior. |
| `HIST-002` | M3 | `R-HIST` | Parameterized integration | Execute every allowed transition independently and inspect history before/after. | Exactly one new row records old status, new status, authenticated actor, transaction timestamp, and optional note; existing rows are byte-for-byte/logically unchanged. |
| `HIST-003` | M3 | `R-HIST` | Integration | Exercise illegal transition, forbidden actor, stale version, invalid input, missing resource, and a forced transactional failure. | Zero history rows are appended for each failed operation. |
| `HIST-004` | M3 | `R-HIST`, `R-RBAC` | Integration/contract | Probe `PUT`, `PATCH`, and `DELETE` against history collection/item paths as all roles and try over-posting history fields elsewhere. | No history mutation API exists (`404`/`405` per routing contract); supplied old/new status, actor, or timestamp cannot be injected. |
| `HIST-005` | M3 | `R-HIST` | Architecture/persistence | Review persistence APIs and exercise all exposed application operations after capturing existing history. If the ERD selects DB-level append-only enforcement, attempt SQL update/delete using the application principal. | Application code exposes append only; no test operation modifies/deletes a prior row. Any selected database enforcement also rejects direct update/delete. |
| `HIST-006` | M3 | `R-HIST`, `R-CUST-ISO` | Integration | Retrieve a multi-event history as each authorised role. | Chronological deterministic order; actor data and notes use role-safe DTOs; customer view excludes internal notes/private staff fields. |
| `CONC-001` | M3 | `R-CONC`, `R-HIST` | PostgreSQL integration | From the same starting version, issue two concurrent legal transitions that compete for the same order. Synchronize transactions so both read before either commits. | Exactly one commits. The loser returns `409 WORK_ORDER_VERSION_CONFLICT`; final state is one valid result and exactly one history row was appended. |
| `CONC-002` | M3 | `R-CONC` | PostgreSQL integration | Race a legal transition with close/reopen/cancel or reassignment against the same version. | No lost update or impossible state; one operation succeeds and stale/incompatible operation returns `409 WORK_ORDER_VERSION_CONFLICT` or the now-applicable lifecycle `409`; each committed transition has exactly one history row. |
| `CONC-003` | M3 | `R-CONC` | PostgreSQL integration | Retry a stale command after refetching the current version/capabilities. | Original stale request never overwrites state; a fresh request is evaluated anew against the state and actor table. |

## 6. Parts, stock, and time integrity

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `STOCK-001` | M3 | `R-STOCK` | PostgreSQL integration | On an eligible assigned job, log quantity `q` where stock `s >= q`. | In one commit, stock becomes `s-q`, one usage row is inserted with `q`, actor/order/part, and captured unit cost; response totals agree. |
| `STOCK-002` | M3 | `R-STOCK`, `R-ERR` | PostgreSQL integration | Request quantity greater than current stock. | `409 INSUFFICIENT_STOCK`; stock unchanged, no usage row/total change, and stock never negative. |
| `STOCK-003` | M3 | `R-STOCK`, `R-DATA` | Parameterized integration | Submit zero, negative, fractional where integer is required, overflow, null, or excessive quantity. | `400` validation response; no read-modify-write side effect. |
| `STOCK-004` | M3 | `R-STOCK` | PostgreSQL integration | Force an insert/constraint failure after the stock-decrement step inside the use case. | Entire transaction rolls back: original stock restored and no usage/totals/notification row remains. |
| `STOCK-005` | M3 | `R-STOCK` | PostgreSQL concurrency | Set stock to 1; synchronize two independent requests each consuming 1. Repeat enough times to expose races. | One succeeds, one receives `409 INSUFFICIENT_STOCK`; final stock is 0 and exactly one usage exists—never `-1`. |
| `STOCK-006` | M3 | `R-STOCK` | PostgreSQL concurrency | With stock `s`, concurrently consume quantities whose sum exceeds `s`, using multiple transactions. | Sum of committed usage is at most `s`; persisted stock equals `s - committed quantity` and is non-negative. |
| `STOCK-007` | M3 | `R-STOCK`, `R-TECH-OWN`, `R-TERM` | Integration | Attempt usage as wrong role/unassigned technician, on terminal/ineligible job, inactive part, or nonexistent/out-of-scope order/part. | Visible wrong role is `403`; unassigned/out-of-scope/not found is `404`; terminal or visible ineligible state is `409`; stock and usage stay unchanged. Exact non-terminal eligibility follows the approved status policy. |
| `STOCK-008` | M3 | `R-STOCK` | Integration | Log usage, then change the part's current unit cost as manager. | Usage retains the unit-cost snapshot; historical cost and report totals do not change retroactively. |
| `PART-001` | M3 | `R-DATA`, `R-RBAC` | Integration/database | As manager create/edit/deactivate parts; as other roles call management routes; submit duplicate normalized SKU, negative stock, or negative unit cost. | Manager-valid operations succeed; wrong roles get `403`; database/API reject duplicates and negative values consistently. |
| `TIME-001` | M3 | `R-DATA`, `R-TECH-OWN` | Integration | Assigned technician or manager logs positive minutes on an eligible order. | One time-log row is committed and labour total is correct. Actor and status eligibility match the approved policy. |
| `TIME-002` | M3 | `R-DATA` | Parameterized integration | Submit zero, negative, overflow, null, or non-integral minutes and an oversized note. | `400`; no log or total change. |
| `TIME-003` | M3 | `R-TECH-OWN`, `R-TERM` | Integration | Wrong role/unassigned technician logs time or any capable actor logs against terminal/ineligible state. | Visible wrong role is `403`, unassigned/out-of-scope is `404`, and terminal/visible ineligible state is `409`; no time row or total change. |

## 7. SLA and notification tests

Exact-equality, completion, hold, cancellation, edit, and reopen cases marked below cannot receive final expected values until Q-002/Q-003/Q-012/Q-022 in `ASSUMPTIONS.md` are decided.

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `SLA-001` | M3 | `R-SLA` | Configuration unit | After Q-022 is resolved, bind the approved defaults with no environment override, then bind explicit valid overrides. | **Blocked pending Q-022.** Once approved, effective defaults equal the approved configured values; valid overrides replace only the supplied values. The proposed `4h/24h/48h/72h` and `2h` values must not be asserted unconditionally. |
| `SLA-001A` | M3 | `R-SLA` | Parameterized unit/integration | After Q-022 and Q-002 are resolved, at a fixed creation instant calculate/persist due time for every priority under the approved duration and calendar policies. | **Blocked pending Q-022 and Q-002.** Once approved, due instants apply each configured duration using the selected calendar semantics; the test must not assume values, elapsed hours, or business hours silently. |
| `SLA-002` | M3 | `R-SLA` | Unit | With due time more than the configured warning window away, evaluate an applicable open order. | `ON_TRACK`. |
| `SLA-003` | M3 | `R-SLA` | Unit | Evaluate clearly inside the configured warning window but before due time. | `AT_RISK`. |
| `SLA-004` | M3 | `R-SLA` | Unit | Evaluate clearly after due time. | `BREACHED`. |
| `SLA-005` | M3 | `R-SLA` | Unit | Evaluate exactly at risk-window start and exactly at due time. | **Pending Q-003:** assert the approved equality boundary and update examples everywhere. |
| `SLA-006` | M3 | `R-SLA` | Integration | Change configured duration/risk values, restart, and create new orders while retaining an existing due instant. | New orders use new valid values; existing due instants do not move implicitly. |
| `SLA-007` | M3 | `R-SLA`, `R-ERR` | Configuration test | After Q-022 is resolved, start with a missing effective value (approved defaults deliberately removed in the test), malformed override, zero, or negative SLA value. Also start with no environment override while approved defaults remain. | Invalid effective configuration fails fast with a safe actionable error; absence of an optional override correctly uses the approved default. |
| `SLA-008` | M3 | `R-SLA`, `R-NOTIF` | PostgreSQL integration | Run scheduler repeatedly before threshold, at first at-risk observation, repeatedly while at-risk, at first breach observation, and repeatedly while breached. | No early message; exactly one at-risk and one breach notification per approved recipient/event; repeats are idempotent. |
| `SLA-009` | M3 | `R-SLA` | Integration | Scan terminal, completed, on-hold, cancelled, and reopened orders around thresholds. | **Pending Q-003/Q-012:** once resolved, each status follows one shared policy and terminal/inapplicable records do not receive unintended alerts. |
| `SLA-010` | M4 | `R-SLA` | Aggregate integration | Calculate compliance on deterministic orders covering open, met, breached, cancelled, and reopened cases and apply date filters. | **Pending Q-012:** values match the approved denominator and date attribution; no divide-by-zero error. |
| `NOTIF-001` | M3 | `R-NOTIF` | Integration | First-assign and then reassign an order and inspect each transaction. | The incoming technician receives the required notification. First assignment/status/history/notification and reassignment/assignee/version/notification each commit or roll back as one unit; reassignment creates no status-history row. |
| `NOTIF-002` | M3 | `R-NOTIF`, `R-RBAC` | Integration | As each user, list and mark read their own notifications, then try another user's notification id and manipulate recipient filters/body. | User sees and mutates only own notifications; cross-user ids are non-disclosing; client cannot change recipient/owner. No notification detail-get route is assumed. |
| `NOTIF-003` | M3 | `R-NOTIF` | Integration | Mark an unread notification read twice with a controlled clock. | First call records read state per contract; retry is safe/idempotent and does not create another notification. Reversible unread behavior awaits Q-025. |
| `NOTIF-004` | M3 | `R-NOTIF`, `R-LIST` | Integration | Populate more notifications than one page and request page/sort/read filters, including excessive size. | Bounded, deterministic, recipient-scoped results and correct unread/page counts. |

## 8. Attachments and upload attacks

Cases that depend on content storage, allowed MIME types, byte/count limits, eligible statuses, or customer visibility become executable only after Q-005/Q-006/Q-019/Q-023/Q-024 are approved.

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `ATT-001` | M4 | `R-ATT`, `R-TECH-OWN` | Integration with approved storage backend | After Q-023/Q-024 approval, assigned technician and manager upload an allowed, under-limit proof image to an eligible order. | `201`; metadata and one durable content object commit under the approved design; list response includes metadata but never content bytes. If A-027 is approved, assert one metadata row and one linked PostgreSQL content row. |
| `ATT-002` | M4 | `R-ATT` | Parameterized integration | Upload zero-byte, over-limit, disallowed declared MIME, valid image with false extension/type, non-image renamed as image, truncated/corrupt image, and decompression/pixel bomb fixture. | Safe `400`, `413`, or `415` per documented cause; neither metadata nor content persists; response does not echo parser internals. |
| `ATT-003` | M4 | `R-ATT` | Integration | Use filenames containing `../`, absolute paths, separators, control characters, header delimiters, HTML/script, Unicode edge cases, and excessive length. | Filename is rejected or safely normalized under the approved rule; no traversal/header injection/XSS and no filesystem path is exposed. |
| `ATT-004` | M4 | `R-ATT` | Approved-storage integration | Force content persistence failure after metadata creation and metadata failure after content preparation. | The Q-023-approved consistency/cleanup design leaves no orphan metadata or content. If A-027 is approved, the PostgreSQL transaction leaves neither row. |
| `ATT-005` | M4 | `R-ATT`, `R-RBAC`, `R-CUST-ISO`, `R-TECH-OWN` | Parameterized integration | List/download/upload as every role across own/in-scope and foreign/unassigned orders, including mismatched work-order and attachment ids. | Exact access matrix is enforced server-side; customer and dispatcher read routes receive default-deny `403`, technician out-of-assignment resources receive scoped `404`, and no forbidden response reveals metadata or bytes. Update only after an approved visibility decision. |
| `ATT-006` | M4 | `R-ATT` | Integration | Download a valid image whose original filename and declared MIME were adversarial. | Validated safe `Content-Type`, safe quoted/encoded `Content-Disposition`, `X-Content-Type-Options: nosniff`, exact byte length, and no storage path. |
| `ATT-007` | M4 | `R-ATT`, `R-TERM` | Integration | Upload to closed/cancelled and every other state around completion. | `CLOSED` and `CANCELLED` return `409 TERMINAL_WORK_ORDER_STATE`; each non-terminal state follows the approved eligible-status rule with no partial metadata/content row. |
| `ATT-008` | M4 | `R-ATT`, `R-LIST` | Integration/performance | List many attachments and work orders with attachments while observing persistence/query behavior. | Content is not fetched by metadata/list queries; downloads fetch only the authorized selected content under the approved storage design. |

## 9. Domain validation, lists, errors, and reports

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `DATA-001` | M1/M2 | `R-DATA` | Integration/database | Create site under customer A, then create work order claiming customer B with that visible site; bypass API with persistence fixture where appropriate. | API returns `400 CUSTOMER_SITE_MISMATCH`; a cross-scope hidden site remains `404`. Database/domain integrity rejects bypass; no partial order/history. |
| `DATA-002` | M1/M2 | `R-DATA` | Integration/database | Create duplicate normalized emails, work-order codes, and part SKUs under concurrency/case variants defined by normalization policy. | Unique constraint wins safely; one record at most and conflict/validation response contains no SQL detail. |
| `DATA-003` | M2 | `R-DATA` | Integration | Submit invalid required title/description/priority, invalid ids, overly long fields, and customer-controlled internal fields. | Field-specific `400` errors use common shape; no record/history. Exact field bounds come from API contract. |
| `DATA-004` | M2/M3 | `R-DATA` | Integration | Update `CLOSED`/`CANCELLED`, inactive parent/target, or stale-version records. | Appropriate `409`; no silent overwrite or integrity break. |
| `LIST-001` | M2 | `R-LIST` | Parameterized integration | Call every pageable collection with no page arguments, valid boundaries, negative page, zero/excessive size, unknown sort field, and malformed direction. | Zero-based default `page=0`, default `size=20`, maximum `size=100`, and repeatable `sort=field,direction` follow the shared convention; invalid values return `400` and no query is unbounded. |
| `LIST-002` | M2 | `R-LIST` | Integration | Create equal primary sort values; walk all pages in every allowed direction. | Stable documented secondary ordering produces no duplicates or omissions between unchanged pages. |
| `LIST-003` | M2/M4 | `R-LIST`, `R-CUST-ISO`, `R-TECH-OWN` | Integration | Combine every authorised work-order/report filter with search and try unauthorised customer/site/technician filter values. | Filters compose correctly inside principal scope; ignored/denied filters never broaden it; counts and content agree. |
| `LIST-004` | M2 | `R-LIST` | Integration/query inspection | Exercise common customer/site/work-order list and detail paths with representative data volume. | No N+1 growth in common queries and indexes support documented common filters; exact performance threshold is a human/operational decision. |
| `ERR-001` | M1–M4 | `R-ERR` | Contract/integration | Trigger validation, unauthenticated, forbidden, missing/scoped-missing, illegal transition, terminal, stale version, customer/site mismatch, and insufficient-stock errors. | Each status/code matches contract and every response contains `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`, and `traceId`, plus only applicable safe `parameters`/`fieldErrors`; no stack trace, class name, SQL, secret, JWT, or other-tenant detail. |
| `ERR-002` | M1–M4 | `R-ERR` | Integration | Send unsupported method/media type and unacceptable representation to representative routes. | Documented `405`, `415`, or `406` JSON problem; no mutation. |
| `REPORT-001` | M4 | `R-RBAC` | Integration | Call summary/reports as manager and all other roles, including raw HTTP and filter manipulation. | Manager only; all others `403`; response is bounded and filter-scoped. |
| `REPORT-002` | M4 | `R-DATA`, `R-SLA` | PostgreSQL integration | Seed deterministic statuses, labour, captured part costs, sites, technicians, and dates; request each supported filter combination. | Counts/sums/breakdowns exactly match fixtures; SLA metric waits for approved denominator policy. |
| `REPORT-003` | M4 | `R-DATA` | Integration | Request empty date/filter result and invalid/inverted date ranges. | Empty result has documented zero/empty values without divide-by-zero; invalid range is `400`. |

## 10. Flyway, PostgreSQL, and clean startup

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `MIG-001` | M1 | `R-MIG` | Testcontainers/packaging | Start a brand-new supported PostgreSQL instance with an empty database and production-like migration settings; start the application. | Flyway applies every migration in order, seed strategy behaves as documented, Hibernate `validate` passes, application becomes healthy, and no Hibernate DDL creates/updates schema. |
| `MIG-002` | M1 | `R-MIG` | Testcontainers | After `MIG-001`, run Flyway validate and restart the application without clearing data. | Validate succeeds, no migration reruns, no duplicate seeds, and persisted data remains. |
| `MIG-003` | M1–M4 | `R-MIG` | Testcontainers | For every new migration, migrate a database at the immediately prior released version to latest, then validate/start. | Upgrade succeeds without editing prior migration checksums or losing valid data. |
| `MIG-004` | M1 | `R-MIG` | Negative packaging | Deliberately mismatch an entity/schema in an isolated test or omit a migration. | Startup fails at validation; it does not silently create/alter tables. |
| `MIG-005` | M1 | `R-MIG` | Database inspection | Inspect catalog constraints/indexes after fresh migration. | Foreign keys, unique/check constraints, version/audit columns, and common-query indexes match `docs/ERD.md`; metadata/content tables are separate. |
| `MIG-006` | M1/M4 | `R-MIG`, `R-AUTH` | Repository/security scan | Inspect migrations, fixtures, images, and tracked configuration for plaintext/production credentials. | No production secret or plaintext password; any documented demo seed login uses BCrypt and cannot be enabled accidentally contrary to deployment policy. |

## 11. Front-end role and security behavior

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `UI-AUTH-001` | M2 | `R-UI`, `R-AUTH` | Front-end component/integration | Render signed-out state and navigate directly to every protected route. | Redirect/prompt to authenticate; protected page data/action does not render. |
| `UI-RBAC-001` | M2–M4 | `R-UI`, `R-RBAC` | Parameterized front-end | For each role, render navigation and directly enter every role route from the access matrix. | Allowed navigation/pages appear; denied ones show the approved forbidden/not-found flow and never issue an authorised-looking mutation. |
| `UI-RBAC-002` | M3 | `R-UI`, `R-LIFE` | Front-end component | Render orders in every state with each role/ownership combination and server-authoritative capabilities. | Only currently permitted assignment/status/part/time/close/reopen actions are offered; terminal orders have no mutation controls. |
| `UI-RBAC-003` | M2–M4 | `R-UI`, `R-CUST-ISO`, `R-TECH-OWN` | Front-end integration | Manipulate browser URL/query state to another customer/order/technician and mock server `403`/`404`. | No stale protected data remains; safe denied/not-found state shown. Separate direct-API tests prove the server denial. |
| `UI-AUTH-002` | M2 | `R-UI`, `R-AUTH` | Front-end integration | Let token expire during list/detail/form use; API returns `401`. | Auth state is cleared/reauthentication requested per design; protected cached data is not shown to a subsequent user. |
| `UI-ERR-001` | M2–M4 | `R-UI`, `R-ERR` | Front-end component | Return `400`, `401`, `403`, `404`, illegal/terminal/version/stock `409`, network error, empty data, and loading state. | Each has an actionable, non-secret UI state; stale conflict causes refetch rather than optimistic overwrite. |
| `UI-CUST-001` | M4 | `R-UI`, `R-CUST-ISO` | Front-end component | Render customer work-order/detail/history models containing only safe contract fields while attachment access remains denied. | No component expects or displays internal fields or proof-photo controls; organisation/site selector cannot choose another organisation. Update only if customer attachment visibility is approved. |
| `UI-TECH-001` | M3/M4 | `R-UI`, `R-TECH-OWN` | Front-end component/manual | Exercise assigned-job list/detail and forms at common phone widths with keyboard-only input. | Controls remain labelled, reachable, and usable; state/actions match server capability response. |
| `UI-BOARD-001` | M2 | `R-UI`, `R-LIST` | Front-end component/API | Supply more than `columnSize` open records in each state plus closed/cancelled records; test zero/excessive limits and allowed filters/sorts. | Default board groups `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`; excludes terminal work; each group returns at most the validated 1-100 `columnSize` with correct count/`hasMore`, deterministic order, filters, and role scope. |

## 12. OpenAPI, packaging, and deployment

| ID | Milestone | Tags | Level | Setup and action | Expected result |
|---|---|---|---|---|---|
| `OAPI-001` | M4 | `R-OAPI` | Contract | Generate OpenAPI from a clean build and compare operation inventory with `docs/API_CONTRACT.md` and application request mappings. | Every implemented public operation appears once; no undocumented feature route or missing planned route. |
| `OAPI-002` | M4 | `R-OAPI`, `R-AUTH` | Contract | Inspect security schemes and per-operation security. | Bearer JWT scheme is valid; protected endpoints declare it; login and deliberately public health/docs routes match the approved exposure policy. |
| `OAPI-003` | M4 | `R-OAPI`, `R-ERR`, `R-LIST` | Contract | Validate request/response schemas, enum values, page/filter/sort parameters, multipart uploads, binary downloads, and error responses. | Generated schema matches runtime DTOs and shared conventions, including lifecycle `409` details and role notes. |
| `OAPI-004` | M4 | `R-OAPI` | Manual/integration | Open Swagger UI at documented URL and exercise login-authorised representative read/command calls. | UI loads in approved environment and sends bearer auth; actual status/body match the contract. |
| `DEP-001` | M1/M4 | `R-DEPLOY` | Packaging | Run `docker compose config` with documented example environment and inspect resolved services, health checks, volumes, and networks. | Configuration is valid; no secret is baked into the committed Compose file. |
| `DEP-002` | M4 | `R-DEPLOY` | Packaging | Build front-end and back-end production artifacts and multi-stage images from a clean checkout. | Reproducible successful builds; runtime images contain required artifacts but not source credentials/dev-only secrets. |
| `DEP-003` | M4 | `R-DEPLOY`, `R-MIG` | Packaging/smoke | Start database, API, and front end from documented production-like environment with a fresh persistent volume. | Database becomes healthy, Flyway completes, API readiness/health passes, SPA loads and reaches correct API base URL. |
| `DEP-004` | M4 | `R-DEPLOY`, `R-AUTH` | Negative packaging | Start production configuration without JWT signing material, DB credential, or another required secret. | Service fails fast with a safe message; it never falls back to a committed/default production secret. |
| `DEP-005` | M4 | `R-DEPLOY`, `R-MIG` | Packaging/manual | Create data, restart/recreate application containers without deleting the named database volume, and log in again. | Data and Flyway history persist; no duplicate seed or schema recreation. |
| `DEP-006` | M4 | `R-DEPLOY` | Live manual | On selected host verify HTTPS, configured CORS from the deployed SPA, rejected unapproved origin, health/readiness, API URL, and Swagger exposure. | TLS and allowed origin work; unapproved origin lacks CORS permission; endpoints expose no environment secrets. Exact host/exposure awaits Q-013/Q-016 under ADRs 0001/0002. |
| `DEP-007` | M4 | `R-DEPLOY`, `R-RBAC`, `R-LIFE`, `R-STOCK` | Live smoke | Log in as all four roles and complete a representative request, assignment, technician lifecycle, manager close/reopen path, part/time log, customer-safe view, notification, attachment, and report check. | End-to-end results match access/state/data contracts and persist across a restart. Do not mark PASS without dated live evidence. |
| `DEP-008` | M4 | `R-DEPLOY` | Clean-checkout manual/CI | In a new temporary checkout, follow only README setup/test commands on supported tooling. | PostgreSQL, migrations, back-end tests/build, front-end install/lint/tests/build, images, and smoke flow complete without unrecorded local files or secrets. |

## 13. Milestone quality gates

| Gate | Required passing groups | Manual or policy-dependent items that must be called out |
|---|---|---|
| **M1 — foundation/auth** | `AUTH-*`, applicable `AUTHZ-*`, `MIG-*`, `DEP-001` | Production signing/lifetime choices and production docs exposure remain explicit if unresolved. |
| **M2 — customers/sites/work orders/board** | `IDOR-C-001..005`, `OWN-T-001..002`, `DATA-001..004`, `LIST-*`, relevant `UI-*` | Responsive/visual checks may be manual but cannot be silently marked automated. |
| **M3 — lifecycle/dispatch/parts/time/SLA** | all 49 lifecycle cells, `TERM-*`, `HIST-*`, `CONC-*`, `STOCK-*`, `PART-*`, `TIME-*`, `SLA-*`, `NOTIF-*`, technician UI | SLA policy-dependent tests must say PENDING/BLOCKED, never PASS using an assumed boundary. |
| **M4 — reports/customer portal/attachments/production** | remaining IDOR/attachment/report/UI tests, `OAPI-*`, `DEP-*`, complete regression | Live host, TLS, Swagger exposure, customer attachment visibility, and unresolved policies require recorded human decisions/evidence. |

No milestone passes while a required test is failing or omitted. A test may be `PASS`, `FAIL`, `BLOCKED` (with the exact unresolved decision or environment blocker), or `MANUAL-ONLY`; “not run” is not a pass.

## 14. Coverage and consistency review

This matrix covers the requested high-risk paths:

- cross-customer IDOR and query manipulation;
- technician ownership for list, detail, lifecycle, parts, time, and attachments;
- direct API and over-posting attacks;
- missing, malformed, tampered, unsupported, and expired JWTs;
- all 49 status pairs, actor rules, command-route distinction, and terminal immutability;
- creation/transition history, failed-command history behavior, and append-only exposure;
- concurrent status commands and explicit conflict behavior;
- transactional decrement, rollback, and concurrent negative-stock prevention;
- empty PostgreSQL Flyway migrate + validate + Hibernate startup and restart;
- customer-safe DTOs and front-end role/route/action guards;
- SLA calculation/scheduler idempotency and in-app notification isolation;
- proof-image validation, path/content attacks, approved storage isolation, and attachment IDOR;
- shared errors, pagination/filtering, OpenAPI drift, clean builds, Docker, and live deployment.

The following gaps still block final expected results and need human decisions rather than test-writer assumptions:

1. ADR 0002/Q-013 token lifetime/skew, active-user freshness, client storage, and revocation policy.
2. `ASSUMPTIONS.md` Q-002/Q-003/Q-008/Q-012/Q-015/Q-022 calendar/equality/stop/hold/cancellation/priority-edit/reopen/compliance/scheduler/recipient policies.
3. `ASSUMPTIONS.md` Q-005/Q-006/Q-017/Q-019/Q-023/Q-024/Q-025 attachment storage/types/limits/status/visibility/deletion/scanning/retention and notification experience policies; ADR 0005 fixes only the in-app channel.
4. Final API field lengths and the filter allowlists affected by unresolved business fields; work-order commands already use body `expectedVersion` as the planning concurrency convention.
5. ERD choice for database-level versus application-enforced history immutability and the exact work-order/version locking mechanism.
6. Production host, URLs, TLS/CORS values, observability, backup/recovery objectives, and Swagger/health exposure.

When any item is decided, update the owning ADR/spec/API document and this matrix in the same change. The final acceptance review must report contradictions between these sources as failures, not choose a convenient behavior during implementation.
