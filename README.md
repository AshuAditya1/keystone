# Project KEYSTONE

Project KEYSTONE is Meridian Facilities Management's field-service management platform. This
repository is a monorepo containing a Java 21 Spring Boot backend, a React/TypeScript/Vite
frontend, PostgreSQL local infrastructure, and the governing specifications in `docs/`.

Day 1 intentionally contains only the runnable foundation and health connectivity. It does not
contain authentication, JWT handling, users, roles, customers, sites, work orders, or other
business functionality.

## Repository layout

```text
keystone/
|-- backend/             Spring Boot API foundation
|-- frontend/            React application foundation
|-- docs/                Product, security, API, data, and test contracts
|-- docker-compose.yml   Local PostgreSQL
|-- .env.example         Non-secret local configuration example
|-- AGENTS.md            Engineering guardrails
`-- TASKS.md             M1-M4 delivery plan
```

## Prerequisites

- Java 21 (`java` and `javac`)
- Docker Desktop or Docker Engine with Compose v2
- Node.js 20.19 or newer and npm

The Maven Wrapper downloads the required Maven distribution. On Windows PowerShell, use
`npm.cmd` if the local execution policy blocks `npm.ps1`.

## Windows PowerShell setup

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up -d db
docker compose ps db
```

Wait until the database reports `healthy`, then start the backend in one PowerShell window:

```powershell
& .\backend\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The backend health endpoint is `http://localhost:8080/actuator/health`. Local Swagger UI is
`http://localhost:8080/swagger-ui.html`. PostgreSQL is published on local port `5433` by default to
avoid collisions with an independently installed PostgreSQL service; `POSTGRES_PORT` and `DB_URL`
remain overridable.

Install and start the frontend in another PowerShell window:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run dev
```

Open `http://localhost:5173`. The System health page calls the backend through the typed Axios
client and displays loading, success, or error state.

## Unix-like shell setup

From the repository root:

```bash
cp .env.example .env
docker compose config
docker compose up -d db
docker compose ps db
```

Wait until the database reports `healthy`, then start the backend in one terminal:

```bash
./backend/mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Install and start the frontend in another terminal:

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open `http://localhost:5173`. The backend health endpoint is
`http://localhost:8080/actuator/health`.

## Verification commands

### Windows PowerShell

```powershell
java -version
& .\backend\mvnw.cmd --version
& .\backend\mvnw.cmd spotless:check
& .\backend\mvnw.cmd clean verify
& .\backend\mvnw.cmd package

npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run format
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build

docker compose config
docker build -t keystone-backend:day1 .\backend
docker build -t keystone-frontend:day1 .\frontend
```

### Unix-like shells

```bash
java -version
./backend/mvnw --version
./backend/mvnw spotless:check
./backend/mvnw clean verify
./backend/mvnw package

npm --prefix frontend ci
npm --prefix frontend run format
npm --prefix frontend run lint
npm --prefix frontend test -- --run
npm --prefix frontend run build

docker compose config
docker build -t keystone-backend:day1 ./backend
docker build -t keystone-frontend:day1 ./frontend
```

Backend integration tests use PostgreSQL through Testcontainers. Docker must be running; the
tests deliberately do not substitute an in-memory database.

## Configuration profiles

- `local`: safe local PostgreSQL and CORS defaults, all overridable through environment variables.
- `test`: PostgreSQL supplied by Testcontainers; no in-memory database.
- `production`: requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `CORS_ALLOWED_ORIGINS`.
  Swagger/OpenAPI is disabled unless `OPENAPI_ENABLED=true`.

Vite reads `VITE_API_BASE_URL` from the root environment or root `.env` file. The typed client has
the safe local fallback `http://localhost:8080`; production image builds should pass the deployed
API URL explicitly:

```powershell
docker build --build-arg VITE_API_BASE_URL=https://api.example.invalid -t keystone-frontend:day1 .\frontend
```

No production credential, database password, or JWT secret is committed. JWT configuration does
not exist yet because authentication is outside the Day 1 task.

## Temporary Day 1 security

Spring Security remains enabled and stateless. Only these paths are public:

- `/actuator/health` and its health-group subpaths
- `/v3/api-docs/**`
- `/swagger-ui.html` and `/swagger-ui/**`

Every other request is denied. CORS permits only configured origins and read-only health/docs
methods. This temporary policy is replaced by the authenticated default-deny policy during the
approved M1 authentication task; it is not an authentication implementation.

## Stopping local services

Stop and remove Compose containers and networks while preserving the named PostgreSQL volume:

```powershell
docker compose down
```

The command is identical in Unix-like shells. Removing the named volume is intentionally not part
of the standard command because it destroys local database data.

## Source of truth

Read `AGENTS.md` before implementation work. Product scope, access rules, lifecycle behavior, API
contracts, data constraints, test obligations, decisions, assumptions, and milestone order remain
defined by the documents under `docs/` and `TASKS.md`.
