#!/usr/bin/env python3
"""Owner of every string id: which app/src/main/res/values/strings_<file>.xml holds it.

The owner is computed from usage, not from the id prefix (ADR 0028, L05 AA-01):

1. `platform` holds every id a system surface reaches (manifest, res/xml*, widget and launcher-preview layouts,
   the Kotlin files in PLATFORM_KOTLIN) and every id the shared text builders in PLATFORM_BUILDERS reference.
   `scripts/strings-frozen-keys.txt` is this set, sorted, and only grows.
2. `common` holds ids used by two or more owner units, or by `core/**` at all.
3. Otherwise the single owner unit (a v2.3 module) holds the id.

Usage comes from app/src/{main,github,play}: `R.string`/`R.plurals` (and the `AppR` alias), layouts and menus
through their users (`*Binding`, `R.layout`, `R.menu`, `@menu/` and `<include>` parents). `src/debug` previews and
the test source sets decide only for an id that has no other user.

Modes:
  --split <rev> [--diff]  write strings_<file>.xml from <rev>'s strings.xml, names, values and order kept, and
                          delete strings.xml; --diff prints what would change instead (nothing = in sync).
  --where <id|path>       the file for an id, or for a new string used from that source file.
  --report                id, file, users as TSV.
"""

import argparse
import difflib
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app"
SRC = APP / "src"
RES = SRC / "main" / "res"
VALUES = RES / "values"
CATALOG = "app/src/main/res/values/strings.xml"
PACKAGE = "dev/alllexey/itmowidgets"

PRIMARY_SETS = ("main", "github", "play")
PREVIEW_SETS = ("debug",)
TEST_SETS = ("test", "testGithub", "androidTest", "androidTestPlay")

# The v2.3 modules (master plan section 3.1); `account` keeps one file per package until L16 moves them.
FEATURE_UNITS = {
    "qr": "qr",
    "home": "home",
    "schedule": "schedule",
    "sport": "sport",
    "recordbook": "recordbook",
    "social": "social",
    "friendselector": "social",
    "settings": "settings",
    "resources": "resources",
    "reviews": "reviews",
    "auth": "auth",
    "onboarding": "onboarding",
    "me": "me",
    "weblogin": "weblogin",
    "web": "web",
    "update": "update",
    "debug": "debug",
}
FILES = (
    "app", "common", "platform", "debug", "qr", "home", "schedule", "sport", "recordbook", "social", "settings",
    "resources", "reviews", "auth", "onboarding", "me", "weblogin", "web", "update",
)
CORE = "core"

# System surfaces written in Kotlin: widgets, notifications and push, the QS tile, the device calendar.
PLATFORM_KOTLIN = (
    "app/AndroidAppNotifier.kt",
    "core/notification/AppNotificationChannels.kt",
    "feature/schedule/work/AndroidScheduleChangeNotifier.kt",
    "feature/recordbook/work/AndroidMarksNotifier.kt",
    "feature/social/data/push/FriendshipPushHandler.kt",
    "feature/sport/data/push/SportSignPushHandler.kt",
    "feature/schedule/ui/widget/DayScheduleWidgetProvider.kt",
    "feature/schedule/ui/widget/SingleLessonWidgetProvider.kt",
    "feature/schedule/ui/widget/ScheduleWidgetProviders.kt",
    "feature/schedule/ui/widget/ScheduleWidgetRemoteViewsService.kt",
    "feature/schedule/ui/widget/ScheduleWidgetRenderer.kt",
    "feature/schedule/ui/widget/ScheduleListRowRenderer.kt",
    "feature/schedule/ui/widget/ScheduleWidgetText.kt",
    "feature/qr/ui/widget/QrCodeWidgetProvider.kt",
    "feature/qr/ui/QrTileService.kt",
    "feature/settings/ui/QrTileRequest.kt",
    "feature/schedule/data/calendar/AndroidPhoneCalendars.kt",
)
# Text builders shared by screens and the surfaces above; every id they reference is a platform id.
PLATFORM_BUILDERS = (
    "core/ui/ScheduleChangeTexts.kt",
    "core/ui/LocationTitles.kt",
    "core/ui/LessonTypes.kt",
    "core/ui/MarkTexts.kt",
)
APP_LAYOUTS = {"activity_main"}
APP_MENUS = {"bottom_nav"}

ENTRY = re.compile(
    r"(?P<comment><!--.*?-->)"
    r"|(?P<string><string\b[^>]*?(?:/>|>.*?</string>))"
    r"|(?P<plurals><plurals\b[^>]*>.*?</plurals>)",
    re.S,
)
NAME = re.compile(r'\bname="([^"]+)"')
OWN_R = r"(?:(?<![\w.])|(?<=itmowidgets\.))"
BINDING = re.compile(r"\b([A-Z]\w*)Binding\b")


def kotlin_refs(text, kind):
    alias = "AppR" if re.search(r"import\s+dev\.alllexey\.itmowidgets\.R\s+as\s+AppR\b", text) else "R"
    return set(re.findall(OWN_R + alias + r"\." + kind + r"\.(\w+)", text))


def xml_refs(text, kind):
    """`@kind/name` in every attribute or text except tools:."""
    found = set()
    for attribute, name in re.findall(r'([\w:]+)\s*=\s*"@' + kind + r'/(\w+)"', text):
        if not attribute.startswith("tools:"):
            found.add(name)
    found.update(re.findall(r">\s*@" + kind + r"/(\w+)\s*<", text))
    return found


def string_refs_xml(text):
    return xml_refs(text, "string") | xml_refs(text, "plurals")


class Entry:
    def __init__(self, name, kind, raw, comments, blank):
        self.name = name
        self.kind = kind
        self.raw = raw
        self.comments = comments
        self.blank = blank


def parse_catalog(text, source):
    body = text.strip()
    if not body.startswith("<resources>") or not body.endswith("</resources>"):
        sys.exit(f"{source}: expected a bare <resources> root")
    body = body[len("<resources>"):-len("</resources>")]
    entries, comments, blank, position = [], [], False, 0
    for match in ENTRY.finditer(body):
        gap = body[position:match.start()]
        if gap.strip():
            sys.exit(f"{source}: unexpected content {gap.strip()[:60]!r}")
        blank = blank or gap.count("\n") >= 2
        position = match.end()
        if match.group("comment"):
            comments.append(match.group("comment"))
            continue
        kind = "string" if match.group("string") else "plurals"
        name = NAME.search(match.group(0)).group(1)
        entries.append(Entry(name, kind, match.group(0), comments, blank))
        comments, blank = [], False
    if body[position:].strip():
        sys.exit(f"{source}: unexpected content {body[position:].strip()[:60]!r}")
    return entries


def catalog_from(rev):
    text = subprocess.run(
        ["git", "-C", str(ROOT), "show", f"{rev}:{CATALOG}"], check=True, capture_output=True, text=True
    ).stdout
    return parse_catalog(text, f"{rev}:{CATALOG}")


def catalog_from_tree():
    entries = []
    for path in sorted(VALUES.glob("strings*.xml")):
        entries.extend(parse_catalog(path.read_text(encoding="utf-8"), str(path.relative_to(ROOT))))
    return entries


def kotlin_unit(path):
    """Owner unit of a Kotlin source by its package path under dev/alllexey/itmowidgets."""
    parts = path.parts
    try:
        start = next(i for i in range(len(parts)) if "/".join(parts[i:i + 3]) == PACKAGE) + 3
    except StopIteration:
        return None
    rest = parts[start:]
    if not rest or rest[0] in ("app", "di") or len(rest) == 1:
        return "app"
    if rest[0] == "core":
        return "debug" if rest[1:2] == ("debug",) else CORE
    if rest[0] == "feature" and len(rest) > 1:
        return FEATURE_UNITS.get(rest[1])
    return None


def package_path(path):
    parts = path.parts
    for i in range(len(parts)):
        if "/".join(parts[i:i + 3]) == PACKAGE:
            return "/".join(parts[i + 3:])
    return None


def pascal(name):
    return "".join(part[:1].upper() + part[1:] for part in name.split("_"))


class Usage:
    """Users of every string id, layout and menu in the working tree."""

    def __init__(self):
        self.layouts = {p.stem: p for p in sorted((RES / "layout").glob("*.xml"))}
        self.menus = {p.stem: p for p in sorted((RES / "menu").glob("*.xml"))}
        bindings = {pascal(name): name for name in self.layouts}
        # tier -> name -> {user path: unit}
        self.layout_users = defaultdict(lambda: defaultdict(dict))
        self.menu_users = defaultdict(lambda: defaultdict(dict))
        self.string_users = defaultdict(lambda: defaultdict(dict))
        self.platform_users = defaultdict(set)
        self.platform_layouts = set()

        for tier, sets in (("primary", PRIMARY_SETS), ("preview", PREVIEW_SETS), ("test", TEST_SETS)):
            for source_set in sets:
                for path in sorted((SRC / source_set).rglob("*.kt")):
                    text = path.read_text(encoding="utf-8")
                    unit = "debug" if source_set in PREVIEW_SETS else kotlin_unit(path)
                    user = str(path.relative_to(ROOT))
                    for name in kotlin_refs(text, "layout"):
                        self.layout_users[tier][name][user] = unit
                    for binding in BINDING.findall(text):
                        if binding in bindings:
                            self.layout_users[tier][bindings[binding]][user] = unit
                    for name in kotlin_refs(text, "menu"):
                        self.menu_users[tier][name][user] = unit
                    ids = kotlin_refs(text, "string") | kotlin_refs(text, "plurals")
                    platform = source_set == "main" and package_path(path) in PLATFORM_KOTLIN + PLATFORM_BUILDERS
                    for name in ids:
                        self.string_users[tier][name][user] = unit
                        if platform:
                            self.platform_users[name].add(user)
                    if source_set == "main" and package_path(path) in PLATFORM_KOTLIN:
                        self.platform_layouts.update(kotlin_refs(text, "layout"))

        surfaces = [SRC / s / "AndroidManifest.xml" for s in PRIMARY_SETS + PREVIEW_SETS]
        surfaces += sorted(p for d in RES.glob("xml*") for p in d.glob("*.xml"))
        for path in surfaces:
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            self.platform_layouts.update(xml_refs(text, "layout"))
            for name in string_refs_xml(text):
                self.platform_users[name].add(str(path.relative_to(ROOT)))

        includes = {name: xml_refs(path.read_text(encoding="utf-8"), "layout") for name, path in self.layouts.items()}
        pending = list(self.platform_layouts)
        while pending:
            for child in includes.get(pending.pop(), ()):
                if child not in self.platform_layouts:
                    self.platform_layouts.add(child)
                    pending.append(child)

        for name, path in self.layouts.items():
            user = str(path.relative_to(ROOT))
            for child in includes[name]:
                self.layout_users["include"][child][user] = name
            for menu in xml_refs(path.read_text(encoding="utf-8"), "menu"):
                self.menu_users["include"][menu][user] = name

        self.layout_units = {}
        for name in self.layouts:
            self.layout_units[name] = self._layout_units(name, set())
        self.menu_units = {name: self._menu_units(name) for name in self.menus}

        for name, path in self.layouts.items():
            text = path.read_text(encoding="utf-8")
            user = str(path.relative_to(ROOT))
            for string in string_refs_xml(text):
                if name in self.platform_layouts:
                    self.platform_users[string].add(user)
                for tier, units in self.layout_units[name].items():
                    for unit in units:
                        self.string_users[tier][string][f"{user}<{unit}>"] = unit
        for name, path in self.menus.items():
            user = str(path.relative_to(ROOT))
            for string in string_refs_xml(path.read_text(encoding="utf-8")):
                for tier, units in self.menu_units[name].items():
                    for unit in units:
                        self.string_users[tier][string][f"{user}<{unit}>"] = unit
        for path in sorted((RES / "navigation").glob("*.xml")):
            for string in string_refs_xml(path.read_text(encoding="utf-8")):
                self.string_users["primary"][string][str(path.relative_to(ROOT))] = "app"

    def _layout_units(self, name, seen):
        """tier -> units of a layout's users; <include> parents pass their own units on."""
        if name in APP_LAYOUTS:
            return {"primary": {"app"}}
        if name in seen:
            return {}
        seen = seen | {name}
        result = defaultdict(set)
        for tier in ("primary", "preview", "test"):
            result[tier].update(self.layout_users[tier][name].values())
        for parent in self.layout_users["include"][name].values():
            for tier, units in self._layout_units(parent, seen).items():
                result[tier].update(units)
        return {tier: units for tier, units in result.items() if units}

    def _menu_units(self, name):
        if name in APP_MENUS:
            return {"primary": {"app"}}
        result = defaultdict(set)
        for tier in ("primary", "preview", "test"):
            result[tier].update(self.menu_users[tier][name].values())
        for parent in self.menu_users["include"][name].values():
            for tier, units in self.layout_units[parent].items():
                result[tier].update(units)
        return {tier: units for tier, units in result.items() if units}

    def platform_ids(self, catalog_names):
        return sorted(name for name in self.platform_users if name in catalog_names)

    def owner(self, name):
        """(file, users) for an id."""
        if name in self.platform_users:
            return "platform", sorted(self.platform_users[name])
        for tier in ("primary", "preview", "test"):
            users = self.string_users[tier].get(name)
            if users:
                return self.file_for(set(users.values())), sorted(users)
        return None, []

    @staticmethod
    def file_for(units):
        if None in units:
            sys.exit(f"a user outside the known packages: {units}")
        if CORE in units or len(units) > 1:
            return "common"
        return next(iter(units))

    def where_path(self, raw):
        path = (Path.cwd() / raw).resolve()
        if not path.exists():
            path = (ROOT / raw).resolve()
        relative = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
        if path.suffix == ".kt":
            if package_path(path) in PLATFORM_KOTLIN + PLATFORM_BUILDERS:
                return "platform"
            source_set = relative.parts[2] if relative.parts[:2] == ("app", "src") else "main"
            return self.file_for({"debug" if source_set in PREVIEW_SETS else kotlin_unit(path)})
        if path.name == "AndroidManifest.xml" or path.parent.name.startswith("xml"):
            return "platform"
        if path.parent.name == "navigation":
            return "app"
        if path.parent.name == "layout":
            if path.stem in self.platform_layouts:
                return "platform"
            units = self.layout_units.get(path.stem, {})
        elif path.parent.name == "menu":
            units = self.menu_units.get(path.stem, {})
        else:
            sys.exit(f"{raw}: not a Kotlin source, layout, menu, navigation graph, res/xml file or manifest")
        for tier in ("primary", "preview", "test"):
            if units.get(tier):
                return self.file_for(units[tier])
        sys.exit(f"{raw}: no user of this resource")


def target(file):
    return VALUES / f"strings_{file}.xml"


def render(entries):
    lines = ["<resources>"]
    for index, entry in enumerate(entries):
        if entry.blank and index > 0:
            lines.append("")
        lines.extend(f"    {comment}" for comment in entry.comments)
        lines.append(f"    {entry.raw}")
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def split(rev, diff):
    entries = catalog_from(rev)
    usage = Usage()
    by_file = defaultdict(list)
    unused = []
    for entry in entries:
        file, _ = usage.owner(entry.name)
        if file is None:
            unused.append(entry.name)
        by_file[file].append(entry)
    if unused:
        sys.exit("ids without any user: " + ", ".join(unused))
    wanted = {target(file): render(by_file[file]) for file in FILES if by_file[file]}
    frozen = ROOT / "scripts" / "strings-frozen-keys.txt"
    wanted[frozen] = "".join(f"{name}\n" for name in sorted(e.name for e in by_file["platform"]))
    stale = [p for p in VALUES.glob("strings*.xml") if p not in wanted]
    if diff:
        for path, text in wanted.items():
            current = path.read_text(encoding="utf-8").splitlines(True) if path.exists() else []
            name = str(path.relative_to(ROOT))
            sys.stdout.writelines(difflib.unified_diff(current, text.splitlines(True), f"a/{name}", f"b/{name}"))
        for path in stale:
            print(f"stale: {path.relative_to(ROOT)}")
        return
    for path in stale:
        path.unlink()
    for path, text in wanted.items():
        path.write_text(text, encoding="utf-8")
    for file in FILES:
        if by_file[file]:
            print(f"{target(file).relative_to(ROOT)}\t{len(by_file[file])}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--split", metavar="REV")
    mode.add_argument("--where", metavar="ID_OR_PATH")
    mode.add_argument("--report", action="store_true")
    parser.add_argument("--diff", action="store_true", help="with --split: print differences, write nothing")
    args = parser.parse_args()
    if args.diff and not args.split:
        parser.error("--diff needs --split")

    if args.split:
        split(args.split, args.diff)
        return
    usage = Usage()
    if args.where:
        if "/" in args.where or args.where.endswith((".kt", ".xml")):
            file = usage.where_path(args.where)
        else:
            file, _ = usage.owner(args.where)
            if file is None:
                sys.exit(f"{args.where}: no user in app/src; use --where <source path> for a new string")
        print(target(file).relative_to(ROOT))
        return
    names = {entry.name: entry for entry in catalog_from_tree()}
    for name in names:
        file, users = usage.owner(name)
        print(f"{name}\t{file}\t{','.join(users)}")


if __name__ == "__main__":
    main()
