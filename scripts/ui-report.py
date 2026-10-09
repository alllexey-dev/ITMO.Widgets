#!/usr/bin/env python3
"""ui-report.py: every run of every instrumentation test, from the device's TestRunner log.

Usage:
  ui-report.py --logcat <file> [--gradle <file>] [--root <repo>] [--marker <text>]
  ui-report.py --list-suites [--root <repo>]

Why (L04 TC-UIREPORT): `verify.sh ui all` runs the members of a JUnit suite (ShellSuite) twice, once inside the
suite and once on their own. AGP's JUnit XML, its HTML report, test-result.pb and the closing "Finished <n> tests"
keep one result per class and method, the last one, so a pass in the second run hid a failure in the first.

- --logcat is `adb logcat -v raw -s TestRunner:V` captured while Gradle ran: androidx.test's LogRunListener writes
  `started:`, `finished:`, `failed:` with the exception, `assumption failed:` and `ignored:` for every run, duplicates
  included. Lines up to the last line that contains --marker (default `itmo-verify-ui-start`) are older runs and are
  skipped; without a marker line the whole file counts.
- --gradle is Gradle's console output; every `<class> > <method>[<device>] FAILED` line is a failed run too, so a
  failure still counts when the log capture missed it (or when there is no device log, as on a managed device).
- A run is identified by its suite: the suites are the `@Suite.SuiteClasses` of app/src/androidTest*/ under --root,
  and a stretch of runs whose classes follow a suite's members in order (two members at least) ran inside it; other
  runs of a member ran standalone. A failure names the `ShellModeRule` shells its message names
  ("in the NAV3 shell"), or the default shell for a class without the rule.

Prints one `ui-report:` block. Exit code: 0 no failed run, 1 at least one failed or unfinished run, 2 usage error.
"""

import argparse
import collections
import glob
import os
import re
import sys

DEFAULT_MARKER = "itmo-verify-ui-start"
NAME_RE = re.compile(r"^(?P<method>.*)\((?P<cls>[^()]*)\)$")
GRADLE_FAILED_RE = re.compile(r"^(?P<cls>[\w.$]+) > (?P<method>.+?)\[(?P<device>[^\]]*)\]\s+FAILED\s*$")
ANSI_RE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
SHELL_RE = re.compile(r"\bin the ([A-Z][A-Z0-9_]*) shell\b")


class Run:
    def __init__(self, cls, method, status="running"):
        self.cls = cls
        self.method = method
        self.status = status  # running, passed, failed, skipped, ignored, unfinished
        self.trace = []
        self.suite = None
        self.occurrence = 1
        self.occurrences = 1
        self.from_gradle = False

    @property
    def name(self):
        return "%s#%s" % (self.cls, self.method)


def split_name(display):
    match = NAME_RE.match(display.strip())
    if not match:
        return display.strip(), ""
    return match.group("cls"), match.group("method")


def parse_logcat(lines, marker):
    start = 0
    for index, line in enumerate(lines):
        if marker in line:
            start = index + 1
    runs = []
    last = {}
    exception = None  # the run an exception block belongs to, or a list that swallows it
    pending = None  # the run whose `failed:` line came last; its exception block follows
    for raw in lines[start:]:
        line = raw.rstrip("\r\n")
        if exception is not None:
            if line == "----- end exception -----":
                exception = None
            elif isinstance(exception, Run):
                exception.trace.append(line)
            continue
        if line == "----- begin exception -----":
            exception = pending if pending is not None else []
            continue
        pending = None
        if line.startswith("run started:"):
            continue
        if line.startswith("run finished:"):
            for run in runs:
                if run.status == "running":
                    run.status = "unfinished"
            continue
        for prefix, kind in (("started: ", "started"), ("finished: ", "finished"), ("failed: ", "failed"),
                             ("assumption failed: ", "assumption"), ("ignored: ", "ignored")):
            if line.startswith(prefix):
                cls, method = split_name(line[len(prefix):])
                key = (cls, method)
                current = last.get(key)
                if kind == "started":
                    current = Run(cls, method)
                    runs.append(current)
                    last[key] = current
                elif kind == "ignored":
                    runs.append(Run(cls, method, "ignored"))
                elif kind == "finished":
                    if current is not None and current.status == "running":
                        current.status = "passed"
                elif kind == "failed":
                    # A class-level failure (an @BeforeClass, a crashed runner) has no `started:` line.
                    if current is None or current.status not in ("running", "failed"):
                        current = Run(cls, method)
                        runs.append(current)
                        last[key] = current
                    current.status = "failed"
                    pending = current
                elif kind == "assumption":
                    if current is not None and current.status == "running":
                        current.status = "skipped"
                break
    for run in runs:
        if run.status == "running":
            run.status = "unfinished"
    return runs


def parse_gradle(lines):
    failures = collections.Counter()
    for raw in lines:
        match = GRADLE_FAILED_RE.match(ANSI_RE.sub("", raw).strip())
        if match:
            failures[(match.group("cls"), match.group("method"))] += 1
    return failures


def source_files(root):
    patterns = [os.path.join(root, "app", "src", "androidTest*", "**", "*.kt"),
                os.path.join(root, "app", "src", "androidTest*", "**", "*.java")]
    files = []
    for pattern in patterns:
        files.extend(glob.glob(pattern, recursive=True))
    return sorted(files)


def read(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return handle.read()
    except OSError:
        return ""


def parse_suites(root):
    """{suite FQCN: [member FQCN, ...]} in declaration order."""
    suites = {}
    for path in source_files(root):
        text = read(path)
        block = re.search(r"@(?:org\.junit\.runners\.)?Suite\.SuiteClasses\((.*?)\)\s*(?:@|class|public|internal|$)",
                          text, re.S)
        if not block:
            continue
        package = re.search(r"^\s*package\s+([\w.]+)", text, re.M)
        package = package.group(1) if package else ""
        imports = {name.split(".")[-1]: name for name in re.findall(r"^\s*import\s+([\w.]+)", text, re.M)}
        suite = re.search(r"\bclass\s+(\w+)", text[block.end() - 10:])
        if not suite:
            continue
        members = []
        for member in re.findall(r"([\w.]+)::class|([\w.]+)\.class", block.group(1)):
            name = member[0] or member[1]
            if "." in name:
                members.append(name)
            else:
                members.append(imports.get(name, "%s.%s" % (package, name) if package else name))
        suites["%s.%s" % (package, suite.group(1)) if package else suite.group(1)] = members
    return suites


def uses_shell_rule(root, cls, cache):
    if cls not in cache:
        rel = cls.split("$")[0].replace(".", os.sep)
        text = ""
        for base in glob.glob(os.path.join(root, "app", "src", "androidTest*", "*")):
            for ext in (".kt", ".java"):
                text = text or read(os.path.join(base, rel + ext))
        cache[cls] = "ShellModeRule" in text
    return cache[cls]


def segments(runs):
    """Consecutive runs of one class; a method seen again starts a new segment (the class ran once more)."""
    result = []
    for index, run in enumerate(runs):
        if result and result[-1][0] == run.cls and run.method not in result[-1][2]:
            result[-1][1].append(index)
            result[-1][2].add(run.method)
        else:
            result.append((run.cls, [index], {run.method}))
    return result


def label_suites(runs, suites):
    segs = segments(runs)
    for suite, members in suites.items():
        windows = []
        index = 0
        while index < len(segs):
            if segs[index][0] not in members:
                index += 1
                continue
            position = members.index(segs[index][0])
            end = index + 1
            while end < len(segs) and segs[end][0] in members[position + 1:]:
                position = members.index(segs[end][0], position + 1)
                end += 1
            windows.append((index, end))
            index = end
        best = max((end - start for start, end in windows), default=0)
        if best < 2 or [end - start for start, end in windows].count(best) > 1:
            continue
        for start, end in windows:
            if end - start == best:
                for seg in segs[start:end]:
                    for run_index in seg[1]:
                        runs[run_index].suite = suite.split(".")[-1]


def label_occurrences(runs):
    totals = collections.Counter((run.cls, run.method) for run in runs)
    seen = collections.Counter()
    for run in runs:
        key = (run.cls, run.method)
        seen[key] += 1
        run.occurrence = seen[key]
        run.occurrences = totals[key]


def describe(run, root, rule_cache, members):
    parts = []
    if run.from_gradle:
        parts.append("run not in the device log")
    elif run.suite:
        parts.append("in " + run.suite)
    elif run.cls in members:
        parts.append("standalone")
    if run.occurrences > 1 and not run.from_gradle:
        parts.append("run %d of %d" % (run.occurrence, run.occurrences))
    shells = []
    for match in SHELL_RE.finditer("\n".join(run.trace)):
        if match.group(1) not in shells:
            shells.append(match.group(1))
    if shells:
        parts.append("shell " + ", ".join(shells))
    elif uses_shell_rule(root, run.cls, rule_cache):
        parts.append("shell not named")
    else:
        parts.append("default shell")
    return "; ".join(parts)


def first_line(run):
    if run.status == "unfinished":
        return "started but never finished (the instrumentation crashed or was stopped)"
    for line in run.trace:
        if line.strip():
            text = line.strip()
            return text if len(text) <= 240 else text[:237] + "..."
    return "(no exception text in the log)"


def main(argv):
    parser = argparse.ArgumentParser(add_help=True, description=__doc__.split("\n\n")[0])
    parser.add_argument("--logcat")
    parser.add_argument("--gradle")
    parser.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    parser.add_argument("--marker", default=DEFAULT_MARKER)
    parser.add_argument("--list-suites", action="store_true")
    args = parser.parse_args(argv)

    suites = parse_suites(args.root)
    if args.list_suites:
        for suite, members in sorted(suites.items()):
            print("%s: %s" % (suite, ", ".join(members)))
        return 0
    if not args.logcat and not args.gradle:
        parser.error("pass --logcat and/or --gradle")

    log_lines = read(args.logcat).splitlines() if args.logcat else []
    runs = parse_logcat(log_lines, args.marker)
    gradle = parse_gradle(read(args.gradle).splitlines()) if args.gradle else collections.Counter()
    label_occurrences(runs)
    label_suites(runs, suites)
    members = {member for listed in suites.values() for member in listed}
    rule_cache = {}

    out = []
    if runs:
        distinct = collections.Counter((run.cls, run.method) for run in runs)
        repeated = sum(1 for count in distinct.values() if count > 1)
        line = "%d runs of %d tests" % (len(runs), len(distinct))
        if repeated:
            line += " (%d ran more than once)" % repeated
        out.append(line)
    elif args.logcat:
        out.append("no TestRunner events after the marker in %s; failures below come from Gradle's output only"
                   % args.logcat)

    failed = [run for run in runs if run.status in ("failed", "unfinished")]
    logged = collections.Counter((run.cls, run.method) for run in failed)
    extra = []
    for key, count in sorted(gradle.items()):
        for _ in range(count - logged.get(key, 0)):
            ghost = Run(key[0], key[1], "failed")
            ghost.from_gradle = True
            ghost.trace = ["(from Gradle's output; the device log has no matching failure)"]
            extra.append(ghost)

    if not failed and not extra:
        out.append("no failed run")
        print("\n".join("ui-report: " + line for line in out))
        return 0

    tests = {(run.cls, run.method) for run in failed + extra}
    out.append("FAILED: %d run%s of %d test%s" % (len(failed) + len(extra), "" if len(failed) + len(extra) == 1 else "s",
                                                   len(tests), "" if len(tests) == 1 else "s"))
    for run in failed + extra:
        out.append("  FAIL %s [%s] %s" % (run.name, describe(run, args.root, rule_cache, members), first_line(run)))
    for run in failed:
        others = [other for other in runs if other is not run and (other.cls, other.method) == (run.cls, run.method)
                  and other.status == "passed"]
        if others:
            where = ", ".join(sorted({other.suite and "in " + other.suite or "standalone" for other in others}))
            out.append("  note: %s also passed (%s); Gradle's XML, HTML and 'Finished' count keep only the last run"
                       % (run.name, where))
    print("\n".join("ui-report: " + line for line in out))
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
