# Project KEYSTONE — Build Playbook

Your complete, step-by-step guide to taking **Project KEYSTONE** (a field-service management platform) from an empty Windows machine to a deployed, graded submission.

> **Read this first — what is already done, and what is left for you.**
>
> **All four milestones are written.** Every entity, endpoint, business rule, screen, and migration for M1–M4 is in the `keystone/` folder. You are not building the application from prompts; that part is finished.
>
> **What is left is the part only you can do**, because it needs your machine and your accounts:
>
> 1. **Install the tools** — Part 1. Nothing else works until this is done.
> 2. **Run the first build** and fix any compile errors. The code was authored in an environment with no Java compiler, so your first `mvn package` is the first time a compiler has seen it. Expect a small number of mechanical fixes, not design problems. The README section "First build" explains how to work through them.
> 3. **Get it running locally** — Part 2.2, then confirm with `scripts/verify.ps1`.
> 4. **Push to GitHub and deploy to Render** — Part 2.3, which is a complete runbook including the four things that usually go wrong.
> 5. **Record the demo and submit** — Part 4.
>
> Part 3 (the prompt library) is kept as **reference**: it documents what each milestone was supposed to contain and the reasoning behind it. Read it to understand the code and to answer questions about it — you no longer need it to generate anything.

---

## Part 0 — What you are building and how the marks work

KEYSTONE replaces spreadsheets and phone calls with one system where **dispatchers** raise and assign work orders, **technicians** update them from the field, **managers** watch SLAs and dashboards, and **customers** log requests and track progress.

The single most important idea in the whole brief: **the rules live on the server, never only in the UI.** The work-order lifecycle (which status can move to which) and the access rules (who can see/do what) must be enforced in the Spring Boot service layer and re-checked on every request. Graders will call your API directly to try to break these. This is where most of the marks are.

**How the 70 marks this brief governs break down:**

| Component | Marks | What earns them |
|---|---|---|
| M1 Foundation | 10 | Clean layered structure, working JWT/RBAC, sound entities, migrations that run on a fresh DB |
| M2 Core work orders | 10 | Validated CRUD, a usable Kanban board, list/filter/search/pagination |
| M3 Workflow & rules | **20** | Correctly enforced lifecycle, server-side authorisation, transactional parts/time, working SLA logic |
| M4 Productize | 10 | Useful dashboard, working customer portal, API docs, **live deployment** |
| Code quality & structure | 5 | Clean layering, DTOs, error handling, meaningful commits, clear README |
| Deployment | 5 | Back end, front end, DB live and reachable with seed logins |
| API documentation | 5 | Complete, accurate OpenAPI/Swagger |
| Demo video | 5 | 3–5 min walkthrough of each role and the lifecycle |

**Golden rules for the whole build**
- Protect Week 3 (lifecycle, security, transactions, SLA) above all — it carries the most marks and is the hardest to fake.
- Deploy a skeleton in Week 1 and keep it live. Never leave deployment to the last day.
- Never commit secrets. DB passwords and the JWT secret come from environment variables.
- Every list endpoint is paginated. Every input is validated on the server. Every error returns a consistent JSON shape, never a stack trace.

---

## Part 1 — Environment setup (Windows, from scratch)

You'll install: a package manager (optional but easier), **JDK 21**, **Node.js**, **Git**, **Docker Desktop**, **PostgreSQL client tools**, and two editors. Do them in order. After each install, run the verify command in a **new** terminal (environment changes only apply to newly opened terminals).

Open **PowerShell** (press `Win`, type "PowerShell", Enter). You do *not* need admin PowerShell for most of this, but some installers will prompt for admin.

### 1.1 (Recommended) Install winget or Chocolatey
Modern Windows 10/11 already ships **winget**. Check:
```powershell
winget --version
```
If that prints a version, use the `winget` commands below. If not, install [App Installer from the Microsoft Store](https://apps.microsoft.com/detail/9nblggh4nns1), or use Chocolatey instead:
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; `
[System.Net.ServicePointManager]::SecurityProtocol = 3072; `
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```
(Chocolatey commands are shown as alternatives where useful. You only need one package manager.)

### 1.2 Install JDK 21 (Eclipse Temurin)
The backend targets **Java 21 LTS**.
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```
*(Chocolatey: `choco install temurin21 -y`)*

Close and reopen PowerShell, then verify:
```powershell
java -version
```
You want to see `openjdk version "21..."`.

**Set `JAVA_HOME`** (many tools need it). Find the install path (usually `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot`), then set it permanently:
```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot", "User")
```
Adjust the version folder to match what's actually installed (run `ls "C:\Program Files\Eclipse Adoptium\"` to see it). Reopen PowerShell and verify: `echo $env:JAVA_HOME`.

**Install Maven** (the backend build tool):
```powershell
winget install Apache.Maven
```
*(Chocolatey: `choco install maven -y`)*

Reopen PowerShell and verify (this needs `JAVA_HOME` set from the step above):
```powershell
mvn -version
```
> You'll run backend commands with `mvn` (e.g. `mvn spring-boot:run`, `mvn test`, `mvn clean package`). Two notes: **IntelliJ IDEA bundles its own Maven**, so you can also just click Run in the IDE without the command line; and the **Docker build image includes Maven**, so deployment needs nothing extra installed.

### 1.3 Install Node.js (LTS)
The frontend uses React + TypeScript with Vite.
```powershell
winget install OpenJS.NodeJS.LTS
```
*(Chocolatey: `choco install nodejs-lts -y`)*

Reopen PowerShell and verify:
```powershell
node -v
npm -v
```
Node 20 or 22 is fine.

### 1.4 Install Git and configure it
```powershell
winget install Git.Git
```
*(Chocolatey: `choco install git -y`)*

Reopen PowerShell, verify and configure your identity (use the email tied to your GitHub account):
```powershell
git --version
git config --global user.name "Aditya Kumar"
git config --global user.email "you@example.com"
git config --global init.defaultBranch main
git config --global core.autocrlf true
```
`core.autocrlf true` avoids Windows/Linux line-ending noise in commits.

### 1.5 Install Docker Desktop
Docker runs PostgreSQL locally without a manual DB install, and packages the app for deployment.
```powershell
winget install Docker.DockerDesktop
```
*(Or download from https://www.docker.com/products/docker-desktop/)*

Then:
1. **Reboot** if prompted (Docker enables the WSL2 / virtualization backend).
2. Launch **Docker Desktop** from the Start menu and let it finish starting (whale icon in the system tray stops animating).
3. Accept the service agreement. You do **not** need a paid plan.
4. Verify in a new PowerShell:
```powershell
docker --version
docker run hello-world
```
If `hello-world` prints a success message, Docker works.

> **If Docker won't start:** enable virtualization in BIOS, and run `wsl --install` in an admin PowerShell, then reboot. Docker Desktop needs WSL2 on Windows Home.

### 1.6 PostgreSQL
You have two options. **Recommended: run Postgres in Docker** (nothing to install, matches deployment, easy reset). The project's `docker-compose.yml` already defines it, so you don't need a separate install.

Optionally install the **psql client** and **pgAdmin** GUI to inspect the database:
```powershell
winget install PostgreSQL.pgAdmin
```
The full PostgreSQL installer (if you prefer a native server instead of Docker) is at https://www.postgresql.org/download/windows/ — during setup set a password you'll remember and keep the default port **5432**. If you install a native Postgres, stop it (or change the Docker port) so both don't fight over 5432.

### 1.7 Editors
- **IntelliJ IDEA Community Edition** — best for the Spring Boot backend.
  ```powershell
  winget install JetBrains.IntelliJIDEA.Community
  ```
- **Visual Studio Code** — best for the React frontend.
  ```powershell
  winget install Microsoft.VisualStudioCode
  ```
  In VS Code, install these extensions: **ESLint**, **Prettier**, and **TypeScript**. Optionally the **Extension Pack for Java** if you want to browse backend code in VS Code too.

### 1.8 Final verification checklist
Open a fresh PowerShell and run each — all should print a version:
```powershell
java -version          # 21.x
echo $env:JAVA_HOME     # path ending in jdk-21...
mvn -version            # Apache Maven 3.9.x, using Java 21
node -v; npm -v         # node 20/22
git --version
docker --version
docker compose version
```
If every line succeeds, your machine is ready.

---

## Part 2 — Accounts & tool configuration

### 2.1 GitHub
1. Create a free account at https://github.com if you don't have one.
2. Create a new **empty** repository named `keystone` (no README, no .gitignore — the project already has them). Keep it **private** and add your mentor as a collaborator, or make it public per the brief.
3. **Authenticate git to GitHub.** Easiest path on Windows is the credential manager, which is bundled with Git for Windows — the first time you `git push`, a browser window opens to sign in. Alternatively, use SSH:
   ```powershell
   ssh-keygen -t ed25519 -C "you@example.com"   # press Enter through the prompts
   Get-Content ~/.ssh/id_ed25519.pub | clip       # copies the public key
   ```
   Then paste it into GitHub → Settings → SSH and GPG keys → New SSH key.
4. **Push the project** (once the code exists locally — see the prompt library for when):
   ```powershell
   cd path\to\keystone
   git init
   git add .
   git commit -m "chore: initial KEYSTONE scaffold (M1 foundation)"
   git branch -M main
   git remote add origin https://github.com/<your-username>/keystone.git
   git push -u origin main
   ```
5. **Ways of working (from the brief):** small, meaningful commits; the `main` branch always builds; use short-lived branches + pull requests for each feature (e.g. `feat/work-order-lifecycle`). A simple, professional commit style: `feat:`, `fix:`, `chore:`, `docs:`, `test:` prefixes.

### 2.2 Docker (day-to-day commands)
You'll mostly use Docker Compose to run Postgres (and later the whole stack) locally. From the `keystone/` folder:
```powershell
docker compose up -d db            # start only the database in the background
docker compose logs -f db          # watch DB logs
docker compose down                # stop everything
docker compose down -v             # stop AND wipe the DB volume (fresh start)
docker compose up --build          # rebuild images and run the full stack
```
The "fresh start" command (`down -v`) is your friend: it proves your Flyway migrations and seed data build the database from nothing, which is exactly what graders check.

### 2.3 Render (deployment) — the full runbook

You are deploying three things: a **PostgreSQL** instance, the **backend** web service (Docker), and the **frontend** static site. Budget about 30 minutes the first time, most of it waiting for builds.

There are two paths. **Path A (blueprint)** is fewer clicks and wires the database credentials for you. **Path B (manual)** is more clicks but every value is visible, so when something is wrong you can see which one. If you have never used Render before, use Path B — it teaches you where everything lives, and you will be able to debug it.

#### Step 0 — push to GitHub first

Render deploys from a repository, so this has to happen first.

```bash
cd keystone
git init
git add -A
git commit -m "KEYSTONE: field service management platform"
git branch -M main
git remote add origin https://github.com/<your-username>/keystone.git
git push -u origin main
```

If `git push` asks for a password, use a **personal access token**, not your account password (GitHub → Settings → Developer settings → Personal access tokens → Fine-grained → repo access). Paste the token as the password.

Check that `backend/target/` and `frontend/node_modules/` did **not** get pushed — `.gitignore` excludes them, and `git ls-files | grep target` should print nothing.

#### Path A — blueprint (render.yaml)

1. Sign in at https://render.com with GitHub and authorize access to your `keystone` repo.
2. **New → Blueprint**, pick the repo. Render reads `render.yaml` and shows three resources: `keystone-db`, `keystone-backend`, `keystone-frontend`. Apply.
3. Wait for the backend build (5–10 min the first time; it's a multi-stage Maven Docker build).
4. Two values could not be known in advance, so set them now — they're marked `sync: false` in the blueprint:
   - On **keystone-frontend** → Environment → `VITE_API_BASE_URL` = `https://keystone-backend-xxxx.onrender.com/api` (your backend's URL, **with `/api` on the end**).
   - On **keystone-backend** → Environment → `APP_CORS_ALLOWED_ORIGINS` = `https://keystone-frontend-xxxx.onrender.com` (your frontend's URL, **no trailing slash, no `/api`**).
5. **Redeploy both** — the frontend because `VITE_API_BASE_URL` is compiled into the bundle at build time, the backend to pick up the CORS origin.

#### Path B — manual, three services

**B1. Database.** New → **Postgres**. Name `keystone-db`, database name `keystone`, user `keystone`, Free plan, region closest to you. Create it, then from its page copy these four values (you'll need them in B2):

- **Hostname** (looks like `dpg-xxxx-a.oregon-postgres.render.com`)
- **Port** (`5432`)
- **Database** (`keystone`)
- **Username** and **Password**

Use the **Internal** hostname if the backend is in the same region — it's faster and not exposed to the internet.

**B2. Backend.** New → **Web Service** → connect the repo → **Root Directory** `backend`. Render detects the Dockerfile; leave Runtime as Docker. Free plan. Set **Health Check Path** to `/api/health`. Then add environment variables:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` | the hostname from B1 |
| `DB_PORT` | `5432` |
| `DB_NAME` | `keystone` |
| `DB_USERNAME` | the username from B1 |
| `DB_PASSWORD` | the password from B1 |
| `JWT_SECRET` | a long random string — `openssl rand -base64 48`, or any 50+ random characters |
| `JWT_EXPIRATION_MINUTES` | `120` |
| `APP_CORS_ALLOWED_ORIGINS` | leave blank for now; you fill it in at B4 |

Deploy. Watch the log for `Successfully applied 3 migrations` — that's Flyway building the schema from nothing, and it is the single best sign the deploy is healthy. Then open `https://<backend>.onrender.com/api/health`.

> Prefer one variable over five? Set `SPRING_DATASOURCE_URL` to `jdbc:postgresql://<host>:5432/keystone` plus `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` instead of the `DB_*` group. The prod profile accepts either and prefers `SPRING_DATASOURCE_URL` when it is set. Note the `jdbc:` prefix — Render's own connection string starts with `postgres://`, which the JDBC driver rejects, so you cannot paste it in unchanged.

**B3. Frontend.** New → **Static Site** → same repo → **Root Directory** `frontend`, **Build Command** `npm install && npm run build`, **Publish Directory** `dist`. Add one environment variable:

| Key | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://<your-backend>.onrender.com/api` |

Then add a rewrite rule, or every URL except the home page will 404 on refresh: **Redirects/Rewrites** → Source `/*`, Destination `/index.html`, Action **Rewrite**. This is what makes client-side routing work on a static host — the server has no `/work-orders` file to serve, so it must hand `index.html` to React and let the router sort it out.

**B4. Close the CORS loop.** Copy the static site's URL, set it as `APP_CORS_ALLOWED_ORIGINS` on the backend, and redeploy the backend.

#### The four things that actually go wrong

Every one of these has the same symptom — "the site loads but login does nothing" — so check them in this order:

1. **`VITE_API_BASE_URL` missing `/api`, or set after the build.** Vite inlines env vars at build time; changing it later does nothing until you redeploy. Confirm by opening the deployed site, DevTools → Network, attempting a login, and reading the request URL. If it says `/api/auth/login` on the *frontend's* domain, the variable never reached the build.
2. **CORS origin wrong.** It must be scheme + host with no trailing slash and no path. `https://x.onrender.com/` (trailing slash) does not match. The browser console says "blocked by CORS policy" — believe it.
3. **Free backend asleep.** Free services spin down after ~15 minutes idle and take 30–60 seconds to wake. The first login after a nap looks like a hang. Hit the health URL and wait for it before assuming failure — and before recording a demo.
4. **Database credentials.** If the log shows `UnknownHostException` or `password authentication failed`, `DB_HOST`/`DB_PASSWORD` are wrong or the profile isn't `prod`. If it shows a connection refused to `localhost:5432`, `SPRING_PROFILES_ACTIVE=prod` was never set, so the local defaults are being used.

#### Verify the deployment properly

Signing in as a manager proves very little. Prove the security model instead, which is what the brief actually rewards:

1. Sign in as `manager@meridian.dev` — the dashboard loads with SLA figures.
2. Sign in as `alice@acme.dev` (customer) — she sees only Acme's work orders. Note the work-order code, sign out.
3. Sign in as `tech1@meridian.dev` and paste the URL of a job *not* assigned to him. The server returns 403, not a rendered page. **This is the demo that matters**: it shows authorization is enforced on the server, not by hiding buttons.
4. As the technician, try to complete a job with no time or parts logged — rejected with a clear message.
5. As a technician, try to close a job — rejected; only a manager may close.


---

## Part 3 — The prompt library

This is the heart of the playbook: the sequence of prompts to paste to the AI to build KEYSTONE correctly. They're ordered to match the 4-week plan. Work them in order.

### 3.1 How to prompt so the build stays mistake-free
- **One milestone per session where possible.** Long sessions drift. Start a fresh chat for each week, and paste the "context primer" (below) first.
- **Always attach the brief PDF** to a new session, or paste the relevant section.
- **Ask for acceptance criteria to be restated and checked.** End build prompts with "list which acceptance criteria this satisfies and how to verify each."
- **Verify before moving on.** After each prompt, run the app / tests / the given curl commands. Don't stack a new feature on an unverified one.
- **Keep the server the source of truth.** If a prompt result enforces a rule only in React, reject it and ask for server-side enforcement + a test that calls the API directly.
- **Commit after every green step** with a meaningful message.

**Context primer — paste this at the start of any new KEYSTONE session:**
```
We are building "Project KEYSTONE", a field-service management platform, per the attached
Zidio brief. Fixed stack: Spring Boot 3 (Java 21), Spring Security + JWT (stateless),
Spring Data JPA, PostgreSQL, Flyway; React + TypeScript (Vite); Maven; deployed on Render;
Docker for local + deploy. Repo is a monorepo: backend/ and frontend/.

Non-negotiables: thin controllers, rich services, DTOs at the boundary (never serialise JPA
entities). All rules (work-order lifecycle + role-based access) enforced in the service layer
and re-checked server-side on every request — assume the API is called directly. All inputs
validated server-side; errors use one consistent JSON shape via @ControllerAdvice. All list
endpoints paginated. No secrets in the repo. Every schema change is a Flyway migration that
runs on a clean database. Write tests for lifecycle transitions and authorization rules.

Before coding, confirm you understand the current state of the repo and tell me your plan.
```

---

### 3.2 Week 1 — Foundation (Milestone M1)
*Goal: a running skeleton — auth, domain model, migrations, seed data, and a live hello-world deploy.*

**Prompt 1.1 — Project scaffold & running skeleton (Day 1)**
```
Scaffold the KEYSTONE monorepo so it runs end to end as a skeleton:
- backend/: Spring Boot 3 (Java 21) Maven project with package com.meridian.keystone,
  dependencies: web, validation, security, data-jpa, postgresql, flyway-core + flyway-database-postgresql,
  springdoc-openapi-starter-webmvc-ui, jjwt (api/impl/jackson), and test.
  Add application.yml with profiles: default (local via docker Postgres), and prod (env-var driven).
  Add a /api/health endpoint (permitAll) returning {status:"UP"}.
- frontend/: React + TypeScript via Vite, an axios API client reading VITE_API_BASE_URL, and a page
  that calls /api/health and shows the result.
- docker-compose.yml running Postgres 16 + backend + frontend for local dev.
- backend/Dockerfile (multi-stage, builds with the wrapper) and frontend build config for a static host.
- Root README.md with local setup, env vars, and how to run migrations/seed.
- .gitignore covering Java/Maven/Node and .env files.
Then give me the exact PowerShell commands to run it locally and confirm the skeleton is up.
```
*Verify:* `docker compose up --build` → open the frontend, it shows the health status; open `http://localhost:8080/swagger-ui.html`. Commit + push, then deploy the skeleton to Render (Part 2.3).

**Prompt 1.2 — Domain model & migrations (Day 2–3)**
```
Implement the KEYSTONE domain model as JPA entities in com.meridian.keystone.domain, exactly per
Section 05 and Appendix A of the brief: Customer, Site, User (with Role enum: DISPATCHER, TECHNICIAN,
MANAGER, CUSTOMER), WorkOrder (with WorkOrderStatus enum NEW/ASSIGNED/IN_PROGRESS/ON_HOLD/COMPLETED/
CLOSED/CANCELLED and Priority enum), WorkOrderStatusHistory (append-only), Part, PartUsage, TimeLog.
Relationships: Customer 1-* Site, Site 1-* WorkOrder, WorkOrder *-1 assignee(User), WorkOrder 1-*
history/partUsage/timeLog. Enforce integrity: a work order must have a customer and site; stock cannot
go negative (DB check constraint + service guard). Give every entity created/updated auditing timestamps.
Write Flyway migrations V1__init_schema.sql (tables, FKs, indexes, constraints) and V2__seed_data.sql
(one user per role with BCrypt password hashes, 2 customers, a few sites, a handful of parts, and a
couple of sample work orders). Use a human-readable work-order code (e.g. WO-2026-0001).
Confirm the migrations run on a clean DB (docker compose down -v && up).
```
*Verify:* fresh DB comes up, tables exist (inspect in pgAdmin), seed rows present.

**Prompt 1.3 — Authentication, JWT & RBAC (Day 4–5)**
```
Implement stateless JWT authentication and role-based access with Spring Security:
- POST /api/auth/login (email+password) → returns a signed JWT (expiring) + the user's role and basic profile.
- BCrypt password hashing; a UserDetailsService backed by the User entity.
- A JWT authentication filter that validates the token on every request; reject expired/tampered tokens with 401.
- SecurityFilterChain: /api/auth/**, /api/health, and Swagger endpoints are public; everything else authenticated.
- Method-level authorization (@PreAuthorize) enabled, ready for per-endpoint role checks.
- GET /api/auth/me returns the current user.
- The JWT secret and expiry come from config/env, never hard-coded.
- Global @ControllerAdvice returning a consistent error JSON {timestamp,status,error,message,path,fieldErrors}.
Then write tests proving: login works, a protected endpoint rejects no/expired/tampered tokens (401),
and a wrong-role user is forbidden (403). List which M1 acceptance criteria (F1) this satisfies.
```
*Verify:* run the tests; `curl` login to get a token, call `/api/auth/me` with and without it. **This completes M1** — commit, push, redeploy, and do your Week 1 mentor review.

---

### 3.3 Week 2 — Core work orders (Milestone M2)
*Goal: work orders end to end — create, list, board — with validation and DTOs.*

**Prompt 2.1 — Customers & sites (Day 6)**
```
Implement customer and site management (feature F2) with the layered pattern (controller → service →
repository) and DTOs (never expose entities):
- CRUD for customers and their sites, restricted to DISPATCHER/MANAGER for writes.
- A site always belongs to a customer (validated).
- List endpoints are searchable (by name) and paginated (Spring Pageable), returning a consistent page shape.
- Repository queries are scoped so a CUSTOMER can only ever read their own organisation's data.
- Bean Validation on all inputs; errors flow through the global handler.
Add the React pages: a customers list (search + pagination) and a customer detail with its sites,
plus create/edit forms. Restrict UI by role but rely on the server for enforcement.
List which F2 acceptance criteria are met and how to verify.
```

**Prompt 2.2 — Work-order CRUD + validation + DTOs (Day 7–8)**
```
Implement work-order management (feature F3):
- POST /api/work-orders creates one with title, description, priority, customer, site; server-side validation.
- GET /api/work-orders (paginated, filterable by status/priority/assignee/customer, role-scoped) and
  GET /api/work-orders/{id} (with its status history).
- PUT /api/work-orders/{id} edits while the order is open; reject edits once CLOSED/CANCELLED (409).
- Each work order gets a unique human-readable code (WO-YYYY-NNNN).
- New work orders start in NEW status and write an initial status-history row.
- DTOs for request and response; map with a mapper, never serialise entities.
Add React pages for creating and viewing/editing a work order. Show the code, status, priority, customer, site.
List which F3 acceptance criteria are met and how to verify.
```

**Prompt 2.3 — Kanban board + list/filter/search/pagination (Day 9–10)**
```
Build the work-order board (feature F4 item 4) and finish list ergonomics:
- A Kanban board in React grouping open work orders into columns by status (NEW, ASSIGNED, IN_PROGRESS,
  ON_HOLD, COMPLETED). Cards show code, title, priority, assignee, SLA due.
- The backing list endpoint supports filtering, sorting, search, and pagination; never returns unbounded results.
- Handle empty and loading states.
This completes M2. Give me curl examples for the filtered/paginated list and confirm the board renders
from real data. List which acceptance criteria (F3, F4) are satisfied.
```
*Verify + commit + push + redeploy, then Week 2 mentor review.*

---

### 3.4 Week 3 — Workflow & rules (Milestone M3 — highest marks)
*Goal: the governed lifecycle, dispatch, transactional parts/time, and SLA tracking. Spend your best effort here.*

**Prompt 3.1 — Work-order state machine + guarded transitions + history (Day 11–12)**
```
Implement the work-order lifecycle as a server-side state machine (Section 07), enforced in the service
layer — not the UI:
- States: NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, COMPLETED, CLOSED, CANCELLED.
- Allowed transitions ONLY: NEW→ASSIGNED, NEW→CANCELLED; ASSIGNED→IN_PROGRESS, ASSIGNED→CANCELLED,
  ASSIGNED→NEW (unassign); IN_PROGRESS→ON_HOLD, IN_PROGRESS→COMPLETED, IN_PROGRESS→CANCELLED;
  ON_HOLD→IN_PROGRESS, ON_HOLD→CANCELLED; COMPLETED→CLOSED, COMPLETED→IN_PROGRESS (reopen).
  CLOSED and CANCELLED are terminal. Any other transition → HTTP 409 with a clear message.
- Role rules: only MANAGER can CLOSE; only the assigned TECHNICIAN can start/hold/resume/complete their job;
  DISPATCHER/MANAGER can assign/cancel. Re-check roles server-side.
- POST /api/work-orders/{id}/status performs a validated transition and writes an append-only
  WorkOrderStatusHistory row (from, to, who, when, optional note) in the same transaction.
- Write tests proving: every illegal transition is rejected with 409, terminal states can't move,
  a wrong-role actor gets 403, and each successful transition writes exactly one history row.
Do NOT allow the status to be changed via the generic PUT endpoint — only via this transition endpoint.
List which acceptance criteria this satisfies.
```

**Prompt 3.2 — Dispatch/assignment + technician field view (Day 13)**
```
Implement dispatch (F4) and the technician field view (F5):
- POST /api/work-orders/{id}/assign (DISPATCHER/MANAGER) assigns a technician, moves NEW→ASSIGNED,
  writes history, and creates a notification for the technician. Reassignment allowed while open.
- A technician sees ONLY their assigned jobs (server-scoped query, not a UI filter).
- Technician actions: start (ASSIGNED→IN_PROGRESS), hold/resume, complete — all via the transition endpoint.
- The technician React view is responsive and usable on a phone (test at 375px width).
Write a test that a technician cannot act on a work order not assigned to them (403) and cannot reassign or close.
List which acceptance criteria are met.
```

**Prompt 3.3 — Parts & time logging, transactional (Day 14)**
```
Implement parts and time logging (feature F6) with strict transactional integrity:
- POST /api/work-orders/{id}/parts logs a PartUsage and decrements Part stock in ONE @Transactional unit;
  if stock would go negative, reject the whole operation (no partial update). Add a DB check constraint too.
- POST /api/work-orders/{id}/time logs minutes + optional note.
- The work order rolls up totals: total parts cost and total labour minutes.
- Only the assigned technician (or manager) can log against a job, and only while it is IN_PROGRESS/ON_HOLD.
Write a concurrency-aware test proving stock never goes negative and that a failed parts log rolls back fully.
List which acceptance criteria are met.
```

**Prompt 3.4 — SLA due dates + scheduled breach check + notifications (Day 15)**
```
Implement SLA tracking (feature F7):
- On creation, set an SLA due date from priority (e.g. URGENT=4h, HIGH=8h, MEDIUM=24h, LOW=72h — make configurable).
- A scheduled job (@Scheduled) flags work orders approaching or in breach; expose SLA status (ON_TRACK,
  AT_RISK, BREACHED) on the work order.
- Breaches are visible to managers and generate a notification.
- SLA status appears on the board and (later) the dashboard.
Write a test using a fixed clock proving an overdue order is flagged BREACHED and one near due is AT_RISK.
This completes M3. List which acceptance criteria are met.
```
*Verify all Week 3 tests pass + commit + push + redeploy, then Week 3 mentor review. This milestone is worth the most — make sure direct API calls can't bypass the lifecycle or the role rules.*

---

### 3.5 Week 4 — Productize (Milestone M4)
*Goal: dashboard, customer portal, API docs, tests, and a polished live deployment.*

**Prompt 4.1 — Dashboard & reporting (Day 16–17)**
```
Implement the manager dashboard (feature F8) and GET /api/reports/summary:
- Counts by status, count of overdue/at-risk work, and SLA compliance %.
- Figures reflect current data and any active filters (date range, customer, technician).
- At least one breakdown by technician or by site.
- Handle empty and loading states in the React dashboard; show simple charts.
All numbers are computed server-side from live data. List which F8 acceptance criteria are met.
```

**Prompt 4.2 — Customer portal (Day 18)**
```
Implement the customer portal (feature F9):
- A CUSTOMER can raise a request for one of THEIR sites; it enters the same work-order pipeline (status NEW).
- They can view status + history of only their own work orders; never other customers' data or internal fields.
- Enforce ownership server-side (a customer changing an id in the URL must get 403/404, tested).
Add the React customer pages (raise request, my requests list, request detail with status timeline).
Also polish the technician view (photo/attachment upload if time permits). List which F9 acceptance criteria are met.
```

**Prompt 4.3 — OpenAPI docs, tests, and full deployment (Day 19)**
```
Finalise for submission:
- Complete and annotate the OpenAPI/Swagger docs (springdoc): every endpoint, request/response schemas,
  auth described, grouped by resource. Swagger UI browsable and accurate.
- Fill test gaps: lifecycle transitions, authorization rules, transactional parts/time — the high-value paths.
- Provide a render.yaml blueprint provisioning Postgres + backend web service + frontend static site,
  with all env vars (JWT_SECRET, datasource, CORS origins) documented.
- Update the README: overview, stack, local setup, env vars, how to run migrations/seed, architecture summary,
  and the seed login credentials for all four roles.
Walk me through deploying all three services to Render and verifying the live URLs + Swagger work with seed logins.
```

**Prompt 4.4 — Final QA & demo (Day 20)**
```
Do a final QA pass against every acceptance criterion (F1–F9) and the submission checklist. Produce:
- A short QA report: each feature, pass/fail, and how it was verified (include direct-API-call security checks).
- A 3–5 minute demo script that walks through each role (dispatcher, technician, manager, customer) and the
  full work-order lifecycle end to end, including one blocked illegal transition and one blocked cross-customer access.
Then list anything not yet meeting its acceptance criteria so I can fix it before submitting.
```

---

## Part 4 — Verification & submission

### 4.1 Acceptance-criteria checklist (your definition of done)
Tick each before submitting. These come straight from Section 09 of the brief.

- **F1 Auth & roles:** login returns JWT · four roles with distinct permissions · protected endpoints enforce role server-side · BCrypt passwords, tokens expire.
- **F2 Customers & sites:** dispatchers/managers create/edit customers+sites · a site always belongs to a customer · lists searchable + paginated · customers see only their own data.
- **F3 Work orders:** create with title/description/priority/customer/site · server-side validation · editable while open, immutable once closed/cancelled · unique human-readable code.
- **F4 Dispatch:** dispatcher assigns to technician · assignment → ASSIGNED + notifies · reassignment while open · Kanban board of open work.
- **F5 Technician view:** sees only their jobs · start/hold/resume/complete · logs parts + time · responsive on a phone.
- **F6 Parts & time:** logging parts decrements stock in one transaction · time records minutes + note · totals roll up · stock never negative.
- **F7 SLA:** SLA due date by priority · scheduled job flags at-risk/breached · breaches visible to managers + notify · SLA status on board + dashboard.
- **F8 Dashboard:** counts by status, overdue, SLA compliance · reflects current data + filters · a breakdown by technician or site · empty/loading states.
- **F9 Customer portal:** raise a request for their site · view their own status+history only · cannot see others' data · requests enter the same pipeline.

**Cross-cutting (the marks-separating stuff):** lifecycle + authz hold against **direct API calls**; parts/time are transactionally correct; everything is deployed and reachable with seed logins; API fully documented.

### 4.2 Submission checklist (Section 15)
1. Git repo (public, or private with mentor access) for backend + frontend.
2. Live URLs: deployed API, deployed frontend, Swagger UI.
3. Seed login credentials for each of the four roles.
4. README: overview, stack, local setup, env vars, migrations/seed, architecture summary.
5. A 3–5 minute demo video (unlisted link) walking through each role and the lifecycle.
6. The completed submission form with all links.

**Clean-checkout rule:** clone into a brand-new folder, follow only the README, and confirm the platform comes up (`docker compose up --build`, migrations + seed run automatically). If it only works on your machine, fix that before submitting.

### 4.3 Demo video script (3–5 min)

**Before you hit record:** open the deployed backend's `/api/health` and wait for it to answer. Free Render services sleep after ~15 minutes idle and take 30–60 seconds to wake, and there is no way to make that look good on camera.

1. **(20s)** What KEYSTONE is and the stack. Show it deployed (the live URL).
2. **(45s) Dispatcher:** log in, create a customer + site, raise a work order, assign it to a technician. Show it move on the board.
3. **(45s) Technician:** log in on a narrow/phone view, open the assigned job, start it, log a part (show stock decrement) and some time, complete it.
4. **(45s) Manager:** log in, show the dashboard (status counts, overdue, SLA compliance), close the completed job.
5. **(45s) Customer:** log in, raise a request, track its status/history — and show you *cannot* see another customer's data.
6. **(45s) The rules — this is the money shot.** Three refusals, each for a different reason, which is the point:
   - an illegal transition (`NEW → COMPLETED`) → **409**, the move is not on the diagram at all;
   - a technician trying to close a job → **403**, a legal move but not by them;
   - completing a job with no time or parts logged → rejected, because a completed job with no record of work is not billable.

   Do at least one of these directly in Swagger rather than in the UI. It shows the server refuses on its own, not because a button was hidden.
7. **(20s)** Show Swagger UI and the append-only status history on a job, then wrap up.

---

*Built for Aditya's KEYSTONE build. The code is written; your job is to compile it, run it, deploy it, and be able to explain it. Verify each step, commit often, and get the deployment live before you polish anything.*
