#!/usr/bin/env bash
# verify.sh [quick]                        checks, then root verifyQuick: every module's tests, Konsist, lintGithubDebug, APKs
# verify.sh checks                         the Gradle-free checks of quick: docs, generator, ui-report, ship-check tests
# verify.sh full                           root verifyFull (both lints), then klibs
# verify.sh klibs [<module>]               iosSimulatorArm64 klibs of every shared module, or of shared/<module>
# verify.sh shots <module>|app|all [--record | --gallery <dir>] [--shard <i>/<n>] [-P<name>=<value>...]
#                                          :shared:<module>, :app or all of them: screenshotsVerify (screenshotsRecord)
# verify.sh ui <Class>[,<Class>...]|all [--shard <i>/<n>]   :app:connectedGithubDebugAndroidTest on a pool emulator
# verify.sh ui @platform [--managed-device] the platform list under Orchestrator (pool emulator, or CI's managed device)
# verify.sh ship                           scripts/ship-check.sh when it exists, else full + check-play-policy.sh
# verify.sh run -- <gradle args...>        ad hoc Gradle tasks (kn slot when an argument names an iOS task)
#
# The one build entry point for agents (master plan section 7.5, L04 TC-09; contract in the verify-script recipe).
#
# - Every Gradle part runs in its own scripts/slot.sh call (android, or kn for iOS tasks), one after another and
#   never nested, with --max-workers=$ITMO_MAX_WORKERS from the slot. ITMO_SLOT_SH overrides the slot script.
# - kn parts (klibs, the klibs of full, run with an iOS task) pass -Dorg.gradle.jvmargs with KN_HEAP instead of the
#   -Xmx of gradle.properties, so K/N builds get a daemon of their own and Android daemons keep theirs (TC-15).
#   A run that passes its own -Dorg.gradle.jvmargs keeps it.
# - JAVA_HOME defaults to JDK 21 and ANDROID_HOME to ~/Library/Android/sdk on macOS; the Gradle daemon JDK comes
#   from gradle/gradle-daemon-jvm.properties either way.
# - Every Gradle part gets -PmyItmoApiDir=<the worktree's MyItmoApi pin> (ADR 0024), read from
#   $(git rev-parse --absolute-git-dir)/itmo-myitmoapi-dir, which `lane new|pin` writes. A missing pin or one off
#   gradle/myitmoapi.ref warns with the `lane pin` command and builds on. Skipped when MYITMOAPI_DIR is set (CI) or a
#   run passes its own -PmyItmoApiDir or -PmyItmoApiFromCentral.
# - shots all runs every shared module with a *ScreenshotTest class or a screenshots/ directory, plus :app, in one
#   Gradle call with --continue, so one run reports every module's differences. --gallery <dir> (an absent or empty
#   directory) renders every capture in all four appearances into <dir>/<module>/ without comparing and without
#   touching the baselines (screenshotsRecord with -Pshots.gallery and -Pshots.appearance=full).
# - --shard <i>/<n> (0 <= i < n) runs one of n parts, so CI spreads a mode over parallel jobs (android-ci.yml,
#   android-ui.yml): shots all keeps every n-th module of its list starting at the i-th; ui passes AndroidJUnitRunner's
#   numShards and shardIndex, which split the test methods by a hash of their names (a suite member and its
#   standalone run share a shard).
# - ui accepts FQCNs or bare class names (resolved to the one file under app/src/androidTest*/), optionally with
#   #method. It refuses unless ANDROID_SERIAL is emulator-<port> and the device reports ro.boot.qemu (or
#   ro.kernel.qemu) = 1; emulator-5554 only from a worktree whose itmo-lane marker reads `integrator`.
# - ui @platform runs the FQCNs of app/src/androidTest/platform-tests.txt with -Pitmo.orchestrator=true (Android
#   Test Orchestrator, clearPackageData; L04 TC-14). --managed-device runs them on the Gradle Managed Device ciDevice
#   instead of ANDROID_SERIAL and only with CI=true: locally it would create an AVD.
# - Never runs --stop, publishToMavenLocal, connected* outside ui, or install*/uninstall* tasks.
# - The last line of a finished run is `VERIFY A <mode> PASS|FAIL <secs>s <sha7>[+dirty]`.
# - ui ends in scripts/ui-report.py (TC-UIREPORT): while Gradle runs, the device's TestRunner log is captured
#   after a marker line, and every run of every test is listed, so a suite member that fails inside ShellSuite and
#   passes standalone later (AGP's XML, HTML and "Finished <n> tests" keep only the last run) still fails the
#   mode. A failed run is named with its suite or "standalone", "run k of n" and the ShellModeRule shell. Gradle's
#   `FAILED` lines count too, so the managed device (no log capture) is covered.
# - Exit code: 0 pass, 1 fail, 2 refused (usage, missing harness, unsafe device); refusals print no VERIFY line.

set -u

me=verify.sh
REPO_LETTER=A
# K/N daemon heap (TC-15): the release framework link of :shared:ios peaks at about 2.3 GB of heap (5.4 GB
# footprint with LLVM); -Xmx2g slowed it down, -Xmx6g did not speed it up.
KN_HEAP=3g
# TC-14: the platform instrumentation list and the CI-only Gradle Managed Device of app/build.gradle.kts.
PLATFORM_LIST=app/src/androidTest/platform-tests.txt
MANAGED_DEVICE=ciDevice

refuse() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) || refuse "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
root=$(cd "$script_dir/.." && pwd -P) || refuse "cannot resolve the repository root"
cd "$root" || refuse "cannot enter $root"

if [ "$(uname -s)" = Darwin ]; then
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2> /dev/null) && export JAVA_HOME || unset JAVA_HOME
  fi
fi

# Internal: the command slot.sh runs inside a held slot, where ITMO_MAX_WORKERS is known.
if [ "${1:-}" = __gradle ]; then
  shift
  exec ./gradlew ${ITMO_MAX_WORKERS:+"--max-workers=$ITMO_MAX_WORKERS"} "$@"
fi

usage() {
  sed -n '2,11p' "$self" | sed 's/^# //' >&2
  exit 2
}

slot_sh=${ITMO_SLOT_SH:-$root/scripts/slot.sh}
[ -x "$slot_sh" ] || refuse "no executable slot script at $slot_sh"

mode=${1:-quick}
[ $# -gt 0 ] && shift
case "$mode" in
  -h | --help) usage ;;
  quick | checks | full | klibs | shots | ui | ship | run) ;;
  *) note "unknown mode '$mode'"; usage ;;
esac

started=$(date +%s)
sha=$(git rev-parse --short=7 HEAD 2> /dev/null || printf 'nogit')
[ -n "$(git status --porcelain 2> /dev/null)" ] && sha="$sha+dirty"

finish() { # rc
  local verdict=PASS
  [ "$1" -eq 0 ] || verdict=FAIL
  printf 'VERIFY %s %s %s %ss %s\n' "$REPO_LETTER" "$mode" "$verdict" "$(($(date +%s) - started))" "$sha"
  [ "$1" -eq 0 ] && exit 0
  exit 1
}

slot_part() { # kind cmd...
  local kind=$1
  shift
  note "[$kind slot] $*"
  "$slot_sh" "$kind" -- "$@"
}

# gradle.properties' org.gradle.jvmargs with its -Xmx replaced by KN_HEAP.
kn_jvmargs() {
  local base
  base=$(sed -n 's/^org\.gradle\.jvmargs=//p' "$root/gradle.properties" | tail -n 1 |
    sed -E 's/(^| )-Xmx[^ ]*//g; s/^ +//')
  printf '%s' "-Xmx$KN_HEAP${base:+ $base}"
}

gradle_part() { # kind gradle-args...
  local kind=$1 arg
  local -a jvm=()
  shift
  if [ "$kind" = kn ]; then
    jvm=("-Dorg.gradle.jvmargs=$(kn_jvmargs)")
    for arg in "$@"; do
      case "$arg" in -Dorg.gradle.jvmargs=*) jvm=() ;; esac
    done
  fi
  note "[$kind slot] ./gradlew${jvm[*]:+ ${jvm[*]}} $*${pin_arg:+ $pin_arg}"
  "$slot_sh" "$kind" -- "$self" __gradle ${jvm[@]+"${jvm[@]}"} "$@" ${pin_arg:+"$pin_arg"}
}

# ---- MyItmoApi pin (ADR 0024, L04 TC-05) -----------------------------------------------------------------

# Sets pin_arg to -PmyItmoApiDir=<pin> for the worktree's pin; warns and leaves it empty when there is none.
pin_arg=""
resolve_pin() { # gradle-args of a run...
  local ref_file="$root/gradle/myitmoapi.ref" ref git_dir record pin pin_head arg fix
  [ -f "$ref_file" ] || return 0
  [ -z "${MYITMOAPI_DIR:-}" ] || return 0
  for arg in "$@"; do
    case "$arg" in -PmyItmoApiDir=* | -PmyItmoApiFromCentral=*) return 0 ;; esac
  done
  ref=$(head -n 1 "$ref_file" | tr -d ' \t\r')
  fix="run \`~/proj/.wt/bin/lane pin $root\` to build against MyItmoApi ${ref:0:7}"
  if ! git_dir=$(git rev-parse --absolute-git-dir 2> /dev/null); then
    note "warning: not a git worktree, building without a MyItmoApi pin"
    return 0
  fi
  record="$git_dir/itmo-myitmoapi-dir"
  pin=$(head -n 1 "$record" 2> /dev/null | tr -d '\r')
  if [ -z "$pin" ] || [ ! -d "$pin" ]; then
    note "warning: no MyItmoApi pin for this worktree (${pin:-no $record}); building without one. $fix"
    return 0
  fi
  pin_head=$(git -C "$pin" rev-parse HEAD 2> /dev/null)
  [ "$pin_head" = "$ref" ] ||
    note "warning: MyItmoApi pin $pin is at ${pin_head:-an unknown commit}, not at the ref. $fix"
  pin_arg="-PmyItmoApiDir=$pin"
}

no_args() { [ $# -eq 0 ] || refuse "$mode takes no arguments (got: $*)"; }

# Sets shard_index and shard_count from <i>/<n>, 0 <= i < n.
shard_index="" shard_count=""
parse_shard() { # spec
  [[ ${1:-} =~ ^([0-9]+)/([1-9][0-9]*)$ ]] || refuse "--shard takes <index>/<count>, got '${1:-}'"
  shard_index=${BASH_REMATCH[1]}
  shard_count=${BASH_REMATCH[2]}
  [ "$shard_index" -lt "$shard_count" ] || refuse "--shard: index $shard_index is not below the count $shard_count"
}

# ---- quick, full, klibs, ship ----------------------------------------------------------------------------

# The task lists live in the root build.gradle.kts (verifyQuick, verifyFull, verifyIosKlibs).

run_checks() {
  if [ -e "$root/scripts/check-docs.sh" ]; then
    note "scripts/check-docs.sh (outside any slot)"
    "$root/scripts/check-docs.sh" || return 1
  fi
  # The feature-module generator against the QR pilot: about a second, no Gradle (DC-04).
  if [ -e "$root/scripts/test-new-feature-module.sh" ]; then
    local out
    note "scripts/test-new-feature-module.sh (outside any slot)"
    out=$("$root/scripts/test-new-feature-module.sh" 2>&1) || { printf '%s\n' "$out" >&2; return 1; }
    printf '%s\n' "$out" | tail -n 1 >&2
  fi
  # The ui-report parser on seeded logs: well under a second, no Gradle, no device (TC-UIREPORT).
  if [ -e "$root/scripts/test-ui-report.sh" ]; then
    local report
    note "scripts/test-ui-report.sh (outside any slot)"
    report=$("$root/scripts/test-ui-report.sh" 2>&1) || { printf '%s\n' "$report" >&2; return 1; }
    printf '%s\n' "$report" | tail -n 1 >&2
  fi
  # ship-check's CI source on a scratch repository and a gh stub: a few seconds, no Gradle, no network (TC-CI2).
  if [ -e "$root/scripts/test-ship-check.sh" ]; then
    local ship
    note "scripts/test-ship-check.sh (outside any slot)"
    ship=$("$root/scripts/test-ship-check.sh" 2>&1) || { printf '%s\n' "$ship" >&2; return 1; }
    printf '%s\n' "$ship" | tail -n 1 >&2
  fi
}

run_quick() {
  run_checks || return 1
  gradle_part android verifyQuick
}

run_full() {
  gradle_part android verifyFull || return 1
  run_klibs
}

# Compile-only (no link*, no *Test on Apple targets): needs no Xcode, and runs on Linux x86_64 in CI (SP-10).
run_klibs() {
  local module=${1:-}
  [ $# -le 1 ] || refuse "usage: klibs [<module>]"
  if [ -z "$module" ]; then
    gradle_part kn verifyIosKlibs
    return
  fi
  [[ $module =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]] || refuse "klibs: '$module' is not a module name"
  [ -f "$root/shared/$module/build.gradle.kts" ] || refuse "klibs: no module shared/$module"
  gradle_part kn ":shared:$module:compileKotlinIosSimulatorArm64"
}

run_ship() {
  # ship-check.sh takes its own slots; calling it from a held slot would deadlock, so hand over before any.
  if [ -e "$root/scripts/ship-check.sh" ]; then
    note "handing over to scripts/ship-check.sh"
    exec "$root/scripts/ship-check.sh" "$@"
  fi
  no_args "$@"
  run_full || return 1
  slot_part android "$root/scripts/check-play-policy.sh"
}

# ---- shots -----------------------------------------------------------------------------------------------

run_shots() {
  local module=${1:-} task=screenshotsVerify out rc arg gallery="" record=""
  local -a props=() projects=() tasks=()
  [ -n "$module" ] || refuse "usage: shots <module>|app|all [--record | --gallery <dir>] [--shard <i>/<n>] [-P<name>=<value>...]"
  shift
  while [ $# -gt 0 ]; do
    arg=$1
    shift
    case "$arg" in
      --record) record=1 ;;
      --shard)
        [ "$module" = all ] || refuse "shots: --shard only with all"
        parse_shard "${1:-}"
        shift
        ;;
      --gallery)
        [ $# -gt 0 ] && [ -n "$1" ] || refuse "shots: --gallery needs a directory"
        gallery=$1
        shift
        ;;
      -P?*=*) props+=("$arg") ;;
      *) refuse "shots: unknown argument '$arg'" ;;
    esac
  done
  [ -z "$record" ] || [ -z "$gallery" ] || refuse "shots: --record and --gallery exclude each other"
  [ -z "$record" ] || task=screenshotsRecord
  [[ $module =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]] || refuse "shots: '$module' is not a module name"
  grep -rqs --include='*.kts' --include='*.kt' screenshotsVerify "$root/build-logic" 2> /dev/null ||
    refuse "screenshot harness not installed (no screenshotsVerify task in build-logic yet)"
  case "$module" in
    app) projects=(:app) ;;
    all)
      local index=0
      while IFS= read -r arg; do
        [ -z "$shard_count" ] || [ $((index % shard_count)) -eq "$shard_index" ] && projects+=("$arg")
        index=$((index + 1))
      done < <(shot_projects)
      [ "${#projects[@]}" -gt 0 ] || refuse "shots: shard $shard_index/$shard_count has no module"
      [ -z "$shard_count" ] || note "shots shard $shard_index/$shard_count: ${projects[*]}"
      ;;
    *)
      [ -d "$root/shared/$module" ] || refuse "shots: no module shared/$module"
      projects=(":shared:$module")
      ;;
  esac
  if [ -n "$gallery" ]; then
    task=screenshotsRecord
    mkdir -p "$gallery" && gallery=$(cd "$gallery" && pwd -P) || refuse "shots: cannot create the gallery $gallery"
    [ -z "$(ls -A "$gallery")" ] || refuse "shots: the gallery $gallery is not empty; pass a new directory"
    props+=("-Pshots.gallery=$gallery" "-Pshots.appearance=full")
  fi
  for arg in "${projects[@]}"; do tasks+=("$arg:$task"); done
  [ "${#tasks[@]}" -eq 1 ] || tasks=(--continue "${tasks[@]}")
  out=$(mktemp "${TMPDIR:-/tmp}/verify-shots.XXXXXX") || refuse "cannot create a temporary file"
  gradle_part android "${tasks[@]}" ${props[@]+"${props[@]}"} 2>&1 | tee "$out"
  rc=${PIPESTATUS[0]}
  if [ "$rc" -ne 0 ] && grep -qE "(Task '$task' not found|Cannot locate tasks that match)" "$out"; then
    rm -f "$out"
    refuse "screenshot harness not installed in ${projects[*]}"
  fi
  rm -f "$out"
  [ -z "$gallery" ] || gallery_summary "$gallery"
  return "$rc"
}

# :app and every shared module with a screenshot test or baselines, one Gradle path per line.
shot_projects() {
  local dir
  for dir in "$root"/shared/*/; do
    dir=${dir%/}
    [ -f "$dir/build.gradle.kts" ] || continue
    if [ -d "$dir/screenshots" ] ||
      [ -n "$(find "$dir/src" -path '*/src/androidHostTest/*' -name '*ScreenshotTest.kt' -print -quit 2> /dev/null)" ]; then
      printf ':shared:%s\n' "${dir##*/}"
    fi
  done
  printf ':app\n'
}

gallery_summary() { # dir
  local dir
  for dir in "$1"/*/; do
    [ -d "$dir" ] || continue
    note "gallery: $(find "$dir" -name '*.png' | wc -l | tr -d ' ') PNGs in $dir"
  done
}

# ---- ui --------------------------------------------------------------------------------------------------

# Prints the FQCN of the androidTest class named by $1 (bare or fully qualified); refuses on none or several.
resolve_class() {
  local given=$1 base file pkg fqcn hits="" count=0 rel
  base=${given##*.}
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    pkg=$(sed -n 's/^[[:space:]]*package[[:space:]][[:space:]]*\([A-Za-z0-9_.]*\).*/\1/p' "$file" | head -n 1)
    fqcn=${pkg:+$pkg.}$base
    if [ "$given" = "$base" ] || [ "$given" = "$fqcn" ]; then
      count=$((count + 1))
      hits="$hits $fqcn:$file"
    fi
  done <<EOF
$(find app/src -type f -path 'app/src/androidTest*' \( -name "$base.kt" -o -name "$base.java" \) 2> /dev/null | sort)
EOF
  [ "$count" -gt 0 ] || refuse "ui: no test class '$given' under app/src/androidTest*/"
  [ "$count" -eq 1 ] || refuse "ui: '$given' matches $count files, pass the FQCN:$hits"
  hits=${hits# }
  fqcn=${hits%%:*}
  file=${hits#*:}
  rel=${file#app/src/}
  case "${rel%%/*}" in
    androidTest | androidTestDebug | androidTestGithub | androidTestGithubDebug) ;;
    *) refuse "ui: $file is not in a github debug source set; ui runs :app:connectedGithubDebugAndroidTest" ;;
  esac
  printf '%s' "$fqcn"
}

check_device() {
  local serial=${ANDROID_SERIAL:-} marker="" git_dir adb qemu prop
  [[ $serial =~ ^emulator-[0-9]+$ ]] ||
    refuse "ui: ANDROID_SERIAL must name a pool emulator (emulator-<port>), got '${serial:-unset}'"
  if [ "$serial" = emulator-5554 ]; then
    git_dir=$(git rev-parse --absolute-git-dir 2> /dev/null) &&
      marker=$(head -n 1 "$git_dir/itmo-lane" 2> /dev/null | tr -d ' \t\r')
    [ "$marker" = integrator ] || refuse "ui: emulator-5554 is the integrator's; use a pool emulator"
  fi
  adb="${ANDROID_HOME:-}/platform-tools/adb"
  [ -x "$adb" ] || refuse "ui: no adb at $adb (set ANDROID_HOME)"
  for prop in ro.boot.qemu ro.kernel.qemu; do
    qemu=$("$adb" -s "$serial" shell getprop "$prop" 2> /dev/null | tr -d ' \t\r')
    [ "$qemu" = 1 ] && return 0
  done
  refuse "ui: $serial is not a reachable emulator (getprop ro.boot.qemu / ro.kernel.qemu is not 1)"
}

# The FQCNs of the platform list, comma-separated; comments and blank lines skipped.
platform_classes() {
  local list="$root/$PLATFORM_LIST" classes
  [ -f "$list" ] || refuse "ui: no platform list $PLATFORM_LIST"
  classes=$(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$list" | grep -v '^$' | paste -sd , -)
  [ -n "$classes" ] || refuse "ui: $PLATFORM_LIST lists no class"
  printf '%s' "$classes"
}

run_ui() {
  local spec=${1:-} item name method classes="" fqcn old_ifs task=:app:connectedGithubDebugAndroidTest
  local -a extra=()
  [ -n "$spec" ] || refuse "usage: ui <Class>[,<Class>...]|all|@platform [--managed-device] [--shard <i>/<n>]"
  shift
  if [ "$spec" = @platform ]; then
    extra=(-Pitmo.orchestrator=true)
    if [ "${1:-}" = --managed-device ]; then
      shift
      [ "${CI:-}" = true ] || refuse "ui: --managed-device only in CI (CI=true); locally it would create an AVD"
      task=:app:${MANAGED_DEVICE}GithubDebugAndroidTest
      extra+=(-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect)
    fi
    spec=$(platform_classes) || exit 2
  fi
  if [ "${1:-}" = --shard ]; then
    parse_shard "${2:-}"
    shift 2
    extra+=("-Pandroid.testInstrumentationRunnerArguments.numShards=$shard_count"
      "-Pandroid.testInstrumentationRunnerArguments.shardIndex=$shard_index")
  fi
  no_args "$@"
  if [ "$spec" != all ]; then
    old_ifs=$IFS
    IFS=,
    set -f
    # shellcheck disable=SC2086 # split on commas only
    set -- $spec
    set +f
    IFS=$old_ifs
    [ $# -gt 0 ] || refuse "ui: empty class list"
    for item in "$@"; do
      [[ $item =~ ^[A-Za-z_][A-Za-z0-9_.]*(#[A-Za-z_][A-Za-z0-9_]*)?$ ]] || refuse "ui: '$item' is not a class name"
      name=${item%%#*}
      method=""
      [ "$name" = "$item" ] || method="#${item#*#}"
      fqcn=$(resolve_class "$name") || exit 2
      classes="$classes,$fqcn$method"
    done
    classes=${classes#,}
  fi
  local device=""
  if [ "$task" = :app:connectedGithubDebugAndroidTest ]; then
    check_device
    device=$ANDROID_SERIAL
    note "running on $ANDROID_SERIAL: ${classes:-all instrumentation tests}"
  else
    note "running on the managed device $MANAGED_DEVICE: $classes"
  fi
  ui_report_start "$device"
  gradle_part android "$task" ${extra[@]+"${extra[@]}"} \
    ${classes:+"-Pandroid.testInstrumentationRunnerArguments.class=$classes"} 2>&1 | tee "$ui_dir/gradle"
  ui_report_finish "${PIPESTATUS[0]}"
}

# ---- ui report (TC-UIREPORT) -----------------------------------------------------------------------------

ui_dir="" ui_logcat_pid="" ui_marker=""
ui_cleanup() {
  [ -z "$ui_logcat_pid" ] || kill "$ui_logcat_pid" 2> /dev/null
  [ -z "$ui_dir" ] || rm -rf "$ui_dir"
}

# Starts the TestRunner log capture of serial $1 (none for the managed device) and waits for its marker line.
ui_report_start() {
  local serial=$1 adb="${ANDROID_HOME:-}/platform-tools/adb" tries=0
  ui_dir=$(mktemp -d "${TMPDIR:-/tmp}/verify-ui.XXXXXX") || refuse "cannot create a temporary directory"
  trap ui_cleanup EXIT
  : > "$ui_dir/logcat"
  [ -n "$serial" ] || return 0
  ui_marker="itmo-verify-ui-start $$-$(date +%s)"
  "$adb" -s "$serial" logcat -v raw -s TestRunner:V -T 1 > "$ui_dir/logcat" 2> /dev/null &
  ui_logcat_pid=$!
  # The reader may attach after the first marker; the report starts after the last one it saw.
  while [ "$tries" -lt 20 ]; do
    "$adb" -s "$serial" shell log -t TestRunner "'$ui_marker'" > /dev/null 2>&1
    grep -qF "$ui_marker" "$ui_dir/logcat" 2> /dev/null && return 0
    tries=$((tries + 1))
    sleep 0.5
  done
  note "warning: the TestRunner log of $serial shows no marker; the ui report reads Gradle's output only"
  kill "$ui_logcat_pid" 2> /dev/null
  ui_logcat_pid=""
  : > "$ui_dir/logcat"
}

# Stops the capture, prints the report and returns Gradle's code $1, or 1 when the report found a failed run.
ui_report_finish() {
  local rc=$1 report_rc tries=0
  if [ -n "$ui_logcat_pid" ]; then
    # logcat delivers the last lines a moment after the instrumentation exits.
    while [ "$tries" -lt 10 ] && ! tail -n 1 "$ui_dir/logcat" | grep -q '^run finished:'; do
      tries=$((tries + 1))
      sleep 0.5
    done
    kill "$ui_logcat_pid" 2> /dev/null
    wait "$ui_logcat_pid" 2> /dev/null
    ui_logcat_pid=""
  fi
  if ! command -v python3 > /dev/null 2>&1; then
    note "warning: no python3, so no ui report; the verdict is Gradle's"
    return "$rc"
  fi
  python3 -I "$root/scripts/ui-report.py" --root "$root" --marker "${ui_marker:-itmo-verify-ui-start}" \
    --logcat "$ui_dir/logcat" --gradle "$ui_dir/gradle" >&2
  report_rc=$?
  if [ "$report_rc" -eq 1 ] && [ "$rc" -eq 0 ]; then
    note "Gradle passed, but the ui report lists a failed run (a later run of the same test hid it)"
    rc=1
  elif [ "$report_rc" -gt 1 ]; then
    note "warning: scripts/ui-report.py exited $report_rc; the verdict is Gradle's"
  fi
  return "$rc"
}

# ---- run -------------------------------------------------------------------------------------------------

run_run() {
  local kind=android arg task
  [ "${1:-}" = -- ] || refuse "usage: run -- <gradle args...>"
  shift
  [ $# -gt 0 ] || refuse "run: no Gradle arguments"
  for arg in "$@"; do
    task=${arg##*:}
    case "$arg" in
      --stop | --stop=*) refuse "run: never --stop (other agents share the daemons)" ;;
      -*) continue ;;
    esac
    case "$task" in
      *ToMavenLocal* | *toMavenLocal*) refuse "run: no publishing to Maven Local ($arg)" ;;
      install* | uninstall*) refuse "run: no installs outside ui ($arg)" ;;
      connected* | deviceCheck | allDevices* | "$MANAGED_DEVICE"* | cleanManagedDevices)
        refuse "run: device tests go through \`verify.sh ui\` ($arg)"
        ;;
    esac
    case "$arg" in
      *Ios* | *iosSimulatorArm64* | *iosArm64* | *iosX64* | link*Framework* | *:link*Framework*) kind=kn ;;
    esac
  done
  gradle_part "$kind" "$@"
}

# ---- main ------------------------------------------------------------------------------------------------

if [ "$mode" = run ]; then resolve_pin "$@"; else resolve_pin; fi

case "$mode" in
  quick) no_args "$@"; run_quick ;;
  checks) no_args "$@"; run_checks ;;
  full) no_args "$@"; run_full ;;
  klibs) run_klibs "$@" ;;
  ship) run_ship "$@" ;;
  shots) run_shots "$@" ;;
  ui) run_ui "$@" ;;
  run) run_run "$@" ;;
esac
finish $?
