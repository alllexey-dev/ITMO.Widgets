#!/usr/bin/env bash
# test.sh [--ci] [--record]                           checks, xcodegen, build and test of the scheme without UITests
# test.sh [--ci] [--record] --only <Target>/<Class>   the same, one test class (xcodebuild -only-testing)
# test.sh [--ci] ui [<Class>...]                      the XCUITest target UITests, or only the listed classes
# test.sh [--ci] kn <module>...                       iosSimulatorArm64Test of shared modules (`core` or `:shared:core`)
# test.sh sim                                         print this worktree's simulator UDID, creating it if needed
# test.sh --cleanup                                   delete this worktree's simulator
#
# The iOS build and test entry point, locally and in CI (docs/ios.md, Build and test).
#
# - Sources scripts/ios/env.sh first: a different Xcode or XcodeGen than the tested one warns.
# - Runs every scripts/ios/check-*.sh before building (add a check as a new file).
# - xcodegen generates iosApp/ITMOWidgets.xcodeproj (ignored); xcodebuild runs build-for-testing, then
#   test-without-building, with DerivedData in iosApp/build/DerivedData and the result bundle in
#   iosApp/build/test-results/. The app target's Run Script builds the Kotlin framework with Gradle.
# - One simulator per worktree, `itmo-<worktree directory>` (`itmo-ci` with --ci), created from the pinned device
#   type and runtime on first use; --cleanup (and the end of a --ci run) deletes it. No other simulator is touched.
# - The default run and --only skip the UITests target; `ui` runs only it (XCUITest launches the app, so it is slower).
#   It first waits for SpringBoard's notification server, then deletes the app, so iOS asks for notifications again.
# - Snapshots (swift-snapshot-testing): the test process gets SNAPSHOT_TESTING_RECORD (all with --record, never
#   with --ci, else unset: the library records a missing reference and fails the test) and SNAPSHOT_ARTIFACTS =
#   iosApp/build/snapshot-artifacts (failure images, emptied before each run) through xcodebuild's TEST_RUNNER_ prefix.
# - kn: Gradle on the same simulator, with --continue: a failing module does not hide the rest.
# - sim: the simulator scripts/ios/screenshots.sh drives between runs; prints only the UDID on stdout.
# - --ci: CI=true; deletes the simulator at the end.
# - The last line of a finished run is `test.sh: ios[-only|-ui|-kn] PASS|FAIL <secs>s <sha7>[+dirty]`.
# - Exit code: 0 pass, 1 fail, 2 refused (usage).

set -u

me=test.sh

refuse() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) || refuse "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
root=$(cd "$script_dir/../.." && pwd -P) || refuse "cannot resolve the repository root"
cd "$root" || refuse "cannot enter $root"

usage() {
  sed -n '2,7p' "$self" | sed 's/^# //' >&2
  exit 2
}

# ---- arguments -----------------------------------------------------------------------------------------------

ci=0 record=0 mode=all only="" modules=() classes=()
while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage ;;
    --ci) ci=1; shift ;;
    --record) record=1; shift ;;
    --only)
      [ $# -ge 2 ] || refuse "--only needs <Target>/<Class>"
      [[ $2 =~ ^[A-Za-z_][A-Za-z0-9_]*/[A-Za-z_][A-Za-z0-9_]*(/[A-Za-z_][A-Za-z0-9_]*)?$ ]] ||
        refuse "--only: '$2' is not <Target>/<Class>[/<method>]"
      mode=only only=$2
      shift 2
      ;;
    --cleanup) mode=cleanup; shift ;;
    sim) mode=sim; shift ;;
    ui)
      mode=ui
      shift
      while [ $# -gt 0 ]; do
        case "$1" in --*) break ;; esac
        [[ $1 =~ ^[A-Za-z_][A-Za-z0-9_]*(/[A-Za-z_][A-Za-z0-9_]*)?$ ]] || refuse "ui: '$1' is not <Class>[/<method>]"
        classes+=("$1")
        shift
      done
      ;;
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
case "$mode" in all | only) ;; *) [ "$record" -eq 0 ] || refuse "--record applies to the default run and --only" ;; esac

# shellcheck source=scripts/ios/env.sh
source "$script_dir/env.sh"

started=$(date +%s)
sha=$(git rev-parse --short=7 HEAD 2> /dev/null || printf 'nogit')
[ -n "$(git status --porcelain 2> /dev/null)" ] && sha="$sha+dirty"

finish() { # label rc
  local verdict=PASS
  [ "$2" -eq 0 ] || verdict=FAIL
  printf '%s: %s %s %ss %s\n' "$me" "$1" "$verdict" "$(($(date +%s) - started))" "$sha"
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

if [ "$mode" = sim ]; then
  ensure_sim
  printf '%s\n' "$udid"
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

# SpringBoard's notification server takes in the installed apps two minutes after SpringBoard starts. An app installed
# before that never gets the notification dialog (its request is dropped) nor its scheduled notifications ("local
# client does not exist") while it stays installed, so a UI run installs the app only after that load: it waits up to
# ten minutes for the load's log line from the SpringBoard running now (a first boot can restart it).
wait_for_notification_server() {
  local pid started deadline=$((SECONDS + 600))
  while [ "$SECONDS" -lt "$deadline" ]; do
    pid=$(xcrun simctl spawn "$udid" launchctl list 2> /dev/null | awk '$3 == "com.apple.SpringBoard" { print $1 }')
    if started=$(LC_ALL=C date -j -f '%a %b %e %T %Y' "$(LC_ALL=C ps -o lstart= -p "$pid" 2> /dev/null | xargs)" \
      '+%Y-%m-%d %H:%M:%S' 2> /dev/null) &&
      xcrun simctl spawn "$udid" log show --style compact --start "$started" \
        --predicate "process == 'SpringBoard' AND eventMessage CONTAINS 'initialLoad=1'" 2> /dev/null |
      grep -q initialLoad; then
      note "SpringBoard (pid $pid, up since $started) has loaded its notification sources"
      return 0
    fi
    sleep 5
  done
  note "SpringBoard (pid ${pid:-none}, up since ${started:-?}) has not loaded its notification sources; the" \
    "notification tests may fail. Its notification log since boot:"
  xcrun simctl spawn "$udid" log show --style compact --last 20m \
    --predicate "process == 'SpringBoard' AND (eventMessage CONTAINS 'BBServer startup' OR eventMessage CONTAINS 'notificationSourcesDidInstall')" \
    2>&1 | tail -n 20 >&2
}

run_xcode() { # label
  local results="iosApp/build/test-results" artifacts="$root/iosApp/build/snapshot-artifacts" bundle label=$1 class
  local -a test_args=()
  case "$mode" in
    all) test_args=("-skip-testing:UITests") ;;
    only) test_args=("-only-testing:$only") ;;
    ui)
      test_args=("-only-testing:UITests")
      if [ ${#classes[@]} -gt 0 ]; then
        test_args=()
        for class in "${classes[@]}"; do test_args+=("-only-testing:UITests/$class"); done
      fi
      ;;
  esac
  if [ "$record" -eq 1 ]; then
    export TEST_RUNNER_SNAPSHOT_TESTING_RECORD=all
  elif [ "$ci" -eq 1 ]; then
    export TEST_RUNNER_SNAPSHOT_TESTING_RECORD=never
  fi
  rm -rf "$artifacts"
  mkdir -p "$artifacts"
  export TEST_RUNNER_SNAPSHOT_ARTIFACTS="$artifacts"
  run_checks || return 1
  note "xcodegen generate"
  xcodegen generate --quiet --spec iosApp/project.yml || return 1
  ensure_sim
  mkdir -p "$results"
  bundle="$results/$label.xcresult"
  rm -rf "$bundle"
  local -a xcode_args=(
    -project iosApp/ITMOWidgets.xcodeproj
    -scheme ITMOWidgets
    -configuration Debug
    -destination "platform=iOS Simulator,id=$udid"
    -derivedDataPath iosApp/build/DerivedData
  )
  note "xcodebuild build-for-testing for $sim_name"
  xcodebuild "${xcode_args[@]}" build-for-testing || return 1
  if [ "$mode" = ui ]; then
    xcrun simctl bootstatus "$udid" -b > /dev/null || return 1
    wait_for_notification_server
    # iOS keeps a notification answer until the app is deleted: every UI run starts unasked.
    xcrun simctl uninstall "$udid" dev.alllexey.itmowidgets 2> /dev/null
  fi
  note "xcodebuild test-without-building on $sim_name ${test_args[*]:-}"
  xcodebuild "${xcode_args[@]}" -resultBundlePath "$bundle" ${test_args[@]+"${test_args[@]}"} test-without-building
}

run_kn() {
  local init
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
  note "./gradlew ${tasks[*]}"
  ./gradlew --continue --init-script "$init" "${tasks[@]}"
}

case "$mode" in
  all | only | ui)
    label=ios
    [ "$mode" = all ] || label="ios-$mode"
    run_xcode "$label"
    rc=$?
    [ "$ci" -eq 1 ] && delete_sim
    finish "$label" "$rc"
    ;;
  kn)
    run_kn
    rc=$?
    [ "$ci" -eq 1 ] && delete_sim
    finish ios-kn "$rc"
    ;;
esac
