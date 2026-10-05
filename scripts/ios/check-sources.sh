#!/usr/bin/env bash
# check-sources.sh    source rules of the iOS app; scripts/ios/test.sh runs it before every build (L18 IO-20)
#
# - No Cyrillic in the Swift of iosApp/Sources, iosApp/Extensions and iosApp/Shared: user-visible text comes from
#   the catalog through the generated string tables (ADR 0028). Tests may compare with Russian text.
# - No App Group or team ID literal in that Swift or in shared/**/src/iosMain Kotlin: every process reads
#   `AppGroupID` and `KeychainGroup` from its own Info.plist (docs/ios.md, Identifiers).
# - The generated iosApp/Shared files are fresh: each table has the keys its catalog files give it, AppSymbol.swift
#   the cases of docs/design/icons.tsv. Texts are compared byte for byte by `checkStringCatalog` in every Android
#   build; this check is the cheap part that needs no Gradle. Fix: scripts/verify.sh run -- :app:exportAppleStrings
# - Exit code: 0 clean, 1 violations (one line each on stderr).

set -u

root=$(cd "$(dirname "$0")/../.." 2> /dev/null && pwd -P) || {
  printf 'check-sources.sh: cannot resolve the repository root\n' >&2
  exit 1
}

exec /usr/bin/python3 - "$root" <<'PY'
import json, re, sys
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
problems = []

def rel(path):
    return path.relative_to(root).as_posix()

# ---- Swift and Kotlin sources ----------------------------------------------------------------------------------

swift = [p for d in ("iosApp/Sources", "iosApp/Extensions", "iosApp/Shared") for p in sorted((root / d).rglob("*.swift"))]
kotlin = sorted(root.glob("shared/*/src/iosMain/**/*.kt"))
cyrillic = re.compile("[\u0400-\u04ff]")
identifier = re.compile(r"group\.dev\.alllexey|\b[A-Z0-9]{10}\.dev\.alllexey")

for path in swift:
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if cyrillic.search(line):
            problems.append(f"{rel(path)}:{number}: Cyrillic in Swift; use a catalog key (AppStrings, generated symbols)")
for path in swift + kotlin:
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if identifier.search(line):
            problems.append(f"{rel(path)}:{number}: App Group or team ID literal; read it from the process's Info.plist")

# ---- generated tables ------------------------------------------------------------------------------------------

catalog = (
    sorted(root.glob("app/src/main/res/values/strings_*.xml"))
    + sorted(root.glob("shared/*/src/commonMain/composeResources/values*/strings*.xml"))
    + sorted(root.glob("iosApp/Strings/strings_ios*.xml"))
)
ios_file = re.compile(r"strings_ios[A-Za-z0-9_]*\.xml")

def table_of(path):
    if path.parent.name.startswith("values-") or path.name == "strings_debug.xml":
        return None
    if path.name == "strings_platform.xml" or ios_file.fullmatch(path.name):
        return "Localizable"
    return path.name.removesuffix(".xml")

expected = {}
for path in catalog:
    table = table_of(path)
    if table is None:
        continue
    keys = [e.get("name") for e in ET.parse(path).getroot() if e.tag in ("string", "plurals")]
    expected.setdefault(table, set()).update(keys)
for line in (root / "build-logic/strings/apple-tables.properties").read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        table, _, key = line.split("=", 1)[0].strip().partition(".")
        expected.setdefault(table, set()).add(key)

strings_dir = root / "iosApp/Shared/Strings"
committed = {p.stem: p for p in sorted(strings_dir.glob("*.xcstrings"))}
for table in sorted(set(expected) | set(committed)):
    if table not in committed:
        problems.append(f"iosApp/Shared/Strings/{table}.xcstrings is missing")
        continue
    if table not in expected:
        problems.append(f"{rel(committed[table])} has no catalog file")
        continue
    keys = set(json.loads(committed[table].read_text(encoding="utf-8"))["strings"])
    for key in sorted(expected[table] - keys):
        problems.append(f"{rel(committed[table])} lacks {key}")
    for key in sorted(keys - expected[table]):
        problems.append(f"{rel(committed[table])} has {key}, which no catalog file defines")

rows = [r.split("\t") for r in (root / "docs/design/icons.tsv").read_text(encoding="utf-8").splitlines() if r.strip()]
header = rows[0]
ids = {r[header.index("id")] for r in rows[1:] if r[header.index("kind")] in ("shared", "custom")}
symbols = root / "iosApp/Shared/Symbols/AppSymbol.swift"
cases = set(re.findall(r'^\s+case \S+ = "([^"]+)"$', symbols.read_text(encoding="utf-8"), re.M))
for icon in sorted(ids ^ cases):
    problems.append(f"{rel(symbols)} and docs/design/icons.tsv disagree on {icon}")

if problems:
    for problem in problems:
        print(f"check-sources.sh: {problem}", file=sys.stderr)
    if any(".xcstrings" in p or "AppSymbol" in p for p in problems):
        print("check-sources.sh: regenerate with scripts/verify.sh run -- :app:exportAppleStrings", file=sys.stderr)
    sys.exit(1)
PY
