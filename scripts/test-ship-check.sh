#!/bin/bash
# test-ship-check.sh    self-test of the CI source of scripts/ship-check.sh, no Gradle, no device, no network
#
# Copies ship-check.sh into a scratch repository with a stub scripts/verify.sh and puts a `gh` stub first on PATH
# that serves canned `run list`, `run view` and `run download` answers for the scratch commit. Cases:
#   1. all checks green and the ship artifact's stages 5 and 6 PASS: exit 0, every row names its CI run, and an
#      older red run of the same SHA does not count
#   2. android-ui red: stage 4 FAIL, the later stages NOT RUN, exit 1
#   3. android-ui never reported while every run finished: stage 4 FAIL as missing
#   4. verify-quick still running without --wait: stage 2 FAIL as not finished
#   5. --wait polls until the running check finishes, then passes
#   6. android-ship red with stage 5 PASS and 6 FAIL in its artifact (--keep-going): only stage 6 fails
#   7. android-ship green but no artifact: stages 5 and 6 FAIL
#   8. a dirty worktree is refused (exit 2) while CI stages are selected
#   9. --local never calls gh; 10. gh not signed in falls back to the local source
#
# Portable: macOS /bin/bash 3.2 with BSD tools and GNU tools. Exit code: 0 pass, 1 fail, 2 environment error.

set -u

me=test-ship-check.sh

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) ||
  { echo "$me: cannot resolve the script directory" >&2; exit 2; }
ship="$script_dir/ship-check.sh"
[ -f "$ship" ] || { echo "$me: no $ship" >&2; exit 2; }
command -v python3 > /dev/null 2>&1 || { echo "$me: python3 is not on PATH" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/ship-check.XXXXXX") || { echo "$me: no temporary directory" >&2; exit 2; }
tmp=$(cd "$tmp" && pwd -P)
trap 'rm -rf "$tmp"' EXIT

failures=0
fail() { echo "$me: FAIL: $*" >&2; failures=$((failures + 1)); }

# ---- scratch repository ----------------------------------------------------------------------------------

repo="$tmp/repo"
mkdir -p "$repo/scripts" "$tmp/bin" || exit 2
cp "$ship" "$repo/scripts/ship-check.sh" || exit 2
printf '#!/bin/sh\necho "verify.sh stub: $*"\n' > "$repo/scripts/verify.sh"
chmod +x "$repo/scripts/ship-check.sh" "$repo/scripts/verify.sh"
export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL="$tmp/gitconfig"
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_COMMON_DIR GIT_OBJECT_DIRECTORY
(
  cd "$repo" && git init -q && git add -A &&
    git -c user.name=test -c user.email=test@example.invalid -c commit.gpgsign=false commit -q -m scratch
) || { echo "$me: cannot create the scratch repository" >&2; exit 2; }
head=$(git -C "$repo" rev-parse HEAD)
sha=${head:0:7}

# The stub answers from $SHIP_FAKE: runs.json, run-<id>.json and artifact-<id>/ (the artifact ship-<sha7>).
# Every `run list` call starts a new poll; files under poll<k>/ override the base ones for poll k. Calls are
# appended to calls.log; auth-fail makes `gh auth status` fail.
cat > "$tmp/bin/gh" << 'STUB'
#!/bin/bash
fake=$SHIP_FAKE
printf '%s\n' "$*" >> "$fake/calls.log"
pick() { # file -> path for the current poll
  local k
  k=$(cat "$fake/poll" 2> /dev/null || echo 0)
  if [ -f "$fake/poll$k/$1" ]; then echo "$fake/poll$k/$1"; else echo "$fake/$1"; fi
}
case "$1 $2" in
  "auth status") [ ! -e "$fake/auth-fail" ] ;;
  "run list")
    echo $(($(cat "$fake/poll" 2> /dev/null || echo 0) + 1)) > "$fake/poll"
    cat "$(pick runs.json)"
    ;;
  "run view") cat "$(pick "run-$3.json")" ;;
  "run download")
    [ "$4 $5" = "-n ship-$SHIP_SHA" ] && [ "$6" = -D ] && [ -d "$fake/artifact-$3" ] ||
      { echo "no valid artifacts found to download" >&2; exit 1; }
    mkdir -p "$7" && cp -R "$fake/artifact-$3/." "$7/"
    ;;
  *) echo "gh stub: unexpected: $*" >&2; exit 1 ;;
esac
STUB
chmod +x "$tmp/bin/gh"
export SHIP_SHA=$sha

# ---- fixtures ----------------------------------------------------------------------------------------------

URL=https://github.com/example/app/actions/runs

run() { # id status created-minute -> one run of `gh run list --json`
  printf '{"databaseId": %s, "status": "%s", "createdAt": "2026-10-10T10:%s:00Z", "url": "%s/%s"}' \
    "$1" "$2" "$3" "$URL" "$1"
}

job() { # run-id name status conclusion -> one job of `gh run view --json jobs`, 7 min 30 s long when done
  printf '{"name": "%s", "status": "%s", "conclusion": "%s", "startedAt": "2026-10-10T10:00:00Z",' "$2" "$3" "$4"
  printf ' "completedAt": "2026-10-10T10:07:30Z", "url": "%s/%s/job/%s1"}' "$URL" "$1" "$1"
}

jobs() { # file job-json... -> run-<id>.json
  local file=$1 sep="" j
  shift
  {
    printf '{"jobs": ['
    for j in "$@"; do
      printf '%s%s' "$sep" "$j"
      sep=", "
    done
    printf ']}\n'
  } > "$file"
}

artifact() { # dir stage5 stage6 -> artifact with summary.md in the ship-check format
  mkdir -p "$1"
  {
    printf '# Ship check %s\n\n| Stage | Result | Time | Source | Log |\n|---|---|---|---|---|\n' "$sha"
    printf '| 5 upgrade | %s | 60s | local | `stage5-upgrade.log` |\n' "$2"
    printf '| 6 shrunk | %s | 90s | local | `stage6-shrunk.log` |\n' "$3"
  } > "$1/summary.md"
}

# green <dir>: verify-quick (run 11, with an older red run 10), android-ui (run 12), android-ship (run 13).
green() {
  local f=$1
  mkdir -p "$f"
  printf '[%s, %s, %s, %s]\n' "$(run 10 completed 00)" "$(run 11 completed 05)" "$(run 12 completed 05)" \
    "$(run 13 completed 06)" > "$f/runs.json"
  jobs "$f/run-10.json" "$(job 10 verify-quick completed failure)"
  jobs "$f/run-11.json" "$(job 11 verify-quick completed success)" "$(job 11 ios-check completed success)"
  jobs "$f/run-12.json" "$(job 12 android-ui completed success)"
  jobs "$f/run-13.json" "$(job 13 android-ship completed success)"
  artifact "$f/artifact-13" PASS PASS
}

# check <case> <args...>: runs ship-check from the scratch repository into $tmp/<case>; sets rc, out, summ.
check() {
  local name=$1
  shift
  export SHIP_FAKE="$tmp/$name/fake"
  mkdir -p "$SHIP_FAKE"
  : > "$SHIP_FAKE/calls.log"
  out=$(cd "$repo" && PATH="$tmp/bin:$PATH" ITMO_WT="$tmp/$name/wt" ITMO_SHIP_POLL_SECONDS=0 \
    scripts/ship-check.sh "$@" 2>&1)
  rc=$?
  summ="$tmp/$name/wt/run/ship/$sha/summary.md"
}

expect_rc() { [ "$rc" = "$2" ] || fail "$1: exit $rc, want $2; output:"$'\n'"$out"; }
expect_row() { # case regex
  grep -Eq "$2" "$summ" 2> /dev/null || fail "$1: no summary row matching '$2' in:"$'\n'"$(cat "$summ" 2> /dev/null)"
}

CI='\| CI run https://github.com/example/app/actions/runs/'

# ---- 1. all green --------------------------------------------------------------------------------------------

green "$tmp/green/fake"
check green --only 2,4,5,6
expect_rc green 0
expect_row green '^\| Stage \| Result \| Time \| Source \| Log \|$'
expect_row green "^\\| 2 full \\| PASS \\| 450s $CI""11/job/111 \\|"
expect_row green "^\\| 4 ui \\| PASS \\| 450s $CI""12/job/121 \\|"
expect_row green "^\\| 5 upgrade \\| PASS \\| 450s $CI""13/job/131 \\|"
expect_row green "^\\| 6 shrunk \\| PASS \\| 450s $CI""13/job/131 \\|"
printf '%s\n' "$out" | tail -n 1 | grep -Eq "^VERIFY A ship PASS [0-9]+s $sha\$" ||
  fail "green: last line is not the VERIFY line: $(printf '%s\n' "$out" | tail -n 1)"

# ---- 2. android-ui red ---------------------------------------------------------------------------------------

green "$tmp/ui-red/fake"
jobs "$tmp/ui-red/fake/run-12.json" "$(job 12 android-ui completed failure)"
check ui-red --only 2,4,5,6
expect_rc ui-red 1
expect_row ui-red '^\| 2 full \| PASS \|'
expect_row ui-red "^\\| 4 ui \\| FAIL \\| 450s $CI""12/job/121 \\|"
expect_row ui-red '^\| 5 upgrade \| NOT RUN \|'
grep -q "CI check android-ui concluded 'failure'" "$tmp/ui-red/wt/run/ship/$sha/stage4-ui.log" ||
  fail "ui-red: stage 4 log does not name the conclusion"
printf '%s\n' "$out" | tail -n 1 | grep -q "^VERIFY A ship FAIL " || fail "ui-red: VERIFY line is not FAIL"

# ---- 3. android-ui missing -----------------------------------------------------------------------------------

green "$tmp/ui-missing/fake"
jobs "$tmp/ui-missing/fake/run-12.json" "$(job 12 something-else completed success)"
check ui-missing --only 4
expect_rc ui-missing 1
expect_row ui-missing '^\| 4 ui \| FAIL \| - \| CI: android-ui not reported \|'
grep -q "no CI check android-ui" "$tmp/ui-missing/wt/run/ship/$sha/stage4-ui.log" ||
  fail "ui-missing: stage 4 log does not say the check is missing"

# ---- 4. pending without --wait -------------------------------------------------------------------------------

green "$tmp/pending/fake"
printf '[%s, %s]\n' "$(run 11 in_progress 05)" "$(run 13 completed 06)" > "$tmp/pending/fake/runs.json"
jobs "$tmp/pending/fake/run-11.json" "$(job 11 verify-quick in_progress "")"
check pending --only 2
expect_rc pending 1
expect_row pending '^\| 2 full \| FAIL \| - \|'
grep -q "has not finished" "$tmp/pending/wt/run/ship/$sha/stage2-full.log" ||
  fail "pending: stage 2 log does not say the check is still running"

# ---- 5. --wait -----------------------------------------------------------------------------------------------

green "$tmp/wait/fake"
mkdir -p "$tmp/wait/fake/poll1"
printf '[%s]\n' "$(run 11 in_progress 05)" > "$tmp/wait/fake/poll1/runs.json"
jobs "$tmp/wait/fake/poll1/run-11.json" "$(job 11 verify-quick in_progress "")" "$(job 11 ios-check queued "")"
check wait --only 2,4,5,6 --wait
expect_rc wait 0
[ "$(grep -c '^run list' "$SHIP_FAKE/calls.log")" = 2 ] || fail "wait: want 2 polls, got calls:"$'\n'"$(cat "$SHIP_FAKE/calls.log")"
printf '%s\n' "$out" | grep -q "waiting for CI on $sha: verify-quick android-ui android-ship" ||
  fail "wait: no waiting note naming the pending checks"

# ---- 6. android-ship red in stage 6 only ---------------------------------------------------------------------

green "$tmp/shrunk-red/fake"
jobs "$tmp/shrunk-red/fake/run-13.json" "$(job 13 android-ship completed failure)"
artifact "$tmp/shrunk-red/fake/artifact-13" PASS FAIL
check shrunk-red --only 5,6 --keep-going
expect_rc shrunk-red 1
expect_row shrunk-red '^\| 5 upgrade \| PASS \|'
expect_row shrunk-red '^\| 6 shrunk \| FAIL \|'

# ---- 7. no artifact ------------------------------------------------------------------------------------------

green "$tmp/no-artifact/fake"
rm -rf "$tmp/no-artifact/fake/artifact-13"
check no-artifact --only 5,6 --keep-going
expect_rc no-artifact 1
expect_row no-artifact '^\| 5 upgrade \| FAIL \|'
expect_row no-artifact '^\| 6 shrunk \| FAIL \|'
grep -q "no summary.md in the artifact ship-$sha" "$tmp/no-artifact/wt/run/ship/$sha/stage5-upgrade.log" ||
  fail "no-artifact: stage 5 log does not name the missing artifact"

# ---- 8. dirty worktree ---------------------------------------------------------------------------------------

green "$tmp/dirty/fake"
echo scratch > "$repo/untracked.txt"
check dirty --only 2
expect_rc dirty 2
printf '%s\n' "$out" | grep -q "uncommitted changes" || fail "dirty: no refusal message: $out"
rm -f "$repo/untracked.txt"

# ---- 9. --local ----------------------------------------------------------------------------------------------

green "$tmp/local/fake"
check local --local --only 2
expect_rc local 0
expect_row local '^\| 2 full \| PASS \| [0-9]+s \| local \|'
[ ! -s "$SHIP_FAKE/calls.log" ] || fail "local: gh was called:"$'\n'"$(cat "$SHIP_FAKE/calls.log")"
grep -q "verify.sh stub: full" "$tmp/local/wt/run/ship/$sha/stage2-full.log" ||
  fail "local: stage 2 did not run verify.sh full"

# ---- 10. gh not signed in ------------------------------------------------------------------------------------

green "$tmp/no-auth/fake"
: > "$tmp/no-auth/fake/auth-fail"
check no-auth --only 2
expect_rc no-auth 0
expect_row no-auth '^\| 2 full \| PASS \| [0-9]+s \| local \|'
printf '%s\n' "$out" | grep -q "gh is missing or not signed in" || fail "no-auth: no fallback note"

if [ "$failures" -ne 0 ]; then
  echo "$me: $failures failure(s)" >&2
  exit 1
fi
echo "$me: PASS (10 cases)"
