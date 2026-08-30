# KEYSTONE — Get it running on Windows (Node + JDK 21)

This is the shortest path from the folder on disk to a working app you can log into.

## First: why "Go Live" showed nothing

VS Code's **Go Live** (Live Server) is a static file server — it hands the browser files exactly as they are on disk. `frontend/index.html` is deliberately almost empty:

```html
<div id="root"></div>
<script type="module" src="/src/main.tsx"></script>
```

The browser asks for `main.tsx`, gets TypeScript with JSX inside it, and cannot execute it — browsers only run JavaScript. React never starts, so `<div id="root">` stays empty and you see a blank page. Press F12 and the Console will say as much (a MIME-type or "unexpected token '<'" error).

There is no Live Server setting that fixes this. React + TypeScript must be compiled first, which is what Vite's dev server does. **Do not use Go Live on this project.** Use `npm run dev` below.

---

## Step 1 — See the UI (2 minutes)

```powershell
cd <path-to>\keystone\frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The login screen appears, styled, with the demo-account buttons.

**Signing in will fail at this point, and that's correct** — the form posts to `/api/auth/login`, and the backend isn't running yet. You've proved the frontend builds and renders. Leave this terminal running; Vite reloads on save.

## Step 2 — Install the two missing tools

You have Node and JDK 21. You still need **Maven** (to build the backend — this repo has no `mvnw` wrapper) and **PostgreSQL** (the app's database; Flyway builds the schema into it).

```powershell
winget install Apache.Maven
winget install PostgreSQL.PostgreSQL.16
```

During the PostgreSQL install, set the `postgres` superuser password to something you'll remember, and keep the default port **5432**.

Now **open a brand-new terminal** — Windows only applies PATH changes to newly opened terminals, and skipping this is the most common reason `mvn` "isn't recognised" right after installing it. Then confirm:

```powershell
mvn -v
java -version
psql --version
```

## Step 3 — Create the database

```powershell
psql -U postgres
```

At the `postgres=#` prompt, paste these five lines:

```sql
CREATE DATABASE keystone;
CREATE USER keystone WITH PASSWORD 'keystone';
GRANT ALL PRIVILEGES ON DATABASE keystone TO keystone;
\c keystone
GRANT ALL ON SCHEMA public TO keystone;
```

Then `\q` to exit.

That last `GRANT ALL ON SCHEMA public` matters and is easy to miss. From PostgreSQL 15 onward, ordinary users no longer get `CREATE` on the `public` schema by default. Without it Flyway starts, tries to create the first table, and fails with `permission denied for schema public` — which reads like a bug in the app but is purely a database grant.

These credentials match what `application.yml` already defaults to (`localhost:5432/keystone`, user `keystone`, password `keystone`), so you don't need to set a single environment variable for local work.

## Step 4 — Run the backend

In a second terminal:

```powershell
cd <path-to>\keystone\backend
mvn spring-boot:run
```

The first run downloads all dependencies — several minutes, and a lot of scrolling. Two things to watch for:

- **`Successfully applied 3 migrations`** — Flyway just built all 8 tables and the seed data from nothing. This is the single best sign the backend is healthy.
- **`Started KeystoneApplication`** — it's up.

Then check **http://localhost:8080/api/health**, and browse the API at **http://localhost:8080/swagger-ui.html**.

> **If it fails to compile:** this code was written without a Java compiler available, so this is the first time one has seen it. Expect a small number of mechanical errors (a missing import, a type that needs adjusting) rather than design problems. Fix **only the first error**, re-run, and repeat — one bad import can produce dozens of downstream complaints that vanish on their own. Paste the errors to me and I'll fix them.

## Step 5 — Log in

Back at **http://localhost:5173**, sign in with:

| Role | Email | Password |
|---|---|---|
| Manager | `manager@meridian.dev` | `ChangeMe123!` |
| Dispatcher | `dispatcher@meridian.dev` | `ChangeMe123!` |
| Technician | `tech1@meridian.dev` | `ChangeMe123!` |
| Customer | `alice@acme.dev` | `ChangeMe123!` |

You don't need to configure anything to connect the two halves: `vite.config.ts` proxies `/api` to `localhost:8080` during `npm run dev`, so there's no CORS setup for local development.

Each role sees a different application. Sign in as the manager for the dashboard, the technician for the field view, and Alice to see the customer portal scoped to Acme's jobs only.

---

## Everyday commands

```powershell
# terminal 1 — frontend
cd keystone\frontend ; npm run dev

# terminal 2 — backend
cd keystone\backend ; mvn spring-boot:run
```

To rebuild the database from scratch (proves migrations work on an empty database, which is what reviewers check):

```powershell
psql -U postgres -c "DROP DATABASE keystone;" -c "CREATE DATABASE keystone;"
psql -U postgres -d keystone -c "GRANT ALL ON SCHEMA public TO keystone;"
```

Then restart the backend and watch Flyway rebuild everything.

## If something's wrong

**Blank page at localhost:5173** — is Vite actually running in that terminal, or did you open the file directly / use Go Live? The URL must be `localhost:5173`, not a `file:///` path.

**Login says "Network Error"** — the backend isn't up. Check `http://localhost:8080/api/health` in a browser.

**Login says "Invalid email or password"** — the backend is up but the seed data isn't there. Look back through the backend log for the Flyway line; if migrations didn't run, the users don't exist.

**`permission denied for schema public`** — the `GRANT ALL ON SCHEMA public` from Step 3 was missed.

**`port 8080 already in use`** — something else has it. `netstat -ano | findstr :8080` shows the process ID.
