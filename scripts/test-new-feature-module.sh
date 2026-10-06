#!/bin/bash
# test-new-feature-module.sh    self-test of scripts/new-feature-module.sh, no Gradle
#
# 1. Renders `qr` into a temporary root that has this repository's settings.gradle.kts and docs index, then diffs
#    the normalized build file, Koin module and test skeletons against shared/feature-qr. Only the differences
#    listed in EXPECTED_* below may remain; anything else (a template or the pilot drifting) fails.
# 2. A second run adds nothing and changes no byte; a file edited after the first run is never overwritten.
# 3. A new feature (`selftest`, not in settings.gradle.kts) also gets its doc stub, one docs/README.md line before
#    "## Decisions" and the printed include line; an existing one (`qr`) gets neither.
# 4. Bad names are refused with exit code 2.
#
# Normalizing: imports, comments and blank lines go; the pilot's screen names map to the generator's
# (QrCodeViewModel -> QrViewModel, QrCodeRepository -> QrRepository, QrPassScreen -> QrScreen). Test files are
# compared as skeletons: package, class annotations, class line and the TestMainDispatcher set-up; the test bodies
# are the module's own.
#
# Portable: macOS /bin/bash 3.2 with BSD tools and GNU tools. Exit code: 0 pass, 1 fail, 2 environment error.

set -u

me=test-new-feature-module.sh

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) ||
  { echo "$me: cannot resolve the script directory" >&2; exit 2; }
repo=$(cd "$script_dir/.." && pwd -P)
generator="$script_dir/new-feature-module.sh"
[ -x "$generator" ] || { echo "$me: $generator is not executable" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/new-feature-module.XXXXXX") || { echo "$me: no temporary directory" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT

failures=0
fail() { printf 'FAIL %s\n' "$*"; failures=$((failures + 1)); }
pass() { printf 'ok   %s\n' "$*"; }

pkg=src/commonMain/kotlin/dev/alllexey/itmowidgets/feature/qr
test_pkg=src/commonTest/kotlin/dev/alllexey/itmowidgets/feature/qr
host_pkg=src/androidHostTest/kotlin/dev/alllexey/itmowidgets/feature/qr

# Differences the pilot keeps on purpose, as `-<pilot line>` / `+<generated line>` after normalizing.
# The pilot exports its strings to :app until --retire (widget preview); a new module has no Android export.
EXPECTED_BUILD='-itmowidgetsStrings {
-androidExport("values/strings_qr.xml")
-}'
EXPECTED_MODULE=''
# The pilot also bridges its colour setting and the wall clock.
EXPECTED_MODULE_TEST='-fun theQrModuleResolvesWithTheBridgedRepositoryPreferencesAndClock() {
+fun theQrModuleResolvesWithTheBridgedTypes() {
-QrAppearancePreferences::class,
-Clock::class,'
EXPECTED_SCREENSHOT_TEST=''
# runCurrent() needs the opt-in; the pilot's test compiles with a warning instead.
EXPECTED_VIEWMODEL_TEST='+@OptIn(ExperimentalCoroutinesApi::class)'
EXPECTED_SCREEN_TEST=''

# Kotlin and Gradle files: no imports, comments or blank lines, indentation dropped, pilot names mapped.
normalize() {
  sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' "$1" |
    grep -v -e '^import ' -e '^//' -e '^/\*' -e '^\*' -e '^$' |
    sed -e 's/QrCodeViewModel/QrViewModel/g' -e 's/QrCodeRepository/QrRepository/g' -e 's/QrPassScreen/QrScreen/g'
}

# Test files: the skeleton every module test shares.
skeleton() {
  normalize "$1" |
    grep -E '^(package |@(OptIn|RunWith|BeforeTest|AfterTest)|class |private val main = |fun (setUp|tearDown)\(\))'
}

# compare <label> <normalizer> <pilot file> <generated file> <expected>
compare() {
  if [ ! -f "$3" ]; then fail "$1: no pilot file $3"; return; fi
  if [ ! -f "$4" ]; then fail "$1: not generated: $4"; return; fi
  "$2" "$3" > "$tmp/pilot.txt"
  "$2" "$4" > "$tmp/generated.txt"
  diff -U0 "$tmp/pilot.txt" "$tmp/generated.txt" | grep -E '^[-+]' | grep -vE '^(---|\+\+\+) ' > "$tmp/actual.txt"
  if [ -n "$5" ]; then printf '%s\n' "$5" > "$tmp/expected.txt"; else : > "$tmp/expected.txt"; fi
  if cmp -s "$tmp/expected.txt" "$tmp/actual.txt"; then
    pass "$1"
  else
    fail "$1: unexpected difference to the pilot (- pilot, + generated):"
    diff "$tmp/expected.txt" "$tmp/actual.txt" | sed 's/^/     /'
  fi
}

snapshot() {
  (cd "$1" && find . -type f | LC_ALL=C sort | while read -r file; do cksum "$file"; done)
}

# A root with what the generator reads: settings.gradle.kts, the docs index and the existing feature docs.
make_root() {
  mkdir -p "$1/docs/features" || exit 2
  cp "$repo/settings.gradle.kts" "$1/" || exit 2
  cp "$repo/docs/README.md" "$1/docs/" || exit 2
  cp "$repo"/docs/features/*.md "$1/docs/features/" || exit 2
}

# 1. qr against the pilot.
root="$tmp/qr-root"
make_root "$root"
pilot="$repo/shared/feature-qr"
out=$("$generator" --root "$root" qr 2>&1) || { fail "qr: the generator failed: $out"; }
gen="$root/shared/feature-qr"
compare "build file" normalize "$pilot/build.gradle.kts" "$gen/build.gradle.kts" "$EXPECTED_BUILD"
compare "Koin module" normalize "$pilot/$pkg/di/QrModule.kt" "$gen/$pkg/di/QrModule.kt" "$EXPECTED_MODULE"
compare "Koin module test" normalize "$pilot/$host_pkg/di/QrModuleTest.kt" "$gen/$host_pkg/di/QrModuleTest.kt" \
  "$EXPECTED_MODULE_TEST"
compare "screenshot test" normalize "$pilot/$host_pkg/QrScreenshotTest.kt" "$gen/$host_pkg/QrScreenshotTest.kt" \
  "$EXPECTED_SCREENSHOT_TEST"
compare "ViewModel test skeleton" skeleton "$pilot/$test_pkg/presentation/QrCodeViewModelTest.kt" \
  "$gen/$test_pkg/presentation/QrViewModelTest.kt" "$EXPECTED_VIEWMODEL_TEST"
compare "screen test skeleton" skeleton "$pilot/$test_pkg/ui/QrPassScreenTest.kt" \
  "$gen/$test_pkg/ui/QrScreenTest.kt" "$EXPECTED_SCREEN_TEST"

for dir in domain data presentation ui di; do
  if ls "$gen/$pkg/$dir/"*.kt > /dev/null 2>&1; then pass "qr: $dir stub"; else fail "qr: no $dir stub"; fi
done
for file in AGENTS.md src/commonMain/composeResources/values/strings_qr.xml; do
  if [ -f "$gen/$file" ]; then pass "qr: $file"; else fail "qr: no $file"; fi
done
lines=$(wc -l < "$gen/AGENTS.md" | tr -d ' ')
if [ "$lines" -le 40 ]; then pass "qr: AGENTS.md has $lines lines (at most 40)"; else
  fail "qr: AGENTS.md has $lines lines"; fi
sections=$(grep '^## ' "$gen/AGENTS.md" | tr '\n' '|')
if [ "$sections" = '## Owns|## Depends on|## Verify|## Hot files|## Docs|' ]; then
  pass "qr: AGENTS.md sections"
else
  fail "qr: AGENTS.md sections are $sections"
fi
if grep -qF '](../../docs/features/qr.md)' "$gen/AGENTS.md"; then pass "qr: AGENTS.md links the feature doc"; else
  fail "qr: AGENTS.md does not link docs/features/qr.md"; fi
if grep -rq '__name__\|__Name__\|__docs__' "$gen" "$root/docs"; then fail "qr: a placeholder survived"; else
  pass "qr: no placeholder left"; fi
if cmp -s "$repo/docs/README.md" "$root/docs/README.md" &&
  cmp -s "$repo/docs/features/qr.md" "$root/docs/features/qr.md"; then
  pass "qr: existing feature keeps its doc and the index"
else
  fail "qr: the generator touched docs of an existing feature"
fi
case "$out" in
  *include*) fail "qr: printed an include line for a declared module" ;;
  *) pass "qr: no include line for a declared module" ;;
esac

# 2. Idempotent and never overwrites.
before=$(snapshot "$root")
out=$("$generator" --root "$root" qr 2>&1)
after=$(snapshot "$root")
if [ "$before" = "$after" ]; then pass "second run changes nothing"; else fail "second run changed files"; fi
case "$out" in
  *"added 0,"*) pass "second run adds nothing" ;;
  *) fail "second run reports: $out" ;;
esac
echo "// edited" >> "$gen/$pkg/di/QrModule.kt"
rm "$gen/$pkg/ui/QrScreen.kt"
"$generator" --root "$root" qr > /dev/null 2>&1
if tail -n 1 "$gen/$pkg/di/QrModule.kt" | grep -q '^// edited$'; then pass "an existing file is kept"; else
  fail "an existing file was overwritten"; fi
if [ -f "$gen/$pkg/ui/QrScreen.kt" ]; then pass "a missing file is added back"; else
  fail "a missing file stayed missing"; fi

# 3. A new feature gets a doc stub, one index line and the include hint.
root="$tmp/selftest-root"
make_root "$root"
out=$("$generator" --root "$root" selftest 2>&1)
"$generator" --root "$root" selftest > /dev/null 2>&1
if [ -f "$root/docs/features/selftest.md" ]; then pass "selftest: doc stub"; else fail "selftest: no doc stub"; fi
count=$(grep -c '(features/selftest.md)' "$root/docs/README.md")
if [ "$count" -eq 1 ]; then pass "selftest: one index line"; else fail "selftest: $count index lines"; fi
at=$(grep -n '(features/selftest.md)' "$root/docs/README.md" | cut -d: -f1)
next=$(sed -n "$((at + 2))p" "$root/docs/README.md")
if [ "$(sed -n "$((at + 1))p" "$root/docs/README.md")" = "" ] && [ "$next" = "## Decisions" ]; then
  pass "selftest: the line closes the Features list"
else
  fail "selftest: the index line is not right before '## Decisions'"
fi
case "$out" in
  *'include(":shared:feature-selftest")'*) pass "selftest: include line printed" ;;
  *) fail "selftest: no include line in: $out" ;;
esac
if cmp -s "$repo/settings.gradle.kts" "$root/settings.gradle.kts"; then
  pass "selftest: settings.gradle.kts untouched"
else
  fail "selftest: settings.gradle.kts changed"; fi
if grep -qF '](../../docs/features/selftest.md)' "$root/shared/feature-selftest/AGENTS.md"; then
  pass "selftest: AGENTS.md links its doc stub"
else
  fail "selftest: AGENTS.md does not link its doc stub"
fi
selftest_vm=src/commonMain/kotlin/dev/alllexey/itmowidgets/feature/selftest/presentation/SelftestViewModel.kt
if grep -q 'class SelftestViewModel(' "$root/shared/feature-selftest/$selftest_vm"; then
  pass "selftest: names rendered"
else
  fail "selftest: SelftestViewModel not rendered"
fi

# 4. Names.
for bad in Sample 1qr feature-qr qr_pass ""; do
  "$generator" --root "$tmp/qr-root" "$bad" > /dev/null 2>&1
  status=$?
  if [ "$status" -eq 2 ]; then pass "refuses '$bad'"; else fail "accepted '$bad' (exit $status)"; fi
done

if [ "$failures" -eq 0 ]; then
  echo "$me: PASS"
  exit 0
fi
echo "$me: FAIL ($failures)"
exit 1
