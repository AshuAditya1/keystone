# Project KEYSTONE Architecture Decision Records

## Purpose

Architecture Decision Records (ADRs) capture durable engineering choices that affect the structure, security, data integrity, or operation of Project KEYSTONE. An ADR explains the context, the selected decision, its consequences, and the alternatives considered.

ADRs do not replace the product brief, `PROJECT_SPEC.md`, `STATE_MACHINE.md`, `ACCESS_MATRIX.md`, or `ASSUMPTIONS.md`. Product requirements stay in the specification; unresolved or reversible policy choices stay in the assumptions register. If sources disagree, the precedence in `AGENTS.md` applies.

This numbered index supersedes the older `ADR-001` through `ADR-004` filenames embedded in the reference playbook. Those obsolete records must not be recreated. SLA policy and attachment storage remain assumptions/open questions unless a later explicit request authorizes a new ADR.

## Status Definitions

| Status | Meaning |
|---|---|
| `Proposed` | Under review; not yet authoritative. |
| `Accepted` | Approved and binding for implementation until superseded. |
| `Superseded` | Replaced by a newer ADR; retained for historical context and linked to its replacement. |
| `Deprecated` | Still recorded but should not be used for new work. |
| `Rejected` | Considered and explicitly not adopted. |

An accepted ADR is changed by adding a new ADR that supersedes it, not by silently rewriting the historical decision after implementation has begun. Corrections made during this planning-only baseline may update an ADR before application code exists.

## Naming Convention

Use a four-digit, zero-padded sequence followed by a short kebab-case subject:

```text
NNNN-short-decision-name.md
```

Examples: `0002-stateless-jwt.md`, `0004-flyway-only-schema-management.md`.

Numbers are never reused. The first heading uses `# NNNN: Decision title`.

## ADR Index

| ADR | Status | Decision |
|---|---|---|
| [0001: Monorepo](0001-monorepo.md) | Accepted | Keep the backend, frontend, documentation, and delivery assets in one repository. |
| [0002: Stateless JWT](0002-stateless-jwt.md) | Accepted | Authenticate API requests with expiring stateless bearer access JWTs. |
| [0003: Service-layer state machine](0003-service-layer-state-machine.md) | Accepted | Enforce lifecycle, actor, audit, and transactional invariants in the service/domain layer. |
| [0004: Flyway-only schema management](0004-flyway-only-schema-management.md) | Accepted | Let Flyway alone manage PostgreSQL schema; Hibernate validates it. |
| [0005: In-app notifications](0005-in-app-notifications.md) | Accepted | Deliver required project notifications in the authenticated application. |

## Reusable ADR Template

```markdown
# NNNN: Decision title

- **Status:** Proposed
- **Date:** YYYY-MM-DD
- **Decision owners:** Names or team
- **Supersedes:** None, or ADR link

## Context

Describe the problem, constraints, and why a durable decision is needed.

## Decision

State the selected decision precisely, including its boundaries.

## Consequences

### Positive

- Benefit.

### Costs and constraints

- Cost or trade-off.

## Alternatives Considered

### Alternative name

Explain why it was not selected.

## Follow-up and Open Questions

Link unresolved product or implementation policy to `../ASSUMPTIONS.md`; do not resolve it implicitly in this ADR.

## References

- Source requirement or repository contract.
```
