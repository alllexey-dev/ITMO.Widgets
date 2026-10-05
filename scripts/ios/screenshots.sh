#!/usr/bin/env bash
# screenshots.sh [<Class>...]   UITests (or the listed classes) in light and dark into iosApp/build/screenshots/
#
# Review screenshots of the app on the simulator (L18 IO-17, docs/ios.md, Visual verification). Not goldens:
# nothing here is committed or compared.
#
# - Takes this worktree's simulator from `scripts/ios/test.sh sim`, boots it and overrides the status bar (9:41,
#   full battery, Wi-Fi and cellular bars, no carrier) so every screenshot has the same chrome.
# - Per appearance (light, dark): `simctl ui appearance`, then `scripts/ios/test.sh ui [<Class>...]` (the kn slot,
#   English launch arguments from UITests' AppLaunch.swift), then the result bundle's screenshot attachments are
#   exported to iosApp/build/screenshots/<appearance>/<attachment name>.png (`attachScreenshot(named:)` names them
#   `<Class>-<name>`). The directory is emptied first.
# - Clears the status bar override and restores the light appearance at the end; the simulator stays, delete it
#   with `scripts/ios/test.sh --cleanup`.
# - Exit code: 0 when both runs pass, 1 when a run or the export fails, 2 refused (usage, toolchain).

set -u

me=screenshots.sh

refuse() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) || refuse "cannot resolve the script directory"
root=$(cd "$script_dir/../.." && pwd -P) || refuse "cannot resolve the repository root"
cd "$root" || refuse "cannot enter $root"

classes=()
for arg in "$@"; do
  case "$arg" in
    -h | --help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
    -*) refuse "unknown option '$arg'" ;;
    *)
      [[ $arg =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || refuse "'$arg' is not a UITests class name"
      classes+=("$arg")
      ;;
  esac
done

# shellcheck source=scripts/ios/env.sh
source "$script_dir/env.sh" || refuse "toolchain off its pins (scripts/ios/env.sh)"

udid=$("$script_dir/test.sh" sim) || refuse "no simulator (scripts/ios/test.sh sim)"
[ -n "$udid" ] || refuse "no simulator UDID from scripts/ios/test.sh sim"

out="iosApp/build/screenshots"
results="iosApp/build/test-results"
rm -rf "$out"
mkdir -p "$out"

note "booting $udid"
xcrun simctl bootstatus "$udid" -b > /dev/null || refuse "cannot boot simulator $udid"
xcrun simctl status_bar "$udid" override \
  --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 --cellularMode active --cellularBars 4 \
  --operatorName '' --batteryState charged --batteryLevel 100 || refuse "cannot override the status bar"

restore() {
  xcrun simctl status_bar "$udid" clear > /dev/null 2>&1
  xcrun simctl ui "$udid" appearance light > /dev/null 2>&1
}
trap restore EXIT

export_screenshots() { # bundle dir
  local tmp
  tmp=$(mktemp -d "${TMPDIR:-/tmp}/itmo-screenshots.XXXXXX") || return 1
  xcrun xcresulttool export attachments --path "$1" --output-path "$tmp" > /dev/null || { rm -rf "$tmp"; return 1; }
  /usr/bin/python3 - "$tmp" "$2" << 'EOF' || { rm -rf "$tmp"; return 1; }
import json, os, re, shutil, sys

src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
count = 0
with open(os.path.join(src, "manifest.json")) as f:
    manifest = json.load(f)
for test in manifest:
    for attachment in test.get("attachments", []):
        exported = attachment["exportedFileName"]
        if not exported.lower().endswith(".png"):
            continue
        # XCTest suffixes the attachment name with "_<index>_<UUID>"; keep the name the test gave.
        name = re.sub(r"_\d+_[0-9A-Fa-f-]{36}$", "", os.path.splitext(attachment.get("suggestedHumanReadableName") or exported)[0])
        shutil.copyfile(os.path.join(src, exported), os.path.join(dst, name + ".png"))
        count += 1
print(f"screenshots.sh: {count} screenshot(s) in {dst}", file=sys.stderr)
EOF
  rm -rf "$tmp"
}

rc=0
for appearance in light dark; do
  note "appearance $appearance"
  xcrun simctl ui "$udid" appearance "$appearance" || { rc=1; continue; }
  "$script_dir/test.sh" ui ${classes[@]+"${classes[@]}"} || rc=1
  if [ -d "$results/ios-ui.xcresult" ]; then
    export_screenshots "$results/ios-ui.xcresult" "$out/$appearance" || { note "export failed for $appearance"; rc=1; }
    rm -rf "$results/screenshots-$appearance.xcresult"
    mv "$results/ios-ui.xcresult" "$results/screenshots-$appearance.xcresult" || true
  else
    note "no result bundle for $appearance"
    rc=1
  fi
done

note "screenshots in ${out#"$root"/}/{light,dark}"
exit "$rc"
