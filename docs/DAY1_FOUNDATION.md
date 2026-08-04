# Day 1 Foundation

## Scope

The Day 1 implementation establishes a runnable monorepo only. It deliberately contains no
business entity, business controller, authentication endpoint, JWT implementation, role logic,
seed user, work-order behavior, or later-milestone interface.

## Backend foundation

- Base package: `com.keystone` (the documented fallback because no package was previously fixed).
- Java 21 and Spring Boot 3.5 with Maven Wrapper.
- Spring Web, Bean Validation, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Actuator, and
  springdoc OpenAPI.
- `local`, `test`, and `production` configuration profiles.
- Flyway is the only schema owner and Hibernate always uses `ddl-auto=validate`.
- `V1__create_schema_metadata.sql` creates only a metadata table and inserts the Day 1 baseline
  marker. It does not create any business schema.

The production profile has no database-credential or CORS defaults. Local defaults match the
non-secret Docker Compose development values and can be overridden from the environment.

## Temporary security boundary

Spring Security is active with stateless session management. Health and locally enabled
OpenAPI/Swagger routes are explicitly permitted. Every other request is denied. HTTP Basic, form
login, and logout endpoints are disabled because the project has not implemented authentication.

CORS origins come from `CORS_ALLOWED_ORIGINS`. The temporary CORS policy permits only `GET`,
`HEAD`, and preflight `OPTIONS`, which is sufficient for health and documentation access. This
configuration must be replaced—not broadened—by the Prompt 3 stateless JWT and RBAC work.

## Frontend foundation

The frontend uses the approved React/TypeScript/Vite stack and contains one route. Its typed Axios
module calls `/actuator/health` using `VITE_API_BASE_URL`. TanStack Query drives the loading,
success, and failure states. No authentication page, dashboard, work-order page, or role-aware
navigation exists.

## Verification boundary

PostgreSQL-specific startup and migration tests use Testcontainers. The live acceptance sequence
is Docker Compose database health, backend Flyway/Hibernate startup, Actuator health, frontend
startup, and browser confirmation of the healthy status. Exact commands are maintained in the
root `README.md`.

## Verification evidence (2026-08-05)

- Java 21.0.11 and Maven Wrapper 3.9.14 were verified.
- `docker compose config` passed; PostgreSQL 17 became healthy on the configured local port.
- `mvnw.cmd clean verify` passed with three PostgreSQL/Testcontainers integration tests. Flyway
  applied V1, Hibernate validation completed, health and OpenAPI returned `200`, and the temporary
  default-deny route returned `403`.
- Live CORS allowed `http://localhost:5173`; an unapproved origin received `403` without an
  `Access-Control-Allow-Origin` header.
- `npm ci`, Prettier check, ESLint, three Vitest component tests, and the Vite production build
  passed.
- Both multi-stage Dockerfiles built successfully as `keystone-backend:day1` and
  `keystone-frontend:day1`.
- Vite served the application successfully on port 5173. The in-app browser runtime returned
  `No browser is available`, so the final visual confirmation of the live success message remains
  a manual check. Component tests cover loading, success, and failure rendering.

`npm audit` reports one high-severity React Router advisory as two affected package entries for
React Router 7.18.2. The advisory applies to RSC action mode, which this client-only BrowserRouter
application does not use. The older version suggested by npm reintroduces multiple other
high-severity advisories, so no forced downgrade or audit rewrite was applied. Recheck for a patched
current release before the next frontend dependency update.
