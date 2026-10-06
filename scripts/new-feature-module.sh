#!/bin/bash
# new-feature-module.sh [--root <dir>] <name>
#
# Renders templates/feature-module/ into shared/feature-<name>: the build file with the itmowidgets.cmp.ui and
# itmowidgets.testing conventions, commonMain stubs under feature/<name>/{domain,data,presentation,ui,di} (Koin
# module, a ViewModel with the uiState/events/refresh(RefreshMode) contract, a stateless screen and its route),
# composeResources/values/strings_<name>.xml, a commonTest ViewModel test with a fake, a commonTest screen host
# test, an androidHostTest Koin graph test and preview screenshot test, and the module's AGENTS.md stub.
# The shape is the QR pilot's (shared/feature-qr; docs/recipes/shared-module-screen.md).
#
# - Idempotent: adds only missing files and never overwrites one, so it is safe on an existing module.
# - A feature without a doc (module not in settings.gradle.kts and no docs/features/<name>.md) also gets a
#   docs/features/<name>.md stub and its line in docs/README.md; an existing feature never does.
# - Never edits settings.gradle.kts (L04 owns it): for a new module it prints the include line instead.
# - --root renders into another repository root (the self-test renders into a temporary directory); the templates
#   always come from this script's repository.
# - <name> is the package segment: lowercase letters and digits, starting with a letter (qr, sport, recordbook).
#
# Portable: macOS /bin/bash 3.2 with BSD tools and GNU tools; no associative arrays.
# Exit code: 0 done, 2 usage error.

set -u

me=new-feature-module.sh

die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
usage() { sed -n '2p' "$0" | sed 's/^# //' >&2; exit 2; }

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) || die "cannot resolve the script directory"
templates="$script_dir/../templates/feature-module"
[ -d "$templates/module" ] || die "no templates at $templates/module"

root="$script_dir/.."
name=""
while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage ;;
    --root)
      [ $# -ge 2 ] || usage
      root=$2
      shift 2
      ;;
    -*) die "unknown option '$1'" ;;
    *)
      [ -z "$name" ] || usage
      name=$1
      shift
      ;;
  esac
done
[ -n "$name" ] || usage
case "$name" in
  [a-z]*) ;;
  *) die "'$name' must start with a lowercase letter" ;;
esac
case "$name" in
  *[!a-z0-9]*) die "'$name' may hold only lowercase letters and digits (it is a package segment)" ;;
esac
[ -d "$root" ] || die "no directory $root"
root=$(cd "$root" && pwd -P) || die "cannot enter $root"

first=$(printf '%s' "$name" | cut -c1 | tr '[:lower:]' '[:upper:]')
Name="$first$(printf '%s' "$name" | cut -c2-)"
module_rel="shared/feature-$name"
module="$root/$module_rel"
settings="$root/settings.gradle.kts"
doc_rel="docs/features/$name.md"
index="$root/docs/README.md"

declared=0
if [ -f "$settings" ] && grep -q "\":$(echo "$module_rel" | tr '/' ':')\"" "$settings"; then
  declared=1
fi

added=0
kept=0

# render <template> <destination> <docs line>: copies with the placeholders replaced.
render() {
  mkdir -p "$(dirname "$2")" || die "cannot create $(dirname "$2")"
  awk -v lower="$name" -v upper="$Name" -v docs="$3" '
    $0 == "__docs__" { print docs; next }
    { gsub(/__Name__/, upper); gsub(/__name__/, lower); print }
  ' "$1" > "$2" || die "cannot write $2"
}

# 1. The feature doc first: the module's AGENTS.md links it when it exists.
if [ ! -f "$root/$doc_rel" ] && [ "$declared" -eq 0 ]; then
  render "$templates/docs/feature.md.tmpl" "$root/$doc_rel" ""
  echo "added $doc_rel"
  added=$((added + 1))
  line=$(awk -v lower="$name" -v upper="$Name" '{ gsub(/__Name__/, upper); gsub(/__name__/, lower); print }' \
    "$templates/docs/index-line.md.tmpl")
  if [ ! -f "$index" ]; then
    echo "note: no docs/README.md; index the doc by hand: $line"
  elif ! grep -qF "(features/$name.md)" "$index"; then
    # The line goes at the end of the Features list: before the blank line that precedes "## Decisions".
    at=$(awk '/^## Decisions/ { print NR; exit }' "$index")
    if [ -n "$at" ] && [ "$at" -gt 1 ] && [ -z "$(sed -n "$((at - 1))p" "$index")" ]; then
      awk -v at="$((at - 1))" -v line="$line" 'NR == at { print line } { print }' "$index" > "$index.new" &&
        mv "$index.new" "$index" || die "cannot update docs/README.md"
    else
      printf '%s\n' "$line" >> "$index" || die "cannot update docs/README.md"
      echo "note: no '## Decisions' heading in docs/README.md; the index line went to its end, move it"
    fi
    echo "indexed $doc_rel in docs/README.md"
  fi
fi

if [ -f "$root/$doc_rel" ]; then
  docs_line="- [Feature doc](../../$doc_rel)"
else
  docs_line="- [Feature docs](../../docs/README.md#features): the pages this module serves."
fi

# 2. The module, file by file; an existing file is kept as it is.
build_existed=0
[ -f "$module/build.gradle.kts" ] && build_existed=1
files=$(cd "$templates/module" && find . -type f -name '*.tmpl' | sed 's|^\./||' | LC_ALL=C sort)
for template in $files; do
  rel=$(printf '%s' "${template%.tmpl}" | sed -e "s/__Name__/$Name/g" -e "s/__name__/$name/g")
  if [ -e "$module/$rel" ]; then
    kept=$((kept + 1))
  else
    render "$templates/module/$template" "$module/$rel" "$docs_line"
    echo "added $module_rel/$rel"
    added=$((added + 1))
  fi
done

echo "$me: $name: added $added, kept $kept"

# 3. What the script does not touch.
if [ "$build_existed" -eq 1 ]; then
  missing=""
  for dependency in 'project(":shared:core")' 'project(":shared:designsystem")' 'project(":shared:testing")' \
    libs.jetbrains.lifecycle.viewmodel libs.koin.core libs.koin.core.viewmodel libs.koin.compose.viewmodel \
    libs.jetbrains.lifecycle.runtime.compose libs.koin.test; do
    grep -qF "$dependency)" "$module/build.gradle.kts" || missing="$missing $dependency"
  done
  if [ -n "$missing" ]; then
    echo "the kept $module_rel/build.gradle.kts lacks what the stubs use (L04 review):$missing"
  fi
fi
if [ "$declared" -eq 0 ]; then
  echo "settings.gradle.kts does not include $module_rel; L04 adds: include(\":$(echo "$module_rel" | tr '/' ':')\")"
fi
if [ "$added" -gt 0 ]; then
  cat <<EOF
next:
- app: a Fragment host returning itmoComposeView { ${Name}Route(onBack = ...) }, the ${Name}Repository bridge in
  di/bridge/${Name}Bridge.kt and its KoinModules lines (L07 review), the module in app/build.gradle.kts (L04)
- strings: scripts/verify.sh run -- :app:exportAppleStrings
- goldens: scripts/verify.sh shots feature-$name --record -Pshots.appearance=full (all four appearances, as the
  pilot; once recorded, every shots run checks them), then look at every PNG
- check: scripts/verify.sh quick; scripts/verify.sh klibs feature-$name
EOF
fi
