# 0001: Monorepo

- **Status:** Accepted
- **Date:** 2026-08-04
- **Decision owners:** Project KEYSTONE engineering
- **Supersedes:** None

## Context

KEYSTONE must deliver a Spring Boot API, a React web application, shared contracts, tests, database change history, and deployment documentation within a coordinated 20-day plan. Frontend and backend changes frequently share one API, lifecycle, and authorization contract.

The build playbook selects a monorepo and a modular monolith. The brief does not require independently deployed domain services or separate release trains.

## Decision

Use one repository containing:

- a Java 21 / Spring Boot 3 backend application under `backend/` when implementation begins;
- a React / TypeScript / Vite frontend application under `frontend/` when implementation begins;
- planning, API, architecture, deployment, and review documentation under `docs/`;
- root-level delivery and local-development assets; and
- one coordinated version-control history for cross-cutting changes.

The backend is a modular monolith: one deployable Spring Boot process and one PostgreSQL system of record, organized into coherent feature areas. It is not split into microservices, and it does not introduce event streaming or distributed transactions.

Repository placement does not weaken module boundaries. API contracts, tests, database changes, and affected client code must change coherently. Business transactions and lifecycle ownership are governed separately by [0003](0003-service-layer-state-machine.md).

## Consequences

### Positive

- Cross-layer contract changes can be reviewed and versioned together.
- One build context simplifies the 20-day delivery and clean-checkout verification.
- A single process and database permit local transactions for lifecycle, audit, assignment, and stock invariants.
- Documentation and acceptance evidence stay beside the implementation they describe.

### Costs and constraints

- Module boundaries rely on disciplined package design and tests rather than deployment isolation.
- Frontend and backend release cadence is initially coupled.
- The backend and its database scale as a unit unless a later measured need justifies another ADR.

## Alternatives Considered

### Separate frontend and backend repositories

Rejected. It would add coordination overhead without a supplied independent ownership or release requirement.

### Microservices

Rejected for the required scope. They add network failure modes, distributed consistency, and deployment complexity without a corresponding product need.

## Follow-up and Open Questions

Hosting provider, runtime topology, domains, TLS, backups, observability, and recovery objectives remain open in Q-016 of `../ASSUMPTIONS.md`. They do not change the monorepo decision.

## References

- `../reference/keystone_codex_build_playbook.md`
- `../PROJECT_SPEC.md`
- `../../AGENTS.md`
