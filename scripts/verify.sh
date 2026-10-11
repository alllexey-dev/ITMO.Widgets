#!/usr/bin/env bash
# verify.sh checks                         the Gradle-free checks: docs links, ui-report self-test
# verify.sh shots <module>|app|all [--record | --gallery <dir>] [--shard <i>/<n>] [-P<name>=<value>...]
#                                          :shared:<module>, :app or all of them: screenshotsVerify (screenshotsRecord)
# verify.sh ui <Class>[,<Class>...]|all [--shard <i>/<n>]   :app:connectedGithubDebugAndroidTest on ANDROID_SERIAL
# verify.sh ui @platform [--managed-device] the platform list under Orchestrator (an emulator, or CI's managed device)
#
# The modes CI runs (android-ci.yml, android-ui.yml, android-nightly.yml) that are more than one Gradle call. For
# everything else call Gradle directly: ./gradlew verifyQuick, verifyFull, verifyIosKlibs.
#
# - JAVA_HOME defaults to JDK 21 and ANDROID_HOME to ~/Library/Android/sdk on macOS; the Gradle daemon JDK comes
#   from gradle/gradle-daemon-jvm.properties either way.
# - shots all runs every shared module with a *ScreenshotTest class or a screenshots/ directory, plus :app, in one
#   Gradle call with --continue, so one run reports every module's differences. --gallery <dir> (an absent or empty
#   directory) renders every capture in all four appearances into <dir>/<module>/ without comparing and without
#   touching the baselines (screenshotsRecord with -Pshots.gallery and -Pshots.appearance=full).
# - --shard <i>/<n> (0 <= i < n) runs one of n parts, so CI spreads a mode over parallel jobs: shots all keeps every
#   n-th module of its list starting at the i-th; ui passes AndroidJUnitRunner's numShards and shardIndex, which split
#   the test methods by a hash of their names (a suite member and its standalone run share a shard).
# - ui accepts FQCNs or bare class names (resolved to the one file under app/src/androidTest*/), optionally with
#   #method. It needs ANDROID_SERIAL and refuses a device that is not an emulator (the tests clear app data).
# - ui @platform runs the FQCNs of app/src/androidTest/platform-tests.txt with -Pitmo.orchestrator=true (Android
#   Test Orchestrator, clearPackageData). --managed-device runs them on the Gradle Managed Device ciDevice instead of
#   ANDROID_SERIAL and only with CI=true: locally it would create an AVD.
# - ui ends in scripts/ui-report.py: while Gradle runs, the device's TestRunner log is captured after a marker line,
#   and every run of every test is listed, so a suite member that fails inside ShellSuite and passes standalone later
#   (AGP's XML, HTML and "Finished <n> tests" keep only the last run) still fails the mode. A failed run is named
#   with its suite or "standalone", "run k of n" and the ShellModeRule shell. Gradle's `FAILED` lines count too, so
#   the managed device (no log capture) is covered. A capture that ended before `run finished:` is replaced by the
#   device's log buffer when that still holds the marker.
# - Exit code: 0 pass, 1 fail, 2 refused (usage, missing harness, unsafe device).

set -u

me=verify.sh
# The platform instrumentation list and the CI-only Gradle Managed Device of app/build.gradle.kts.
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

usage() {
  sed -n '2,6p' "$self" | sed 's/^# //' >&2
  exit 2
}

mode=${1:-}
[ $# -gt 0 ] && shift
case "$mode" in
  -h | --help | "") usage ;;
  checks | shots | ui) ;;
  *) note "unknown mode '$mode'"; usage ;;
esac

gradle_part() { # gradle-args...
  note "./gradlew $*"
  ./gradlew "$@"
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

# ---- checks ----------------------------------------------------------------------------------------------

run_checks() {
  note "scripts/check-docs.sh"
  "$root/scripts/check-docs.sh" || return 1
  # The ui-report parser on seeded logs: well under a second, no Gradle, no device.
  local report
  note "scripts/test-ui-report.sh"
  report=$("$root/scripts/test-ui-report.sh" 2>&1) || { printf '%s\n' "$report" >&2; return 1; }
  printf '%s\n' "$report" | tail -n 1 >&2
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
  gradle_part "${tasks[@]}" ${props[@]+"${props[@]}"} 2>&1 | tee "$out"
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
  local serial=${ANDROID_SERIAL:-} adb qemu prop
  [ -n "$serial" ] || refuse "ui: set ANDROID_SERIAL to the emulator to run on (adb devices)"
  adb="${ANDROID_HOME:-}/platform-tools/adb"
  [ -x "$adb" ] || refuse "ui: no adb at $adb (set ANDROID_HOME)"
  for prop in ro.boot.qemu ro.kernel.qemu; do
    qemu=$("$adb" -s "$serial" shell getprop "$prop" 2> /dev/null | tr -d ' \t\r')
    [ "$qemu" = 1 ] && return 0
  done
  refuse "ui: $serial is not a reachable emulator (getprop ro.boot.qemu / ro.kernel.qemu is not 1); the tests clear app data"
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
  gradle_part "$task" ${extra[@]+"${extra[@]}"} \
    ${classes:+"-Pandroid.testInstrumentationRunnerArguments.class=$classes"} 2>&1 | tee "$ui_dir/gradle"
  ui_report_finish "${PIPESTATUS[0]}"
}

# ---- ui report -----------------------------------------------------------------------------------------

ui_dir="" ui_logcat_pid="" ui_marker="" ui_serial=""
ui_cleanup() {
  [ -z "$ui_logcat_pid" ] || kill "$ui_logcat_pid" 2> /dev/null
  [ -z "$ui_dir" ] || rm -rf "$ui_dir"
}

# Starts the TestRunner log capture of serial $1 (none for the managed device) and waits for its marker line.
ui_report_start() {
  local serial=$1 adb="${ANDROID_HOME:-}/platform-tools/adb" tries=0
  ui_serial=$serial
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
    # A live capture that ended early (its adb connection dropped) misses runs; the device's log buffer, when it
    # still holds the marker, has them all.
    if ! grep -q '^run finished:' "$ui_dir/logcat"; then
      "${ANDROID_HOME:-}/platform-tools/adb" -s "$ui_serial" logcat -d -v raw -s TestRunner:V > "$ui_dir/buffer" 2> /dev/null
      if grep -qF "$ui_marker" "$ui_dir/buffer" && grep -q '^run finished:' "$ui_dir/buffer"; then
        note "the live TestRunner capture ended early; the report reads the device's log buffer"
        mv "$ui_dir/buffer" "$ui_dir/logcat"
      fi
    fi
  fi
  if ! command -v python3 > /dev/null 2>&1; then
    note "warning: no python3, so no ui report; the verdict is Gradle's"
    return "$rc"
  fi
  python3 -I "$root/scripts/ui-report.py" --root "$root" --marker "${ui_marker:-itmo-verify-ui-start}" \
    --logcat "$ui_dir/logcat" --gradle "$ui_dir/gradle" \
    ${ui_serial:+--junit "$root/app/build/outputs/androidTest-results/connected"} >&2
  report_rc=$?
  if [ "$report_rc" -eq 1 ] && [ "$rc" -eq 0 ]; then
    note "Gradle passed, but the ui report lists a failed run (a later run of the same test hid it)"
    rc=1
  elif [ "$report_rc" -gt 1 ]; then
    note "warning: scripts/ui-report.py exited $report_rc; the verdict is Gradle's"
  fi
  return "$rc"
}

# ---- main ------------------------------------------------------------------------------------------------

case "$mode" in
  checks) no_args "$@"; run_checks ;;
  shots) run_shots "$@" ;;
  ui) run_ui "$@" ;;
esac
