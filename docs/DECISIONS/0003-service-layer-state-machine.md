# 0003: Service-layer work-order state machine

- **Status:** Accepted
- **Date:** 2026-08-04
- **Decision owners:** Project KEYSTONE engineering
- **Supersedes:** None

## Context

Work-order lifecycle, customer isolation, technician ownership, audit history, assignment, and inventory usage are the highest-risk business invariants in KEYSTONE. They must behave identically for UI and direct API calls and must not be spread across controllers, entity setters, or client logic.

## Decision

Use thin HTTP controllers, explicit request/response DTOs, transaction-owning application services, persistence repositories, and reusable domain-policy components. JPA entities are never accepted from or returned through the API.

An explicit service/domain state machine is the only authority for work-order status changes. The exact legal transitions are:

| From | To | Authorized actor | Command |
|---|---|---|---|
| `NEW` | `ASSIGNED` | Dispatcher or manager | Assignment command only |
| `NEW` | `CANCELLED` | Dispatcher or manager | Status command |
| `ASSIGNED` | `IN_PROGRESS` | Currently assigned technician | Status command |
| `ASSIGNED` | `CANCELLED` | Dispatcher or manager | Status command |
| `IN_PROGRESS` | `ON_HOLD` | Currently assigned technician | Status command |
| `ON_HOLD` | `IN_PROGRESS` | Currently assigned technician | Status command |
| `IN_PROGRESS` | `COMPLETED` | Currently assigned technician | Status command |
| `COMPLETED` | `IN_PROGRESS` | Manager | Status command (reopen) |
| `COMPLETED` | `CLOSED` | Manager | Status command |

`CLOSED` and `CANCELLED` are terminal. No actor can transition or otherwise mutate a terminal work order. A manager has dispatcher capabilities but does not automatically gain assigned-technician-only start/hold/resume/complete actions.

For an authenticated actor with in-scope access:

- an illegal non-terminal state pair returns HTTP `409` with code `ILLEGAL_WORK_ORDER_TRANSITION` and safe current/attempted status details;
- any mutation from `CLOSED` or `CANCELLED` returns HTTP `409` with code `TERMINAL_WORK_ORDER_STATE`;
- a stale concurrent work-order command returns HTTP `409` with code `WORK_ORDER_VERSION_CONFLICT`;
- a forbidden visible action returns `403`; and
- an order outside the caller's customer or technician scope returns the same `404` shape as a nonexistent identifier before state details are disclosed.

Status history is append-only:

- creation writes exactly one `null -> NEW` record;
- every accepted transition writes exactly one record with old status, new status, actor, server timestamp, and optional note;
- the work-order update, history insert, and required synchronous side effects share one transaction;
- a rejected or rolled-back command writes no history row; and
- no API or repository operation updates or deletes history records.

Service-layer transaction ownership also applies to inventory usage. Actor/status validation, stock availability, a row lock or equivalent atomic decrement, historical unit-cost capture, and part-usage insertion occur in one transaction. A database constraint enforces `stock >= 0` as a final defense, and concurrency tests must prove stock cannot become negative or partially decrement.

The complete 7-by-7 matrix, actor precedence, assignment rules, history behavior, and error precedence remain specified in `../STATE_MACHINE.md`; this ADR fixes where and how those rules are enforced rather than duplicating a second mutable policy source.

## Consequences

### Positive

- UI and direct API calls share one lifecycle and authorization implementation.
- Transaction boundaries prevent a changed status without history or consumed stock without usage evidence.
- Explicit DTOs reduce over-posting, recursive serialization, and customer-data leakage.
- Exhaustive policy tests can cover all 49 state pairs and every actor category.

### Costs and constraints

- Services and policy components require deliberate interfaces and mapping code.
- Concurrent mutation needs optimistic locking or another explicit conflict strategy.
- Database constraints cannot replace service actor/state rules, so both layers require tests.

## Alternatives Considered

### Generic status field update

Rejected. It cannot safely express transition, actor, terminal, history, and side-effect rules.

### Lifecycle rules in controllers or the React client

Rejected. They would be duplicated, bypassable by direct requests, and difficult to transact.

### Mutable history or same-state reassignment history

Rejected. History is append-only transition evidence; reassignment that preserves status must not create a fake transition row.

## Follow-up and Open Questions

Ordinary editable fields, eligible states for parts/time/proof, completed-work reassignment, and separate assignment-audit behavior remain Q-006, Q-007, Q-010, and Q-021 in `../ASSUMPTIONS.md`. They cannot change the fixed lifecycle graph without an approved specification change.

## References

- `../STATE_MACHINE.md`
- `../ACCESS_MATRIX.md`
- `../API_CONTRACT.md`
- `../TEST_MATRIX.md`
- `../reference/keystone_codex_build_playbook.md`
