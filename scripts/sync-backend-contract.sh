#!/usr/bin/env bash
# sync-backend-contract.sh <backend-sha> [--allow-older]  vendor Backend's contract at <backend-sha>
# sync-backend-contract.sh --check                        re-extract the recorded commit and diff
# sync-backend-contract.sh --self-test                    fixture cases in a throwaway git repository
#
# Backend owns the wire contract; :shared:backend-client reads a copy of it (option A of analysis 92). The copy,
# shared/backend-client/src/commonTest/resources/contract/, is generated: only this script writes it, and a rebase
# conflict there is solved by rerunning it with the newer commit, never by hand.
#
# The copy is replaced wholesale with
# - Backend's src/test/resources/contract/** byte for byte (http/<area>/, requests/, fcm/, index.json, README.md);
# - Backend's docs/openapi.json as openapi.json, when that commit has it;
# - BACKEND_COMMIT: the full commit SHA on line 1, then `version <Backend version>` and `date <commit date>`.
#
# Backend is ${ITMO_BACKEND_REPO:-$HOME/proj/itmo-widgets-backend}, read only through git rev-parse, merge-base,
# ls-tree and cat-file: no checkout, stash, fetch or write there. <backend-sha> must be merged work (an ancestor of
# origin/v2.3/next) and must not go back behind the recorded commit unless --allow-older is given. A commit
# Backend's clone does not have exits 2 with the fetch command to run.
#
# ITMO_CONTRACT_DIR overrides the destination (the self-test uses it).
# Portable: macOS /bin/bash 3.2 with BSD tools and Ubuntu with GNU tools.
# Exit code: 0 pass, 1 refused or drift, 2 usage or environment error.

set -u

me=sync-backend-contract.sh
BACKEND_REF=origin/v2.3/next
SOURCE_DIR=src/test/resources/contract
OPENAPI=docs/openapi.json
VERSION_FILE=build.gradle.kts
RELATIVE_DEST=shared/backend-client/src/commonTest/resources/contract

die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
usage() { die "usage: $me <backend-sha> [--allow-older] | --check | --self-test"; }

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) || die "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"

backend=${ITMO_BACKEND_REPO:-$HOME/proj/itmo-widgets-backend}

tmp=$(mktemp -d "${TMPDIR:-/tmp}/sync-backend-contract.XXXXXX") || die "cannot create a temporary directory"
trap 'rm -rf "$tmp"' EXIT

destination() {
  if [ -n "${ITMO_CONTRACT_DIR:-}" ]; then
    printf '%s\n' "$ITMO_CONTRACT_DIR"
  else
    local root
    root=$(git -C "$script_dir/.." rev-parse --show-toplevel 2>/dev/null) || die "not inside a git repository"
    printf '%s\n' "$root/$RELATIVE_DEST"
  fi
}

bgit() { git -C "$backend" "$@"; }

check_backend() {
  bgit rev-parse --git-dir > /dev/null 2>&1 || die "no Backend git repository at $backend (set ITMO_BACKEND_REPO)"
}

# Prints the full SHA of commit $1, or exits 2 with the fetch command when Backend's clone lacks it.
resolve() {
  local sha
  if sha=$(bgit rev-parse --verify --quiet "$1^{commit}"); then
    printf '%s\n' "$sha"
  else
    printf '%s: Backend has no commit %s; run\n  git -C %s fetch origin\nand retry\n' "$me" "$1" "$backend" >&2
    exit 2
  fi
}

# The recorded SHA of BACKEND_COMMIT in directory $1, or nothing.
recorded_sha() {
  [ -f "$1/BACKEND_COMMIT" ] || return 0
  head -n 1 "$1/BACKEND_COMMIT"
}

# Writes the contract of commit $1 into the new directory $2.
extract() {
  local sha=$1 out=$2 mode type object mode_type_object path relative version date
  mkdir -p "$out" || die "cannot create $out"
  bgit cat-file -e "$sha:$SOURCE_DIR" 2> /dev/null || die "Backend commit $sha has no $SOURCE_DIR"
  bgit ls-tree -r "$sha" -- "$SOURCE_DIR/" > "$tmp/tree" || die "cannot list $SOURCE_DIR at $sha"
  while IFS="$(printf '\t')" read -r mode_type_object path; do
    set -- $mode_type_object
    mode=$1 type=$2 object=$3
    [ "$type" = blob ] || die "unexpected $type at $path"
    case "$mode" in 100644 | 100755) ;; *) die "unexpected mode $mode at $path" ;; esac
    relative=${path#"$SOURCE_DIR"/}
    mkdir -p "$out/$(dirname "$relative")" || die "cannot create a directory for $relative"
    bgit cat-file blob "$object" > "$out/$relative" || die "cannot read $path at $sha"
  done < "$tmp/tree"
  if bgit cat-file -e "$sha:$OPENAPI" 2> /dev/null; then
    [ ! -e "$out/openapi.json" ] || die "$SOURCE_DIR/openapi.json at $sha clashes with $OPENAPI"
    bgit cat-file blob "$sha:$OPENAPI" > "$out/openapi.json" || die "cannot read $OPENAPI at $sha"
  fi
  [ ! -e "$out/BACKEND_COMMIT" ] || die "$SOURCE_DIR/BACKEND_COMMIT at $sha clashes with the record"
  version=$(bgit cat-file blob "$sha:$VERSION_FILE" 2> /dev/null |
    sed -n 's/^version[[:space:]]*=[[:space:]]*"\([^"]*\)".*$/\1/p' | head -n 1)
  [ -n "$version" ] || die "no version line in $VERSION_FILE at $sha"
  date=$(bgit show -s --format=%cI "$sha") || die "cannot read the date of $sha"
  printf '%s\nversion %s\ndate %s\n' "$sha" "$version" "$date" > "$out/BACKEND_COMMIT"
}

sync() {
  local requested=$1 allow_older=$2 dest sha next recorded parent staging
  check_backend
  dest=$(destination) || exit 2
  sha=$(resolve "$requested") || exit 2
  next=$(bgit rev-parse --verify --quiet "$BACKEND_REF^{commit}") ||
    die "Backend has no $BACKEND_REF; run git -C $backend fetch origin"
  if ! bgit merge-base --is-ancestor "$sha" "$next"; then
    printf '%s: %s is not merged into Backend %s; vendor merged work only\n' "$me" "$sha" "$BACKEND_REF" >&2
    exit 1
  fi
  recorded=$(recorded_sha "$dest")
  if [ -n "$recorded" ] && [ "$allow_older" -eq 0 ]; then
    recorded=$(resolve "$recorded") || exit 2
    if ! bgit merge-base --is-ancestor "$recorded" "$sha"; then
      printf '%s: %s is not a descendant of the recorded %s; pass --allow-older to go back\n' \
        "$me" "$sha" "$recorded" >&2
      exit 1
    fi
  fi
  parent=$(dirname "$dest")
  mkdir -p "$parent" || die "cannot create $parent"
  staging=$(mktemp -d "$parent/.contract.XXXXXX") || die "cannot create a staging directory in $parent"
  ( extract "$sha" "$staging/contract" ) || { rm -rf "$staging"; exit 2; }
  rm -rf "$dest" && mv "$staging/contract" "$dest" && rm -rf "$staging" || die "cannot replace $dest"
  printf '%s: vendored Backend %s into %s\n' "$me" "$sha" "$dest"
}

check() {
  local dest recorded sha
  check_backend
  dest=$(destination) || exit 2
  recorded=$(recorded_sha "$dest")
  [ -n "$recorded" ] || { printf '%s: no %s/BACKEND_COMMIT; run %s <backend-sha>\n' "$me" "$dest" "$me" >&2; exit 1; }
  sha=$(resolve "$recorded") || exit 2
  ( extract "$sha" "$tmp/expected" ) || exit 2
  if diff -r "$tmp/expected" "$dest" > "$tmp/diff"; then
    printf '%s: %s matches Backend %s\n' "$me" "$dest" "$sha"
  else
    cat "$tmp/diff" >&2
    printf '%s: %s drifted from Backend %s; rerun %s <backend-sha>, never edit by hand\n' \
      "$me" "$dest" "$sha" "$me" >&2
    exit 1
  fi
}

# ---- self-test ------------------------------------------------------------------------------------------------

st_failures=0
st_report() { # ok(1|0) description
  if [ "$1" = 1 ]; then printf 'ok   %s\n' "$2"; else printf 'FAIL %s\n' "$2"; st_failures=$((st_failures + 1)); fi
}
st_run() { # expected-exit description args...
  local expected=$1 description=$2 status
  shift 2
  ITMO_BACKEND_REPO="$st_backend" ITMO_CONTRACT_DIR="$st_dest" "$self" "$@" > "$tmp/out" 2>&1
  status=$?
  if [ "$status" = "$expected" ]; then st_report 1 "$description"; else
    st_report 0 "$description (exit $status, expected $expected)"; sed 's/^/     /' "$tmp/out"
  fi
}
st_git() {
  git -C "$st_backend" -c user.name=Synthetic -c user.email=synthetic@example.invalid -c commit.gpgsign=false \
    -c core.hooksPath=/dev/null "$@" > /dev/null 2>&1
}
st_commit() { # message; prints the new SHA
  st_git add -A && st_git commit -q -m "$1" && git -C "$st_backend" rev-parse HEAD
}
st_has() { [ -f "$st_dest/$1" ] && echo 1 || echo 0; }
st_lacks() { [ -e "$st_dest/$1" ] && echo 0 || echo 1; }

self_test() {
  local c1 c2 c3 side line
  st_backend="$tmp/backend"
  st_dest="$tmp/app/resources/contract"
  mkdir -p "$st_backend/$SOURCE_DIR/http/users" "$st_backend/$SOURCE_DIR/requests" "$st_backend/docs" ||
    die "cannot create the fixture repository"
  git init -q "$st_backend" || die "git init failed"
  printf 'version = "1.7.0"\n' > "$st_backend/$VERSION_FILE"
  printf '{"success":true}\n' > "$st_backend/$SOURCE_DIR/http/users/a.json"
  printf '{"idToken":"synthetic"}\n' > "$st_backend/$SOURCE_DIR/requests/R.json"
  printf '[]\n' > "$st_backend/$SOURCE_DIR/index.json"
  printf 'Not part of the contract.\n' > "$st_backend/docs/other.md"
  c1=$(st_commit c1) || die "commit c1 failed"
  printf 'version = "1.8.0"\n' > "$st_backend/$VERSION_FILE"
  printf '{"success":false}\n' > "$st_backend/$SOURCE_DIR/http/users/a.json"
  rm "$st_backend/$SOURCE_DIR/requests/R.json"
  printf '{"openapi":"3.1.0"}\n' > "$st_backend/$OPENAPI"
  c2=$(st_commit c2) || die "commit c2 failed"
  git -C "$st_backend" update-ref "refs/remotes/$BACKEND_REF" "$c2" || die "update-ref failed"
  st_git checkout -q -b side
  printf '{"unmerged":true}\n' > "$st_backend/$SOURCE_DIR/index.json"
  side=$(st_commit side) || die "commit side failed"

  mkdir -p "$st_dest" && printf 'stale\n' > "$st_dest/stale.json"
  st_run 0 "the first sync passes" "$c1"
  st_report "$(st_lacks stale.json)" "replace removes files Backend does not have"
  st_report "$(cmp -s "$st_dest/http/users/a.json" <(git -C "$st_backend" show "$c1:$SOURCE_DIR/http/users/a.json") &&
    echo 1 || echo 0)" "replace copies fixtures byte for byte"
  st_report "$(st_has requests/R.json)" "replace keeps the directory layout"
  st_report "$(st_lacks openapi.json)" "no openapi.json while Backend has none"
  st_report "$(st_lacks other.md)" "only the contract directory is copied"
  line=$(head -n 1 "$st_dest/BACKEND_COMMIT")
  st_report "$([ "$line" = "$c1" ] && echo 1 || echo 0)" "BACKEND_COMMIT records the full SHA"
  st_report "$(grep -qx 'version 1.7.0' "$st_dest/BACKEND_COMMIT" && grep -q '^date ' "$st_dest/BACKEND_COMMIT" &&
    echo 1 || echo 0)" "BACKEND_COMMIT records the version and the date"
  st_run 0 "check passes right after a sync" --check

  st_run 0 "a newer commit syncs" "$(printf '%s' "$c2" | cut -c1-10)"
  st_report "$(st_has openapi.json)" "openapi.json is vendored once Backend has it"
  st_report "$(st_lacks requests/R.json)" "a fixture Backend removed disappears"
  st_report "$(grep -qx 'version 1.8.0' "$st_dest/BACKEND_COMMIT" && echo 1 || echo 0)" "the version follows"
  st_run 0 "the same commit syncs again" "$c2"

  st_run 1 "an older commit is refused" "$c1"
  st_report "$([ "$(head -n 1 "$st_dest/BACKEND_COMMIT")" = "$c2" ] && echo 1 || echo 0)" \
    "a refused sync leaves the copy alone"
  st_run 0 "--allow-older goes back" "$c1" --allow-older
  st_run 0 "a newer commit syncs after going back" "$c2"

  st_run 1 "an unmerged commit is refused" "$side"
  c3=0123456789abcdef0123456789abcdef01234567
  st_run 2 "a missing commit exits 2" "$c3"
  st_report "$(grep -q 'fetch origin' "$tmp/out" && echo 1 || echo 0)" "a missing commit prints the fetch command"
  ITMO_BACKEND_REPO="$tmp/missing" ITMO_CONTRACT_DIR="$st_dest" "$self" --check > "$tmp/out" 2>&1
  st_report "$([ $? -eq 2 ] && echo 1 || echo 0)" "a missing Backend repository exits 2"

  printf '{"edited":true}\n' > "$st_dest/http/users/a.json"
  st_run 1 "check finds an edited fixture" --check
  st_run 0 "a sync repairs the drift" "$c2"
  printf '{}\n' > "$st_dest/http/users/extra.json"
  st_run 1 "check finds an added file" --check
  st_run 0 "a sync removes the added file" "$c2"
  rm "$st_dest/openapi.json"
  st_run 1 "check finds a removed file" --check
  st_run 0 "a sync restores the removed file" "$c2"
  printf '%s\n' "$c3" > "$st_dest/BACKEND_COMMIT"
  st_run 2 "check of an unknown recorded commit exits 2" --check

  if [ "$st_failures" -gt 0 ]; then
    printf 'sync-backend-contract self-test: FAIL (%d case(s))\n' "$st_failures"
    exit 1
  fi
  printf 'sync-backend-contract self-test: PASS\n'
}

case "${1:-}" in
  "") usage ;;
  --self-test) [ $# -eq 1 ] || usage; self_test ;;
  --check) [ $# -eq 1 ] || usage; check ;;
  --allow-older) usage ;;
  -*) usage ;;
  *)
    case "$#:${2:-}" in
      1:) sync "$1" 0 ;;
      2:--allow-older) sync "$1" 1 ;;
      *) usage ;;
    esac
    ;;
esac
