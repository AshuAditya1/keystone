#!/usr/bin/env python3
"""
Offline static checks for the KEYSTONE backend and frontend.

This project is built and reviewed in an environment with no JDK 21, no Maven
and no network, so `mvn verify` cannot be the first line of defence. These
checks are the substitute: they catch the specific mistakes that are easy to
make when writing a lot of code without a compiler, and they are all pure text
analysis, so they run anywhere Python does.

  Java
    J1  package declaration matches the file's directory
    J2  braces, parens and brackets balance (string/char/comment aware)
    J3  every `import com.meridian.keystone.*` resolves to a real file
    J4  every project type referenced in a file is imported or same-package
    J5  no unused project-type imports (they are legal, but they are noise and
        they hide the fact that a dependency was dropped)
    J6  no fully-qualified `java.util.` / `java.time.` uses in method bodies

  TypeScript
    T1  braces/parens/brackets balance
    T2  no unused imports  (tsconfig has noUnusedLocals: a stray import is a
        hard build failure, not a warning)

  Cross-cutting  (the checks that replace a compiler that can see both halves)
    X1  every path the frontend calls exists on a Spring controller, and every
        controller route is reachable from the frontend
    X2  every CSS class the components use has a rule, and every rule is used
    X3  every sort key the frontend offers is on the server's sort whitelist —
        an unlisted key is rejected at runtime as a 400, which is exactly the
        kind of bug that only shows up when a reviewer clicks that column

  Repo hygiene
    S1  no secrets committed: no JWT secret, DB password or API key literals.
        A line may be exempted with a `keystone:allow-secret <reason>` comment
        on it or immediately above it; the reason is required so that every
        exemption is argued for in the diff.

Exit code is non-zero if anything failed, so CI or a shell script can gate on
it.

Usage:  python3 scripts/static-check.py [repo-root]
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

JAVA_PKG_ROOT = "com.meridian.keystone"

# ---------------------------------------------------------------------------
# tokenizing helpers
# ---------------------------------------------------------------------------


def strip_java_noise(text: str) -> str:
    """Blank out string literals, char literals and comments.

    Replaced with spaces rather than deleted so reported line numbers stay
    correct and so tokens either side do not accidentally join up.
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if c == '"' and text[i : i + 3] == '"""':          # text block
            j = text.find('"""', i + 3)
            j = n if j == -1 else j + 3
            out.append(" " if "\n" not in text[i:j] else _keep_newlines(text[i:j]))
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append(" " * (min(j + 1, n) - i))
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            out.append(" " * (min(j + 1, n) - i))
            i = j + 1
            continue
        if c == "/" and nxt == "/":
            j = text.find("\n", i)
            j = n if j == -1 else j
            out.append(" " * (j - i))
            i = j
            continue
        if c == "/" and nxt == "*":
            j = text.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append(_keep_newlines(text[i:j]))
            i = j
            continue

        out.append(c)
        i += 1
    return "".join(out)


def _keep_newlines(chunk: str) -> str:
    return "".join("\n" if ch == "\n" else " " for ch in chunk)


# Characters that can legally precede the start of a string literal in TS. An
# apostrophe in JSX prose ("what you'll see") is preceded by a letter, which is
# how we tell the two apart without writing a JSX parser.
TS_PRE_STRING = set(" \t\n(=[{,:?|&>!+;*-/\r")


def strip_ts_noise(text: str) -> str:
    """Blank out strings, template literals and comments in TS/TSX.

    JSX text is not lexed as code, so a naive Java-style tokenizer treats the
    apostrophe in prose as a char literal and swallows everything after it. This
    version only opens a single-quoted string when the quote sits in a plausible
    code position; inside template literals it keeps the `${...}` expressions,
    because they contain real code and real brackets.
    """
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if c == "/" and nxt == "/":
            j = text.find("\n", i)
            j = n if j == -1 else j
            out.append(" " * (j - i))
            i = j
            continue
        if c == "/" and nxt == "*":
            j = text.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append(_keep_newlines(text[i:j]))
            i = j
            continue
        if c == "`":
            i += 1
            out.append(" ")
            while i < n and text[i] != "`":
                if text[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                if text[i] == "$" and i + 1 < n and text[i + 1] == "{":
                    # keep the interpolated expression: it is code
                    depth = 0
                    out.append("  ")
                    i += 2
                    depth = 1
                    while i < n and depth > 0:
                        if text[i] == "{":
                            depth += 1
                        elif text[i] == "}":
                            depth -= 1
                            if depth == 0:
                                out.append(" ")
                                i += 1
                                break
                        out.append(text[i])
                        i += 1
                    continue
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append(" ")
            i += 1
            continue
        if c == '"' or c == "'":
            prev = text[i - 1] if i > 0 else "\n"
            if c == "'" and prev not in TS_PRE_STRING:
                out.append(c)      # prose apostrophe, not a literal
                i += 1
                continue
            j = i + 1
            while j < n and text[j] != c and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            if j >= n or text[j] == "\n":
                out.append(c)      # unterminated on this line: treat as prose
                i += 1
                continue
            out.append(" " * (j + 1 - i))
            i = j + 1
            continue

        out.append(c)
        i += 1
    return "".join(out)


def check_balance(path: Path, code: str, failures: list[str], tag: str) -> None:
    pairs = {")": "(", "]": "[", "}": "{"}
    stack: list[tuple[str, int]] = []
    line = 1
    for ch in code:
        if ch == "\n":
            line += 1
        elif ch in "([{":
            stack.append((ch, line))
        elif ch in ")]}":
            if not stack:
                failures.append(f"{tag} {path}:{line}: stray '{ch}'")
                return
            open_ch, open_line = stack.pop()
            if open_ch != pairs[ch]:
                failures.append(
                    f"{tag} {path}:{line}: '{ch}' closes '{open_ch}' "
                    f"opened at line {open_line}"
                )
                return
    if stack:
        open_ch, open_line = stack[-1]
        failures.append(f"{tag} {path}: unclosed '{open_ch}' from line {open_line}")


# ---------------------------------------------------------------------------
# Java checks
# ---------------------------------------------------------------------------

IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?([\w.]+)\s*;", re.M)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
IDENT_RE = re.compile(r"\b[A-Z][A-Za-z0-9_]*\b")


def check_java(root: Path, failures: list[str], stats: dict[str, int]) -> None:
    src = root / "backend" / "src"
    files = sorted(src.rglob("*.java"))
    if not files:
        failures.append("J0 no java sources found under backend/src")
        return
    stats["java_files"] = len(files)

    # Index every type this project declares -> the package it declares itself
    # to be in. Using the declared package rather than the directory means a
    # misplaced file is reported once, by J1, instead of cascading into J4.
    declared: dict[str, str] = {}
    for f in files:
        head = f.read_text(encoding="utf-8")[:4000]
        m = PACKAGE_RE.search(head)
        if m:
            declared[f.stem] = m.group(1)

    for f in files:
        text = f.read_text(encoding="utf-8")
        code = strip_java_noise(text)

        # J2
        check_balance(f, code, failures, "J2")

        # J1
        m = PACKAGE_RE.search(code)
        parts = f.parts
        if "java" in parts:
            idx = len(parts) - 1 - parts[::-1].index("java")
            expected = ".".join(parts[idx + 1 : -1])
        else:
            expected = None
        if not m:
            failures.append(f"J1 {f}: no package declaration")
        elif expected and m.group(1) != expected:
            failures.append(
                f"J1 {f}: package is '{m.group(1)}' but path implies '{expected}'"
            )

        pkg = m.group(1) if m else ""
        imports = [(mm.group(2), mm.group(1) is not None) for mm in IMPORT_RE.finditer(code)]
        body = IMPORT_RE.sub("", PACKAGE_RE.sub("", code))

        imported_simple: dict[str, str] = {}
        wildcard_pkgs: list[str] = []
        for fqn, is_static in imports:
            if fqn.endswith(".*"):
                wildcard_pkgs.append(fqn[:-2])
                continue
            simple = fqn.rsplit(".", 1)[-1]
            if is_static:
                continue
            imported_simple[simple] = fqn

            # J3
            if fqn.startswith(JAVA_PKG_ROOT + "."):
                rel = Path(*fqn.split("."))
                roots = [src / "main" / "java", src / "test" / "java"]
                found = any(
                    (r / rel).with_suffix(".java").exists()
                    or (r / rel).parent.with_suffix(".java").exists()  # nested type
                    for r in roots
                )
                if not found:
                    failures.append(f"J3 {f}: import '{fqn}' has no source file")

        used = set(IDENT_RE.findall(body))

        # J4 — project types used without an import, from another package
        for name in sorted(used):
            if name not in declared:
                continue
            owner = declared[name]
            if owner == pkg:
                continue
            if name in imported_simple:
                continue
            if owner in wildcard_pkgs:
                continue
            failures.append(
                f"J4 {f}: uses '{name}' ({owner}.{name}) without importing it"
            )

        # J5 — imported project types that are never referenced
        for simple, fqn in sorted(imported_simple.items()):
            if not fqn.startswith(JAVA_PKG_ROOT + "."):
                continue
            if simple not in used:
                failures.append(f"J5 {f}: unused import '{fqn}'")

        # J6 — fully-qualified JDK types in bodies read as an oversight
        for bad in re.finditer(r"(?<![\w.])(java\.util|java\.time|java\.math)\.[A-Z]\w*", body):
            failures.append(
                f"J6 {f}: fully-qualified '{bad.group(0)}' — import it instead"
            )


# ---------------------------------------------------------------------------
# TypeScript checks
# ---------------------------------------------------------------------------

TS_IMPORT_RE = re.compile(
    r"^\s*import\s+(?:type\s+)?(?P<clause>[^;'\"]*?)\s*from\s*['\"][^'\"]+['\"]\s*;?",
    re.M,
)


def check_ts(root: Path, failures: list[str], stats: dict[str, int]) -> None:
    src = root / "frontend" / "src"
    files = sorted(list(src.rglob("*.ts")) + list(src.rglob("*.tsx")))
    if not files:
        failures.append("T0 no typescript sources found under frontend/src")
        return
    stats["ts_files"] = len(files)

    for f in files:
        text = f.read_text(encoding="utf-8")
        code = strip_ts_noise(text)

        check_balance(f, code, failures, "T1")

        # Imports are matched against the raw text, because the stripper blanks
        # the quoted module path that the pattern needs. strip_ts_noise is
        # length-preserving, so the spans it finds line up with `code`, letting
        # us blank the import block there and search only real code for uses.
        spans = [m.span() for m in TS_IMPORT_RE.finditer(text)]
        chars = list(code)
        for start, end in spans:
            for k in range(start, end):
                if chars[k] != "\n":
                    chars[k] = " "
        body = "".join(chars)

        for m in TS_IMPORT_RE.finditer(text):
            clause = m.group("clause").strip()
            if not clause or clause.startswith("*"):
                continue
            names: list[str] = []
            brace = re.search(r"\{(.*)\}", clause, re.S)
            if brace:
                for piece in brace.group(1).split(","):
                    piece = piece.strip()
                    if not piece:
                        continue
                    names.append(piece.split(" as ")[-1].strip())
                default_part = clause[: brace.start()].strip().rstrip(",").strip()
                if default_part:
                    names.append(default_part)
            else:
                names.append(clause.split(" as ")[-1].strip())
            for name in names:
                if not re.fullmatch(r"[A-Za-z_$][\w$]*", name):
                    continue
                if not re.search(rf"\b{re.escape(name)}\b", body):
                    failures.append(
                        f"T2 {f}: '{name}' is imported but never used "
                        f"(noUnusedLocals will fail the build)"
                    )


# ---------------------------------------------------------------------------
# cross-cutting checks: the two halves of the app agreeing with each other
# ---------------------------------------------------------------------------

MAPPING_VERBS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}

# Routes that exist for infrastructure rather than for the UI, so the fact that
# no React component calls them is correct and not a dead endpoint.
ROUTE_NOT_FROM_UI = {
    "/api/health",
    "/api/ping",
    "/api/ping/authenticated",
    "/api/ping/manager",
}


def _norm_path(path: str) -> str:
    """Collapse path variables so `/users/{id}` and `/users/${id}` compare equal."""
    path = re.sub(r"\$?\{[^}]*\}", "{}", path)
    path = "/" + path.strip("/")
    return path.replace("//", "/")


def collect_backend_routes(root: Path) -> dict[tuple[str, str], str]:
    """Map (verb, path) -> declaring file, for every controller mapping."""
    routes: dict[tuple[str, str], str] = {}
    for f in sorted((root / "backend/src/main/java").rglob("*Controller.java")):
        text = f.read_text(encoding="utf-8", errors="replace")
        base_match = re.search(r'@RequestMapping\(\s*"([^"]*)"', text)
        base = base_match.group(1) if base_match else ""
        for m in re.finditer(
            r"@(" + "|".join(MAPPING_VERBS) + r')\b(?:\(\s*"([^"]*)"\s*\))?', text
        ):
            verb = MAPPING_VERBS[m.group(1)]
            suffix = m.group(2) or ""
            routes[(verb, _norm_path(base + "/" + suffix))] = f.name
    return routes


def collect_frontend_calls(root: Path) -> dict[tuple[str, str], str]:
    """Map (verb, path) -> source location, for every axios call in endpoints.ts."""
    calls: dict[tuple[str, str], str] = {}
    f = root / "frontend/src/endpoints.ts"
    if not f.exists():
        return calls
    base = "/api"  # api.ts: VITE_API_BASE_URL ?? "/api"
    for line_no, line in enumerate(f.read_text(encoding="utf-8").splitlines(), start=1):
        m = re.search(
            r"api\.(get|post|put|delete|patch)\s*(?:<[^(]*>)?\(\s*[\"`]([^\"`]+)[\"`]", line
        )
        if m:
            verb = m.group(1).upper()
            calls[(verb, _norm_path(base + m.group(2)))] = f"endpoints.ts:{line_no}"
    return calls


def check_endpoint_parity(root: Path, failures: list[str], notes: list[str]) -> None:
    backend = collect_backend_routes(root)
    frontend = collect_frontend_calls(root)

    for (verb, path), where in sorted(frontend.items(), key=lambda kv: kv[0][1]):
        if (verb, path) not in backend:
            failures.append(f"X1 {where}: frontend calls {verb} {path} — no controller maps it")

    for (verb, path), fname in sorted(backend.items(), key=lambda kv: kv[0][1]):
        if (verb, path) not in frontend and path not in ROUTE_NOT_FROM_UI:
            notes.append(f"X1 {fname}: {verb} {path} is not called from the UI")


def check_css_parity(root: Path, failures: list[str], notes: list[str]) -> None:
    """Every class the components use must have a rule, and vice versa.

    `tsc` cannot see a typo in a className string, and a missing rule renders as
    a silently unstyled element, so this is the only thing standing between a
    renamed class and a broken-looking screen.
    """
    src = root / "frontend/src"
    css_file = src / "index.css"
    if not css_file.exists():
        return
    css = re.sub(r"/\*.*?\*/", "", css_file.read_text(encoding="utf-8"), flags=re.S)
    defined = set(re.findall(r"\.([a-zA-Z][\w-]*)", css))

    # Class names built as `prefix-${slug(value)}`, expanded against the enum
    # family the prefix names. Keeps the check honest about dynamic classes
    # instead of skipping them, which is where a missing rule would hide.
    families = {
        "status": ["NEW", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "CLOSED", "CANCELLED"],
        "col": ["NEW", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "CLOSED", "CANCELLED"],
        "priority": ["URGENT", "HIGH", "MEDIUM", "LOW"],
        "sla": ["ON_TRACK", "AT_RISK", "BREACHED"],
        "role": ["MANAGER", "DISPATCHER", "TECHNICIAN", "CUSTOMER"],
        "kind": ["SLA_BREACH", "SLA_AT_RISK", "WORK_ORDER_ASSIGNED", "STATUS_CHANGED"],
        "tone": ["DEFAULT", "WARN", "DANGER", "GOOD"],
    }
    slug = lambda v: v.lower().replace("_", "-")  # noqa: E731
    enum_tokens = {slug(v) for vs in families.values() for v in vs}

    def balanced(text: str, start: int) -> str:
        depth = 0
        for j in range(start, len(text)):
            if text[j] == "{":
                depth += 1
            elif text[j] == "}":
                depth -= 1
                if depth == 0:
                    return text[start + 1 : j]
        return text[start + 1 :]

    used: dict[str, str] = {}
    for f in sorted(list(src.glob("*.tsx")) + list(src.glob("*/*.tsx"))):
        text = re.sub(r"//[^\n]*", "", f.read_text(encoding="utf-8"))
        for m in re.finditer(r"className=", text):
            k = m.end()
            tokens: list[str] = []
            if k < len(text) and text[k] == '"':
                tokens = text[k + 1 : text.index('"', k + 1)].split()
            elif k < len(text) and text[k] == "{":
                expr = balanced(text, k)
                # Drop the operands of comparisons first. `target === "CANCELLED"
                # ? "btn-danger" : ...` mentions an enum value inside a className
                # expression without it ever being a class, and harvesting it
                # would report a missing rule for a string that never reaches the
                # DOM.
                expr = re.sub(r'(?:===?|!==?)\s*"[^"]*"', "", expr)
                expr = re.sub(r'"[^"]*"\s*(?:===?|!==?)', "", expr)
                for lit in re.findall(r'"([^"\n]*)"', expr):
                    tokens += lit.split()
                for lit in re.findall(r"`([^`]*)`", expr):
                    for chunk in lit.split():
                        if "${" not in chunk:
                            tokens.append(chunk)
                            continue
                        prefix = chunk.split("${")[0]
                        key = next((k2 for k2 in families if k2 in prefix), None)
                        tokens += (
                            [prefix + slug(v) for v in families[key]] if key else [chunk]
                        )
            for tok in tokens:
                if re.fullmatch(r"[a-zA-Z][\w-]*", tok):
                    used.setdefault(tok, f.name)

    for cls in sorted(set(used) - defined - enum_tokens):
        failures.append(f"X2 {used[cls]}: class '{cls}' has no rule in index.css")
    for cls in sorted(defined - set(used)):
        notes.append(f"X2 index.css: rule '.{cls}' is never used")


# Which service's sort whitelist governs a given sort literal. The api object on
# the line wins, because a work-order screen legitimately sorts its customer
# filter dropdown by `name` — a per-file rule would call that an error.
SORT_OWNER_BY_API = {
    "workOrderApi": "WorkOrderService.java",
    "customerApi": "CustomerService.java",
    "siteApi": "SiteService.java",
    "partApi": "PartService.java",
}

# Fallback for sort literals held in a constant away from their call site, such
# as a SORT_OPTIONS array at the top of a file. Add a row when a new paged screen
# appears; an unlisted file falls back to the union of every whitelist, which
# still catches a misspelling.
SORT_OWNER_BY_FILE = {
    "WorkOrdersPage.tsx": "WorkOrderService.java",
    "DashboardPage.tsx": "WorkOrderService.java",
    "MyWorkPage.tsx": "WorkOrderService.java",
    "BoardPage.tsx": "WorkOrderService.java",
    "CustomersPage.tsx": "CustomerService.java",
    "SitesPage.tsx": "SiteService.java",
    "PartsPage.tsx": "PartService.java",
}


def check_sort_parity(root: Path, failures: list[str]) -> None:
    """A sort key the server does not whitelist is a 400 at runtime.

    PageableFactory rejects anything outside the per-service whitelist, so a
    dropdown offering `stock,asc` instead of `stockQuantity,asc` looks fine until
    someone picks it. Nothing else in the toolchain can catch that.
    """
    whitelists: dict[str, set[str]] = {}
    for f in sorted((root / "backend/src/main/java").rglob("*Service.java")):
        text = f.read_text(encoding="utf-8", errors="replace")
        m = re.search(r"SORTABLE_FIELDS\s*=\s*(?:Set\.of\()?(.*?)\);", text, re.S)
        if m:
            whitelists[f.name] = set(re.findall(r'"([^"]+)"', m.group(1)))
    if not whitelists:
        return
    union = set().union(*whitelists.values())

    for f in sorted((root / "frontend/src").glob("pages/*.tsx")):
        for line_no, line in enumerate(f.read_text(encoding="utf-8").splitlines(), start=1):
            hits = re.findall(r'"([a-zA-Z][\w.]*),(asc|desc)"', line)
            if not hits:
                continue
            api = next((a for a in SORT_OWNER_BY_API if a + "." in line), None)
            owner = SORT_OWNER_BY_API.get(api or "") or SORT_OWNER_BY_FILE.get(f.name, "")
            allowed = whitelists.get(owner, union)
            for field, _dir in hits:
                if field not in allowed:
                    failures.append(
                        f"X3 {f.name}:{line_no}: sort key '{field}' is not in "
                        f"{owner or 'any service'}'s whitelist"
                    )


# ---------------------------------------------------------------------------
# secret scan
# ---------------------------------------------------------------------------

SECRET_PATTERNS = [
    (re.compile(r"(?i)jwt[._-]?secret\s*[:=]\s*['\"][^'\"$\s{}]{8,}"), "JWT secret literal"),
    (re.compile(r"(?i)(password|passwd)\s*[:=]\s*['\"][^'\"$\s{}]{6,}"), "password literal"),
    (re.compile(r"(?i)api[._-]?key\s*[:=]\s*['\"][^'\"$\s{}]{8,}"), "API key literal"),
    (re.compile(r"AKIA[0-9A-Z]{16}"), "AWS access key id"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "private key"),
]

# Local-only development defaults are fine and are deliberately obvious.
SECRET_ALLOWLIST = {
    "docker-compose.yml",
    "application-local.yml",
    "fresh-db.sh",
    "fresh-db.ps1",
    "run.sh",
    "run.ps1",
    "static-check.py",
}

SCAN_SUFFIXES = {".java", ".yml", ".yaml", ".ts", ".tsx", ".properties", ".sql", ".env"}

# An escape hatch that stays visible in review.
#
# A file-level allowlist is too blunt for source files: adding LoginPage.tsx to
# the set above would also hide a real secret pasted into it next month. This
# pragma is scoped to the line it appears on (or the line above it) and only
# counts when a reason follows the marker, so every exception has to be argued
# for in the diff rather than waved through.
ALLOW_PRAGMA = re.compile(r"keystone:allow-secret\s+\S")


def check_secrets(root: Path, failures: list[str]) -> None:
    for f in sorted(root.rglob("*")):
        if not f.is_file() or f.suffix not in SCAN_SUFFIXES:
            continue
        if any(part in {"node_modules", "target", "dist", ".git"} for part in f.parts):
            continue
        if f.name in SECRET_ALLOWLIST:
            continue
        text = f.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        for line_no, line in enumerate(lines, start=1):
            previous = lines[line_no - 2] if line_no >= 2 else ""
            if ALLOW_PRAGMA.search(line) or ALLOW_PRAGMA.search(previous):
                continue
            for pattern, label in SECRET_PATTERNS:
                if pattern.search(line):
                    failures.append(f"S1 {f}:{line_no}: possible {label}")


# ---------------------------------------------------------------------------

def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []
    # Things worth saying out loud that are not defects. An endpoint no screen
    # calls yet is worth knowing about; failing the build over it would just
    # train someone to ignore the output.
    notes: list[str] = []
    stats: dict[str, int] = {}

    check_java(root, failures, stats)
    check_ts(root, failures, stats)
    check_endpoint_parity(root, failures, notes)
    check_css_parity(root, failures, notes)
    check_sort_parity(root, failures)
    check_secrets(root, failures)

    backend_routes = collect_backend_routes(root)
    frontend_calls = collect_frontend_calls(root)

    print(f"KEYSTONE static check — {root}")
    print(f"  java files     : {stats.get('java_files', 0)}")
    print(f"  ts files       : {stats.get('ts_files', 0)}")
    print(f"  routes mapped  : {len(backend_routes)}")
    print(f"  routes called  : {len(frontend_calls)}")
    print()

    if notes:
        print(f"{len(notes)} note(s):\n")
        for line in notes:
            print("  " + line)
        print()

    if failures:
        print(f"FAILED — {len(failures)} finding(s):\n")
        for line in failures:
            print("  " + line)
        return 1

    print("PASSED — no findings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
