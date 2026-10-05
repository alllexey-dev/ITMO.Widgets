#!/usr/bin/env bash
# test.sh [--ci]                           checks, xcodegen, xcodebuild build test of the ITMOWidgets scheme
# test.sh [--ci] --only <Target>/<Class>   the same, one test class (xcodebuild -only-testing)
# test.sh [--ci] kn <module>...            iosSimulatorArm64Test of shared modules (`core` or `:shared:core`)
# test.sh --cleanup                        delete this worktree's simulator
#
# The one iOS build entry point for agents and CI (L18 IO-02, docs/ios.md, Build and test).
#
# - Sources scripts/ios/env.sh first: a toolchain off its pins (Xcode, XcodeGen) refuses with exit 2.
# - Runs every scripts/ios/check-*.sh before building (later cards add checks as new files, never as edits here).
# - xcodegen generates iosApp/ITMOWidgets.xcodeproj (ignored); xcodebuild runs inside `scripts/slot.sh kn` with
#   DerivedData in iosApp/build/DerivedData and the result bundle in iosApp/build/test-results/. The app target's
#   Run Script builds the Kotlin framework through a nested slot.sh, which runs directly because the slot is held.
# - One simulator per worktree, `itmo-<worktree directory>` (`itmo-ci` with --ci), created from the pinned device
#   type and runtime on first use; --cleanup (and the end of a --ci run) deletes it. No other simulator is touched.
# - kn: Gradle with the worktree's MyItmoApi pin (as scripts/verify.sh passes it) on the same simulator.
# - --ci: CI=true, so slot.sh runs commands directly; deletes the simulator at the end.
# - The last line of a finished run is `VERIFY A ios[-only|-kn] PASS|FAIL <secs>s <sha7>[+dirty]`.
# - Exit code: 0 pass, 1 fail, 2 refused (usage, toolchain off its pins).

set -u

me=test.sh

refuse() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) || refuse "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
root=$(cd "$script_dir/../.." && pwd -P) || refuse "cannot resolve the repository root"
cd "$root" || refuse "cannot enter $root"

usage() {
  sed -n '2,5p' "$self" | sed 's/^# //' >&2
  exit 2
}

# ---- arguments -----------------------------------------------------------------------------------------------

ci=0 mode=all only="" modules=()
while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage ;;
    --ci) ci=1; shift ;;
    --only)
      [ $# -ge 2 ] || refuse "--only needs <Target>/<Class>"
      [[ $2 =~ ^[A-Za-z_][A-Za-z0-9_]*/[A-Za-z_][A-Za-z0-9_]*(/[A-Za-z_][A-Za-z0-9_]*)?$ ]] ||
        refuse "--only: '$2' is not <Target>/<Class>[/<method>]"
      mode=only only=$2
      shift 2
      ;;
    --cleanup) mode=cleanup; shift ;;
    kn)
      mode=kn
      shift
      [ $# -gt 0 ] || refuse "kn needs at least one shared module"
      while [ $# -gt 0 ]; do
        case "$1" in --*) break ;; esac
        name=${1#:shared:}
        [[ $name =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]] || refuse "kn: '$1' is not a shared module"
        [ -f "shared/$name/build.gradle.kts" ] || refuse "kn: no module shared/$name"
        modules+=("$name")
        shift
      done
      ;;
    *) note "unknown argument '$1'"; usage ;;
  esac
done

[ "$ci" -eq 1 ] && export CI=true

# shellcheck source=scripts/ios/env.sh
source "$script_dir/env.sh" || refuse "toolchain off its pins (scripts/ios/env.sh)"

started=$(date +%s)
sha=$(git rev-parse --short=7 HEAD 2> /dev/null || printf 'nogit')
[ -n "$(git status --porcelain 2> /dev/null)" ] && sha="$sha+dirty"

finish() { # label rc
  local verdict=PASS
  [ "$2" -eq 0 ] || verdict=FAIL
  printf 'VERIFY A %s %s %ss %s\n' "$1" "$verdict" "$(($(date +%s) - started))" "$sha"
  [ "$2" -eq 0 ] && exit 0
  exit 1
}

# ---- simulator -----------------------------------------------------------------------------------------------

if [ "$ci" -eq 1 ]; then
  sim_name=itmo-ci
else
  sim_name="itmo-$(basename "$root")"
fi

sim_udid() { # -> the UDID of $sim_name, empty when it does not exist
  xcrun simctl list devices -j 2> /dev/null | /usr/bin/python3 -c '
import json, sys
name = sys.argv[1]
for devices in json.load(sys.stdin)["devices"].values():
    for d in devices:
        if d["name"] == name and d.get("isAvailable", True):
            print(d["udid"]); sys.exit(0)
' "$sim_name"
}

ensure_sim() {
  udid=$(sim_udid)
  if [ -z "$udid" ]; then
    note "creating simulator $sim_name ($ITMO_SIM_DEVICE_TYPE, $ITMO_SIM_RUNTIME)"
    udid=$(xcrun simctl create "$sim_name" "$ITMO_SIM_DEVICE_TYPE" "$ITMO_SIM_RUNTIME") ||
      refuse "cannot create simulator $sim_name; is runtime $ITMO_SIM_RUNTIME installed?"
  fi
}

delete_sim() {
  local id
  id=$(sim_udid)
  [ -n "$id" ] || return 0
  xcrun simctl shutdown "$id" > /dev/null 2>&1
  xcrun simctl delete "$id" && note "deleted simulator $sim_name"
}

if [ "$mode" = cleanup ]; then
  delete_sim
  exit 0
fi

# ---- checks --------------------------------------------------------------------------------------------------

run_checks() {
  local check
  for check in "$script_dir"/check-*.sh; do
    [ -e "$check" ] || continue
    note "check: ${check#"$root"/}"
    "$check" || return 1
  done
}

# ---- runs ----------------------------------------------------------------------------------------------------

run_xcode() {
  local results="iosApp/build/test-results" bundle label
  local -a test_args=()
  label=ios
  if [ "$mode" = only ]; then
    label=ios-only
    test_args=("-only-testing:$only")
  fi
  run_checks || return 1
  note "xcodegen generate"
  xcodegen generate --quiet --spec iosApp/project.yml || return 1
  ensure_sim
  mkdir -p "$results"
  bundle="$results/$label.xcresult"
  rm -rf "$bundle"
  note "[kn slot] xcodebuild build test on $sim_name ${test_args[*]:-}"
  scripts/slot.sh kn -- xcodebuild \
    -project iosApp/ITMOWidgets.xcodeproj \
    -scheme ITMOWidgets \
    -configuration Debug \
    -destination "platform=iOS Simulator,id=$udid" \
    -derivedDataPath iosApp/build/DerivedData \
    -resultBundlePath "$bundle" \
    ${test_args[@]+"${test_args[@]}"} \
    build test
}

run_kn() {
  local init pin_args
  local -a tasks=()
  for name in "${modules[@]}"; do tasks+=(":shared:$name:iosSimulatorArm64Test"); done
  ensure_sim
  # Points every K/N simulator test task at this worktree's simulator instead of KGP's shared default device.
  mkdir -p iosApp/build
  init="iosApp/build/kn-simulator.init.gradle"
  cat > "$init" <<EOF
allprojects {
    tasks.matching { it.class.name.startsWith('org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest') }
        .configureEach { it.device.set('$udid') }
}
EOF
  pin_args=$(itmo_ios_pin_args)
  note "[kn slot] ./gradlew ${tasks[*]} $pin_args"
  # shellcheck disable=SC2016 # expanded by the inner shell, inside the slot
  scripts/slot.sh kn -- /bin/bash -c './gradlew ${ITMO_MAX_WORKERS:+--max-workers=$ITMO_MAX_WORKERS} "$@"' gradle \
    --init-script "$init" "${tasks[@]}" $pin_args
}

case "$mode" in
  all | only)
    run_xcode
    rc=$?
    [ "$ci" -eq 1 ] && delete_sim
    finish "$([ "$mode" = only ] && echo ios-only || echo ios)" "$rc"
    ;;
  kn)
    run_kn
    rc=$?
    [ "$ci" -eq 1 ] && delete_sim
    finish ios-kn "$rc"
    ;;
esac
