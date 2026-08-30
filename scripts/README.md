# KEYSTONE helper scripts

One command each. Run them from **PowerShell** in the project root (the folder
with `docker-compose.yml`). Docker Desktop must be installed and running.

| Script | What it does |
|---|---|
| `.\scripts\run.ps1` | Build + start the whole stack (db + backend + frontend), wait until healthy, then print URLs and seed logins. |
| `.\scripts\stop.ps1` | Stop the stack, **keeping** the database. |
| `.\scripts\stop.ps1 -Wipe` | Stop the stack and **wipe** the database volume. |
| `.\scripts\fresh-db.ps1` | Wipe the DB, start db + backend only, and show the Flyway migration log — the "clean DB" confirmation. |
| `.\scripts\verify.ps1` | API smoke test: logs in as each role and checks JWT + RBAC end-to-end. |

## First-time note about PowerShell script execution

If Windows blocks the scripts with a red "running scripts is disabled" message,
allow local scripts for your user once:

```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

Then answer `Y`. This only affects your own account.

## Typical flow

```powershell
.\scripts\run.ps1        # start everything
.\scripts\verify.ps1     # confirm auth + RBAC work
# ... open http://localhost:3000 and log in ...
.\scripts\stop.ps1       # stop when done
```

## macOS / Linux

Equivalent bash scripts live alongside these: `run.sh`, `stop.sh`,
`fresh-db.sh`. Make them executable once with `chmod +x scripts/*.sh`, then run
e.g. `./scripts/run.sh`.
