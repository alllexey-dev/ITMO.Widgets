#!/usr/bin/env python3
"""Write `shared` icons from docs/design/icons.tsv as Material Symbols Rounded vector drawables.

For every `shared` row it downloads
  https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/<symbol>/<default|fill1>/24px.svg
(`fill1` when the row's `fill` is 1) and writes
shared/designsystem/src/commonMain/composeResources/drawable/ic_<id>.xml in the house format
(ADR 0028): the URL in a header comment, 24 dp, a 960 viewport, the SVG's `0 -960` origin moved by
`<group android:translateY="960">`, fill #FF000000, no tint. The use site tints the icon. `:app` gets the same
files as Android drawables through the export of :shared:designsystem.

`android` and `custom` rows are drawn by hand and never written here.
The output depends only on the registry and the downloaded SVG, so a second run changes nothing.

Usage:
  scripts/icons-fetch.py <id> [<id> ...]   write these rows
  scripts/icons-fetch.py --all             write every shared row
"""

import argparse
import sys
import urllib.request
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "docs" / "design" / "icons.tsv"
DRAWABLE = ROOT / "shared" / "designsystem" / "src" / "commonMain" / "composeResources" / "drawable"
URL = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/{symbol}/{style}/24px.svg"
SVG_NS = "{http://www.w3.org/2000/svg}"

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded: {url} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="960" android:viewportHeight="960">
    <group android:translateY="960">
{paths}
    </group>
</vector>
"""
PATH = '        <path android:fillColor="#FF000000" android:pathData="{data}" />'


def read_registry():
    lines = [line for line in REGISTRY.read_text(encoding="utf-8").splitlines() if line]
    header = lines[0].split("\t")
    return {row["id"]: row for row in (dict(zip(header, line.split("\t"))) for line in lines[1:])}


def url_of(row):
    style = "fill1" if row["fill"] == "1" else "default"
    return URL.format(symbol=row["symbol"], style=style)


def path_data(svg_text, url):
    svg = ElementTree.fromstring(svg_text)
    if svg.get("viewBox") != "0 -960 960 960":
        sys.exit(f"{url}: unexpected viewBox {svg.get('viewBox')!r}")
    paths = []
    for element in svg.iter():
        if element is svg:
            continue
        if element.tag != f"{SVG_NS}path" or set(element.attrib) != {"d"}:
            sys.exit(f"{url}: unsupported element {element.tag} {dict(element.attrib)}")
        paths.append(element.get("d"))
    if not paths:
        sys.exit(f"{url}: no path")
    return paths


def render(row):
    url = url_of(row)
    with urllib.request.urlopen(url, timeout=30) as response:
        svg_text = response.read().decode("utf-8")
    paths = "\n".join(PATH.format(data=data) for data in path_data(svg_text, url))
    return TEMPLATE.format(url=url, paths=paths)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("ids", nargs="*", help="registry ids to write")
    parser.add_argument("--all", action="store_true", help="write every shared row")
    args = parser.parse_args()
    if args.all == bool(args.ids):
        parser.error("pass ids or --all")

    registry = read_registry()
    if args.all:
        ids = [icon_id for icon_id, row in registry.items() if row["kind"] == "shared"]
    else:
        unknown = [icon_id for icon_id in args.ids if icon_id not in registry]
        if unknown:
            sys.exit(f"not in {REGISTRY.relative_to(ROOT)}: {', '.join(unknown)}")
        not_shared = [icon_id for icon_id in args.ids if registry[icon_id]["kind"] != "shared"]
        if not_shared:
            sys.exit(f"only shared rows are fetched; drawn by hand: {', '.join(not_shared)}")
        ids = args.ids

    changed = 0
    for icon_id in ids:
        target = DRAWABLE / f"ic_{icon_id}.xml"
        content = render(registry[icon_id])
        if target.is_file() and target.read_text(encoding="utf-8") == content:
            continue
        target.write_text(content, encoding="utf-8")
        changed += 1
        print(f"wrote {target.relative_to(ROOT)}")
    print(f"{len(ids)} icons, {changed} written")


if __name__ == "__main__":
    main()
