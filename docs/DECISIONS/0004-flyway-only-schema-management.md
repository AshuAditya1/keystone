# 0004: Flyway-only schema management

- **Status:** Accepted
- **Date:** 2026-08-04
- **Decision owners:** Project KEYSTONE engineering
- **Supersedes:** None

## Context

KEYSTONE uses PostgreSQL and Spring Data JPA. Schema creation and evolution must be reviewable, repeatable from an empty database, and safe across deployed versions. Runtime-generated Hibernate DDL would make the effective schema dependent on startup behavior rather than an ordered change history.

## Decision

Flyway is the only schema-management mechanism in shared, CI, staging, demonstration, and production-like environments.

- Every table, column, constraint, index, database permission, and controlled seed change is introduced by an ordered Flyway migration.
- Hibernate uses `spring.jpa.hibernate.ddl-auto=validate`; it never uses `create`, `update`, or `create-drop` in a shared/runtime profile.
- Application startup fails when entity mappings and the migrated schema disagree.
- Migrations are tested on a brand-new supported PostgreSQL database and through Flyway `validate`.
- Once a migration is shared or applied outside an isolated pre-baseline workspace, its checksum-bearing content is immutable. Corrections use a new migration.
- Database constraints back critical invariants such as customer/site coherence, unique normalized keys, positive usage/time, and non-negative stock. Service validation and concurrency control remain the first line of defense.
- PostgreSQL-specific transactions, constraints, locks, and indexes are tested against PostgreSQL rather than substituted with an in-memory database.

This ADR defines policy only; it does not create a migration or authorize application implementation during Prompt 0.

## Consequences

### Positive

- A clean database can be reproduced and audited from version-controlled migrations.
- Schema drift fails visibly instead of being silently repaired by Hibernate.
- Upgrades and rollback planning have an explicit ordered history.
- Integrity constraints remain consistent across API, background, and administrative access paths.

### Costs and constraints

- Every schema change requires a deliberate migration and compatibility review.
- Incorrect released migrations cannot be edited in place.
- Test setup must provide PostgreSQL and run migrations before Hibernate validation.

## Alternatives Considered

### Hibernate `create`, `update`, or `create-drop`

Rejected for shared/runtime environments because it is not a controlled production migration history.

### Hand-applied SQL outside version control

Rejected because it is not reproducible or reviewable from a clean checkout.

### Multiple schema-management tools

Rejected. Competing ownership creates ordering and drift ambiguity.

## Follow-up and Open Questions

Whether production runs Flyway during application startup or as a separate release job remains part of Q-016 in `../ASSUMPTIONS.md`. Either option must preserve Flyway as the sole schema owner.

## References

- `../ERD.md`
- `../TEST_MATRIX.md`
- `../reference/keystone_codex_build_playbook.md`
