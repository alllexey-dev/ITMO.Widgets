#!/bin/bash
# test-ui-report.sh    self-test of scripts/ui-report.py, no Gradle and no device
#
# Feeds seeded TestRunner logs and Gradle output to ui-report.py over a fixture root whose ShellSuite has two
# members, and checks the exit code and the report lines:
#   1. a suite member that fails inside the suite and passes standalone afterwards fails the report, named with
#      the suite, "run 1 of 2" and the shell its message names; the later pass does not hide it
#   2. the reverse (standalone fails, the suite run passes) names "standalone"
#   3. every run passing, duplicates included, exits 0
#   4. a failure before the last marker belongs to an older run and is ignored
#   5. a run that started and never finished (the instrumentation crashed) fails
#   6. Gradle's FAILED line counts when the device log is missing
#   7. the repository's own ShellSuite parses to its members
#
# Portable: macOS /bin/bash 3.2 with BSD tools and GNU tools. Exit code: 0 pass, 1 fail, 2 environment error.

set -u

me=test-ui-report.sh

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) ||
  { echo "$me: cannot resolve the script directory" >&2; exit 2; }
repo=$(cd "$script_dir/.." && pwd -P)
report="$script_dir/ui-report.py"
[ -f "$report" ] || { echo "$me: no $report" >&2; exit 2; }
command -v python3 > /dev/null 2>&1 || { echo "$me: python3 is not on PATH" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/ui-report.XXXXXX") || { echo "$me: no temporary directory" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT

failures=0
fail() { echo "$me: FAIL: $*" >&2; failures=$((failures + 1)); }

# ---- fixture root --------------------------------------------------------------------------------------------

src="$tmp/root/app/src/androidTest/java/dev/example"
mkdir -p "$src/shell" "$src/qr" || exit 2
cat > "$src/shell/ShellSuite.kt" << 'EOF'
package dev.example.shell

import dev.example.qr.QrTileFlowTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    QrTileFlowTest::class,
    TabSwipeTest::class,
)
class ShellSuite
EOF
printf 'package dev.example.qr\n\nclass QrTileFlowTest\n' > "$src/qr/QrTileFlowTest.kt"
printf 'package dev.example.shell\n\nclass TabSwipeTest {\n    val shell = ShellModeRule()\n}\n' > "$src/shell/TabSwipeTest.kt"

QR=dev.example.qr.QrTileFlowTest
TAB=dev.example.shell.TabSwipeTest
OTHER=dev.example.AOtherTest

# pass <method> <class>, failrun <method> <class> <message>: TestRunner lines as LogRunListener writes them.
pass() { printf 'started: %s(%s)\nfinished: %s(%s)\n' "$1" "$2" "$1" "$2"; }
failrun() {
  printf 'started: %s(%s)\nfailed: %s(%s)\n----- begin exception -----\n%s\n\tat %s.%s(X.kt:1)\n----- end exception -----\nfinished: %s(%s)\n' \
    "$1" "$2" "$1" "$2" "$3" "$2" "$1" "$1" "$2"
}
# The order `ui all` uses: classes by name, the suite at its own name.
suite_run() { # qr-pass-or-fail-message tab-pass-or-fail-message
  if [ "$1" = pass ]; then pass passIntent "$QR"; else failrun passIntent "$QR" "$1"; fi
  pass tileClick "$QR"
  if [ "$2" = pass ]; then pass swipes "$TAB"; else failrun swipes "$TAB" "$2"; fi
}
standalone_run() { # qr-pass-or-fail-message
  if [ "$1" = pass ]; then pass passIntent "$QR"; else failrun passIntent "$QR" "$1"; fi
  pass tileClick "$QR"
}

run_report() { # name args... -> $tmp/<name>.out, $tmp/<name>.rc
  local name=$1
  shift
  python3 -I "$report" --root "$tmp/root" "$@" > "$tmp/$name.out" 2>&1
  echo $? > "$tmp/$name.rc"
}
expect_rc() { # name rc
  local got
  got=$(cat "$tmp/$1.rc")
  [ "$got" = "$2" ] || { fail "$1: exit $got, expected $2"; sed 's/^/    /' "$tmp/$1.out" >&2; }
}
expect_line() { # name fixed-text
  grep -qF -- "$2" "$tmp/$1.out" || { fail "$1: no line with '$2'"; sed 's/^/    /' "$tmp/$1.out" >&2; }
}
expect_no_line() { # name fixed-text
  ! grep -qF -- "$2" "$tmp/$1.out" || { fail "$1: unexpected '$2'"; sed 's/^/    /' "$tmp/$1.out" >&2; }
}

# ---- 1. fail in the suite, pass standalone afterwards ----------------------------------------------------------

{
  echo 'some older line'
  echo 'itmo-verify-ui-start 1'
  echo 'run started: 6 tests'
  pass works "$OTHER"
  suite_run 'java.lang.AssertionError: the tile did not open the pass' pass
  standalone_run pass
  echo 'run finished: 6 tests, 1 failed, 0 ignored'
} > "$tmp/dup.log"
printf '%s > passIntent[pool(AVD) - 15] \033[31mFAILED \033[0m\n\tjava.lang.AssertionError\nFinished 4 tests on pool(AVD) - 15\n' \
  "$QR" > "$tmp/dup.gradle"
run_report dup --logcat "$tmp/dup.log" --gradle "$tmp/dup.gradle"
expect_rc dup 1
expect_line dup "ui-report: 6 runs of 4 tests (2 ran more than once)"
expect_line dup "ui-report: FAILED: 1 run of 1 test"
expect_line dup "FAIL $QR#passIntent [in ShellSuite; run 1 of 2; default shell] java.lang.AssertionError: the tile did not open the pass"
expect_line dup "note: $QR#passIntent also passed (standalone)"
expect_no_line dup "from Gradle's output"

# The same seeded duplicate with a ShellModeRule member failing in one shell.
{
  echo 'itmo-verify-ui-start 1'
  suite_run pass 'java.lang.AssertionError: swipes in the NAV3 shell: The shell did not settle'
  pass swipes "$TAB"
} > "$tmp/shell.log"
run_report shell --logcat "$tmp/shell.log"
expect_rc shell 1
expect_line shell "FAIL $TAB#swipes [in ShellSuite; run 1 of 2; shell NAV3]"

# ---- 2. fail standalone, pass in the suite ------------------------------------------------------------------

{
  echo 'itmo-verify-ui-start 1'
  standalone_run 'java.lang.IllegalStateException: no tile'
  suite_run pass pass
} > "$tmp/reverse.log"
run_report reverse --logcat "$tmp/reverse.log"
expect_rc reverse 1
expect_line reverse "FAIL $QR#passIntent [standalone; run 1 of 2; default shell] java.lang.IllegalStateException: no tile"
expect_line reverse "also passed (in ShellSuite)"

# ---- 3. everything passes ----------------------------------------------------------------------------------

{
  echo 'itmo-verify-ui-start 1'
  suite_run pass pass
  standalone_run pass
  pass swipes "$TAB"
} > "$tmp/green.log"
printf 'Finished 3 tests on pool(AVD) - 15\nBUILD SUCCESSFUL\n' > "$tmp/green.gradle"
run_report green --logcat "$tmp/green.log" --gradle "$tmp/green.gradle"
expect_rc green 0
expect_line green "ui-report: 6 runs of 3 tests (3 ran more than once)"
expect_line green "ui-report: no failed run"

# ---- 4. an older failure before the last marker ------------------------------------------------------------

{
  echo 'itmo-verify-ui-start 1'
  failrun passIntent "$QR" 'java.lang.AssertionError: from yesterday'
  echo 'itmo-verify-ui-start 2'
  standalone_run pass
} > "$tmp/old.log"
run_report old --logcat "$tmp/old.log"
expect_rc old 0
expect_line old "ui-report: 2 runs of 2 tests"

# ---- 5. a run that never finished ---------------------------------------------------------------------------

{
  echo 'itmo-verify-ui-start 1'
  pass passIntent "$QR"
  echo "started: tileClick($QR)"
} > "$tmp/crash.log"
run_report crash --logcat "$tmp/crash.log"
expect_rc crash 1
expect_line crash "FAIL $QR#tileClick [standalone; default shell] started but never finished"

# ---- 6. Gradle's output only ---------------------------------------------------------------------------------

printf '%s > swipes[ciDevice(AVD) - 34] FAILED\n' "$TAB" > "$tmp/gradle-only.gradle"
run_report gradle-only --gradle "$tmp/gradle-only.gradle"
expect_rc gradle-only 1
expect_line gradle-only "FAIL $TAB#swipes [run not in the device log; shell not named] (from Gradle's output"

# ---- 7. the repository's ShellSuite ----------------------------------------------------------------------------

if [ -f "$repo/app/src/androidTest/java/dev/alllexey/itmowidgets/app/shell/ShellSuite.kt" ]; then
  python3 -I "$report" --root "$repo" --list-suites > "$tmp/suites.out" 2>&1 || fail "--list-suites exited $?"
  expect_line suites "dev.alllexey.itmowidgets.app.shell.ShellSuite: dev.alllexey.itmowidgets.app.MainActivityDeepLinkTest,"
  expect_line suites "dev.alllexey.itmowidgets.feature.qr.QrTileFlowTest"
fi

if [ "$failures" -gt 0 ]; then
  echo "$me: $failures check(s) failed" >&2
  exit 1
fi
echo "$me: ok (seeded duplicates, shells, markers, crashes, Gradle-only and the ShellSuite members)"
