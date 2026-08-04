# Project KEYSTONE REST API Contract

**Status:** Planning contract; no application endpoints exist yet

**Base path:** `/api/v1`

**Default media type:** `application/json`
**Security:** Bearer access JWT except where explicitly public

## 1. Authority and Scope

This document defines the planned HTTP surface, common payload conventions, pagination/filtering behavior, and error model. It does not generate controllers, DTOs, OpenAPI, or JPA code.

- `ACCESS_MATRIX.md` is authoritative for route roles, organisation scope, technician ownership, and customer-safe views.
- `STATE_MACHINE.md` is authoritative for status, assignment, terminal, audit, and lifecycle-conflict behavior.
- `ERD.md` is authoritative for persistence relationships and constraints.
- `ASSUMPTIONS.md` distinguishes working technical choices from product questions that still block final request fields or behavior.

If generated OpenAPI or running behavior differs from this contract, the mismatch must be reviewed and one side deliberately corrected; do not silently change both.

## 2. Requirement versus Planning Choice

### Explicit or supplied requirements

- Provide conventional REST operations for authentication, users/technicians, customers, sites, work orders, assignment, status, history, parts, part usage, time logs, attachments, notifications, and reports.
- Use stateless bearer JWT authentication and enforce all authorization on the server.
- Keep customer users inside their organisation and technicians inside their assignment scope.
- Return `409 Conflict` for illegal transitions and terminal-state mutations.
- Store one append-only history row for creation and every successful transition.
- Support bounded pagination, sorting, filtering, and consistent errors.

### Recorded implementation conventions

- Version the API at `/api/v1`.
- Use UUID resource identifiers and separate human-readable work-order `code` values.
- Use zero-based pagination with default size 20 and maximum size 100.
- Use Problem Details-compatible errors with stable KEYSTONE codes.
- Carry an optimistic `version`/`expectedVersion` on mutable work-order commands.
- Use PUT for approved full resource updates and explicit PATCH/POST commands for activation, read state, assignment, and lifecycle actions.
- Expose no DELETE routes in the required scope.

Open questions at the end of this file prevent affected schemas from being treated as final.

## 3. Protocol Conventions

### 3.1 Authentication

Protected requests send:

```http
Authorization: Bearer <access-token>
```

The API does not use an HTTP session. Missing, malformed, tampered, unsupported, or expired authentication fails before feature logic with `401` and a safe JSON problem. Login failure does not reveal whether the email exists.

### 3.2 Identifiers, enums, time, and money

- IDs are canonical UUID strings.
- Enum values are uppercase tokens exactly as documented; unknown or wrong-case values are `400 VALIDATION_FAILED`.
- Timestamps are ISO-8601 UTC instants, for example `2026-08-04T12:30:00Z`.
- Dates, where used for report filters, are ISO `YYYY-MM-DD` and interpreted under the approved reporting timezone.
- Money is a JSON decimal number and a fixed-precision PostgreSQL decimal. Binary floating point is forbidden for persisted cost.
- JSON uses `camelCase`; database naming uses `snake_case`.
- Clients must ignore unknown response fields for forward compatibility. Servers reject unknown request fields on security-sensitive create/command DTOs to prevent mass assignment.

### 3.3 Success responses

- Single-resource reads and commands return the resource/view directly, not inside a generic `data` envelope.
- Create operations return `201 Created`, the created representation, and a `Location` header.
- Updates/commands return `200 OK` with the new representation unless the row has no useful representation, in which case this contract explicitly says otherwise.
- Collection operations return the shared `PageResponse<T>`.
- Binary attachment download returns bytes, not JSON.

### 3.4 Concurrency

`WorkOrderResponse` and other mutable administration resources expose a numeric `version`. General update requests carry `version`; work-order assignment and status commands carry `expectedVersion`.

A stale write returns:

```text
409 WORK_ORDER_VERSION_CONFLICT
```

The server never retries a non-idempotent command automatically. The client refetches the resource/capabilities before retrying. Exact version handling for non-work-order master data is a working implementation convention and must be documented in generated OpenAPI.

### 3.5 Caching and sensitive data

Authenticated application responses should use private/no-store caching where sensitive data could otherwise persist. Attachment downloads use a validated media type, safe `Content-Disposition`, and `X-Content-Type-Options: nosniff`. No list response embeds attachment bytes.

## 4. Pagination, Sorting, Search, and Filtering

### 4.1 Shared page parameters

| Parameter | Type | Default | Rules |
|---|---|---:|---|
| `page` | integer | `0` | Zero-based and non-negative. |
| `size` | integer | `20` | From 1 through 100 inclusive. |
| `sort` | repeated string | route-specific | Format `field,asc` or `field,desc`; repeat for multiple fields. |
| `q` | string | none | Trimmed, case-insensitive, bounded free-text search over documented fields only. |

Example:

```http
GET /api/v1/work-orders?page=0&size=20&sort=priority,desc&sort=createdAt,desc&q=boiler
```

The server allowlists sortable/filterable fields. Unknown fields/directions, negative pages, and size values outside 1-100 return `400 VALIDATION_FAILED`; the server never silently runs an unbounded query.

Every sort appends `id,asc` as a deterministic final tie-breaker unless `id` is already present. Scope predicates are applied before filters, totals, sort, and page boundaries.

### 4.2 Shared page response

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "sort": ["createdAt,desc", "id,asc"]
}
```

`totalElements` and `totalPages` describe only the caller's authorized scope. They must not count hidden customer or technician records.

### 4.3 Array, boolean, and range filters

- Repeated array parameters use OpenAPI `style=form, explode=true`, for example `status=NEW&status=ASSIGNED`.
- Booleans use `true` or `false` only.
- Instant/date ranges use inclusive `from` and exclusive `to` unless a route explicitly documents otherwise; `from >= to` is invalid.
- A client filter may narrow the caller's base scope, never widen it.

### 4.4 Route filter allowlists

| Collection | Filters | Sort fields | Default sort |
|---|---|---|---|
| `/users` | `q`, `role`, `active`, `customerId` | `displayName`, `email`, `role`, `createdAt` | `displayName,asc` |
| `/users/technicians` | `q`, `active` (default true) | `displayName`, `email` | `displayName,asc` |
| `/customers` | `q`, `active` | `name`, `createdAt`, `updatedAt` | `name,asc` |
| `/sites` | `q`, `customerId`, `active` | `name`, `createdAt`, `updatedAt` | `name,asc` |
| `/work-orders` | `q`, repeated `status`, repeated `priority`, `customerId`, `siteId`, `assigneeId`, repeated `slaStatus`, `overdue`, `createdFrom`, `createdTo` | `code`, `priority`, `status`, `slaDueAt`, `createdAt`, `updatedAt` | `createdAt,desc` |
| `/parts` | `q`, `active` | `sku`, `name`, `stockQuantity`, `createdAt`, `updatedAt` | `name,asc` |
| nested part usages | `partId`, `usedByUserId`, `from`, `to` | `occurredAt`, `quantity` | `occurredAt,desc` |
| nested time logs | `loggedByUserId`, `from`, `to` | `occurredAt`, `minutes` | `occurredAt,desc` |
| nested attachments | `uploadedByUserId`, `mediaType`, `from`, `to` | `uploadedAt`, `originalFilename`, `sizeBytes` | `uploadedAt,desc` |
| `/notifications` | `read`, repeated `type`, `from`, `to` | `createdAt`, `readAt` | `createdAt,desc` |

Forbidden filters return `400`; they do not change authorization. Dispatcher work-order scope is open/non-terminal until Q-019 changes the access matrix. Technician/customer filters are intersected with assignment/organisation scope.

## 5. Error Contract

Errors use `Content-Type: application/problem+json` and this stable shape:

```json
{
  "type": "https://keystone.example/problems/illegal-work-order-transition",
  "title": "Work-order transition is not allowed",
  "status": 409,
  "detail": "The requested transition is not permitted from the current status.",
  "instance": "/api/v1/work-orders/7ab8b711-90c6-4e0f-920c-cacbffbdb52c/status",
  "code": "ILLEGAL_WORK_ORDER_TRANSITION",
  "timestamp": "2026-08-04T12:30:00Z",
  "traceId": "01J4...",
  "parameters": {
    "currentStatus": "IN_PROGRESS",
    "attemptedStatus": "CANCELLED"
  },
  "fieldErrors": []
}
```

Required fields are `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`, and `traceId`. `parameters` and `fieldErrors` appear only when useful and safe. A field error contains `field`, `code`, and a human-safe `message`.

Errors never include stack traces, Java class names, SQL, secrets, tokens, password material, attachment bytes, or details from a hidden organisation/resource.

### 5.1 Status and code catalogue

| HTTP | Stable code | Use |
|---:|---|---|
| `400` | `MALFORMED_REQUEST` | Malformed JSON, invalid parameter syntax, or unreadable multipart metadata. |
| `400` | `VALIDATION_FAILED` | Field, enum, page/filter, relationship-input, or unknown-property validation error. |
| `400` | `CUSTOMER_SITE_MISMATCH` | Visible customer and site do not form an allowed parent pair. Cross-customer hidden sites remain `404`. |
| `401` | `INVALID_CREDENTIALS` | Login failed without identifying which credential was wrong. |
| `401` | `AUTHENTICATION_REQUIRED` | Missing bearer credentials. |
| `401` | `INVALID_TOKEN` | Malformed, unsupported, or signature-invalid token. |
| `401` | `TOKEN_EXPIRED` | Otherwise valid token is expired. |
| `403` | `ACCESS_DENIED` | Valid principal lacks route capability or visible-resource actor permission. |
| `404` | `RESOURCE_NOT_FOUND` | Absent resource or resource concealed by customer/technician/self scope. |
| `405` | `METHOD_NOT_ALLOWED` | Path exists but the HTTP method is not supported; include the standard `Allow` header. |
| `406` | `NOT_ACCEPTABLE` | Requested response media type cannot be produced. |
| `409` | `DUPLICATE_RESOURCE` | Unique email, SKU, code, or another documented uniqueness conflict. |
| `409` | `RESOURCE_INACTIVE` | A visible inactive customer/site/user/part cannot be used by the command. |
| `409` | `ASSIGNEE_NOT_ELIGIBLE` | Visible target user is not an active technician. |
| `409` | `ASSIGNMENT_NOT_ALLOWED` | Assignment/reassignment conflicts with the current non-terminal state/policy. |
| `409` | `ILLEGAL_WORK_ORDER_TRANSITION` | Disallowed non-terminal state pair or `ASSIGNED` requested through `/status`. |
| `409` | `TERMINAL_WORK_ORDER_STATE` | Any work-order mutation when source is `CLOSED` or `CANCELLED`. |
| `409` | `WORK_ORDER_VERSION_CONFLICT` | Optimistic work-order mutation lost a race/stale version. |
| `409` | `INSUFFICIENT_STOCK` | Requested usage exceeds current part stock. |
| `413` | `ATTACHMENT_TOO_LARGE` | Multipart body/file exceeds configured limit. |
| `415` | `UNSUPPORTED_ATTACHMENT_TYPE` | Declared/signature-validated content is not an allowed proof-image type. |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Request media type is unsupported for a non-attachment operation. |
| `500` | `INTERNAL_ERROR` | Unexpected server failure with safe generic detail and trace id. |

Authentication/authorization/resource/lifecycle precedence follows `ACCESS_MATRIX.md` and `STATE_MACHINE.md`. In particular, hidden resources return `404` before state details are disclosed, and an authorized terminal/illegal mutation returns the exact `409` code above with no partial writes.

## 6. Shared Representation Concepts

These are planning field sets, not generated DTO declarations. Exact optionality/lengths await Q-009 through Q-011.

### 6.1 User views

`CurrentUserResponse` / `UserSummary` may contain:

- `id`, `email`, `displayName`, `role`, `active`;
- `customerId` only where required for a customer principal; and
- no password hash, raw credential, token secret, security events, or unrelated organisation fields.

Manager `UserResponse` additionally carries `createdAt`, `updatedAt`, and `version`. User create/update never accepts a password hash. Initial-credential delivery, role change, customer reassociation, and self-deactivation are blocked on Q-009.

### 6.2 Customer and site views

Minimum customer fields are `id`, `name`, `active`, `createdAt`, `updatedAt`, and `version`. Minimum site fields are `id`, `customerId`, `name`, structured address fields, `active`, timestamps, and `version`. The final data dictionary is Q-011.

Customer-role DTOs omit internal notes/administrative fields and return only the principal's organisation/sites.

### 6.3 Work-order views

Internal `WorkOrderResponse` may contain:

- `id`, `code`, `customer`, `site`, `title`, `description`, `priority`, `status`;
- safe assignee summary or null;
- `slaDueAt`, derived `slaStatus`, and any approved SLA outcome fields;
- `totalLabourMinutes`, authorized parts totals, attachment count;
- server-authoritative `allowedActions`;
- `createdAt`, `updatedAt`, and `version`.

Customer `CustomerWorkOrderResponse` contains only the caller's organisation-safe site/request identifiers and display fields, code/title/description, public priority if approved, status, safe SLA presentation, timestamps, and safe history links/items. It excludes internal notes, transition notes, stock, SKU/unit/captured costs, internal totals, private staff/contact data, notification recipients, security/audit internals, and attachments until Q-005 approves them.

### 6.4 History view

Internal history fields are `id`, `oldStatus` (nullable only for creation), `newStatus`, safe actor summary, `occurredAt`, and optional internal `note`.

Customer history exposes `oldStatus`, `newStatus`, and `occurredAt`. Actor/notes remain omitted pending Q-004. History is ordered `occurredAt,asc` then `id,asc` and is not mutable through the API.

### 6.5 Capabilities

`allowedActions` contains only server-derived action tokens such as `ASSIGN`, `REASSIGN`, `START`, `HOLD`, `RESUME`, `COMPLETE`, `REOPEN`, `CLOSE`, `CANCEL`, `LOG_PART`, `LOG_TIME`, or `UPLOAD_PROOF` when current role, ownership, state, and open questions permit them. Clients may use these for rendering but the command endpoint rechecks everything.

## 7. Endpoint Inventory

Roles below summarize `ACCESS_MATRIX.md`; that file remains authoritative.

Each protected endpoint also returns the common `401` authentication and `403` route-capability problems defined in section 5 even when a table's "Principal errors" cell lists only route-specific outcomes. Generated OpenAPI must enumerate both common and route-specific responses for every operation.

### 7.1 Authentication

| Method and route | Access | Request | Success | Principal errors |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | Public | `LoginRequest` | `200 LoginResponse` | `400`, `401 INVALID_CREDENTIALS` |
| `GET /api/v1/auth/me` | Any authenticated role, self | none | `200 CurrentUserResponse` | `401` |

`LoginRequest` contains normalized `email` and plaintext `password` in transit over TLS only. `LoginResponse` contains `accessToken`, literal `tokenType: "Bearer"`, `expiresAt`, and `user`. No refresh token is selected in this baseline.

### 7.2 Users and technician choices

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/users` | Manager | page/search/filter | `200 PageResponse<UserResponse>` | `400`, `401`, `403` |
| `POST /api/v1/users` | Manager | approved `UserCreateRequest` | `201 UserResponse` | `400`, `409` |
| `GET /api/v1/users/{userId}` | Manager | none | `200 UserResponse` | `401`, `403`, `404` |
| `PUT /api/v1/users/{userId}` | Manager | approved `UserUpdateRequest` + `version` | `200 UserResponse` | `400`, `404`, `409` |
| `PATCH /api/v1/users/{userId}/activation` | Manager | `{ "active": boolean, "version": number }` | `200 UserResponse` | `400`, `404`, `409` |
| `GET /api/v1/users/technicians` | Dispatcher, manager | page/search; active=true by default | `200 PageResponse<UserSummary>` | `400`, `401`, `403` |

A customer-role user requires one valid customer organisation; an internal role must not be silently attached to one. Exact role changes, initial credentials, reassociation, and deactivation effects await Q-009/Q-014.

### 7.3 Customers

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/customers` | Dispatcher/manager all; customer own | page/search/filter | `200 PageResponse<CustomerResponse>` or safe view | `400`, `401`, `403` |
| `POST /api/v1/customers` | Dispatcher, manager | `CustomerWriteRequest` | `201 CustomerResponse` | `400`, `409` |
| `GET /api/v1/customers/{customerId}` | Dispatcher/manager; customer own | none | `200` role-safe customer | `401`, `403`, scoped `404` |
| `PUT /api/v1/customers/{customerId}` | Dispatcher, manager | `CustomerWriteRequest` + `version` | `200 CustomerResponse` | `400`, `404`, `409` |
| `PATCH /api/v1/customers/{customerId}/activation` | Manager | `{ "active": boolean, "version": number }` | `200 CustomerResponse` | `400`, `404`, `409` |

No customer delete route exists.

### 7.4 Sites

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/sites` | Dispatcher/manager all; customer own | page/search/filter | `200 PageResponse<SiteResponse>` or safe view | `400`, `401`, `403` |
| `POST /api/v1/sites` | Dispatcher, manager | `SiteWriteRequest` | `201 SiteResponse` | `400`, `404`, `409` |
| `GET /api/v1/sites/{siteId}` | Dispatcher/manager; customer own | none | `200` role-safe site | `401`, `403`, scoped `404` |
| `PUT /api/v1/sites/{siteId}` | Dispatcher, manager | `SiteWriteRequest` + `version` | `200 SiteResponse` | `400`, `404`, `409` |
| `PATCH /api/v1/sites/{siteId}/activation` | Manager | `{ "active": boolean, "version": number }` | `200 SiteResponse` | `400`, `404`, `409` |

`SiteWriteRequest.customerId` is required on create. Moving a site between customers is not approved by a generic update; if `customerId` appears on update it must equal the existing parent.

### 7.5 Work orders and board

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/work-orders` | Dispatcher open; technician assigned; manager all; customer own | page/search/filter | `200 PageResponse<role-safe work order>` | `400`, `401`, `403` |
| `POST /api/v1/work-orders` | Dispatcher, manager, customer-own site | role-specific create DTO | `201 role-safe work order` | `400`, scoped `404`, `409` |
| `GET /api/v1/work-orders/board` | Dispatcher open; technician assigned; manager all | board filters | `200 WorkOrderBoardResponse` | `400`, `401`, `403` |
| `GET /api/v1/work-orders/{workOrderId}` | Per access matrix | none | `200 role-safe work order` | `401`, `403`, scoped `404` |
| `PUT /api/v1/work-orders/{workOrderId}` | Dispatcher open; manager all | approved update fields + `version` | `200 WorkOrderResponse` | `400`, `404`, `409` |
| `POST /api/v1/work-orders/{workOrderId}/assign` | Dispatcher open; manager all by assignment rule | `AssignmentRequest` | `200 WorkOrderResponse` | `400`, `403`, `404`, `409` |
| `POST /api/v1/work-orders/{workOrderId}/status` | Dispatcher open/technician assigned/manager all by exact action | `StatusTransitionRequest` | `200 WorkOrderResponse` | `400`, `403`, `404`, exact `409` |
| `GET /api/v1/work-orders/{workOrderId}/history` | Per access matrix | page parameters optional | `200 PageResponse<role-safe history>` | `400`, `401`, `403`, scoped `404` |

`WorkOrderBoardResponse` has fixed groups for `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`. `CLOSED` and `CANCELLED` never appear on the default board.

The board accepts `columnSize` (default 20, range 1-100), `q`, `sort`, priority, technician, customer/site, and SLA-condition filters allowed by the caller. Each group contains at most `columnSize` cards plus `totalElements` and `hasMore`; it never loads an unbounded column. Default card order is `priority,desc`, `slaDueAt,asc`, `id,asc`. To page beyond a board column, the client uses the standard `/work-orders` list with that status and shared page conventions.

#### Work-order create requests

`InternalWorkOrderCreateRequest` requires `customerId`, `siteId`, `title`, `description`, and `priority`. Status, code, assignee, SLA fields, totals, audit fields, and version are server controlled.

`CustomerWorkOrderCreateRequest` always requires `siteId`, `title`, and `description`; it never accepts `customerId`, status, assignee, internal notes, costs, or audit fields. Whether it contains priority or receives a defined server default is blocked on Q-001 and must be settled before implementation.

Creation always sets `NEW`, calculates/stores `slaDueAt` using the durations approved in Q-022 and the calendar approved in Q-002, generates the code, sets version, and creates `null -> NEW` history in one transaction.

#### General update

The final editable field set by role/status is blocked on Q-010. Status, assignee, work-order code, SLA outcome, totals, audit fields, and history are never general-update fields. Any mutation against `CLOSED`/`CANCELLED` returns `409 TERMINAL_WORK_ORDER_STATE`.

#### Assignment request

```json
{
  "technicianId": "42ac...",
  "note": "Optional internal dispatch note",
  "expectedVersion": 3
}
```

First assignment of `NEW` performs the exact atomic `NEW -> ASSIGNED` behavior in `STATE_MACHINE.md`. Reassignment is allowed for `ASSIGNED`, `IN_PROGRESS`, and `ON_HOLD`, preserves status, notifies the incoming technician, and creates no status-history row. `COMPLETED` returns `409 ASSIGNMENT_NOT_ALLOWED` until Q-007 is resolved. Same-technician behavior awaits Q-021.

#### Status request

```json
{
  "targetStatus": "ON_HOLD",
  "note": "Optional internal transition note",
  "expectedVersion": 4
}
```

The generic route accepts only transitions A2-A9 in `STATE_MACHINE.md`; `targetStatus=ASSIGNED` returns `409 ILLEGAL_WORK_ORDER_TRANSITION` and instructs the client to use assignment. The customer role cannot call this route.

### 7.6 Parts and part usage

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/parts` | Manager; others denied pending Q-020 | page/search/filter | `200 PageResponse<PartResponse>` | `400`, `401`, `403` |
| `POST /api/v1/parts` | Manager | `PartWriteRequest` | `201 PartResponse` | `400`, `409` |
| `GET /api/v1/parts/{partId}` | Manager | none | `200 PartResponse` | `401`, `403`, `404` |
| `PUT /api/v1/parts/{partId}` | Manager | `PartWriteRequest` + `version` | `200 PartResponse` | `400`, `404`, `409` |
| `PATCH /api/v1/parts/{partId}/activation` | Manager | `{ "active": boolean, "version": number }` | `200 PartResponse` | `400`, `404`, `409` |
| `GET /api/v1/work-orders/{workOrderId}/part-usages` | Assigned technician, manager; dispatcher pending Q-019 | page/filter | `200 PageResponse<role-safe PartUsageResponse>` | `400`, `403`, scoped `404` |
| `POST /api/v1/work-orders/{workOrderId}/part-usages` | Assigned technician, manager | `PartUsageCreateRequest` | `201 PartUsageResponse` plus refreshed totals | `400`, `403`, `404`, `409` |

`PartWriteRequest` includes approved data-dictionary fields at minimum `sku`, `name`, `stockQuantity`, and `unitCost`; SKU is unique and stock/cost non-negative. Creation defaults `active=true`; only the explicit activation PATCH changes active state.

`PartUsageCreateRequest` contains `partId`, positive integer `quantity`, optional note, and `expectedVersion` if the work-order aggregate version is used. In one transaction the server rechecks actor/state, locks or atomically updates the part, prevents negative stock, snapshots `unitCostAtUsage`, inserts usage, and returns correct totals. Technician responses omit cost and administrative stock fields.

The required technician part-selection mechanism is blocked on Q-020; the contract deliberately does not grant catalogue access early.

### 7.7 Time logs

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/work-orders/{workOrderId}/time-logs` | Assigned technician, manager; dispatcher pending Q-019 | page/filter | `200 PageResponse<TimeLogResponse>` | `400`, `403`, scoped `404` |
| `POST /api/v1/work-orders/{workOrderId}/time-logs` | Assigned technician, manager | `TimeLogCreateRequest` | `201 TimeLogResponse` plus refreshed total | `400`, `403`, `404`, `409` |

`TimeLogCreateRequest` contains positive integer `minutes`, optional note, and any required aggregate `expectedVersion`. Actor and occurrence time are server derived; backdating and eligible non-terminal statuses await Q-006. No update/delete route is supplied.

### 7.8 Attachments

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/work-orders/{workOrderId}/attachments` | Assigned technician, manager; dispatcher/customer denied pending decisions | page/filter | `200 PageResponse<AttachmentMetadataResponse>` | `400`, `403`, scoped `404` |
| `POST /api/v1/work-orders/{workOrderId}/attachments` | Assigned technician, manager | multipart `file` | `201 AttachmentMetadataResponse` | `400`, `403`, `404`, `409`, `413`, `415` |
| `GET /api/v1/work-orders/{workOrderId}/attachments/{attachmentId}/content` | Assigned technician, manager; dispatcher/customer denied pending decisions | none | `200` image bytes | `403`, scoped `404` |

Metadata fields are `id`, `workOrderId`, safe `originalFilename`, validated `mediaType`, `sizeBytes`, safe uploader summary, and `uploadedAt`. The binary table/path is never represented.

Upload uses `multipart/form-data` with exactly one `file` part. The storage backend, configured size, MIME allowlist, content signature, filename safety, pixel/decompression bounds, and exact eligible non-terminal statuses await Q-023/Q-024/Q-006. `CLOSED` and `CANCELLED` always return `409 TERMINAL_WORK_ORDER_STATE`. Photos are optional. The selected storage backend is never exposed as an API path or persistence entity.

Download verifies both parent and attachment IDs, rechecks current access, and returns a safe filename/content type. There is no delete/replace route in the required scope.

### 7.9 Notifications

| Method and route | Access | Request/query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/notifications` | Any role, self | page + `read`/type/date filters | `200 PageResponse<NotificationResponse>` | `400`, `401` |
| `PATCH /api/v1/notifications/{notificationId}/read` | Recipient self | no arbitrary recipient; optional empty body | `200 NotificationResponse` | `401`, scoped `404` |

`NotificationResponse` includes `id`, `type`, safe title/message or structured display fields, optional authorized work-order reference, `createdAt`, `read`, and `readAt`. It never accepts/returns another recipient ID as an authorization selector.

Mark-read is idempotent: a second call remains `200`, preserves the original `readAt`, and creates no new notification. Reversible unread is not included. Following a work-order link reauthorizes the work order; inbox possession is not permanent resource access.

Required triggers are incoming-technician first assignment/reassignment and idempotent first-entry SLA threshold notifications for approved recipients. Recipient sets beyond the supplied incoming technician and approved managers await Q-008.

### 7.10 Reports

| Method and route | Access | Query | Success | Principal errors |
|---|---|---|---|---|
| `GET /api/v1/reports/summary` | Manager only | `from`, `to`, `customerId`, `siteId`, `technicianId`, repeated `priority` | `200 ReportSummaryResponse` | `400`, `401`, `403` |

`ReportSummaryResponse` contains:

- counts by work-order status;
- open and overdue counts;
- SLA compliance plus at-risk/breached counts;
- total labour minutes and parts cost;
- bounded open workload by technician;
- at least one bounded technician breakdown and one site breakdown; and
- a bounded actionable at-risk/breached list or references.

Every metric uses the same validated filter set. Exact date basis/timezone, SLA denominator, cancelled/reopened handling, and zero-denominator representation await Q-003/Q-012 in `ASSUMPTIONS.md`. The endpoint must use aggregate/projection queries and never load every row into memory.

## 8. HTTP Behavior by Operation

| Operation class | Success | Validation | Unauthorized | Forbidden | Hidden/missing | Conflict |
|---|---:|---:|---:|---:|---:|---:|
| Login | `200` | `400` | `401` bad credentials | n/a | n/a | n/a |
| Create resource | `201` | `400` | `401` | `403` | `404` hidden parent | `409` uniqueness/inactive/state |
| Read/list | `200` | `400` query | `401` | `403` route | `404` direct scoped | n/a |
| General update/activation | `200` | `400` | `401` | `403` | `404` | `409` stale/terminal/inactive |
| Assignment/status command | `200` | `400` | `401` | `403` | `404` | exact state/version `409` |
| Nested part/time create | `201` | `400` | `401` | `403` | `404` | `409` terminal/eligibility/stock |
| Attachment upload | `201` | `400` | `401` | `403` | `404` | `409`; plus `413`/`415` |
| Attachment content | `200` bytes | n/a | `401` | `403` | `404` | n/a |

For an authenticated route-allowed principal, hidden and nonexistent IDs are indistinguishable. For a role wholly denied from a route, `403` is returned before the server reveals whether any supplied identifier exists.

## 9. OpenAPI Requirements

Generated OpenAPI must:

- declare an HTTP bearer JWT security scheme;
- identify the public login operation and protected operations accurately;
- document role/scope requirements in operation descriptions;
- define each role-specific request/response schema and customer-safe field set;
- define all page/filter/sort parameters, bounds, defaults, and allowlists;
- define multipart upload and binary download media types;
- enumerate success plus `400`, `401`, `403`, `404`, `405`, `406`, `409`, `413`, `415`, and `500` where applicable;
- reuse the Problem Details and page schemas;
- enumerate lifecycle/priority/SLA/notification values; and
- be contract-tested against actual Spring request mappings and frontend calls.

Swagger UI exposure in production remains a deployment/security decision; the OpenAPI document itself must still be generated and verified.

## 10. Unresolved Contract Questions

The following are deliberately not finalized:

1. Customer request priority/default and final create/update field dictionary (Q-001/Q-010/Q-011).
2. SLA default durations and warning window, calendar, thresholds, pause/stop/reopen rules, scheduler service level, recipient set, and reporting denominator (Q-022/Q-002/Q-003/Q-015/Q-008/Q-012).
3. Manager user credential delivery, high-risk role/customer changes, self-deactivation, reset/invitation, and deactivation effects (Q-009/Q-014).
4. Exact non-terminal eligibility for parts, time, and proof, including backdating (Q-006).
5. Completed-work and same-technician reassignment plus any separate assignment audit (Q-007/Q-021).
6. Dispatcher terminal/nested visibility, safe part catalogue discovery, and customer proof visibility (Q-019/Q-020/Q-005).
7. Attachment content storage, image types/limits/processing/retention, and whether dispatcher/customer download is ever allowed (Q-023/Q-024/Q-005/Q-017/Q-019).
8. Token runtime/rotation/revocation values and production CORS/docs exposure (Q-013).
9. Currency, data-field lengths/formats, retention, and deployment-dependent public URLs (Q-011/Q-016/Q-017).
10. Notification pagination/unread-count, read reversal, wording/localization, and refresh behavior (Q-025).

Until an item is approved, the explicit deny/blocking behavior in this contract and `ACCESS_MATRIX.md` applies; implementation must not infer a broader API from a UI mockup.
