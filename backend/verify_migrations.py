#!/usr/bin/env python3
"""
Offline verification for the KEYSTONE Flyway migrations.

We cannot run a real Postgres here (no engine, no internet), so instead we
statically cross-check the three things that actually break a migration:

  1. Entities  <-> V1 DDL : every JPA column exists in the table, and the
     table has no unexpected columns (schema drift is the #1 runtime error
     when ddl-auto=none).
  2. V2 seed   <-> V1 DDL : every INSERT targets a real table, names only
     real columns, and each row's value-count matches the column-count.
  3. Seed integrity        : FK values resolve to seeded ids, CHECK
     constraints hold, and WO totals reconcile with the seeded child rows.

The SQL parsing here is quote-aware (a ';' or '(' inside a string literal is
NOT treated as a statement/paren boundary), which mirrors how Postgres and
Flyway actually tokenize.
"""
import re, sys, pathlib

ROOT = pathlib.Path("/sessions/confident-clever-cray/mnt/outputs/keystone/backend/src/main")
DOMAIN = ROOT / "java/com/meridian/keystone/domain"
V1 = ROOT / "resources/db/migration/V1__init_schema.sql"
V2 = ROOT / "resources/db/migration/V2__seed_data.sql"

errors, warnings = [], []
def err(m): errors.append(m)
def warn(m): warnings.append(m)

BASE_COLS = {"id", "version", "created_at", "updated_at"}

# ---------------------------------------------------------------------------
# Quote-aware SQL helpers
# ---------------------------------------------------------------------------
def strip_comments(sql):
    out, i, in_str = [], 0, False
    while i < len(sql):
        ch = sql[i]
        if ch == "'":
            in_str = not in_str; out.append(ch); i += 1; continue
        if not in_str and ch == "-" and i + 1 < len(sql) and sql[i+1] == "-":
            while i < len(sql) and sql[i] != "\n":
                i += 1
            continue
        out.append(ch); i += 1
    return "".join(out)

def split_statements(sql):
    stmts, buf, in_str = [], [], False
    for ch in sql:
        if ch == "'":
            in_str = not in_str
        if ch == ";" and not in_str:
            s = "".join(buf).strip()
            if s: stmts.append(s)
            buf = []
        else:
            buf.append(ch)
    if "".join(buf).strip():
        stmts.append("".join(buf).strip())
    return stmts

def split_top_level(body):
    """Split on commas at paren-depth 0, respecting single-quoted strings."""
    items, depth, buf, in_str = [], 0, [], False
    for ch in body:
        if ch == "'":
            in_str = not in_str
        if not in_str:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            elif ch == "," and depth == 0:
                items.append("".join(buf).strip()); buf = []; continue
        buf.append(ch)
    if "".join(buf).strip():
        items.append("".join(buf).strip())
    return items

def split_rows(vals_blob):
    rows, depth, buf, in_str = [], 0, [], False
    for ch in vals_blob:
        if ch == "'":
            in_str = not in_str
        if not in_str:
            if ch == "(":
                depth += 1
                if depth == 1:
                    buf = []; continue
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    rows.append("".join(buf)); continue
        if depth >= 1:
            buf.append(ch)
    return rows

# ---------------------------------------------------------------------------
# 1. Parse JPA entities -> {table: set(columns)}
# ---------------------------------------------------------------------------
entity_cols = {}
col_re  = re.compile(r'@Column\(name\s*=\s*"([^"]+)"')
join_re = re.compile(r'@JoinColumn\(name\s*=\s*"([^"]+)"')
tbl_re  = re.compile(r'@Table\(name\s*=\s*"([^"]+)"')

for jf in sorted(DOMAIN.glob("*.java")):
    text = jf.read_text()
    if not re.search(r'@Entity\b', text):    # \b so @EntityListeners doesn't match
        continue
    m = tbl_re.search(text)
    if not m:
        err(f"{jf.name}: @Entity without @Table(name=...)"); continue
    table = m.group(1)
    cols = set(col_re.findall(text)) | set(join_re.findall(text))
    if re.search(r'@Id\b', text) and "extends BaseEntity" not in text:
        cols.add("id")
    if "extends BaseEntity" in text:
        cols |= BASE_COLS
    entity_cols[table] = cols

print(f"Parsed {len(entity_cols)} entity tables: {', '.join(sorted(entity_cols))}")

# ---------------------------------------------------------------------------
# 2. Parse V1 DDL
# ---------------------------------------------------------------------------
v1 = strip_comments(V1.read_text())
ddl_cols, fk_edges = {}, []
CONSTRAINT_KW = ("constraint", "foreign", "primary", "unique", "check")

for stmt in split_statements(v1):
    m = re.match(r'CREATE TABLE (\w+)\s*\((.*)\)\s*$', stmt, re.S)
    if not m:
        continue
    tbl, body = m.group(1), m.group(2)
    cols = []
    for item in split_top_level(body):
        if item.lower().startswith(CONSTRAINT_KW):
            continue
        cols.append(item.split()[0])
    ddl_cols[tbl] = cols
    dupes = [c for c in cols if cols.count(c) > 1]
    if dupes:
        err(f"{tbl}: duplicate column definitions {sorted(set(dupes))}")
    for src, tgt_tbl, tgt_col in re.findall(
            r'FOREIGN KEY \((\w+)\) REFERENCES (\w+)\s*\((\w+)\)', body):
        fk_edges.append((tbl, src, tgt_tbl, tgt_col))

print(f"Parsed {len(ddl_cols)} DDL tables: {', '.join(sorted(ddl_cols))}")
print(f"Parsed {len(fk_edges)} foreign keys")

# ---------------------------------------------------------------------------
# 3. Cross-check entities <-> DDL
# ---------------------------------------------------------------------------
for tbl, ecols in entity_cols.items():
    if tbl not in ddl_cols:
        err(f"Entity table '{tbl}' has no CREATE TABLE in V1"); continue
    dset = set(ddl_cols[tbl])
    missing = ecols - dset
    extra   = dset - ecols
    if missing:
        err(f"{tbl}: columns in entity but MISSING from DDL: {sorted(missing)}")
    if extra:
        err(f"{tbl}: columns in DDL but not in entity: {sorted(extra)}")

order = list(ddl_cols.keys())
for tbl, src, tgt_tbl, tgt_col in fk_edges:
    if tgt_tbl not in ddl_cols:
        err(f"FK {tbl}.{src} -> {tgt_tbl}({tgt_col}): target table missing")
    elif tgt_col not in ddl_cols[tgt_tbl]:
        err(f"FK {tbl}.{src} -> {tgt_tbl}({tgt_col}): target column missing")
    elif order.index(tgt_tbl) > order.index(tbl):
        err(f"FK {tbl}.{src} -> {tgt_tbl}: target created AFTER referencing table")
    if src not in ddl_cols.get(tbl, []):
        err(f"FK source column {tbl}.{src} not defined")

# ---------------------------------------------------------------------------
# 4. Parse V2 inserts
# ---------------------------------------------------------------------------
v2 = strip_comments(V2.read_text())
seed = {}
for stmt in split_statements(v2):
    m = re.match(r'INSERT INTO (\w+)\s*\((.*?)\)\s*VALUES\s*(.*)$', stmt, re.S)
    if not m:
        continue
    tbl, collist, vals = m.group(1), m.group(2), m.group(3)
    cols = [c.strip() for c in collist.split(",")]
    if tbl not in ddl_cols:
        err(f"Seed INSERT into unknown table '{tbl}'"); continue
    for c in cols:
        if c not in ddl_cols[tbl]:
            err(f"Seed {tbl}: column '{c}' not in DDL")
    for r in split_rows(vals):
        f = split_top_level(r)
        if len(f) != len(cols):
            err(f"Seed {tbl}: row has {len(f)} values but {len(cols)} columns -> {r.strip()[:70]}")
        seed.setdefault(tbl, []).append(dict(zip(cols, f)))

print("Parsed seed rows: " + ", ".join(f"{t}={len(r)}" for t, r in sorted(seed.items())))

# ---------------------------------------------------------------------------
# 5. Seed integrity
# ---------------------------------------------------------------------------
def ids(tbl):
    return {int(r["id"]) for r in seed.get(tbl, []) if r.get("id") not in (None, "NULL")}
cust_ids, site_ids, user_ids = ids("customers"), ids("sites"), ids("users")
part_ids, wo_ids = ids("parts"), ids("work_orders")

def check_fk(tbl, col, valid, allow_null=True):
    for r in seed.get(tbl, []):
        v = r.get(col)
        if v is None: continue
        if v == "NULL":
            if not allow_null: err(f"{tbl}.{col} is NULL but column is NOT NULL")
            continue
        if int(v) not in valid:
            err(f"{tbl}.{col}={v} references a non-existent id")

check_fk("sites", "customer_id", cust_ids, False)
check_fk("users", "customer_id", cust_ids, True)
check_fk("work_orders", "customer_id", cust_ids, False)
check_fk("work_orders", "site_id", site_ids, False)
check_fk("work_orders", "assignee_id", user_ids, True)
check_fk("work_order_status_history", "work_order_id", wo_ids, False)
check_fk("work_order_status_history", "changed_by_id", user_ids, True)
check_fk("part_usage", "work_order_id", wo_ids, False)
check_fk("part_usage", "part_id", part_ids, False)
check_fk("part_usage", "logged_by_id", user_ids, True)
check_fk("time_logs", "work_order_id", wo_ids, False)
check_fk("time_logs", "technician_id", user_ids, False)

site_owner = {int(r["id"]): int(r["customer_id"]) for r in seed.get("sites", [])}
for r in seed.get("work_orders", []):
    so = site_owner.get(int(r["site_id"]))
    if so is not None and so != int(r["customer_id"]):
        err(f"WO {r['code']}: site {r['site_id']} belongs to customer {so}, "
            f"but WO customer is {r['customer_id']}")

ROLES = {'DISPATCHER','TECHNICIAN','MANAGER','CUSTOMER'}
PRIOS = {'LOW','MEDIUM','HIGH','URGENT'}
STATUS = {'NEW','ASSIGNED','IN_PROGRESS','ON_HOLD','COMPLETED','CLOSED','CANCELLED'}
SLA = {'ON_TRACK','AT_RISK','BREACHED'}
def unq(v): return v.strip().strip("'")
for r in seed.get("users", []):
    if unq(r["role"]) not in ROLES: err(f"user {r['email']}: bad role {r['role']}")
    if unq(r["role"]) == "CUSTOMER" and r["customer_id"] == "NULL":
        err(f"CUSTOMER user {r['email']} has no customer_id")
    if unq(r["role"]) != "CUSTOMER" and r["customer_id"] != "NULL":
        warn(f"staff user {r['email']} has a customer_id set")
for r in seed.get("work_orders", []):
    if unq(r["priority"]) not in PRIOS: err(f"WO {r['code']}: bad priority {r['priority']}")
    if unq(r["status"]) not in STATUS:  err(f"WO {r['code']}: bad status {r['status']}")
    if unq(r["sla_status"]) not in SLA: err(f"WO {r['code']}: bad sla_status {r['sla_status']}")
for r in seed.get("work_order_status_history", []):
    if r["from_status"] != "NULL" and unq(r["from_status"]) not in STATUS:
        err(f"history id={r['id']}: bad from_status {r['from_status']}")
    if unq(r["to_status"]) not in STATUS:
        err(f"history id={r['id']}: bad to_status {r['to_status']}")

for r in seed.get("parts", []):
    if int(r["stock_quantity"]) < 0: err(f"part {r['sku']}: negative stock")
    if float(r["unit_cost"]) < 0:    err(f"part {r['sku']}: negative unit_cost")
for r in seed.get("part_usage", []):
    if int(r["quantity"]) <= 0: err(f"part_usage id={r['id']}: quantity not > 0")
for r in seed.get("time_logs", []):
    if int(r["minutes"]) <= 0: err(f"time_logs id={r['id']}: minutes not > 0")

labor, parts_cost = {}, {}
for r in seed.get("time_logs", []):
    labor[int(r["work_order_id"])] = labor.get(int(r["work_order_id"]),0) + int(r["minutes"])
for r in seed.get("part_usage", []):
    wo = int(r["work_order_id"])
    parts_cost[wo] = round(parts_cost.get(wo,0.0) + int(r["quantity"])*float(r["unit_cost_at_use"]),2)
for r in seed.get("work_orders", []):
    wo = int(r["id"])
    if labor.get(wo,0) != int(r["total_labor_minutes"]):
        err(f"WO {r['code']}: total_labor_minutes={r['total_labor_minutes']} but time logs sum to {labor.get(wo,0)}")
    if abs(parts_cost.get(wo,0.0) - float(r["total_parts_cost"])) > 0.001:
        err(f"WO {r['code']}: total_parts_cost={r['total_parts_cost']} but part usage sums to {parts_cost.get(wo,0.0)}")

for r in seed.get("work_orders", []):
    if not re.fullmatch(r"WO-\d{4}-\d{4}", unq(r["code"])):
        err(f"WO code '{r['code']}' does not match WO-YYYY-NNNN")

init_rows = {int(r["work_order_id"]) for r in seed.get("work_order_status_history", [])
             if r["from_status"] == "NULL"}
for wo in wo_ids:
    if wo not in init_rows:
        warn(f"work order id={wo} has no initial (NULL->...) history row")

last_to = {}
for r in seed.get("work_order_status_history", []):
    last_to[int(r["work_order_id"])] = unq(r["to_status"])
for r in seed.get("work_orders", []):
    wo = int(r["id"])
    if wo in last_to and last_to[wo] != unq(r["status"]):
        warn(f"WO {r['code']}: current status {unq(r['status'])} != last history to_status {last_to[wo]}")

for tbl in seed:
    if f"setval(pg_get_serial_sequence('{tbl}'" not in v2:
        err(f"missing identity-sequence reset (setval) for '{tbl}'")

print("\n" + "="*66)
if errors:
    print(f"FAILED — {len(errors)} error(s):")
    for e in errors: print("  X", e)
else:
    print("PASSED - entities, DDL, and seed are mutually consistent.")
if warnings:
    print(f"\n{len(warnings)} note(s):")
    for w in warnings: print("  -", w)
print("="*66)
sys.exit(1 if errors else 0)
