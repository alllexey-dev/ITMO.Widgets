#!/usr/bin/env bash
# ship-check.sh [--local] [--wait] [--no-device] [--central] [--keep-going] [--only <n>[,<n>...]]
#
# The ship check (master plan section 5.4, L01 SS-01): run on batch heads that carry a toolchain, module-graph,
# shell or storage change, weekly otherwise, on every prerelease head and on the release candidate.
#
# Two sources (TC-CI2). By default, when `gh` is installed and signed in, stages 2, 4, 5 and 6 are read from the
# GitHub Actions results of the head SHA and only stages 1 and 3 run here (they take minutes, not an emulator):
#   2 full      the `verify-quick` check
#   4 ui        the `android-ui` check
#   5, 6        the `android-ship` check and the row of that stage in summary.md of its artifact `ship-<sha7>`
# The newest run of the SHA that has a job of that name counts (`gh run list --commit`, `gh run view`,
# `gh run download`). A check that is missing, still running or not `success` fails its stage; --wait polls every
# ITMO_SHIP_POLL_SECONDS (60) for up to ITMO_SHIP_WAIT_MINUTES (120) until the checks finish. CI results belong to
# the commit, so a dirty worktree is refused here. --local runs every stage on this machine as below.
#
# Stages, each PASS or FAIL in ~/proj/.wt/run/ship/<sha7>/summary.md with its log and source beside it:
#   1 version   versionCode/versionName per ADR 0030; origin/release/2.2 stays below 100
#   2 full      scripts/verify.sh full (unit tests incl. StableIdentifiersTest and Konsist, both lints, assembles)
#   3 release   unsigned :app:assembleGithubRelease :app:bundlePlayRelease and both process*ReleaseManifest through
#               `verify.sh run --`, then SKIP_BUILD=1 scripts/check-play-policy.sh
#   4 ui        scripts/verify.sh ui all on emulator-5554, UpgradeFrom22Test included; the failed runs of its ui
#               report (ShellSuite members run twice) are listed under the table
#   5 upgrade   install the v2.2 githubDebug on emulator-5554, seed the 2.2 data directory of
#               app/src/androidTest/assets/upgrade-2.2/, `adb install -r` the head githubDebug and start it
#   6 shrunk    the release R8 and resource shrinking on the device: install githubMinifiedSmoke and
#               playMinifiedSmoke (release build type, debug-signed, pointed at dev) fresh on emulator-5554, start
#               each and wait for the sign-in screen without a FATAL EXCEPTION. Debug builds never run R8, so this is
#               what catches a startup crash only the shrunk app has (R8-FIX1: two Koin keys merged into one class)
#
# --no-device runs stages 1-3 locally (implies --local). --central passes -PmyItmoApiFromCentral=true to stage 3. --keep-going runs every
# selected stage after a FAIL (default: stop at the first one). --only picks stages (device stages still need the
# integrator's emulator).
#
# - Takes no build slot itself: every Gradle run of the head goes through a scripts/verify.sh mode, and the one-time
#   v2.2 build through `~/proj/.wt/bin/slot.sh android --` (slot.sh is re-entrant through ITMO_SLOT_HELD).
# - Device stages run only on emulator-5554 from a worktree whose itmo-lane marker reads `integrator`; any other
#   ANDROID_SERIAL is refused (the owner's phone shares the applicationId). Release outputs stay unsigned: a
#   keystore.properties in the worktree is refused. Read from CI, no stage needs a device.
# - The last line is `VERIFY A ship|ship-no-device PASS|FAIL <secs>s <sha7>` (the --local-verify format).
# - Exit code: 0 every stage passed, 1 a stage failed, 2 refused (usage, signing config, unsafe device).

set -u

me=ship-check.sh
REPO_LETTER=A
PACKAGE=dev.alllexey.itmowidgets
HEAD_ACTIVITY=.app.MainActivity
DEVICE_SERIAL=emulator-5554
V22_TAG=v2.2
SMOKE_WAIT_SECONDS=30
# Stage 6: how long the app keeps running after the sign-in screen shows, for a crash in work started after it.
SHRUNK_SETTLE_SECONDS=5

refuse() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) || refuse "cannot resolve the script directory"
root=$(cd "$script_dir/.." && pwd -P) || refuse "cannot resolve the repository root"
cd "$root" || refuse "cannot enter $root"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
wt=${ITMO_WT:-$HOME/proj/.wt}
verify_sh="$root/scripts/verify.sh"
adb="$ANDROID_HOME/platform-tools/adb"
assets="$root/app/src/androidTest/assets/upgrade-2.2"
sign_in_strings="$root/shared/feature-account/src/commonMain/composeResources/values/strings_auth.xml"

usage() {
  sed -n '2,2p' "$0" | sed 's/^# //' >&2
  exit 2
}

device=1 central="" keep_going="" only="" source=ci wait=""
while [ $# -gt 0 ]; do
  case "$1" in
    --local) source=local ;;
    --wait) wait=1 ;;
    --no-device) device="" source=local ;;
    --central) central=-PmyItmoApiFromCentral=true ;;
    --keep-going) keep_going=1 ;;
    --only)
      [ $# -gt 1 ] || usage
      only=",$2,"
      [[ $2 =~ ^[1-6](,[1-6])*$ ]] || refuse "--only takes stage numbers 1-6, got '$2'"
      shift
      ;;
    -h | --help) usage ;;
    *) note "unknown argument '$1'"; usage ;;
  esac
  shift
done

selected() { # stage
  if [ -n "$only" ]; then
    case "$only" in *",$1,"*) ;; *) return 1 ;; esac
  fi
  [ "$1" -le 3 ] || [ -n "$device" ]
}

mode=ship
[ -n "$device" ] || mode=ship-no-device

from_ci() { # stage
  [ "$source" = ci ] || return 1
  case "$1" in 2 | 4 | 5 | 6) return 0 ;; esac
  return 1
}

ci_check_of() { # stage -> the GitHub Actions check that stands for it
  case "$1" in
    2) echo verify-quick ;;
    4) echo android-ui ;;
    5 | 6) echo android-ship ;;
  esac
}

ci_needed() { selected 2 || selected 4 || selected 5 || selected 6; }

if [ "$source" = ci ] && ci_needed; then
  if ! command -v gh > /dev/null 2>&1 || ! gh auth status > /dev/null 2>&1; then
    note "gh is missing or not signed in: every stage runs locally (--local)"
    source=local
  fi
fi

[ -x "$verify_sh" ] || refuse "no executable scripts/verify.sh"
if selected 3 && [ -e "$root/keystore.properties" ]; then
  refuse "keystore.properties exists in $root: release outputs must stay unsigned here"
fi

# Device stages: emulator-5554 only, only from the integrator's worktree, only if it really is an emulator.
if [ "$source" = local ] && { selected 4 || selected 5 || selected 6; }; then
  [ -z "${ANDROID_SERIAL:-}" ] || [ "$ANDROID_SERIAL" = "$DEVICE_SERIAL" ] ||
    refuse "device stages run only on $DEVICE_SERIAL, not '$ANDROID_SERIAL' (use --no-device)"
  git_dir=$(git rev-parse --absolute-git-dir 2> /dev/null) || refuse "not a git worktree"
  marker=$(head -n 1 "$git_dir/itmo-lane" 2> /dev/null | tr -d ' \t\r')
  [ "$marker" = integrator ] || refuse "device stages need the integrator's worktree (use --no-device)"
  [ -x "$adb" ] || refuse "no adb at $adb"
  qemu=$("$adb" -s "$DEVICE_SERIAL" shell getprop ro.kernel.qemu 2> /dev/null | tr -d ' \t\r')
  [ "$qemu" = 1 ] || qemu=$("$adb" -s "$DEVICE_SERIAL" shell getprop ro.boot.qemu 2> /dev/null | tr -d ' \t\r')
  [ "$qemu" = 1 ] || refuse "$DEVICE_SERIAL is not a reachable emulator"
  export ANDROID_SERIAL=$DEVICE_SERIAL
fi

head_sha=$(git rev-parse HEAD) || refuse "cannot resolve HEAD"
sha=${head_sha:0:7}
dirty=""
[ -z "$(git status --porcelain 2> /dev/null)" ] || dirty="+dirty"
if [ -n "$dirty" ] && [ "$source" = ci ] && ci_needed; then
  refuse "the worktree has uncommitted changes and CI results cover only $sha: commit them or use --local"
fi
out_dir="$wt/run/ship/$sha"
mkdir -p "$out_dir" || refuse "cannot create $out_dir"
summary="$out_dir/summary.md"
ci_table="$out_dir/ci-checks.tsv"
rm -f "$ci_table"
ci_ship_summary=""
row=()
ui_failures=""
failed=""
started=$(date +%s)

write_summary() {
  local n
  {
    printf '# Ship check %s%s\n\n' "$sha" "$dirty"
    printf -- '- Head: `%s`\n' "$head_sha"
    printf -- '- Worktree: `%s`\n' "$root"
    printf -- '- Mode: %s%s\n' "$mode" "${central:+ (MyItmoApi from Central)}"
    if [ "$source" = ci ]; then
      printf -- '- Source: stages 2, 4, 5, 6 from the CI checks verify-quick, android-ui, android-ship; 1, 3 local\n'
    else
      printf -- '- Source: every stage local\n'
    fi
    printf -- '- Started: %s\n\n' "$(date -r "$started" '+%Y-%m-%d %H:%M:%S %z')"
    printf '| Stage | Result | Time | Source | Log |\n|---|---|---|---|---|\n'
    for n in 1 2 3 4 5 6; do
      [ -z "${row[$n]:-}" ] || printf '%s\n' "${row[$n]}"
    done
    if [ -n "$ui_failures" ]; then
      printf '\n## Failed instrumentation runs (stage 4, scripts/ui-report.py)\n\n'
      printf '%s\n' "$ui_failures" | sed 's/^ui-report: *FAIL /- /'
    fi
  } > "$summary"
}

run_stage() { # n name function
  local n=$1 name=$2 fn=$3 log t0 rc verdict secs src url
  log="$out_dir/stage$n-$name.log"
  if ! selected "$n"; then
    return 0
  fi
  if [ -n "$failed" ] && [ -z "$keep_going" ]; then
    row[$n]="| $n $name | NOT RUN | - | - | - |"
    write_summary
    return 0
  fi
  note "stage $n $name (log: $log)"
  t0=$(date +%s)
  "$fn" 2>&1 | tee "$log"
  rc=${PIPESTATUS[0]}
  verdict=PASS
  if [ "$rc" -ne 0 ]; then
    verdict=FAIL
    failed=1
  fi
  [ "$n" != 4 ] || ui_failures=$(grep '^ui-report: *FAIL ' "$log")
  printf '%s: stage %s %s %s\n' "$me" "$n" "$name" "$verdict" | tee -a "$log" >&2
  secs="$(($(date +%s) - t0))s" src=local
  if from_ci "$n"; then
    url=$(ci_field "$(ci_check_of "$n")" 4)
    secs=$(ci_field "$(ci_check_of "$n")" 5)
    if [ -n "$secs" ] && [ "$secs" != - ]; then secs="${secs}s"; else secs=-; fi
    if [ -n "$url" ] && [ "$url" != - ]; then src="CI run $url"; else src="CI: $(ci_check_of "$n") not reported"; fi
  fi
  row[$n]="| $n $name | $verdict | $secs | $src | \`$(basename "$log")\` |"
  write_summary
}

# ---- CI results (stages 2, 4, 5, 6) ----------------------------------------------------------------------

# Writes $ci_table: one line per check, "name state conclusion url seconds run-id" (tab-separated, "-" when
# unknown). state is completed, pending (running, queued, or not reported while a run of the SHA is unfinished)
# or missing. Of all runs of the SHA the newest with a job of that name counts.
ci_snapshot() { # check...
  python3 - "$head_sha" "$@" > "$ci_table.tmp" << 'PY' && mv "$ci_table.tmp" "$ci_table"
import json
import subprocess
import sys
from datetime import datetime

sha, checks = sys.argv[1], sys.argv[2:]


def gh(*args):
    done = subprocess.run(["gh", *args], stdin=subprocess.DEVNULL, capture_output=True, text=True)
    if done.returncode != 0:
        sys.stderr.write("gh %s: %s\n" % (" ".join(args), done.stderr.strip()))
        return None
    return json.loads(done.stdout or "null")


def seconds(job):
    try:
        fmt = "%Y-%m-%dT%H:%M:%SZ"
        start = datetime.strptime(job["startedAt"], fmt)
        end = datetime.strptime(job["completedAt"], fmt)
        return str(int((end - start).total_seconds()))
    except (KeyError, TypeError, ValueError):
        return "-"


runs = gh("run", "list", "--commit", sha, "--limit", "100", "--json", "databaseId,status,createdAt,url") or []
runs.sort(key=lambda r: r.get("createdAt") or "", reverse=True)
unfinished = not runs or any(r.get("status") != "completed" for r in runs)
jobs = [(r, (gh("run", "view", str(r["databaseId"]), "--json", "jobs") or {}).get("jobs") or []) for r in runs]
for check in checks:
    line = [check, "pending" if unfinished else "missing", "-", "-", "-", "-"]
    for run, run_jobs in jobs:
        job = next((j for j in run_jobs if j.get("name") == check), None)
        if job is None:
            continue
        done = job.get("status") == "completed"
        line = [check, "completed" if done else "pending", job.get("conclusion") or "-",
                job.get("url") or run.get("url") or "-", seconds(job) if done else "-", str(run["databaseId"])]
        break
    print("\t".join(line))
PY
}

ci_field() { # check column -> that column of the check's line in $ci_table
  [ -f "$ci_table" ] || return 0
  awk -F '\t' -v c="$1" -v i="$2" '$1 == c { print $i; exit }' "$ci_table"
}

# Reads the checks of the selected CI stages, with --wait until none is pending, then the ship artifact.
ci_collect() {
  local checks="" deadline run_id
  ! selected 2 || checks="verify-quick"
  ! selected 4 || checks="$checks android-ui"
  { ! selected 5 && ! selected 6; } || checks="$checks android-ship"
  [ -n "$checks" ] || return 0
  deadline=$(($(date +%s) + ${ITMO_SHIP_WAIT_MINUTES:-120} * 60))
  while :; do
    # shellcheck disable=SC2086 # one word per check name
    ci_snapshot $checks || note "reading the CI results of $sha failed"
    awk -F '\t' '$2 == "pending" { found = 1 } END { exit !found }' "$ci_table" 2> /dev/null || break
    [ -n "$wait" ] && [ "$(date +%s)" -lt "$deadline" ] || break
    note "waiting for CI on $sha: $(awk -F '\t' '$2 == "pending" { printf "%s ", $1 }' "$ci_table")"
    sleep "${ITMO_SHIP_POLL_SECONDS:-60}"
  done
  run_id=$(ci_field android-ship 6)
  if [ "$(ci_field android-ship 2)" = completed ] && [ -n "$run_id" ] && [ "$run_id" != - ]; then
    rm -rf "$out_dir/ci-ship"
    if gh run download "$run_id" -n "ship-$sha" -D "$out_dir/ci-ship" > /dev/null 2>&1; then
      ci_ship_summary=$(find "$out_dir/ci-ship" -name summary.md -type f 2> /dev/null | head -n 1)
    else
      note "no artifact ship-$sha in CI run $run_id"
    fi
  fi
}

ci_check_ok() { # check
  local state conclusion url
  state=$(ci_field "$1" 2) conclusion=$(ci_field "$1" 3) url=$(ci_field "$1" 4)
  [ "$url" != - ] || url=""
  case "$state" in
    completed)
      if [ "$conclusion" = success ]; then
        echo "ok: CI check $1 succeeded on $head_sha ($url)"
        return 0
      fi
      echo "FAIL: CI check $1 concluded '$conclusion' on $head_sha ($url)"
      ;;
    pending) echo "FAIL: CI check $1 has not finished on $head_sha${url:+ ($url)}; --wait polls until it does" ;;
    *) echo "FAIL: no CI check $1 on $head_sha (gh run list --commit $head_sha); --local runs the stage here" ;;
  esac
  return 1
}

stage_ci_full() { ci_check_ok verify-quick; }

stage_ci_ui() { ci_check_ok android-ui; }

# Stages 5 and 6 pass on their own row of the artifact's summary.md: the android-ship job fails as a whole when
# either stage fails, so a PASS row of the other stage still counts.
ci_ship_stage() { # n name
  local line result
  ci_check_ok android-ship || [ "$(ci_field android-ship 2)" = completed ] || return 1
  if [ -z "$ci_ship_summary" ]; then
    echo "FAIL: no summary.md in the artifact ship-$sha of CI run $(ci_field android-ship 6)"
    return 1
  fi
  line=$(grep -E "^\| *$1 $2 *\|" "$ci_ship_summary" | head -n 1)
  result=$(printf '%s\n' "$line" | awk -F '|' '{ gsub(/ /, "", $3); print $3 }')
  echo "artifact ship-$sha: ${line:-no row for stage $1 $2 in summary.md}"
  [ "$result" = PASS ]
}

stage_ci_upgrade() { ci_ship_stage 5 upgrade; }

stage_ci_shrunk() { ci_ship_stage 6 shrunk; }

# ---- 1 version -------------------------------------------------------------------------------------------

gradle_value() { # file-content key -> value of `key = ...` in defaultConfig
  printf '%s\n' "$1" | sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*\"\{0,1\}\([^\"[:space:]]*\)\"\{0,1\}[[:space:]]*\$/\1/p" |
    head -n 1
}

stage_version() {
  local build code name major minor patch beta base expected last_beta tag n rel rel_code ok=0
  build=$(cat app/build.gradle.kts) || return 1
  code=$(gradle_value "$build" versionCode)
  name=$(gradle_value "$build" versionName)
  echo "head: versionName=$name versionCode=$code"
  [[ $code =~ ^[0-9]+$ ]] || { echo "FAIL: no numeric versionCode in app/build.gradle.kts"; return 1; }

  if [[ $name =~ ^([0-9]+)\.([0-9]+)-SNAPSHOT$ ]]; then
    major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]}
    base=$((major * 10000 + minor * 100))
    expected=$((base - 10))
    last_beta=0
    for tag in $(git for-each-ref --format='%(refname:short)' "refs/tags/v$major.$minor.0-beta.*"); do
      n=${tag##*beta.}
      [[ $n =~ ^[1-9]$ ]] && [ "$n" -gt "$last_beta" ] && last_beta=$n
    done
    if [ "$code" -eq "$expected" ] || { [ "$last_beta" -gt 0 ] && [ "$code" -eq $((expected + last_beta)) ]; }; then
      echo "ok: development line $name = $code (base $expected, last beta tag: $last_beta)"
    else
      [ "$last_beta" -eq 0 ] || expected="$expected or the last beta's $((expected + last_beta))"
      echo "FAIL: $name needs versionCode $expected, got $code"
      ok=1
    fi
  elif [[ $name =~ ^([0-9]+)\.([0-9]+)\.0-beta\.([0-9]+)$ ]]; then
    major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} beta=${BASH_REMATCH[3]}
    expected=$((major * 10000 + minor * 100 - 10 + beta))
    if ! [[ $beta =~ ^[1-9]$ ]]; then
      echo "FAIL: prerelease $name: N must be 1..9 (ADR 0030)"
      ok=1
    elif [ "$code" -ne "$expected" ]; then
      echo "FAIL: prerelease $name needs versionCode $expected, got $code"
      ok=1
    else
      echo "ok: prerelease $name = $expected"
    fi
  elif [[ $name =~ ^([0-9]+)\.([0-9]+)(\.([0-9]+))?$ ]]; then
    major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} patch=${BASH_REMATCH[4]:-0}
    expected=$((major * 10000 + minor * 100 + patch))
    if [ "$patch" -gt 99 ]; then
      echo "FAIL: release $name: patch above 99"
      ok=1
    elif [ "$code" -ne "$expected" ]; then
      echo "FAIL: release $name needs versionCode $expected, got $code"
      ok=1
    else
      echo "ok: release $name = $expected"
    fi
  else
    echo "FAIL: versionName '$name' is none of M.m-SNAPSHOT, M.m.0-beta.N, M.m[.p] (ADR 0030)"
    ok=1
  fi

  git fetch -q origin release/2.2 2> /dev/null || echo "note: fetch of origin release/2.2 failed, using the local ref"
  if ! rel=$(git show origin/release/2.2:app/build.gradle.kts 2> /dev/null); then
    echo "FAIL: no origin/release/2.2"
    return 1
  fi
  rel_code=$(gradle_value "$rel" versionCode)
  echo "origin/release/2.2: versionName=$(gradle_value "$rel" versionName) versionCode=$rel_code"
  if ! [[ $rel_code =~ ^[0-9]+$ ]] || [ "$rel_code" -ge 100 ]; then
    echo "FAIL: origin/release/2.2 versionCode must stay below 100"
    ok=1
  elif [ "$rel_code" -ge "$code" ]; then
    echo "FAIL: head versionCode $code does not install over release/2.2's $rel_code"
    ok=1
  fi
  return "$ok"
}

# ---- 2 full ----------------------------------------------------------------------------------------------

stage_full() {
  "$verify_sh" full
}

# ---- 3 release -------------------------------------------------------------------------------------------

stage_release() {
  local stamp apk bundle rc=0
  stamp="$out_dir/.stage3-start"
  : > "$stamp"
  # shellcheck disable=SC2086 # $central is one optional word
  "$verify_sh" run -- :app:assembleGithubRelease :app:bundlePlayRelease \
    :app:processGithubReleaseManifest :app:processPlayReleaseManifest $central || return 1
  apk=app/build/outputs/apk/github/release/app-github-release-unsigned.apk
  bundle=app/build/outputs/bundle/playRelease/app-play-release.aab
  if [ ! -f "$apk" ] || [ "$apk" -ot "$stamp" ]; then
    echo "FAIL: no fresh unsigned github release APK at $apk"
    rc=1
  fi
  if [ ! -f "$bundle" ] || [ "$bundle" -ot "$stamp" ]; then
    echo "FAIL: no fresh play bundle at $bundle"
    rc=1
  elif unzip -l "$bundle" | grep -Eq 'META-INF/[^ ]*\.(RSA|DSA|EC|SF)$'; then
    echo "FAIL: $bundle is signed; release outputs stay unsigned"
    rc=1
  fi
  [ "$rc" -eq 0 ] || return 1
  echo "ok: unsigned $apk and $bundle"
  SKIP_BUILD=1 "$root/scripts/check-play-policy.sh"
}

# ---- 4 ui ------------------------------------------------------------------------------------------------

stage_ui() {
  local stamp results
  stamp="$out_dir/.stage4-start"
  : > "$stamp"
  "$verify_sh" ui all || return 1
  results=$(find app/build/outputs/androidTest-results -name '*.xml' -newer "$stamp" -print0 2> /dev/null |
    xargs -0 grep -l 'UpgradeFrom22Test' 2> /dev/null | head -n 1)
  if [ -z "$results" ]; then
    echo "FAIL: no fresh instrumentation result mentions UpgradeFrom22Test"
    return 1
  fi
  echo "ok: UpgradeFrom22Test ran ($results)"
}

# ---- 5 upgrade -------------------------------------------------------------------------------------------

dev() { "$adb" -s "$DEVICE_SERIAL" "$@"; }
dev_sh() { # keeps the remote exit status (adb shell protocol v2)
  local out rc
  out=$("$adb" -s "$DEVICE_SERIAL" shell "$@")
  rc=$?
  [ -z "$out" ] || printf '%s\n' "$out" | tr -d '\r'
  return "$rc"
}

# Device time now, in the format `logcat -T` takes; adb shell joins its arguments into one remote command line, so
# arguments with spaces are quoted for it.
device_now() { dev_sh "date '+%m-%d %H:%M:%S.000'"; }

# Passes when no FATAL EXCEPTION of the app was logged since <since> and the app's process is alive.
started_cleanly() { # since
  local log pid ok=0
  if ! log=$(dev_sh "logcat -d -b crash,main -T '$1'"); then
    echo "FAIL: logcat since $1"
    ok=1
  elif printf '%s\n' "$log" | grep -A 3 'FATAL EXCEPTION' | grep -Eq "Process: $PACKAGE[,:]"; then
    echo "FAIL: FATAL EXCEPTION since the start:"
    printf '%s\n' "$log" | grep -A 30 'FATAL EXCEPTION'
    ok=1
  else
    echo "ok: no FATAL EXCEPTION for $PACKAGE since the start"
  fi
  pid=$(dev_sh pidof "$PACKAGE")
  if [ -z "$pid" ]; then
    echo "FAIL: $PACKAGE is not running"
    ok=1
  else
    echo "ok: $PACKAGE alive (pid $pid)"
  fi
  return "$ok"
}

# Prints the path of the v2.2 githubDebug APK, building it once in a detached worktree at the tag.
v22_apk() {
  local tag_sha cache work slot java17
  tag_sha=$(git rev-parse "$V22_TAG^{commit}" 2> /dev/null) || { echo "FAIL: no tag $V22_TAG" >&2; return 1; }
  cache="$wt/run/apk/$V22_TAG-${tag_sha:0:12}-githubDebug.apk"
  if [ -f "$cache" ]; then
    printf '%s\n' "$cache"
    return 0
  fi
  work="$wt/android/ship-v22"
  if [ ! -e "$work" ]; then
    git worktree add --detach "$work" "$tag_sha" >&2 || return 1
  elif [ "$(git -C "$work" rev-parse HEAD 2> /dev/null)" != "$tag_sha" ]; then
    echo "FAIL: $work is not at $V22_TAG ($tag_sha); remove it with git worktree remove" >&2
    return 1
  fi
  slot=${ITMO_SLOT_SH:-$wt/bin/slot.sh}
  [ -x "$slot" ] || slot="$root/scripts/slot.sh"
  java17=$(/usr/libexec/java_home -v 17 2> /dev/null) || { echo "FAIL: no JDK 17 for the $V22_TAG build" >&2; return 1; }
  echo "building $V22_TAG githubDebug in $work (JDK 17)" >&2
  (
    cd "$work" &&
      JAVA_HOME=$java17 "$slot" android -- \
        bash -c 'exec ./gradlew ${ITMO_MAX_WORKERS:+--max-workers=$ITMO_MAX_WORKERS} :app:assembleGithubDebug'
  ) >&2 || return 1
  mkdir -p "$wt/run/apk" || return 1
  cp "$work/app/build/outputs/apk/github/debug/app-github-debug.apk" "$cache.tmp" && mv "$cache.tmp" "$cache" || return 1
  printf '%s\n' "$cache"
}

# Checks that the app's data directory now holds the 2.2 assets byte for byte.
seeded_sums() {
  local want got
  want=$(cd "$assets" && find files no_backup cache -type f | sort | while IFS= read -r f; do
    printf '%s  %s\n' "$(shasum -a 256 "$f" | cut -d ' ' -f 1)" "$f"
  done)
  # shellcheck disable=SC2046 # one word per asset path, none has spaces
  got=$(dev_sh run-as "$PACKAGE" sha256sum $(cd "$assets" && find files no_backup cache -type f | sort)) || {
    echo "FAIL: seeded files missing in the app's data directory"
    return 1
  }
  if [ "$want" != "$got" ]; then
    echo "FAIL: seeded files differ from the assets"
    diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")
    return 1
  fi
  echo "ok: $(printf '%s\n' "$want" | wc -l | tr -d ' ') asset files seeded byte for byte"
}

stage_upgrade() {
  local old_apk head_apk tmp=/data/local/tmp/ship-upgrade-2.2 since rel dir found f ok=0
  old_apk=$(v22_apk) || return 1
  echo "v2.2 APK: $old_apk"
  "$verify_sh" run -- :app:assembleGithubDebug || return 1
  head_apk="$root/app/build/outputs/apk/github/debug/app-github-debug.apk"
  [ -f "$head_apk" ] || { echo "FAIL: no head githubDebug APK at $head_apk"; return 1; }

  echo "install $V22_TAG on $DEVICE_SERIAL (fresh)"
  dev uninstall "$PACKAGE" > /dev/null 2>&1
  dev install "$old_apk" || return 1
  dev_sh monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || return 1
  sleep 8
  # DataStore keeps its file open while the process lives.
  dev_sh am force-stop "$PACKAGE"

  echo "seed the 2.2 data directory"
  dev_sh rm -rf "$tmp"
  dev_sh mkdir -p "$tmp" || return 1
  for dir in files no_backup cache; do
    dev push "$assets/$dir" "$tmp/" > /dev/null || return 1
  done
  dev_sh chmod -R a+rX "$tmp"
  dev_sh run-as "$PACKAGE" sh -c "'mkdir -p files no_backup cache && cp -R $tmp/files/. files/ && cp -R $tmp/no_backup/. no_backup/ && cp -R $tmp/cache/. cache/'" ||
    { echo "FAIL: run-as copy into the app's data directory"; return 1; }
  dev_sh rm -rf "$tmp"
  seeded_sums || return 1

  echo "install -r the head githubDebug ($sha) and start $HEAD_ACTIVITY"
  dev install -r "$head_apk" || return 1
  since=$(device_now) || return 1
  dev_sh am start -W -n "$PACKAGE/$HEAD_ACTIVITY" || return 1
  sleep "$SMOKE_WAIT_SECONDS"

  started_cleanly "$since" || ok=1

  # Durable 2.2 files stay where 2.2 left them, or a store migrated them and its new file carries a format marker.
  # Caches (cache/) may be read or dropped and are not checked.
  found=$(dev_sh run-as "$PACKAGE" find files no_backup -type f)
  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    if printf '%s\n' "$found" | grep -qxF "$rel"; then
      echo "ok: $rel present"
      continue
    fi
    dir=$(basename "$(dirname "$rel")")
    f=""
    for f in $(printf '%s\n' "$found" | grep -E "(^|/)$dir/[^/]+$"); do
      dev_sh run-as "$PACKAGE" grep -q '"format"' "$f" && break
      f=""
    done
    if [ -n "$f" ]; then
      echo "ok: $rel migrated to $f (format marker)"
    else
      echo "FAIL: $rel is gone and no file in a '$dir' directory carries a format marker"
      ok=1
    fi
  done <<EOF
$(cd "$assets" && find files no_backup -type f | sort)
EOF
  return "$ok"
}

# ---- 6 shrunk --------------------------------------------------------------------------------------------

# The sign-in button's text, read from its resource so the check follows the copy.
sign_in_text() {
  sed -n 's:.*<string name="auth_login_itmo_id">\(.*\)</string>.*:\1:p' "$sign_in_strings" | head -n 1
}

# Polls the window hierarchy for <text> until SMOKE_WAIT_SECONDS have passed.
wait_for_text() { # text
  local deadline=$(($(date +%s) + SMOKE_WAIT_SECONDS))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if dev_sh "uiautomator dump /sdcard/ship-window.xml > /dev/null 2>&1 && cat /sdcard/ship-window.xml" 2> /dev/null |
      grep -qF "text=\"$1\""; then
      return 0
    fi
    sleep 2
  done
  return 1
}

stage_shrunk() {
  local text flavor apk since ok=0
  text=$(sign_in_text)
  [ -n "$text" ] || { echo "FAIL: no auth_login_itmo_id in $sign_in_strings"; return 1; }
  "$verify_sh" run -- :app:assembleGithubMinifiedSmoke :app:assemblePlayMinifiedSmoke || return 1
  for flavor in github play; do
    apk="$root/app/build/outputs/apk/$flavor/minifiedSmoke/app-$flavor-minifiedSmoke.apk"
    [ -f "$apk" ] || { echo "FAIL: no $flavor minifiedSmoke APK at $apk"; ok=1; continue; }
    echo "install $flavor minifiedSmoke ($sha) fresh and start $HEAD_ACTIVITY"
    dev uninstall "$PACKAGE" > /dev/null 2>&1
    dev install "$apk" || { ok=1; continue; }
    since=$(device_now) || return 1
    dev_sh am start -W -n "$PACKAGE/$HEAD_ACTIVITY" || { ok=1; continue; }
    if wait_for_text "$text"; then
      echo "ok: $flavor: sign-in screen (\"$text\") shown"
      sleep "$SHRUNK_SETTLE_SECONDS"
    else
      echo "FAIL: $flavor: no sign-in screen (\"$text\") within ${SMOKE_WAIT_SECONDS}s"
      ok=1
    fi
    started_cleanly "$since" || ok=1
  done
  dev_sh rm -f /sdcard/ship-window.xml
  return "$ok"
}

# ---- main ------------------------------------------------------------------------------------------------

write_summary
run_stage 1 version stage_version
if [ "$source" = ci ]; then
  # The local stages first: CI usually finishes while the release build runs.
  run_stage 3 release stage_release
  [ -n "$failed" ] && [ -z "$keep_going" ] || ci_collect
  run_stage 2 full stage_ci_full
  run_stage 4 ui stage_ci_ui
  run_stage 5 upgrade stage_ci_upgrade
  run_stage 6 shrunk stage_ci_shrunk
else
  run_stage 2 full stage_full
  run_stage 3 release stage_release
  run_stage 4 ui stage_ui
  run_stage 5 upgrade stage_upgrade
  run_stage 6 shrunk stage_shrunk
fi

note "summary: $summary"
cat "$summary" >&2
verdict=PASS
[ -z "$failed" ] || verdict=FAIL
printf 'VERIFY %s %s %s %ss %s\n' "$REPO_LETTER" "$mode" "$verdict" "$(($(date +%s) - started))" "$sha""$dirty"
[ -z "$failed" ]
