#!/usr/bin/env python3
"""Move a feature's string catalog files from :app into its shared module, or retire their Android export.

  strings-move.py [--dry-run] <unit|file>           git mv app/src/main/res/values/strings_<file>.xml to
                                                    shared/feature-<module>/src/commonMain/composeResources/values/
                                                    and add androidExport("values/strings_<file>.xml") to the
                                                    module's build.gradle.kts, so :app keeps its R.string ids.
  strings-move.py [--dry-run] --retire <unit|file>  remove that androidExport line once no R.string, R.plurals,
                                                    @string/ or @plurals/ in app/ names an id of the file.

A unit is a v2.3 feature module (qr, home, schedule, sport, recordbook, social, settings, resources, reviews,
account); `account` stands for its six files, and each of them (auth, onboarding, me, weblogin, web, update) can be
moved alone. Keys, texts and order stay as they are (ADR 0028), and the Apple tables are named after the files, so a
move regenerates no .xcstrings. The script is idempotent, prints every change, and runs no Gradle: the caller's
`scripts/verify.sh quick` checks the result. A permanent partial export is allowed: a file whose ids still have an
app/ user is not retired. The recipe is vibe/v2.3/recipes/strings-and-icons.md.
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app"
APP_VALUES = APP / "src" / "main" / "res" / "values"
SHARED = ROOT / "shared"

ACCOUNT_FILES = ("auth", "onboarding", "me", "weblogin", "web", "update")
UNITS = {
    unit: (unit,)
    for unit in ("qr", "home", "schedule", "sport", "recordbook", "social", "settings", "resources", "reviews")
} | {"account": ACCOUNT_FILES}
MODULE_OF = {file: unit for unit, files in UNITS.items() for file in files}

BLOCK_COMMENT = "// :app reads these files as Android resources until --retire (scripts/strings-move.py, L05 KM-09b)."
BLOCK_START = re.compile(r"^itmowidgetsStrings\s*\{\s*$")
SCANNED = {".kt", ".kts", ".java", ".xml"}
SKIPPED_DIRS = {"build", ".gradle", ".cxx"}


def files_of(name):
    if name in UNITS:
        return UNITS[name]
    if name in MODULE_OF:
        return (name,)
    sys.exit(f"{name}: not a unit ({', '.join(UNITS)}) or a file ({', '.join(MODULE_OF)})")


def module_dir(file):
    return SHARED / f"feature-{MODULE_OF[file]}"


def app_file(file):
    return APP_VALUES / f"strings_{file}.xml"


def shared_file(file):
    return module_dir(file) / "src" / "commonMain" / "composeResources" / "values" / f"strings_{file}.xml"


def export_line(file):
    return f'    androidExport("values/strings_{file}.xml")'


def rel(path):
    return path.relative_to(ROOT)


def build_file(file):
    path = module_dir(file) / "build.gradle.kts"
    if not path.is_file():
        sys.exit(f"{rel(path)}: no build file for {file}")
    return path


def block_range(lines):
    """[start, end] line indexes of the top-level itmowidgetsStrings block, or None."""
    for start, line in enumerate(lines):
        if BLOCK_START.match(line):
            end = next((i for i in range(start + 1, len(lines)) if lines[i] == "}"), None)
            if end is None:
                sys.exit("itmowidgetsStrings { has no closing } at column 0")
            return start, end
    return None


def with_export(text, file):
    lines = text.splitlines()
    line = export_line(file)
    block = block_range(lines)
    if block is None:
        while lines and not lines[-1].strip():
            lines.pop()
        lines += ["", BLOCK_COMMENT, "itmowidgetsStrings {", line, "}"]
    elif line in lines[block[0]:block[1]]:
        return text
    else:
        lines.insert(block[1], line)
    return "\n".join(lines) + "\n"


def without_export(text, file):
    lines = text.splitlines()
    line = export_line(file)
    block = block_range(lines)
    if block is None or line not in lines[block[0]:block[1]]:
        return text
    lines.remove(line)
    start, end = block_range(lines)
    if all(not entry.strip() for entry in lines[start + 1:end]):
        if start > 0 and lines[start - 1] == BLOCK_COMMENT:
            start -= 1
        del lines[start:end + 1]
        if start > 0 and not lines[start - 1].strip() and (start == len(lines) or not lines[start].strip()):
            del lines[start - 1]
    while lines and not lines[-1].strip():
        lines.pop()
    return "\n".join(lines) + "\n"


def write(path, old, new, dry_run, message):
    if old == new:
        return False
    print(("would " if dry_run else "") + message)
    if not dry_run:
        path.write_text(new, encoding="utf-8")
    return True


def move(file, dry_run):
    source, target = app_file(file), shared_file(file)
    if source.exists() and target.exists():
        sys.exit(f"{rel(source)} and {rel(target)} both exist; merge by hand, then rerun")
    if source.exists():
        print(("would " if dry_run else "") + f"git mv {rel(source)} {rel(target)}")
        if not dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["git", "-C", str(ROOT), "mv", str(rel(source)), str(rel(target))], check=True)
    elif target.exists():
        print(f"{file}: already in {rel(target.parent)}")
    else:
        sys.exit(f"{file}: neither {rel(source)} nor {rel(target)} exists")
    build = build_file(file)
    text = build.read_text(encoding="utf-8")
    changed = write(build, text, with_export(text, file), dry_run,
                    f'add androidExport("values/strings_{file}.xml") to {rel(build)}')
    if not changed:
        print(f"{file}: {rel(build)} already exports it")


def ids_of(path):
    root = ElementTree.parse(path).getroot()
    return sorted(e.get("name") for e in root if e.tag in ("string", "plurals"))


def app_users(ids):
    """`path:line: id` for every app/ reference to one of [ids] through R or an XML resource reference."""
    alternatives = "|".join(map(re.escape, ids))
    pattern = re.compile(
        rf"(?:(?<![\w.])|(?<=itmowidgets\.))(?:R|AppR)\.(?:string|plurals)\.({alternatives})\b"
        rf"|@(?:string|plurals)/({alternatives})\b"
    )
    found = []
    for directory, subdirs, names in os.walk(APP):
        subdirs[:] = sorted(d for d in subdirs if d not in SKIPPED_DIRS)
        for name in sorted(names):
            path = Path(directory) / name
            if path.suffix not in SCANNED:
                continue
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
                for match in pattern.finditer(line):
                    found.append(f"{rel(path)}:{number}: {match.group(1) or match.group(2)}")
    return found


def retire(file, dry_run):
    target = shared_file(file)
    if not target.exists():
        print(f"{file}: not moved yet, nothing to retire")
        return True
    build = build_file(file)
    text = build.read_text(encoding="utf-8")
    new = without_export(text, file)
    if new == text:
        print(f"{file}: {rel(build)} exports nothing for it")
        return True
    users = app_users(ids_of(target))
    if users:
        print(f"{file}: kept, app/ still uses its ids:")
        print("\n".join(f"  {user}" for user in users))
        return False
    write(build, text, new, dry_run, f'remove androidExport("values/strings_{file}.xml") from {rel(build)}')
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("name", metavar="unit|file")
    parser.add_argument("--retire", action="store_true", help="remove the androidExport line instead of moving")
    parser.add_argument("--dry-run", action="store_true", help="print what would change, change nothing")
    args = parser.parse_args()

    files = files_of(args.name)
    if args.retire:
        retired = [retire(file, args.dry_run) for file in files]
        sys.exit(0 if all(retired) else 1)
    for file in files:
        move(file, args.dry_run)


if __name__ == "__main__":
    main()
