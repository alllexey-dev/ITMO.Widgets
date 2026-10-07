#!/usr/bin/env bash
# archive.sh --unsigned             Release archive without an Apple signature and the F3 .ipa (app + widget, no NSE)
# archive.sh --signed               Release archive signed through Signing.local.xcconfig and an App Store .ipa
# archive.sh --signed --upload      the same, uploaded to App Store Connect (integrator only, on the owner's word)
#
# Device archives of the iOS app (L18 IO-18a, docs/ios.md, Release). Output goes to iosApp/build/archive/ (ignored);
# the script never publishes anything, and only --upload sends the build anywhere.
#
# - Sources scripts/ios/env.sh first (Xcode and XcodeGen pins), runs xcodegen and `xcodebuild archive` of the
#   ITMOWidgets scheme, Release, generic/platform=iOS, inside `scripts/slot.sh kn` (the Run Script builds the
#   iosArm64 Kotlin framework through the nested slot.sh). DerivedData stays in iosApp/build/DerivedData.
# - CURRENT_PROJECT_VERSION = `git rev-list --count HEAD` of the archived commit, monotonic along v2.3/next and
#   master; MARKETING_VERSION comes from Base.xcconfig (2.3.0). --signed and --upload refuse a dirty tree; an
#   --unsigned build of a dirty tree gets `-dirty` in its file name.
# - --unsigned (F3, SP-23): `CODE_SIGNING_ALLOWED=NO`, which leaves the bundles without entitlements. The NSE is
#   removed (a free team has no push, and it would cost a third App ID), the expanded unsigned entitlements of the
#   widget and the app are applied with an ad-hoc `codesign -s -` (widget first, then the app), so AltStore and
#   SideStore find the App Group and Keychain group to re-sign with, and `Payload/` is zipped into
#   ITMOWidgets-<version>-<build>-unsigned.ipa. Prints whether F3 widgets get real data or the signed-out state.
# - --signed: needs iosApp/Config/Signing.local.xcconfig (written by the integrator after T13); archives with the
#   team's automatic signing (`-allowProvisioningUpdates`) and exports with method app-store-connect, the NSE kept.
#   --upload sets the export destination to upload instead of writing the .ipa.
# - Exit code: 0 done, 1 failed, 2 refused (usage, toolchain, dirty tree, no signing config).

set -u

me=archive.sh

refuse() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }
fail() { printf '%s: %s\n' "$me" "$*" >&2; exit 1; }

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) || refuse "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
root=$(cd "$script_dir/../.." && pwd -P) || refuse "cannot resolve the repository root"
cd "$root" || refuse "cannot enter $root"

usage() {
  sed -n '2,4p' "$self" | sed 's/^# //' >&2
  exit 2
}

# ---- arguments -----------------------------------------------------------------------------------------------

mode="" upload=0
while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage ;;
    --unsigned | --signed)
      [ -z "$mode" ] || refuse "pass one of --unsigned and --signed"
      mode=${1#--}
      shift
      ;;
    --upload) upload=1; shift ;;
    *) note "unknown argument '$1'"; usage ;;
  esac
done
[ -n "$mode" ] || usage
[ "$upload" -eq 0 ] || [ "$mode" = signed ] || refuse "--upload needs --signed"

signing_config="iosApp/Config/Signing.local.xcconfig"
if [ "$mode" = signed ] && [ ! -f "$signing_config" ]; then
  refuse "--signed needs $signing_config (DEVELOPMENT_TEAM, ITMO_CODE_SIGN_IDENTITY, ITMO_ENTITLEMENTS_VARIANT), written by the integrator after T13"
fi

# shellcheck source=scripts/ios/env.sh
source "$script_dir/env.sh" || refuse "toolchain off its pins (scripts/ios/env.sh)"

# ---- version -------------------------------------------------------------------------------------------------

sha=$(git rev-parse --short=7 HEAD 2> /dev/null) || refuse "not a git checkout"
build=$(git rev-list --count HEAD) || refuse "cannot count the commits of HEAD"
dirty=""
if [ -n "$(git status --porcelain --untracked-files=no 2> /dev/null)" ]; then
  [ "$mode" = unsigned ] || refuse "the tree has uncommitted changes; a signed build is always a commit"
  dirty="-dirty"
  note "warning: uncommitted changes; build $build is $sha plus local edits"
fi
marketing=$(sed -n 's/^MARKETING_VERSION *= *\([0-9.]*\) *$/\1/p' iosApp/Config/Base.xcconfig)
[ -n "$marketing" ] || refuse "no MARKETING_VERSION in iosApp/Config/Base.xcconfig"

out="iosApp/build/archive"
archive="$out/ITMOWidgets.xcarchive"
release_link_heap=8g
started=$(date +%s)

# ---- archive -------------------------------------------------------------------------------------------------

xcode_archive() { # extra xcodebuild arguments
  note "xcodegen generate"
  xcodegen generate --quiet --spec iosApp/project.yml || return 1
  rm -rf "$archive"
  mkdir -p "$out"
  note "[kn slot] xcodebuild archive, Release, $mode, $marketing ($build), $sha$dirty"
  # The Release link of the framework does not fit the daemon's 4 GB heap (OutOfMemoryError), so the Kotlin/Native
  # compiler runs in a process of its own with more; xcodebuild passes the environment on to the Run Script's Gradle.
  env "ORG_GRADLE_PROJECT_kotlin.native.disableCompilerDaemon=true" \
    "ORG_GRADLE_PROJECT_kotlin.native.jvmArgs=-Xmx$release_link_heap" \
    scripts/slot.sh kn -- xcodebuild \
    -project iosApp/ITMOWidgets.xcodeproj \
    -scheme ITMOWidgets \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -derivedDataPath iosApp/build/DerivedData \
    -archivePath "$archive" \
    "$@" \
    CURRENT_PROJECT_VERSION="$build" \
    archive
}

# Prints the bundle's Info.plist values the release depends on and fails when one is unexpanded or off.
check_info() { # bundle label
  /usr/bin/python3 - "$1/Info.plist" "$2" "$marketing" "$build" <<'PY'
import plistlib, sys
path, label, marketing, build = sys.argv[1:5]
with open(path, "rb") as f:
    info = plistlib.load(f)
def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for v in value.values():
            yield from strings(v)
    elif isinstance(value, list):
        for v in value:
            yield from strings(v)
bad = [s for s in strings(info) if "$(" in s]
keys = ["CFBundleIdentifier", "CFBundleShortVersionString", "CFBundleVersion", "AppGroupID", "KeychainGroup"]
print(f"  {label}: " + ", ".join(f"{k}={info[k]}" for k in keys if k in info))
problems = []
if bad:
    problems.append(f"unexpanded build settings {bad}")
if info.get("CFBundleShortVersionString") != marketing:
    problems.append(f"CFBundleShortVersionString is not {marketing}")
if info.get("CFBundleVersion") != build:
    problems.append(f"CFBundleVersion is not {build}")
if not info.get("AppGroupID"):
    problems.append("no AppGroupID")
if problems:
    sys.exit(f"{label}: " + "; ".join(problems))
PY
}

# ---- unsigned (F3) -------------------------------------------------------------------------------------------

# Writes the target's unsigned entitlements with $(APP_GROUP_ID) and $(KEYCHAIN_GROUP) taken from the built app's
# Info.plist, the values Xcode expanded for this build (a team-less device build has an empty AppIdentifierPrefix).
expand_entitlements() { # source.entitlements app-Info.plist output.xcent
  /usr/bin/python3 - "$@" <<'PY'
import plistlib, sys
source, info_path, output = sys.argv[1:4]
with open(info_path, "rb") as f:
    info = plistlib.load(f)
values = {"$(APP_GROUP_ID)": info.get("AppGroupID", ""), "$(KEYCHAIN_GROUP)": info.get("KeychainGroup", "")}
def expand(value):
    if isinstance(value, str):
        for key, setting in values.items():
            if key in value:
                if not setting:
                    sys.exit(f"{source}: no value for {key} in the app's Info.plist")
                value = value.replace(key, setting)
        if "$(" in value:
            sys.exit(f"{source}: unexpanded {value}")
        return value
    if isinstance(value, list):
        return [expand(v) for v in value]
    if isinstance(value, dict):
        return {k: expand(v) for k, v in value.items()}
    return value
with open(source, "rb") as f:
    entitlements = expand(plistlib.load(f))
if "aps-environment" in entitlements:
    sys.exit(f"{source}: aps-environment has no place in an F3 build")
with open(output, "wb") as f:
    plistlib.dump(entitlements, f)
PY
}

# Prints the entitlements of a signed bundle as `key=value` lines.
signed_entitlements() { # bundle
  codesign -d --entitlements - --xml "$1" 2> /dev/null | /usr/bin/python3 -c '
import plistlib, sys
data = sys.stdin.buffer.read()
entitlements = plistlib.loads(data) if data.strip() else {}
for key in sorted(entitlements):
    value = entitlements[key]
    print(f"{key}=" + (",".join(map(str, value)) if isinstance(value, list) else str(value)))
'
}

package_unsigned() {
  local app="$archive/Products/Applications/ITMOWidgets.app" staging="$out/unsigned"
  local payload_app widget nse ipa app_groups widget_groups nested
  [ -d "$app" ] || fail "no $app in the archive"
  ipa="$out/ITMOWidgets-$marketing-$build$dirty-unsigned.ipa"
  rm -rf "$staging" "$out"/ITMOWidgets-*-unsigned.ipa
  mkdir -p "$staging/Payload"
  ditto "$app" "$staging/Payload/ITMOWidgets.app" || return 1
  payload_app="$staging/Payload/ITMOWidgets.app"
  widget="$payload_app/PlugIns/ITMOWidgetsWidgets.appex"
  nse="$payload_app/PlugIns/ITMOWidgetsNotificationService.appex"
  [ -d "$widget" ] || fail "no widget extension in the archive"
  rm -rf "$nse"
  note "removed the notification service extension (no push in F3)"

  expand_entitlements iosApp/Resources/ITMOWidgetsWidgets.entitlements "$payload_app/Info.plist" \
    "$staging/ITMOWidgetsWidgets.xcent" || return 1
  expand_entitlements iosApp/Resources/ITMOWidgets.entitlements "$payload_app/Info.plist" \
    "$staging/ITMOWidgets.xcent" || return 1

  # Inside out: nested frameworks and dylibs, the widget, then the app, whose signature seals the rest.
  while IFS= read -r nested; do
    codesign -f -s - "$nested" || return 1
  done < <(find "$payload_app" \( -name '*.framework' -o -name '*.dylib' \) -prune -print)
  codesign -f -s - --entitlements "$staging/ITMOWidgetsWidgets.xcent" "$widget" || return 1
  codesign -f -s - --entitlements "$staging/ITMOWidgets.xcent" "$payload_app" || return 1
  codesign --verify --deep --strict "$payload_app" || fail "the ad-hoc signature does not verify"

  (cd "$staging" && zip -qry "$root/$ipa" Payload) || return 1

  printf 'Info.plist\n'
  check_info "$payload_app" app || return 1
  check_info "$widget" widget || return 1
  printf 'Plug-ins: %s\n' "$(cd "$payload_app/PlugIns" && ls | paste -s -d ' ' -)"
  printf 'Entitlements (ad-hoc signature)\n'
  signed_entitlements "$payload_app" | sed 's/^/  app: /'
  signed_entitlements "$widget" | sed 's/^/  widget: /'
  app_groups=$(signed_entitlements "$payload_app" | sed -n 's/^com.apple.security.application-groups=//p')
  widget_groups=$(signed_entitlements "$widget" | sed -n 's/^com.apple.security.application-groups=//p')
  if signed_entitlements "$payload_app" | grep -q '^aps-environment='; then
    fail "the app requests aps-environment"
  fi

  printf 'IPA: %s (%s bytes)\n' "$ipa" "$(stat -f %z "$ipa")"
  if [ -n "$app_groups" ] && [ "$app_groups" = "$widget_groups" ]; then
    printf 'F3 widgets: real data. App and widget request the App Group %s; AltStore and SideStore re-sign it\n' \
      "$app_groups"
    printf '  into <group>.<TEAMID> and list it under ALTAppGroups, which AppGroupDirectory and AppGroupSnapshot try\n'
    printf '  after AppGroupID (SP-23). Confirm on a device in the owner'"'"'s free-team session before an F3 prerelease;\n'
    printf '  if no container resolves there, the widgets stay in the signed-out state.\n'
  else
    printf 'F3 widgets: demo data. App and widget do not request one App Group (%s / %s), so no container resolves\n' \
      "${app_groups:-none}" "${widget_groups:-none}"
    printf '  after re-signing and the widgets show the signed-out state.\n'
    return 1
  fi
}

# ---- signed --------------------------------------------------------------------------------------------------

export_signed() {
  local options="$out/ExportOptions.plist" destination=export team
  [ "$upload" -eq 1 ] && destination=upload
  team=$(/usr/libexec/PlistBuddy -c 'Print :ApplicationProperties:Team' "$archive/Info.plist" 2> /dev/null) ||
    fail "the archive names no team; is $signing_config complete?"
  /usr/bin/python3 - "$options" "$team" "$destination" <<'PY' || return 1
import plistlib, sys
path, team, destination = sys.argv[1:4]
with open(path, "wb") as f:
    plistlib.dump({"method": "app-store-connect", "destination": destination, "teamID": team,
                   "signingStyle": "automatic", "manageAppVersionAndBuildNumber": False}, f)
PY
  rm -rf "$out/export"
  [ "$upload" -eq 1 ] && note "uploading build $build to App Store Connect (the owner's word is required for this)"
  scripts/slot.sh kn -- xcodebuild -exportArchive \
    -archivePath "$archive" \
    -exportOptionsPlist "$options" \
    -exportPath "$out/export" \
    -allowProvisioningUpdates || return 1
  [ "$upload" -eq 1 ] || printf 'IPA: %s\n' "$(ls "$out"/export/*.ipa)"
}

# ---- run -----------------------------------------------------------------------------------------------------

case "$mode" in
  unsigned)
    xcode_archive CODE_SIGNING_ALLOWED=NO || fail "xcodebuild archive failed"
    package_unsigned || fail "packaging the unsigned .ipa failed"
    ;;
  signed)
    xcode_archive -allowProvisioningUpdates || fail "xcodebuild archive failed"
    export_signed || fail "exporting the signed archive failed"
    ;;
esac
printf 'ARCHIVE %s %s (%s) %ss %s%s\n' "$mode" "$marketing" "$build" "$(($(date +%s) - started))" "$sha" "$dirty"
