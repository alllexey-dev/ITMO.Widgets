#!/usr/bin/env bash
# changelog.sh check                               validate the fragments in changelog.d/
# changelog.sh collect <version> <date> [--dry-run]  fold the fragments into CHANGELOG.md and delete them
# changelog.sh --self-test                         fixture cases in a temporary git repository
#
# Fragment: changelog.d/<lane-id-lowercase>[-<topic>].md, e.g. l12-recordbook.md. An optional first line
# `# <Area>`, then one tight list of `- ` bullets in the CHANGELOG.md voice; continuation lines are indented.
# Names are lowercase only (APFS is case-insensitive). changelog.d/README.md is the format note, not a fragment.
#
# collect: `## <version> — development` becomes `## <version> — <date>`; without that heading,
# `## <version> — <date>` is inserted above the first `## ` heading. Bullets of fragments without an area join the
# list right below the version heading; each `# <Area>` group goes under `### <Area>` (created when missing, in
# fragment name order). The fragments are deleted; commit CHANGELOG.md and the deletions together.
#
# Repository-agnostic: paths come from `git rev-parse --show-toplevel` of the script's directory, so B copies this
# file unchanged. Portable: macOS /bin/bash 3.2 with BSD tools and Ubuntu with GNU tools.
# Exit code: 0 pass, 1 findings, 2 usage or environment error.

set -u

me=changelog.sh
DIR=changelog.d
FRAGMENT_NAME='^l[0-9][0-9]-[a-z0-9][a-z0-9-]*\.md$'

die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
usage() { die "usage: $me check | collect <version> <date> [--dry-run] | --self-test"; }

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) || die "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
root=$(git -C "$script_dir" rev-parse --show-toplevel 2>/dev/null) || die "not inside a git repository"
cd "$root" || die "cannot enter $root"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/changelog.XXXXXX") || die "cannot create a temporary directory"
trap 'rm -rf "$tmp"' EXIT

# Fragment paths in C-locale name order, one per line; every other non-hidden entry of changelog.d/ goes to
# "$tmp/strays".
list_fragments() {
  local f name
  : > "$tmp/fragments"
  : > "$tmp/strays"
  [ -d "$DIR" ] || return 0
  for f in "$DIR"/*; do
    [ -e "$f" ] || continue
    name=${f#"$DIR"/}
    [ "$name" = README.md ] && continue
    if [ -f "$f" ] && printf '%s\n' "$name" | grep -Eq "$FRAGMENT_NAME"; then
      printf '%s\n' "$f" >> "$tmp/fragments"
    else
      printf '%s\n' "$f" >> "$tmp/strays"
    fi
  done
  LC_ALL=C sort -o "$tmp/fragments" "$tmp/fragments"
}

write_lint_awk() {
  cat > "$1" <<'AWK'
# One fragment per FILENAME; prints `FAIL <file>:<line> <message>` per finding.
function fail(msg) { printf "FAIL %s:%d %s\n", FILENAME, FNR, msg; failures++ }
function finish(   f) {
  if (cur == "") return
  if (!bullets) { printf "FAIL %s: no `- ` bullet\n", cur; failures++ }
}
FNR == 1 { finish(); cur = FILENAME; state = "start"; bullets = 0; blank_at = 0 }
{
  if ($0 ~ /\r$/) fail("CRLF line ending")
  if ($0 ~ /[ \t]$/ && $0 !~ /^[ \t]*$/) fail("trailing whitespace")
}
/^[ \t]*$/ {
  if (state == "start" && FNR == 1) fail("blank first line")
  if (state == "list" && !blank_at) blank_at = FNR
  next
}
{
  if (blank_at) { fail("blank line inside the list (line " blank_at "); keep one tight list"); blank_at = 0 }
}
/^#/ {
  if (state == "start" && FNR == 1 && $0 ~ /^# [^ \t#]/) { state = "area"; next }
  fail("only an optional first line `# <Area>` may be a heading")
  next
}
/^- / {
  if ($0 ~ /^- [ \t]*$/) fail("empty bullet")
  else bullets++
  state = "list"
  next
}
/^[ \t]/ {
  if (state != "list") fail("indented line before the first bullet")
  next
}
{ fail("text outside a `- ` bullet") }
END { finish(); exit failures ? 1 : 0 }
AWK
}

run_check() {
  local fails=0 f
  list_fragments
  while IFS= read -r f; do
    printf 'FAIL %s: not a fragment name; use changelog.d/<lane-id-lowercase>[-<topic>].md, lowercase only\n' "$f"
    fails=$((fails + 1))
  done < "$tmp/strays"
  write_lint_awk "$tmp/lint.awk"
  : > "$tmp/lint.out"
  while IFS= read -r f; do
    if [ ! -s "$f" ]; then
      printf 'FAIL %s: empty fragment\n' "$f" >> "$tmp/lint.out"
      continue
    fi
    [ "$(tail -c 1 "$f" | od -An -c | tr -d ' ')" = '\n' ] ||
      printf 'FAIL %s: no final newline\n' "$f" >> "$tmp/lint.out"
    LC_ALL=C awk -f "$tmp/lint.awk" "$f" >> "$tmp/lint.out"
  done < "$tmp/fragments"
  cat "$tmp/lint.out"
  fails=$((fails + $(grep -c '^FAIL' "$tmp/lint.out")))
  printf 'changelog: %s (%d fragment(s), %d failure(s))\n' \
    "$([ $fails -eq 0 ] && echo PASS || echo FAIL)" "$(wc -l < "$tmp/fragments" | tr -d ' ')" "$fails"
  [ $fails -eq 0 ]
}

write_collect_awk() {
  cat > "$1" <<'AWK'
# Input: every fragment (pass=1), then CHANGELOG.md (pass=2). Prints the new CHANGELOG.md.
function add_group(area) {
  if (!(area in group_index)) { group_index[area] = ++n_groups; group_name[n_groups] = area }
  return group_index[area]
}
function trim_block(s) { sub(/^\n+/, "", s); sub(/\n+$/, "", s); return s }
pass == 1 && FNR == 1 { area = "" }
pass == 1 {
  if (FNR == 1 && $0 ~ /^# /) { area = substr($0, 3); add_group(area); next }
  if ($0 ~ /^[ \t]*$/) next
  if (area == "") top_new = top_new $0 "\n"
  else group_new[group_index[area]] = group_new[group_index[area]] $0 "\n"
  next
}
{ line[++n] = $0 }
END {
  dev = 0; first_h2 = 0
  for (i = 1; i <= n; i++) {
    if (line[i] !~ /^## /) continue
    if (!first_h2) first_h2 = i
    if (line[i] == "## " version " — development") { dev = i; break }
    if (index(line[i], "## " version " — ") == 1 || line[i] == "## " version) {
      printf "%s: CHANGELOG.md already has `%s`\n", me, line[i] > "/dev/stderr"; exit 2
    }
  }
  if (dev) {
    start = dev + 1
    for (end = start; end <= n && line[end] !~ /^## /; end++) ;
  } else {
    start = first_h2 ? first_h2 : n + 1
    end = start
  }
  # The old section body: lines above its first `### ` and one block per `### ` group.
  top_old = ""; cur = 0
  for (i = start; i < end; i++) {
    if (line[i] ~ /^### /) {
      a = substr(line[i], 5)
      if (a in group_index) cur = group_index[a]
      else { cur = ++n_groups; group_index[a] = cur; group_name[cur] = a }
      # Old groups go first, in their old order, ahead of groups only fragments bring.
      if (!(cur in is_old)) { old_order[++n_old] = cur; is_old[cur] = 1 }
      continue
    }
    if (cur) group_old[cur] = group_old[cur] line[i] "\n"
    else top_old = top_old line[i] "\n"
  }
  for (i = 1; i < (dev ? dev : start); i++) print line[i]
  if (!dev && start > 1 && line[start - 1] != "") print ""
  print "## " version " — " date
  body = trim_block(trim_block(top_old) "\n" top_new)
  if (body != "") printf "\n%s\n", body
  for (k = 1; k <= n_old; k++) emit_group(old_order[k])
  for (g = 1; g <= n_groups; g++) if (!(g in is_old)) emit_group(g)
  if (end <= n) print ""
  for (i = end; i <= n; i++) print line[i]
}
function emit_group(g,   b) {
  b = trim_block(trim_block(group_old[g]) "\n" group_new[g])
  printf "\n### %s\n", group_name[g]
  if (b != "") printf "\n%s\n", b
}
AWK
}

run_collect() { # version date dry-run
  local version=$1 date=$2 dry=$3 f
  printf '%s\n' "$version" | grep -Eq '^[0-9]+(\.[0-9]+)+([-+][0-9A-Za-z.-]+)?$' ||
    die "version \`$version\` is not like 2.3 or 1.8.0"
  printf '%s\n' "$date" | grep -Eq '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' || die "date \`$date\` is not YYYY-MM-DD"
  [ -f CHANGELOG.md ] || die "no CHANGELOG.md in $root"
  run_check > "$tmp/check.out" || { cat "$tmp/check.out"; printf '%s: fix the fragments first\n' "$me" >&2; exit 1; }
  [ -s "$tmp/fragments" ] || printf '%s: no fragments in %s/; only the heading changes\n' "$me" "$DIR" >&2

  write_collect_awk "$tmp/collect.awk"
  local files=()
  while IFS= read -r f; do files[${#files[@]}]=$f; done < "$tmp/fragments"
  LC_ALL=C awk -v version="$version" -v date="$date" -v me="$me" -f "$tmp/collect.awk" \
    pass=1 ${files[@]+"${files[@]}"} pass=2 CHANGELOG.md > "$tmp/CHANGELOG.md" || exit 2

  if [ "$dry" = 1 ]; then
    diff -u -L CHANGELOG.md -L 'CHANGELOG.md (collected)' CHANGELOG.md "$tmp/CHANGELOG.md"
    while IFS= read -r f; do printf 'would delete %s\n' "$f"; done < "$tmp/fragments"
    return 0
  fi
  cat "$tmp/CHANGELOG.md" > CHANGELOG.md || die "cannot write CHANGELOG.md"
  while IFS= read -r f; do rm -f "$f" && printf 'deleted %s\n' "$f"; done < "$tmp/fragments"
  printf 'collected %d fragment(s) into `## %s — %s`; commit CHANGELOG.md and %s/ together\n' \
    "$(wc -l < "$tmp/fragments" | tr -d ' ')" "$version" "$date" "$DIR"
}

# ---- self-test ------------------------------------------------------------------------------------------------

st_repo=""
st_failures=0
st_fixture() { # with-development-heading (1|0)
  rm -rf "$st_repo"
  mkdir -p "$st_repo/scripts" "$st_repo/$DIR"
  git init -q "$st_repo" || die "git init failed"
  cp "$self" "$st_repo/scripts/changelog.sh"
  printf '# Fragments\n\nNot a fragment.\n' > "$st_repo/$DIR/README.md"
  {
    printf '# Changelog\n\nPreamble.\n\n'
    [ "$1" = 1 ] && printf '## 2.3 — development\n\n- v2.3 starts.\n\n### Widgets\n\n- Old widget line.\n\n'
    printf '## 2.2 — 2026-10-03\n\n### 2026-10-03\n\n- Released.\n'
  } > "$st_repo/CHANGELOG.md"
  printf '# Widgets\n\n- Schedule widget in Compose.\n  Second line.\n' > "$st_repo/$DIR/l10-schedule-widgets.md"
  printf '# Recordbook\n- Recordbook in Compose.\n' > "$st_repo/$DIR/l12-recordbook.md"
  printf -- '- Toolchain bumped.\n' > "$st_repo/$DIR/l04-toolchain.md"
}
st_run() { "$BASH" "$st_repo/scripts/changelog.sh" "$@" > "$tmp/st.out" 2>&1; }
st_report() { # ok description
  if [ "$1" = 1 ]; then printf 'ok    %s\n' "$2"; return; fi
  printf 'FAIL  %s\n' "$2"
  sed 's/^/      /' "$tmp/st.out"
  st_failures=$((st_failures + 1))
}
st_expect() { # expected-exit description args...
  local want=$1 what=$2
  shift 2
  st_run "$@"
  st_report "$([ $? = "$want" ] && echo 1 || echo 0)" "$what"
}
st_bad() { # file content description
  st_fixture 1
  printf '%b' "$2" > "$st_repo/$DIR/$1"
  st_expect 1 "$3" check
}
st_expect_changelog() { # description expected-file
  if cmp -s "$2" "$st_repo/CHANGELOG.md"; then st_report 1 "$1"; return; fi
  diff -u "$2" "$st_repo/CHANGELOG.md" > "$tmp/st.out"
  st_report 0 "$1"
}

self_test() {
  st_repo="$tmp/repo"
  st_fixture 1; st_expect 0 "valid fragments pass" check
  st_fixture 1; rm -f "$st_repo/$DIR"/l*.md; st_expect 0 "no fragments pass" check
  st_bad L18-ios.md '- x\n' "an uppercase name fails"
  st_bad l18_ios.md '- x\n' "an underscore name fails"
  st_bad notes.txt '- x\n' "a non-fragment file fails"
  st_bad l1-x.md '- x\n' "a one-digit lane fails"
  st_bad l18-ios.md '' "an empty fragment fails"
  st_bad l18-ios.md '# iOS\n' "an area without bullets fails"
  st_bad l18-ios.md '- \n' "an empty bullet fails"
  st_bad l18-ios.md 'Prose.\n' "text outside a bullet fails"
  st_bad l18-ios.md '- a\n\n- b\n' "a blank line inside the list fails"
  st_bad l18-ios.md '- a\n## Sub\n- b\n' "a second heading fails"
  st_bad l18-ios.md '- a \n' "trailing whitespace fails"
  st_bad l18-ios.md '- a' "a missing final newline fails"
  st_fixture 1; mkdir "$st_repo/$DIR/l18-dir.md"; st_expect 1 "a directory fails" check

  st_fixture 1
  cat > "$tmp/want.md" <<'MD'
# Changelog

Preamble.

## 2.3 — 2026-12-01

- v2.3 starts.
- Toolchain bumped.

### Widgets

- Old widget line.
- Schedule widget in Compose.
  Second line.

### Recordbook

- Recordbook in Compose.

## 2.2 — 2026-10-03

### 2026-10-03

- Released.
MD
  cp "$st_repo/CHANGELOG.md" "$tmp/before.md"
  st_expect 0 "collect --dry-run passes" collect 2.3 2026-12-01 --dry-run
  st_report "$(cmp -s "$tmp/before.md" "$st_repo/CHANGELOG.md" && [ -f "$st_repo/$DIR/l12-recordbook.md" ] &&
    echo 1 || echo 0)" "collect --dry-run changes nothing"
  st_expect 0 "collect with a development heading passes" collect 2.3 2026-12-01
  st_expect_changelog "collect merges into the development heading" "$tmp/want.md"
  st_report "$([ "$(ls "$st_repo/$DIR")" = README.md ] && echo 1 || echo 0)" "collect deletes the fragments only"
  st_expect 2 "a second collect of the same version fails" collect 2.3 2026-12-02

  st_fixture 0
  cat > "$tmp/want.md" <<'MD'
# Changelog

Preamble.

## 1.8.0 — 2026-12-01

- Toolchain bumped.

### Widgets

- Schedule widget in Compose.
  Second line.

### Recordbook

- Recordbook in Compose.

## 2.2 — 2026-10-03

### 2026-10-03

- Released.
MD
  st_expect 0 "collect without a development heading passes" collect 1.8.0 2026-12-01
  st_expect_changelog "collect inserts the heading below the preamble" "$tmp/want.md"

  st_fixture 1; printf -- '- x\n' > "$st_repo/$DIR/L18-ios.md"
  cp "$st_repo/CHANGELOG.md" "$tmp/before.md"
  st_expect 1 "collect refuses invalid fragments" collect 2.3 2026-12-01
  st_report "$(cmp -s "$tmp/before.md" "$st_repo/CHANGELOG.md" && echo 1 || echo 0)" "a refused collect changes nothing"
  st_fixture 1; st_expect 2 "a bad date fails" collect 2.3 01.12.2026
  st_fixture 1; st_expect 2 "a bad version fails" collect v2.3 2026-12-01

  if [ "$st_failures" -gt 0 ]; then
    printf 'changelog self-test: FAIL (%d case(s))\n' "$st_failures"
    return 1
  fi
  printf 'changelog self-test: PASS\n'
}

case "${1:-}" in
  check) [ $# -eq 1 ] || usage; run_check ;;
  collect)
    [ $# -eq 3 ] || { [ $# -eq 4 ] && [ "$4" = --dry-run ]; } || usage
    run_collect "$2" "$3" "$([ $# -eq 4 ] && echo 1 || echo 0)"
    ;;
  --self-test) [ $# -eq 1 ] || usage; self_test ;;
  *) usage ;;
esac
