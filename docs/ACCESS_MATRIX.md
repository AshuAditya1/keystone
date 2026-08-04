# Project KEYSTONE access-control matrix

## Purpose and authority

This document is the server-side authorization contract for the versioned API under `/api/v1`. It covers both endpoint capability and record-level scope. A client-side route guard or hidden button is usability only and is never an authorization control.

The terms **must**, **must not**, and **only** are binding for implementation and tests. Where the product brief is silent, this document labels the choice as a recorded implementation policy or an unresolved question rather than presenting it as an explicit requirement.

## Explicit brief requirements

- The only roles are `DISPATCHER`, `TECHNICIAN`, `MANAGER`, and `CUSTOMER`.
- A dispatcher creates customers, sites, and work orders; assigns and reassigns technicians; and sees all open work and the board.
- A technician sees only work assigned to that technician; starts, holds, resumes, and completes it; logs parts and time; and uploads proof photos.
- A manager has dispatcher capabilities and additionally closes and reopens work orders, manages users and parts, sees the dashboard and reports, and performs administrative actions.
- A customer raises requests for sites belonging to the customer's organisation and sees only that organisation's work-order status and history, without internal fields.
- Authentication uses stateless JWT bearer tokens. Authorization therefore has to be enforced on every request, including direct API requests that bypass the UI.

## Recorded implementation policies and assumptions

These policies are needed to make the route contract executable. They come from the supplied build playbook or are conservative implementation assumptions; they are not additional product features.

- All product endpoints except `POST /api/v1/auth/login` require a valid access JWT. Whether health and OpenAPI documentation are public is deployment configuration and is outside this product-route matrix.
- Customer request creation is authenticated. The server obtains the customer organisation from the authenticated user, never from an asserted organisation identity in the request.
- A `CUSTOMER` user has exactly one `customerId`. An absent or inactive association prevents customer-scoped product access.
- Manager user and master-data administration uses deactivation, not destructive deletion, so historic references remain intact.
- Manager work-order reads cover the internal operational dataset. Dispatcher reads are limited to non-terminal/open work until broader historical visibility is approved; terminal work is always excluded from the default board.
- The supplied plan makes part catalogue listing manager-only. Technician/dispatcher safe catalogue discovery and dispatcher reads of nested part/time/attachment records remain denied until the unresolved access questions are approved.
- Notifications are always scoped to the authenticated recipient. Supplying another user id is not supported.
- Customer-facing work-order and history responses use dedicated safe DTOs. They are not JPA entities with fields conditionally hidden by the browser.
- Until a human explicitly approves customer proof-photo visibility, attachment metadata and content are internal and customer access is denied.

## Matrix legend

| Mark | Meaning |
|---|---|
| `PUBLIC` | No JWT is required. |
| `ALL` | The role may act across the internal operational dataset, subject to validation and lifecycle rules. |
| `OPEN` | The role may read only non-terminal work orders: `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`. |
| `OWN` | Only records belonging to the authenticated customer's organisation; a customer-safe representation is mandatory. |
| `ASSIGNED` | Only a work order whose current assignee is the authenticated technician. |
| `SELF` | Only the authenticated user's own resource, such as `/auth/me` or notification inbox. |
| `ACTION` | The endpoint is available only for the actor/transition combinations described in `docs/STATE_MACHINE.md`. |
| `DENY` | The server returns `403 ACCESS_DENIED` without invoking business logic, unless a more specific scoped-resource rule below deliberately returns `404`. |

Being allowed to call a route does not override validation, resource scope, lifecycle, terminal-state, active-record, or concurrency constraints.

## Route-by-role authorization matrix

### Authentication and users

| Method and route | Dispatcher | Technician | Manager | Customer | Scope and response notes |
|---|---:|---:|---:|---:|---|
| `POST /api/v1/auth/login` | `PUBLIC` | `PUBLIC` | `PUBLIC` | `PUBLIC` | Valid credentials return a JWT and safe user summary. Role is server-derived. |
| `GET /api/v1/auth/me` | `SELF` | `SELF` | `SELF` | `SELF` | Never returns password hashes, security secrets, or another user's profile. |
| `GET /api/v1/users` | `DENY` | `DENY` | `ALL` | `DENY` | Manager-only user administration; paginated. |
| `POST /api/v1/users` | `DENY` | `DENY` | `ALL` | `DENY` | Manager creates a user; allowed role/organisation combinations are validated. |
| `GET /api/v1/users/{userId}` | `DENY` | `DENY` | `ALL` | `DENY` | Other roles use `/auth/me`, not guessed user ids. |
| `PUT /api/v1/users/{userId}` | `DENY` | `DENY` | `ALL` | `DENY` | Manager-only editable fields; credentials are never returned. |
| `PATCH /api/v1/users/{userId}/activation` | `DENY` | `DENY` | `ALL` | `DENY` | Deactivate/reactivate; no hard delete. Self-deactivation policy remains unresolved. |
| `GET /api/v1/users/technicians` | `ALL` | `DENY` | `ALL` | `DENY` | Dispatcher/manager assignment selector; active technicians only by default and only safe identity fields. |

### Customers and sites

| Method and route | Dispatcher | Technician | Manager | Customer | Scope and response notes |
|---|---:|---:|---:|---:|---|
| `GET /api/v1/customers` | `ALL` | `DENY` | `ALL` | `OWN` | Customer result contains at most its own organisation and uses a safe DTO. Scope is applied before pagination/counting. |
| `POST /api/v1/customers` | `ALL` | `DENY` | `ALL` | `DENY` | Explicit dispatcher capability, inherited by manager. |
| `GET /api/v1/customers/{customerId}` | `ALL` | `DENY` | `ALL` | `OWN` | A different customer's id is returned as `404`, not `403`. |
| `PUT /api/v1/customers/{customerId}` | `ALL` | `DENY` | `ALL` | `DENY` | No terminal work-order rule is bypassed by changing customer data. |
| `PATCH /api/v1/customers/{customerId}/activation` | `DENY` | `DENY` | `ALL` | `DENY` | Manager administrative action; preserves history. |
| `GET /api/v1/sites` | `ALL` | `DENY` | `ALL` | `OWN` | Customer sees only sites in its organisation. `customerId` filters cannot broaden scope. |
| `POST /api/v1/sites` | `ALL` | `DENY` | `ALL` | `DENY` | Parent customer must exist and be eligible. |
| `GET /api/v1/sites/{siteId}` | `ALL` | `DENY` | `ALL` | `OWN` | Cross-customer lookup is indistinguishable from absence (`404`). |
| `PUT /api/v1/sites/{siteId}` | `ALL` | `DENY` | `ALL` | `DENY` | Moving a site between customers is not implied by this permission. |
| `PATCH /api/v1/sites/{siteId}/activation` | `DENY` | `DENY` | `ALL` | `DENY` | Manager administrative action; no hard delete. |

### Work orders, assignment, and lifecycle

| Method and route | Dispatcher | Technician | Manager | Customer | Scope and response notes |
|---|---:|---:|---:|---:|---|
| `GET /api/v1/work-orders` | `OPEN` | `ASSIGNED` | `ALL` | `OWN` | Scope is in the database query before filters, counts, sorting, and pagination. Customer fields are safe-only. |
| `POST /api/v1/work-orders` | `ALL` | `DENY` | `ALL` | `OWN` | Customer request site must belong to the principal's organisation; internal fields are rejected. Creation records `null -> NEW`. |
| `GET /api/v1/work-orders/board` | `ALL` | `ASSIGNED` | `ALL` | `DENY` | Default board includes `NEW`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`, and `COMPLETED`; technician cards remain assignment-scoped. |
| `GET /api/v1/work-orders/{workOrderId}` | `OPEN` | `ASSIGNED` | `ALL` | `OWN` | Out-of-scope direct reads return `404`. Customer receives the safe view. |
| `PUT /api/v1/work-orders/{workOrderId}` | `OPEN` | `DENY` | `ALL` | `DENY` | Only editable, role-appropriate fields and only while non-terminal; a dispatcher terminal id remains scoped `404`, while an authorized manager terminal mutation is `409`. Status and assignee are not general-edit fields. |
| `POST /api/v1/work-orders/{workOrderId}/assign` | `ACTION + OPEN` | `DENY` | `ACTION` | `DENY` | Dispatcher or manager only. Dispatcher resource lookup remains open-scoped; first assignment and reassignment rules are in `STATE_MACHINE.md`. |
| `POST /api/v1/work-orders/{workOrderId}/status` | `ACTION + OPEN` | `ACTION + ASSIGNED` | `ACTION` | `DENY` | Dispatcher: cancel only. Technician: start/hold/resume/complete only. Manager: cancel/reopen/close only. No role receives an implicit manager override. |
| `GET /api/v1/work-orders/{workOrderId}/history` | `OPEN` | `ASSIGNED` | `ALL` | `OWN` | Append-only chronological history. Customer view omits internal note and private actor details. |

### Parts, usage, and time

| Method and route | Dispatcher | Technician | Manager | Customer | Scope and response notes |
|---|---:|---:|---:|---:|---|
| `GET /api/v1/parts` | `DENY`* | `DENY`* | `ALL` | `DENY` | Manager-only catalogue in the supplied plan. A separate safe operational lookup awaits approval. |
| `POST /api/v1/parts` | `DENY` | `DENY` | `ALL` | `DENY` | Manager-only part management. |
| `GET /api/v1/parts/{partId}` | `DENY`* | `DENY`* | `ALL` | `DENY` | Manager-only until a safe operational lookup is approved. |
| `PUT /api/v1/parts/{partId}` | `DENY` | `DENY` | `ALL` | `DENY` | Stock and cost updates require manager authority and integrity validation. |
| `PATCH /api/v1/parts/{partId}/activation` | `DENY` | `DENY` | `ALL` | `DENY` | Deactivation does not alter historic part usage. |
| `GET /api/v1/work-orders/{workOrderId}/part-usages` | `DENY`* | `ASSIGNED` | `ALL` | `DENY` | Technician view omits captured cost; manager receives full internal data. Dispatcher read awaits approval. |
| `POST /api/v1/work-orders/{workOrderId}/part-usages` | `DENY` | `ASSIGNED` | `ALL` | `DENY` | Assigned technician or manager on an eligible non-terminal order; stock decrement and usage insert are atomic. |
| `GET /api/v1/work-orders/{workOrderId}/time-logs` | `DENY`* | `ASSIGNED` | `ALL` | `DENY` | Internal operational data only. Dispatcher read awaits approval. |
| `POST /api/v1/work-orders/{workOrderId}/time-logs` | `DENY` | `ASSIGNED` | `ALL` | `DENY` | Assigned technician or manager on an eligible non-terminal order; positive minutes required. |

### Attachments, notifications, and reports

| Method and route | Dispatcher | Technician | Manager | Customer | Scope and response notes |
|---|---:|---:|---:|---:|---|
| `GET /api/v1/work-orders/{workOrderId}/attachments` | `DENY`* | `ASSIGNED` | `ALL` | `DENY`* | Metadata only. Dispatcher/customer visibility is unresolved, so deny by default. |
| `POST /api/v1/work-orders/{workOrderId}/attachments` | `DENY` | `ASSIGNED` | `ALL` | `DENY` | Assigned technician or manager; eligible non-terminal job; proof-image validation is mandatory. |
| `GET /api/v1/work-orders/{workOrderId}/attachments/{attachmentId}/content` | `DENY`* | `ASSIGNED` | `ALL` | `DENY`* | Both ids must be related and authorized; never expose a storage path. |
| `GET /api/v1/notifications` | `SELF` | `SELF` | `SELF` | `SELF` | Recipient is always current user; unread filter/pagination cannot select another recipient. |
| `PATCH /api/v1/notifications/{notificationId}/read` | `SELF` | `SELF` | `SELF` | `SELF` | Another user's notification is returned as `404`. No arbitrary notification edit endpoint. |
| `GET /api/v1/reports/summary` | `DENY` | `DENY` | `ALL` | `DENY` | Manager-only dashboard/report aggregates; filters do not weaken the role guard. |

`*` These least-privilege denials remain until the unresolved safe part lookup, dispatcher nested-read, or customer attachment decision is made and this matrix, the API contract, and tests are changed together.

No product `DELETE` route is authorized by this matrix. Historical entities are retained and eligible master records are deactivated instead.

## Collection scoping versus direct-resource scoping

### Collection endpoints

Authorization scope is a mandatory predicate in the database query, not a post-query JavaScript filter and not an in-memory filter after pagination.

- A technician work-order query always includes `assignee_id = currentUser.id`.
- A customer work-order, site, or customer query always includes `customer_id = currentUser.customerId`.
- Scope is applied before total counts, aggregate values, search, user-supplied filters, sorting, and page boundaries. Otherwise metadata could leak the existence of inaccessible records.
- A supplied `customerId`, `siteId`, `assigneeId`, status, search string, or report filter can only narrow the caller's base scope. Unsupported or forbidden filter fields are rejected as validation errors; they are never trusted to establish scope.
- A valid narrowing filter that has no matching authorized rows returns an empty page. It does not fall back to an unscoped query.
- Manager reports aggregate only the manager-authorized dataset. No report route is available to other roles merely because a chart is hidden in the UI.

### Direct and nested resource endpoints

- Customer-visible direct lookups use a scoped query such as `id = :id AND customer_id = :principalCustomerId`. A cross-customer id therefore returns `404 RESOURCE_NOT_FOUND`, just like a nonexistent id.
- Technician-visible direct reads use `id = :id AND assignee_id = :principalUserId`. A job assigned to another technician, including a formerly assigned job after reassignment, returns `404`.
- Nested resources require both membership and scope. For example, an attachment id that exists under a different work order returns `404`; checking the child id alone is insufficient.
- Customer request creation resolves the site inside the customer's scope. A real site belonging to another customer is treated as not found.
- Self-scoped resources such as notifications use an owner-scoped lookup. Another recipient's real notification id returns `404`.
- An action on a work order outside a technician's assignment scope also returns scoped `404`. A `403` actor error is reserved for a visible resource where the authenticated role is not permitted to perform the otherwise valid command.

These rules intentionally prevent IDOR and identifier-enumeration attacks while retaining a useful distinction between an unavailable resource and a disallowed capability.

## Customer-safe and role-safe representations

Field filtering is performed by selecting a dedicated response DTO before serialization. The following is the minimum contract; `API_CONTRACT.md` owns the exact field lists.

| View | May include | Must exclude |
|---|---|---|
| Manager internal | Operational work-order data, history, assignee, parts/time totals and authorized cost data | Password hashes, JWT secrets, attachment bytes in metadata responses, unrelated security data |
| Dispatcher operational | Customer/site data and non-terminal work-order operational data, assignee, status/SLA, and open-work history | Terminal-work detail/history and nested usage/time/attachments pending approval; password hashes, JWT secrets, user-administration secrets, and part-management data |
| Assigned technician operational | Data required to service the assigned site/job, allowed actions, and own-job usage/time/attachments | Other technicians' jobs, part catalogue pending a safe lookup decision, unit cost and inventory-administration fields, customer-user private data, and unneeded management fields |
| Customer safe | Own organisation/site identifiers and display data; own request code/title/description/priority/status; safe SLA/due presentation; status names and timestamps in history | Internal notes; transition notes unless later classified public; stock, SKU cost, unit/captured cost and internal totals; technician private/contact data; notification recipients; internal audit/security fields; other customers' identifiers or records; attachments pending decision |

A customer-safe history item therefore exposes the transition and occurrence time but not an optional internal note. Whether it exposes a generic actor label or an assignee display name is unresolved below.

## Authentication and error semantics

| HTTP status | Stable code | When used |
|---:|---|---|
| `401 Unauthorized` | authentication error code defined by the API contract | Missing bearer token, malformed token, invalid signature, tampered token, expired token, or otherwise invalid authentication. The controller/service action is not reached. |
| `403 Forbidden` | `ACCESS_DENIED` | Authenticated user lacks the endpoint capability, or a visible work order has a valid command whose actor rule excludes that role. Do not use `401` for a valid but under-privileged user. |
| `404 Not Found` | `RESOURCE_NOT_FOUND` | Resource genuinely does not exist, or a customer/technician/self-scoped direct resource is outside the caller's visibility. This is the required anti-IDOR response. |
| `409 Conflict` | lifecycle-specific code | Caller is authenticated, route/resource-authorized, but current state conflicts with the requested operation. Exact lifecycle codes and precedence are in `STATE_MACHINE.md`. |

Authorization checks are evaluated in this order where practical:

1. Validate authentication (`401`).
2. Apply coarse route-role guard (`403`) so a wholly disallowed role never invokes domain logic.
3. Resolve the resource through the caller's visibility scope (`404` for absent or hidden records).
4. Validate terminal and route/state-pair conditions (`409`) using the precedence in `STATE_MACHINE.md`.
5. Validate the actor predicate for an otherwise legal command (`403`), then command data and concurrency (`400`/`409` as applicable).

The order must be consistent in tests; it must not vary according to whether a guessed id happens to exist.

## Direct API enforcement requirements

- Spring Security route/method guards provide coarse role checks, and the service layer provides ownership, organisation, related-resource, and actor checks. Controllers do not accept a caller-supplied role as authority.
- Repositories expose scoped queries for customer and technician reads. Calling an unrestricted `findById` and filtering the serialized result later is prohibited.
- The current user's server-side identity and active state are authoritative. A body/query `userId`, `customerId`, `assigneeId`, or role cannot impersonate or widen that identity.
- UI route guards, disabled controls, board filtering, and client-provided `availableActions` are never sufficient. Every command is re-evaluated against fresh database state.
- Bulk/aggregate endpoints, count metadata, attachment content, notification ids, exports, and nested resources receive the same authorization checks as ordinary detail endpoints.
- Customer request DTOs reject internal fields such as status, assignee, internal notes, costs, and arbitrary organisation identity rather than silently persisting them.
- Logging must not record bearer tokens, password material, attachment bytes, or private fields. Security denials may record safe actor/resource identifiers for audit without changing the client-facing concealment response.

## Authorization test obligations

For every protected route family, test no token, invalid/expired token, every disallowed role, an allowed role, and relevant ownership boundaries. At minimum:

- Cross-customer ids, nested site ids, filters, page totals, and work-order history never expose another organisation.
- A technician cannot list, read, transition, log parts/time against, or upload/download attachments for another technician's work, including after reassignment.
- A manager's broad read access does not imply permission to perform technician-only lifecycle commands.
- Direct calls remain denied even if the corresponding UI button or route is manually bypassed.
- A customer never receives internal fields through list, detail, history, error, search, notification, or serialization side channels.
- Wrong-role access is `403`; hidden direct-resource access is `404`; invalid authentication is `401`; an authorized state conflict is `409`.

## Unresolved access-control questions

These require a human decision before the affected implementation is finalized:

1. May customers list/download proof photos, and if so which attachment classification marks an image as customer-visible?
2. In customer-safe history, should actor information be omitted, shown as a generic role, or show a technician display name? Are any transition notes explicitly public?
3. Which SLA fields are customer-facing beyond a safe due-time/status presentation, and should internal breach diagnostics be hidden?
4. Should dispatchers gain read-only access to closed/cancelled work orders, part/time history, a safe active part catalogue, and proof attachments? Current policy denies these beyond open-work data.
5. Which users receive `AT_RISK`, `BREACHED`, assignment, reassignment, and status notifications? The inbox is self-scoped regardless of the recipient policy.
6. What exact manager user-management operations are required: role changes, customer reassociation, password reset, self-deactivation protection, and reactivation?
7. What deactivation rules apply when a customer, site, user, technician, or part is referenced by active work? Historical reads must remain valid either way.

Until these are decided, implementations must use the explicit deny/default behavior stated in the matrix and must not expand visibility on their own.
