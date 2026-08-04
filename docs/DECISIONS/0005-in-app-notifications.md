# 0005: In-app notifications

- **Status:** Accepted
- **Date:** 2026-08-04
- **Decision owners:** Project KEYSTONE engineering
- **Supersedes:** None

## Context

Work orders require notifications. The build playbook selects an in-app channel because an external email provider is not required for the project. The choice should remain separate from optional proof-attachment storage, which is not a locked architecture decision.

## Decision

Deliver required KEYSTONE notifications through the authenticated application.

- Persist a notification for one recipient user, with a safe type/display payload, creation time, read state/time, and an optional work-order reference.
- Let users list and mark read only their own notifications. Following a referenced work order always re-runs current resource authorization.
- Create an incoming-technician notification for first assignment and reassignment in the same transaction as the assignment operation.
- Create SLA at-risk/breach notifications only for recipients approved in the SLA policy. First-entry threshold delivery must be idempotent across repeated scheduler runs.
- Do not leak another recipient, customer organisation, internal note, private technician data, or now-inaccessible work-order data through notification payloads or counts.
- Use bounded, paginated inbox queries and a durable uniqueness/deduplication mechanism for events that require idempotency.

Email, SMS, mobile push, browser push, and webhook delivery are not part of the accepted channel decision. This ADR does not decide notification wording, every trigger, the recipient set, retention, polling interval, or reversible unread behavior.

## Consequences

### Positive

- No external delivery provider or credential is required for the initial scope.
- Assignment and notification records can commit or roll back together.
- Recipient scoping and read state can be tested through the same authenticated API.
- Idempotency prevents repeated scheduler scans from flooding users.

### Costs and constraints

- Users receive notifications only while using or refreshing the application.
- Persistence needs recipient/read/time indexes and retention policy.
- Every notification link needs current authorization rather than trusting historical inbox ownership.

## Alternatives Considered

### Email

Not selected. It adds a provider, credentials, delivery failures, retries, and policy not required by the project.

### Push, SMS, or webhooks

Not selected because they require additional infrastructure and recipient policy.

### Ephemeral client-only alerts

Rejected. They cannot provide a durable recipient inbox or reliable assignment/SLA evidence.

## Follow-up and Open Questions

Final assignment/SLA recipients, retention/privacy, read-state behavior, pagination/unread-count semantics, wording/localization, and UI refresh mechanism remain Q-008, Q-017, and Q-025 in `../ASSUMPTIONS.md`.

## References

- `../API_CONTRACT.md`
- `../ERD.md`
- `../TEST_MATRIX.md`
- `../reference/keystone_codex_build_playbook.md`
