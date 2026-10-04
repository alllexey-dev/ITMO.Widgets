#!/usr/bin/env bash
# check-docs.sh [--strict]    docs drift guard over the *.md files of `git ls-files -co --exclude-standard`
# check-docs.sh --self-test   fixture cases in a temporary git repository
#
# Rules (finding keys in brackets; keys carry a token or a rule name, never a line number or a count):
# - path   [<doc>:path:<token>] a backticked token without spaces that ends in `/` or in a doc-relevant
#          extension equals, or is a `/`-boundary suffix of, a listed file or directory. Skipped: tokens with
#          < * { $ or an ellipsis, URLs, absolute and ~/ paths, ../itmo-widgets-* and ../MyItmoApi, the B/ M/ W/ C/
#          shorthands, filesDir/ cacheDir/ noBackupFilesDir/, names `git check-ignore --no-index` matches and the
#          never-open names below (docs name them to forbid them). Bare class names and refs are not paths.
# - link   [<doc>:link:<target>] relative Markdown links resolve to a listed file or directory and their
#          anchors to a heading (GitHub slugs, -1 for a repeated heading) or an HTML id of the target.
#          History (CHANGELOG.md, changelog.d/**, docs/decisions/**) is exempt from path and link.
# - vibe   [<doc>:vibe:<path>] no doc anywhere names a path below vibe/ (the bare `vibe/` directory may be named).
# - lines  [AGENTS.md:lines] AGENTS.md has at most 130 lines.
# - unindexed [<doc>:unindexed] every docs/**/*.md is linked from docs/README.md or from a directory README.md
#          that docs/README.md links; fails for docs/decisions/*.md, warns for other docs (fails with --strict).
#
# Ratchet: scripts/check-docs.known accepts today's findings, one `<entry>  # <reason>` per line, where <entry> is
# a finding key or a whole doc path. New findings fail; stale entries warn and fail only with --strict, so the
# list only shrinks and no other lane edits it.
#
# Portable: macOS /bin/bash 3.2 with BSD tools and Ubuntu with GNU tools; git, grep -E, awk and sed only.
# Exit code: 0 pass, 1 findings, 2 usage or environment error.

set -u

me=check-docs.sh
NEVER_OPEN="serviceAccountKey.json properties.env"
AGENTS_MAX_LINES=130

die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) || die "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
root=$(git -C "$script_dir/.." rev-parse --show-toplevel 2>/dev/null) || die "not inside a git repository"
cd "$root" || die "cannot enter $root"

strict=0
mode=check
case "${1:-}" in
  "") ;;
  --strict) strict=1 ;;
  --self-test) mode=self-test ;;
  *) die "usage: $me [--strict | --self-test]" ;;
esac
[ $# -le 1 ] || die "usage: $me [--strict | --self-test]"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-docs.XXXXXX") || die "cannot create a temporary directory"
trap 'rm -rf "$tmp"' EXIT

write_scan_awk() {
  cat > "$1" <<'AWK'
# Input: the listed files (pass 0), then every doc twice (pass=1 headings, line counts and vibe; pass=2 paths
# and links). Output: one finding per line, tab-separated: severity (E|W), key, location, message, and the
# normalized token for the ignore filter (path findings only).
function last_slash(p,   i, j) {
  j = 0
  while ((i = index(substr(p, j + 1), "/")) > 0) j += i
  return j
}
function dir_of(p,   i) { i = last_slash(p); return i ? substr(p, 1, i - 1) : "" }
function add_suffixes(p,   i) {
  suffix[p] = 1
  while ((i = index(p, "/")) > 0) { p = substr(p, i + 1); suffix[p] = 1 }
}
function add_listed(p,   i) {
  listed[p] = 1
  add_suffixes(p)
  while ((i = last_slash(p)) > 0) {
    p = substr(p, 1, i - 1)
    if (p in listed) break
    listed[p] = 1
    add_suffixes(p)
  }
}
# Collapses . and .. segments; returns ESCAPE when the path leaves the repository.
function normalize(p,   n, parts, i, out, depth, stack) {
  n = split(p, parts, "/")
  depth = 0
  for (i = 1; i <= n; i++) {
    if (parts[i] == "" || parts[i] == ".") continue
    if (parts[i] == "..") { if (depth == 0) return ESCAPE; depth--; continue }
    stack[++depth] = parts[i]
  }
  out = ""
  for (i = 1; i <= depth; i++) out = out (i > 1 ? "/" : "") stack[i]
  return out
}
function strip_all(s, t,   i) {
  while ((i = index(s, t)) > 0) s = substr(s, 1, i - 1) substr(s, i + length(t))
  return s
}
function replace_all(s, t, r,   i, out) {
  out = ""
  while ((i = index(s, t)) > 0) { out = out substr(s, 1, i - 1) r; s = substr(s, i + length(t)) }
  return out s
}
# GitHub heading anchor: rendered text, lower case, punctuation and symbols removed, each space a hyphen.
function slug(h,   s, i, c, out) {
  s = h
  gsub(/\]\([^)]*\)/, "", s)
  s = tolower(s)
  if (index(s, CYR_A)) for (i = 1; i <= n_upper; i++) s = replace_all(s, upper[i], lower[i])
  for (i = 1; i <= n_symbols; i++) s = strip_all(s, symbols[i])
  out = ""
  for (i = 1; i <= length(s); i++) {
    c = substr(s, i, 1)
    if (c ~ /[a-z0-9 _-]/ || c > "~") out = out c
  }
  sub(/^ +/, "", out)
  sub(/ +$/, "", out)
  gsub(/ /, "-", out)
  return out
}
function add_anchor(file, a,   n) {
  n = anchor_count[file, a]++
  anchors[file, (n ? a "-" n : a)] = 1
}
function emit(sev, key, msg, token) {
  printf "%s\t%s\t%s:%d\t%s\t%s\n", sev, key, FILENAME, FNR, msg, token
}
function is_path_token(t) {
  if (t ~ /[ \t]/ || t ~ /[<*{$]/ || index(t, ELLIPSIS) || index(t, "...") || index(t, "://")) return 0
  if (t ~ /^[A-Za-z][A-Za-z0-9+.-]*:/ || t ~ /^\/\//) return 0
  if (t ~ /^\// || t ~ /^~/) return 0
  if (t ~ /^\.\.\/(itmo-widgets-|MyItmoApi)/) return 0
  if (t ~ /^[BMWC]\// || t ~ /^(filesDir|cacheDir|noBackupFilesDir)\//) return 0
  if (t ~ /^\.[A-Za-z0-9]+$/) return 0
  if (t ~ /\/$/) return 1
  return t ~ PATH_EXTENSIONS
}
# The vibe rule owns vibe/ paths, so the result does not depend on whether vibe/ is ignored locally.
function check_token(t,   rel, u, b) {
  if (!is_path_token(t)) return
  rel = t
  while (rel ~ /^\.\.?\//) sub(/^\.\.?\//, "", rel)
  u = rel
  sub(/\/+$/, "", u)
  if (u == "" || u == "vibe" || u ~ /^vibe\// || (u in suffix)) return
  b = u
  sub(/^.*\//, "", b)
  if (index(" " never_open " ", " " b " ")) return
  emit("E", FILENAME ":path:" t, "`" t "` resolves to no listed file or directory", rel)
}
function check_link(raw,   t, path, frag, i, target) {
  t = raw
  sub(/^</, "", t)
  sub(/>$/, "", t)
  sub(/[ \t].*$/, "", t)
  if (t == "" || t ~ /^[A-Za-z][A-Za-z0-9+.-]*:/ || t ~ /^\//) return
  i = index(t, "#")
  if (i) { path = substr(t, 1, i - 1); frag = substr(t, i + 1) } else { path = t; frag = "" }
  sub(/\?.*$/, "", path)
  if (path == "") target = FILENAME
  else {
    target = normalize((cur_dir == "" ? "" : cur_dir "/") path)
    if (target == ESCAPE || target ~ /^vibe\//) return
  }
  if (target != "" && !(target in listed)) {
    if (!history) emit("E", FILENAME ":link:" t, "link target `" t "` does not exist", "")
    return
  }
  links[FILENAME] = links[FILENAME] SUBSEP target
  if (target != "" && ((target "/README.md") in listed)) links[FILENAME] = links[FILENAME] SUBSEP target "/README.md"
  if (frag == "" || history || target !~ /\.md$/) return
  if (!((target, frag) in anchors) && !((target, tolower(frag)) in anchors))
    emit("E", FILENAME ":link:" t, "anchor `#" frag "` matches no heading of " target, "")
}
function scan_vibe(line,   s, t, k) {
  s = line
  while (match(s, /(^|[^A-Za-z0-9_.-])vibe\/[A-Za-z0-9_.*<{~-]/)) {
    s = substr(s, RSTART)
    k = index(s, "vibe/")
    s = substr(s, k)
    t = s
    sub(/[ \t`'"()<>\[\]|,;].*$/, "", t)
    sub(/[.:]+$/, "", t)
    emit("E", FILENAME ":vibe:" t, "names `" t "` below vibe/", "")
    s = substr(s, 6)
  }
}
BEGIN {
  ESCAPE = "\001"
  PATH_EXTENSIONS = "\\.(kt|kts|java|swift|xml|md|sh|json|toml|yml|yaml|gradle|properties|sql|plist|xcstrings|pro|txt" \
    "|png|webp|svg)$"
  FS = "\n"
  ELLIPSIS = "…"
  n_symbols = split("« » — – … → ← ↔ ’ ‘ “ ” · • ≤ ≥ × ✓ № §", symbols, " ")
  CYR_A = sprintf("%c", 208)
  n_upper = 0
  for (i = 0; i < 16; i++) {
    upper[++n_upper] = sprintf("%c%c", 208, 144 + i); lower[n_upper] = sprintf("%c%c", 208, 176 + i)
    upper[++n_upper] = sprintf("%c%c", 208, 160 + i); lower[n_upper] = sprintf("%c%c", 209, 128 + i)
  }
  upper[++n_upper] = sprintf("%c%c", 208, 129); lower[n_upper] = sprintf("%c%c", 209, 145)
}
pass == 0 { add_listed($0); next }
FNR == 1 {
  fence = ""
  cur_dir = dir_of(FILENAME)
  history = (FILENAME ~ /(^|\/)CHANGELOG\.md$/ || FILENAME ~ /^changelog\.d\// || FILENAME ~ /^docs\/decisions\//)
  if (pass == 1) docs[FILENAME] = 1
}
pass == 1 { line_count[FILENAME] = FNR; scan_vibe($0) }
{
  if (fence != "") {
    if (index($0, fence) && $0 ~ /^ *(```|~~~)/) fence = ""
    next
  }
  if ($0 ~ /^ *(```|~~~)/) { s = $0; sub(/^ */, "", s); fence = substr(s, 1, 3); next }
}
pass == 1 {
  if ($0 ~ /^#+ /) {
    h = $0
    match(h, /^#+/)
    if (RLENGTH <= 6) {
      h = substr(h, RLENGTH + 2)
      sub(/[ \t]+#+[ \t]*$/, "", h)
      add_anchor(FILENAME, slug(h))
    }
  }
  s = $0
  while (match(s, /(id|name)="[^"]+"/)) {
    a = substr(s, RSTART, RLENGTH)
    sub(/^[a-z]+="/, "", a)
    sub(/"$/, "", a)
    anchors[FILENAME, a] = 1
    s = substr(s, RSTART + RLENGTH)
  }
  next
}
pass == 2 {
  s = $0
  rest = ""
  while (match(s, /`[^`]+`/)) {
    if (!history) check_token(substr(s, RSTART + 1, RLENGTH - 2))
    rest = rest substr(s, 1, RSTART - 1)
    s = substr(s, RSTART + RLENGTH)
  }
  s = rest s
  if (match(s, /^ *\[[^\]^][^\]]*\]: */)) check_link(substr(s, RSTART + RLENGTH))
  while (match(s, /\]\([^)]*\)/)) {
    check_link(substr(s, RSTART + 2, RLENGTH - 3))
    s = substr(s, RSTART + RLENGTH)
  }
}
END {
  if (("AGENTS.md" in line_count) && line_count["AGENTS.md"] > agents_max)
    printf "E\tAGENTS.md:lines\tAGENTS.md:%d\thas %d lines, at most %d\t\n",
      line_count["AGENTS.md"], line_count["AGENTS.md"], agents_max
  n = split(links["docs/README.md"], targets, SUBSEP)
  for (i = 1; i <= n; i++) if (targets[i] != "") indexed[targets[i]] = 1
  for (i = 1; i <= n; i++) {
    if (targets[i] !~ /\/README\.md$/ || targets[i] == "docs/README.md") continue
    m = split(links[targets[i]], sub_targets, SUBSEP)
    for (j = 1; j <= m; j++) if (sub_targets[j] != "") indexed[sub_targets[j]] = 1
  }
  for (f in docs) {
    if (f !~ /^docs\// || f == "docs/README.md" || (f in indexed)) continue
    if (f ~ /^docs\/decisions\//) printf "E\t%s:unindexed\t%s:1\tis not linked from docs/README.md\t\n", f, f
    else printf "W\t%s:unindexed\t%s:1\tis not linked from docs/README.md or a directory README it links\t\n", f, f
  }
}
AWK
}

write_ratchet_awk() {
  cat > "$1" <<'AWK'
# Input: the ignored tokens, the known file, the raw findings. Prints the report; the last line is the verdict.
function trim(s) { sub(/^[ \t]+/, "", s); sub(/[ \t]+$/, "", s); return s }
BEGIN { FS = "\t" }
FILENAME == ARGV[1] { ignored[$0] = 1; next }
FILENAME == ARGV[2] {
  line = $0
  if (line ~ /^[ \t]*(#|$)/) next
  i = index(line, " #")
  j = index(line, "\t#")
  if (j && (!i || j < i)) i = j
  entry = trim(i ? substr(line, 1, i - 1) : line)
  reason = i ? trim(substr(line, i + 2)) : ""
  if (reason == "") { printf "FAIL %s: known entry `%s` has no `# reason`\n", known_name, entry; fail++; next }
  known[entry] = FNR
  next
}
{
  if ($5 != "" && ($5 in ignored)) next
  key = $2
  file = key
  sub(/:.*$/, "", file)
  if (key in known) { used[key] = 1; next }
  if (file in known) { used[file] = 1; next }
  if ($1 == "E" || strict) { printf "FAIL %s %s [%s]\n", $3, $4, key; fail++ }
  else { printf "warn %s %s [%s]\n", $3, $4, key; warned++ }
}
END {
  for (entry in known) {
    if (entry in used) continue
    printf "%s %s:%d stale entry `%s`: no such finding any more, delete the line\n",
      (strict ? "FAIL" : "warn"), known_name, known[entry], entry
    if (strict) fail++; else warned++
  }
  printf "check-docs: %s (%d failure(s), %d warning(s)%s)\n",
    (fail ? "FAIL" : "PASS"), fail, warned, (strict ? ", strict" : "")
  exit fail ? 1 : 0
}
AWK
}

run_check() {
  local docs=() f
  git ls-files -co --exclude-standard > "$tmp/files" || die "git ls-files failed"
  while IFS= read -r f; do
    case $f in *.md) [ -f "$f" ] && docs[${#docs[@]}]=$f ;; esac
  done < "$tmp/files"
  [ ${#docs[@]} -gt 0 ] || die "no *.md files listed"

  write_scan_awk "$tmp/scan.awk"
  write_ratchet_awk "$tmp/ratchet.awk"
  LC_ALL=C awk -v agents_max="$AGENTS_MAX_LINES" -v never_open="$NEVER_OPEN" -f "$tmp/scan.awk" \
    "$tmp/files" pass=1 "${docs[@]}" pass=2 "${docs[@]}" > "$tmp/raw" || die "the scan failed"

  awk -F '\t' '$5 != "" && !seen[$5]++ { print $5 }' "$tmp/raw" > "$tmp/candidates"
  : > "$tmp/ignored"
  if [ -s "$tmp/candidates" ]; then
    git check-ignore --no-index --stdin < "$tmp/candidates" > "$tmp/ignored"
    [ $? -le 1 ] || die "git check-ignore failed"
  fi
  local known="$root/scripts/check-docs.known"
  [ -f "$known" ] || known=/dev/null
  LC_ALL=C awk -v strict="$strict" -v known_name="scripts/check-docs.known" -f "$tmp/ratchet.awk" \
    "$tmp/ignored" "$known" "$tmp/raw"
}

# ---- self-test ------------------------------------------------------------------------------------------------

st_repo=""
st_fixture() {
  rm -rf "$st_repo"
  local qr_dir="$st_repo/shared/feature/qr/src/commonMain/kotlin/dev/itmo/feature/qr/ui"
  mkdir -p "$st_repo/scripts" "$st_repo/docs/decisions" "$st_repo/docs/features" "$st_repo/vibe" "$qr_dir"
  git init -q "$st_repo" || die "git init failed"
  cp "$self" "$st_repo/scripts/check-docs.sh"
  printf 'local.properties\n' > "$st_repo/.gitignore"
  printf '# accepted findings\n' > "$st_repo/scripts/check-docs.known"
  printf 'class QrTileService\n' > "$qr_dir/QrTileService.kt"
  printf '# Agents\n\nNever commit `local.properties` or `serviceAccountKey.json`.\n' > "$st_repo/AGENTS.md"
  printf '# Plan\n' > "$st_repo/vibe/x.md"
  cat > "$st_repo/docs/README.md" <<'MD'
# Docs

- [QR](features/qr.md), its [tile](features/qr.md#tile-and-shortcuts)
- [The second tile](features/qr.md#tile-and-shortcuts-1)
- [0001 Example](decisions/0001-example.md)
- [Top](#docs) and [agents](../AGENTS.md)
- [Плитка](features/qr.md#плитка-qr-пропуск--android-13)
MD
  cat > "$st_repo/docs/features/qr.md" <<'MD'
# QR

The tile is `shared/feature/qr/src/commonMain/kotlin/dev/itmo/feature/qr/ui/QrTileService.kt`.

## Tile and shortcuts

## Tile and shortcuts

## Плитка «QR-пропуск» — Android 13+

```bash
# not a heading: `missing/in/fence.kt`
```
MD
  printf '# 0001 Example\n\nOnce in `app/src/main/Gone.kt`, see [old](../old.md).\n' \
    > "$st_repo/docs/decisions/0001-example.md"
  printf '# Changelog\n\n- Removed `app/src/main/Gone.kt`, see [old](old.md).\n' > "$st_repo/CHANGELOG.md"
}
st_append() { printf '%s\n' "$2" >> "$st_repo/$1"; }

st_failures=0
st_expect() { # expected-exit description args...
  local want=$1 what=$2 got
  shift 2
  "$BASH" "$st_repo/scripts/check-docs.sh" "$@" > "$tmp/st.out" 2>&1
  got=$?
  if [ "$got" = "$want" ]; then
    printf 'ok    %s\n' "$what"
  else
    printf 'FAIL  %s: exit %s, expected %s\n' "$what" "$got" "$want"
    sed 's/^/      /' "$tmp/st.out"
    st_failures=$((st_failures + 1))
  fi
}

self_test() {
  st_repo="$tmp/repo"
  st_fixture; st_expect 0 "clean fixture passes"
  st_fixture; st_expect 0 "clean fixture passes --strict" --strict
  st_fixture; st_append docs/features/qr.md 'See `docs/features/missing.md`.'
  st_expect 1 "dangling path fails"
  st_fixture; st_append docs/features/qr.md 'See [missing](missing.md).'
  st_expect 1 "dangling link fails"
  st_fixture; st_append docs/features/qr.md 'See [tile](#no-such-heading).'
  st_expect 1 "dangling anchor fails"
  st_fixture; st_append docs/features/qr.md 'Plan: [plan](../../vibe/x.md).'
  st_expect 1 "a vibe/x.md link fails"
  st_fixture; printf '# 0002 Unindexed\n' > "$st_repo/docs/decisions/0002-unindexed.md"
  st_expect 1 "an unindexed ADR fails"
  st_fixture; printf '# Orphan\n' > "$st_repo/docs/features/orphan.md"
  st_expect 0 "an unindexed feature doc only warns"
  st_expect 1 "an unindexed feature doc fails --strict" --strict
  st_fixture; st_append scripts/check-docs.known 'docs/features/qr.md:path:gone.kt  # moved'
  st_expect 0 "a stale known entry only warns"
  st_expect 1 "a stale known entry fails --strict" --strict
  st_fixture; st_append docs/features/qr.md 'See `docs/features/missing.md`.'
  st_append scripts/check-docs.known 'docs/features/qr.md:path:docs/features/missing.md  # written in a later card'
  st_expect 0 "a known finding passes --strict" --strict
  st_fixture; st_append scripts/check-docs.known 'docs/features/qr.md:path:docs/features/missing.md'
  st_append docs/features/qr.md 'See `docs/features/missing.md`.'
  st_expect 1 "a known entry without a reason fails"
  st_fixture; st_append CHANGELOG.md '- Dropped `app/src/main/Other.kt` and [link](nowhere.md#x).'
  st_expect 0 "a dead path and link in CHANGELOG.md pass" --strict
  st_fixture; st_append docs/features/qr.md 'The class `QrTileService`, the branches `v2.3/next` and `release/2.2`.'
  st_expect 0 "a bare class name and ref names pass" --strict
  st_fixture
  st_append docs/features/qr.md 'Moved: `feature/qr/ui/QrTileService.kt`, `QrTileService.kt`, `docs/features/`.'
  st_expect 0 "a moved file cited by a path suffix passes" --strict
  st_fixture; st_append docs/features/qr.md 'No `ture/qr/ui/QrTileService.kt`.'
  st_expect 1 "a suffix inside a path segment fails"
  st_fixture
  st_append docs/features/qr.md 'Skipped: `<module>/build.gradle.kts`, `~/x.md`, `B/docs/x.md`, `filesDir/a.json`,'
  st_append docs/features/qr.md '`../itmo-widgets-core/docs/contract.md`, `https://x.dev/a.md`, `a/.../b/`,'
  st_append docs/features/qr.md '`local.properties`, `serviceAccountKey.json`.'
  st_expect 0 "patterns, home, sibling and URL tokens pass" --strict
  st_fixture; awk 'BEGIN { for (i = 0; i < 131; i++) print "line" }' > "$st_repo/AGENTS.md"
  st_expect 1 "AGENTS.md over 130 lines fails"

  if [ "$st_failures" -gt 0 ]; then
    printf 'check-docs self-test: FAIL (%d case(s))\n' "$st_failures"
    return 1
  fi
  printf 'check-docs self-test: PASS\n'
}

if [ "$mode" = self-test ]; then
  self_test
else
  run_check
fi
