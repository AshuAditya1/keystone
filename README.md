# KEYSTONE — Field Service Management Platform

KEYSTONE is the field-service platform **Meridian Facilities Management** uses to run its maintenance operation — from the moment a customer reports a problem to the moment the job is closed. Dispatchers raise and assign work orders, technicians update them from the field, managers watch SLAs and dashboards, and customers log requests and track progress on their own sites.

This is a monorepo: a **Spring Boot 3 (Java 21)** REST API, a **React + TypeScript (Vite)** single-page app, and a **PostgreSQL** database with **Flyway**-managed schema.

> **Just want it running on your machine?** Start with [`QUICKSTART.md`](QUICKSTART.md) — the exact commands for Windows with Node + JDK 21, including the database setup. (And note: VS Code's "Go Live" cannot run this app — React/TypeScript needs a compile step. Use `npm run dev`.)
>
> **New here?** Read [`docs/PLAYBOOK.md`](docs/PLAYBOOK.md) — it walks through installing every tool from scratch, configuring GitHub and Render, and deploying.

---

## Build status

| Milestone | Scope | State |
|---|---|---|
| **M1 — Foundation** | Auth (JWT) + RBAC, domain model, Flyway migrations, seed data, running skeleton | ✅ Complete |
| **M2 — Core work orders** | Customer/site management, work-order CRUD, Kanban board, list/filter/search/paging | ✅ Complete |
| **M3 — Workflow & rules** | Lifecycle state machine, dispatch, transactional parts/time, SLA tracking, notifications | ✅ Complete |
| **M4 — Productize** | Manager dashboard, customer portal, user admin, OpenAPI docs, deployment | ✅ Complete |

**What "complete" means here, stated plainly:** every line of all four milestones is written and internally consistent, and the repo passes the static checker described under [Verification](#verification). It has **not** been compiled or run — the environment it was authored in has no JDK compiler, Maven, Node, or database. The first `mvn` and `npm` build happens on your machine. See [First build](#first-build-read-this-before-you-report-a-bug).

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Back-end framework | Spring Boot 3.3.4 (Web, Validation) |
| Security | Spring Security 6 + JWT (stateless, `jjwt` 0.12.6) |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway (schema is code, never hand-edited) |
| API docs | springdoc-openapi (Swagger UI) |
| Front end | React 18 + TypeScript 5.6 (Vite 5), react-router 6, axios |
| Build | Maven (backend), npm (frontend) |
| Containers / deploy | Docker, Docker Compose, Render |

## Architecture

A layered Spring Boot service behind a React SPA. Requests flow browser → controller → service → repository → PostgreSQL.

- **Controllers** — HTTP only: bind and validate input, map DTOs, delegate. No business logic.
- **Services** — business rules, the work-order state machine, SLA logic, transaction boundaries.
- **Repositories** — Spring Data JPA; queries scoped so a customer can only ever read their own data.
- **Persistence** — PostgreSQL with Flyway migrations. No schema changes by hand.

Principles: thin controllers, rich services; **DTOs at the boundary** (JPA entities are never serialised to clients); transactions guard multi-step invariants (status, history row, and stock commit together or not at all); and **all authorization is re-checked server-side** — a hidden button is never the security boundary.

## Repository structure

```
keystone/
  backend/                        # Spring Boot service
    src/main/java/com/meridian/keystone/
      config/                     # app configuration beans
      security/                   # JWT, filters, Spring Security config
      auth/                       # login / me endpoints + DTOs
      domain/                     # JPA entities + enums (incl. the state machine)
      dto/                        # request/response DTOs
      repository/                 # Spring Data JPA repositories + Specifications
      service/                    # business logic, state machine, SLA sweep
      controller/                 # thin REST controllers
      exception/                  # global error handling
      common/                     # shared helpers (PageableFactory, etc.)
    src/main/resources/
      application.yml             # config (default + prod profiles)
      db/migration/               # Flyway scripts (V1__, V2__, V3__)
    Dockerfile
  frontend/                       # React + TypeScript (Vite)
    src/
      components/ui.tsx           # shared presentational vocabulary
      pages/                      # one file per screen
      endpoints.ts                # every HTTP call the app makes, in one place
      api.ts  auth.tsx  format.ts  types.ts  index.css
    Dockerfile
    nginx.conf
  docs/PLAYBOOK.md                # setup + deployment + submission guide
  scripts/                        # run / stop / fresh-db / verify / static-check
  docker-compose.yml              # local stack: db + backend + frontend
  render.yaml                     # Render blueprint
  .env.example
```

---

## Prerequisites

- **Docker Desktop** (the only hard requirement for the quick start).
- For local development without containers: **JDK 21**, **Maven 3.9+**, **Node.js 20 or 22**.

Full Windows-from-scratch install instructions are in [`docs/PLAYBOOK.md`](docs/PLAYBOOK.md) Part 1.

## Quick start (Docker — the clean-checkout path)

From a fresh clone, one command builds and runs the database, backend, and frontend. Flyway creates the schema and loads seed data automatically on first boot.

**The easy way — one helper script** (waits until healthy, then prints the URLs and logins):

```powershell
.\scripts\run.ps1        # Windows / PowerShell
./scripts/run.sh         # macOS / Linux
```

**Or the raw command it wraps:**

```bash
docker compose up --build
```

Then open:

- **Frontend:** http://localhost:3000
- **Backend health:** http://localhost:8080/api/health
- **Swagger UI:** http://localhost:8080/swagger-ui.html

Confirm auth and RBAC work end-to-end with the smoke test:

```powershell
.\scripts\verify.ps1     # (verify.sh on macOS/Linux)
```

To wipe everything and prove a truly clean start:

```bash
docker compose down -v && docker compose up --build
# or:  .\scripts\fresh-db.ps1   (wipes, boots db+backend, shows the Flyway log)
```

All helper scripts are documented in [`scripts/README.md`](scripts/README.md).

## First build (read this before you report a bug)

This codebase was written without a compiler available, so the very first `mvn` and `npm` run is also the first time a compiler has ever seen it. That is an unusual situation and it deserves an honest warning rather than a confident claim that everything works.

What has been verified: package/class-name agreement across all Java sources, brace balance, no unused imports, no unresolved intra-project imports, that every endpoint the frontend calls exists on a controller, that every CSS class the components use has a rule, that every sort key the UI offers is on the server's whitelist, and that no secrets are committed. What has **not** been verified: type checking, generics, Spring bean wiring at runtime, and SQL execution.

If the first build fails, it will almost certainly be a small mechanical fix (a missing import, a type mismatch) rather than a design problem. Build the backend first, since it is the larger surface:

```bash
cd backend && mvn -q clean package        # then read the first error only
cd ../frontend && npm install && npm run build
```

Fix the top error, re-run, repeat. Resist fixing errors further down the list first — one missing import can produce dozens of downstream complaints that vanish on their own.

## Local development (without full Docker)

Run just the database in Docker, and the apps natively for hot reload:

```bash
# 1) database only
docker compose up -d db

# 2) backend (in backend/)
cd backend
mvn spring-boot:run          # serves http://localhost:8080

# 3) frontend (in frontend/)
cd frontend
npm install
npm run dev                  # serves http://localhost:5173
```

The default Spring profile points at `localhost:5432` with the credentials below, so this works out of the box.

## Environment variables

| Variable | Used by | Default (local) | Notes |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | backend | `jdbc:postgresql://localhost:5432/keystone` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | backend | `keystone` | |
| `SPRING_DATASOURCE_PASSWORD` | backend | `keystone` | |
| `JWT_SECRET` | backend | dev placeholder | **Use a long random value** outside local (`openssl rand -base64 48`) |
| `JWT_EXPIRATION_MINUTES` | backend | `120` | token lifetime |
| `APP_CORS_ALLOWED_ORIGINS` | backend | `http://localhost:3000,http://localhost:5173` | comma-separated origins |
| `VITE_API_BASE_URL` | frontend | `/api` | baked in at build time; must be the backend's public URL + `/api` on Render |

No secret has a committed value. The JWT secret and database credentials are read from the environment only; there is a static check that fails the build if a literal creeps in.

On Render, the `prod` profile builds the JDBC URL from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (wired from the managed database). See `render.yaml` and [`docs/PLAYBOOK.md`](docs/PLAYBOOK.md) Part 2.

## Database & migrations

The schema is owned entirely by **Flyway** — every change is a versioned SQL script in `backend/src/main/resources/db/migration`, applied automatically on startup. Nothing is created by Hibernate at runtime (`ddl-auto: none`), so the database is identical everywhere.

- `V1__init_schema.sql` — tables, foreign keys, constraints, indexes.
- `V2__seed_data.sql` — demo data (users, customers, sites, parts, sample work orders).
- `V3__sla_notifications.sql` — SLA fields and the notifications table.

To change the schema, add `V4__…sql`. Never edit an applied migration: Flyway checksums them and will refuse to start.

To reset the database completely, wipe the Docker volume: `docker compose down -v`.

## Seed logins

All seed users share the password **`ChangeMe123!`** — demo credentials for local and review use only. See [Before real use](#before-real-use).

| Role | Email | Can do |
|---|---|---|
| Manager / Admin | `manager@meridian.dev` | Everything: close jobs, manage users and parts, dashboard |
| Dispatcher | `dispatcher@meridian.dev` | Create customers/sites/work orders; assign; see all jobs and the board |
| Technician | `tech1@meridian.dev` | See assigned jobs; start/hold/complete; log parts and time |
| Technician | `tech2@meridian.dev` | (second technician, for reassignment demos) |
| Customer | `alice@acme.dev` | Raise requests for their own sites; view only their own work orders |

## API reference

Interactive Swagger UI is at `/swagger-ui.html`; the raw spec at `/v3/api-docs`. Public endpoints are `POST /api/auth/login`, `GET /api/health`, and the Swagger routes. Everything else requires `Authorization: Bearer <token>`.

In the table, **any** means any authenticated user — which is not the same as unrestricted: those handlers are scoped in the service layer, so a customer requesting `GET /api/work-orders` receives only their own jobs, and a technician receives only jobs assigned to them. Scoping a read is authorization too, it just cannot be expressed as a role annotation.

| Method | Path | Allowed |
|---|---|---|
| `POST` | `/api/auth/login` | public |
| `GET` | `/api/auth/me` | any |
| `GET` | `/api/health` | public |
| `GET` | `/api/customers` | any (scoped) |
| `GET` | `/api/customers/{id}` | any (scoped) |
| `GET` | `/api/customers/{id}/sites` | any (scoped) |
| `POST` | `/api/customers` | manager |
| `PUT` | `/api/customers/{id}` | manager |
| `DELETE` | `/api/customers/{id}` | manager |
| `GET` | `/api/sites` | any (scoped) |
| `GET` | `/api/sites/{id}` | any (scoped) |
| `POST` | `/api/sites` | manager, dispatcher |
| `PUT` | `/api/sites/{id}` | manager, dispatcher |
| `DELETE` | `/api/sites/{id}` | manager |
| `GET` | `/api/work-orders` | any (scoped) |
| `GET` | `/api/work-orders/board` | any (scoped) |
| `GET` | `/api/work-orders/my` | any (own jobs) |
| `GET` | `/api/work-orders/{id}` | any (scoped) |
| `POST` | `/api/work-orders` | manager, dispatcher, customer |
| `PUT` | `/api/work-orders/{id}` | manager, dispatcher |
| `POST` | `/api/work-orders/{id}/assign` | manager, dispatcher |
| `POST` | `/api/work-orders/{id}/unassign` | manager, dispatcher |
| `POST` | `/api/work-orders/{id}/transition` | any (per-transition rules in service) |
| `POST` | `/api/work-orders/{id}/parts` | manager, technician |
| `DELETE` | `/api/work-orders/{id}/parts/{lineId}` | manager, technician |
| `POST` | `/api/work-orders/{id}/time` | manager, technician |
| `GET` | `/api/parts` | manager, dispatcher, technician |
| `GET` | `/api/parts/catalog` | manager, dispatcher, technician |
| `GET` | `/api/parts/{id}` | manager, dispatcher, technician |
| `POST` | `/api/parts` | manager |
| `PUT` | `/api/parts/{id}` | manager |
| `DELETE` | `/api/parts/{id}` | manager |
| `GET` | `/api/users` | manager |
| `GET` | `/api/users/technicians` | manager, dispatcher |
| `GET` | `/api/users/{id}` | manager |
| `POST` | `/api/users` | manager |
| `PUT` | `/api/users/{id}` | manager |
| `GET` | `/api/dashboard/summary` | manager |
| `GET` | `/api/notifications` | any (own) |
| `GET` | `/api/notifications/unread-count` | any (own) |
| `POST` | `/api/notifications/{id}/read` | any (own) |
| `POST` | `/api/notifications/read-all` | any (own) |

Paged endpoints accept `page`, `size` (max 100), and `sort=field,dir`. The sort field must be on that endpoint's whitelist — an unlisted field is a `400` rather than a silent default, so a typo surfaces immediately instead of quietly returning the wrong order.

## Work-order lifecycle

```
NEW ─▶ ASSIGNED ─▶ IN_PROGRESS ─▶ COMPLETED ─▶ CLOSED (terminal)
 │        │  └─ (unassign)  └─▶ ON_HOLD ─▶ IN_PROGRESS
 │        │                       (and COMPLETED ─▶ IN_PROGRESS to reopen)
 └────────┴──────────────────────▶ CANCELLED (terminal)
```

The permitted edges, which is the authoritative version — `WorkOrderStatus` holds exactly this table:

| From | May move to |
|---|---|
| `NEW` | `ASSIGNED`, `CANCELLED` |
| `ASSIGNED` | `IN_PROGRESS`, `NEW` (unassign), `CANCELLED` |
| `IN_PROGRESS` | `ON_HOLD`, `COMPLETED`, `CANCELLED` |
| `ON_HOLD` | `IN_PROGRESS`, `CANCELLED` |
| `COMPLETED` | `CLOSED`, `IN_PROGRESS` (reopen) |
| `CLOSED` | — terminal |
| `CANCELLED` | — terminal |

A transition passes through two gates, and the distinction matters because the two failures mean different things to the caller:

1. **Structural** — is this edge in the table at all? `NEW → COMPLETED` is not, and no role can make it so. Rejected with **409 Conflict**.
2. **Authorization** — may *you* make this legal move? Only a manager may `CLOSE`; only the assigned technician (or a manager) may start, hold, or complete. Rejected with **403 Forbidden**.

A `409` says "the request is impossible"; a `403` says "the request is possible but not by you." Collapsing them into one status would leave the client unable to tell a bug from a permission problem.

The set of transitions currently available to the calling user is computed on the server and returned with each work order, so the UI renders buttons from the server's answer rather than reimplementing the rules and drifting out of step.

## Business rules

These are enforced in the service layer, inside a transaction, and none of them depend on the UI:

- **Work must be logged before a job can be completed.** Completing with no parts and no time recorded is rejected — a completed job with no evidence of work is not a record anyone can bill or audit.
- **Stock can never go negative.** Consuming parts locks the row (`PESSIMISTIC_WRITE`) so two technicians claiming the last widget cannot both succeed; the second gets a clear error instead of stock at `-1`.
- **Parts and time can only be logged while a job is `IN_PROGRESS` or `ON_HOLD`.** Logging against a closed job would silently change a historical total.
- **Status history is append-only.** Every transition writes a row with who, when, from, and to. Nothing updates or deletes history, so the audit trail cannot be rewritten.
- **Totals are recomputed from the ledger, never incremented.** A cached counter drifts the first time a line is removed; recomputing costs a query and is always right.
- **Technicians cannot raise work orders**, and a customer can only raise one against a site they own.
- **A customer account must be linked to a customer record**, otherwise "their own data" has no meaning and the scoping rules have nothing to filter on.

## SLA tracking

Each work order gets a deadline from its priority when it is created. A scheduled sweep classifies every open job as `ON_TRACK`, `AT_RISK`, or `BREACHED` and writes a notification when a job crosses a threshold, so a breach announces itself instead of waiting to be noticed.

A completed job keeps the verdict it earned at completion. Recomputing it later would quietly turn last month's breach into a pass once the deadline receded into the past, which would make the metric useless precisely when someone is relying on it.

## Verification

Because the toolchain used to author this repo had no compiler, a static checker stands in for one and enforces the things a reviewer would otherwise have to check by eye:

```bash
python3 scripts/static-check.py .
```

It covers Java package/class agreement, brace balance, unused and unresolvable imports, TypeScript brace balance and unused imports, and four cross-cutting rules that no single-language tool can see:

- **X1** every path the frontend calls exists on a controller, and every controller route is reachable from the frontend.
- **X2** every CSS class the components use has a rule, and every rule is used. This caught a real bug: low-stock rows were rendering with a `low` class that had no styling, so the warning was invisible.
- **X3** every sort key the UI offers is on the server's whitelist, since an unlisted key is a runtime `400` that only appears when someone clicks that column.
- **S1** no secrets committed. A line can be exempted with a `keystone:allow-secret <reason>` comment on it or immediately above it; the reason is required, so every exemption has to be argued for in the diff instead of waved through.

Each check has been tested against a deliberately broken copy of the repo to confirm it actually fires — a check that silently matches nothing passes every time and protects nothing.

## Testing

```bash
cd backend
mvn test
```

Tests concentrate on the rules that would be expensive to get wrong: authentication, the authorization matrix, the lifecycle transitions, and the transactional parts/stock path.

## Deployment

Deployed on **Render** as three services — managed PostgreSQL, the backend web service (Docker), and the frontend static site. Step-by-step instructions, including every environment variable and the two settings people usually miss (`VITE_API_BASE_URL` at build time, and adding the static site's URL to `APP_CORS_ALLOWED_ORIGINS`), are in [`docs/PLAYBOOK.md`](docs/PLAYBOOK.md) Part 2.

## Before real use

This is a portfolio and training build. Two things are deliberately convenient for a reviewer and would be indefensible with real tenants on the system:

1. **Delete the seed users and rotate the demo password.** The demo password has been rotated to `ChangeMe123!` across all seed data, scripts, and the login page. A migration (`V5__remove_seed_data.sql`) is prepared with commented-out SQL to delete the seed accounts — uncomment it before deploying to a real production environment.
2. **Set a real `JWT_SECRET` and keep it out of the repo.** The local default is a development placeholder. Anyone holding it can mint a valid token for any user, including a manager.

The SLA sweep job is now protected by a ShedLock distributed lock (`V4__shedlock.sql`), so running multiple backend replicas will not produce duplicate notifications.

---

*Project KEYSTONE · Zidio Development — Java Full-Stack Engineering Brief.*
