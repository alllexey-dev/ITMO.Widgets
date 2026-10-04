#!/bin/bash
# lane new <repo> <lane-id> <slug> [--base <ref>]
# lane next <repo>
# lane pin <worktree>
# lane done <worktree> | lane done --branch <repo> <branch>
# lane list [<repo>] [--offline]
#
# ITMO.Widgets v2.3 (L01 G-14a). Creates and removes lane worktrees under
# ~/proj/.wt/<repo>/, writes the `itmo-lane` marker the pre-push guard reads, and
# keeps one detached MyItmoApi pin per Android worktree
# (~/proj/.wt/myitmoapi/pin-<worktree>-<sha7>, path recorded in
# $(git rev-parse --absolute-git-dir)/itmo-myitmoapi-dir for scripts/verify.sh).
#
#   repo     android | backend | core | myitmoapi | web
#   lane-id  the lane file name without .md, e.g. L11-sport
#   slug     <card-id lowercased>-<short-slug>, e.g. lp-5a-filters
#
# new   worktree ~/proj/.wt/<repo>/<lane-id lowercased>-<slug> on a new branch
#       v2.3/<lane-id lowercased>/<slug> from origin/v2.3/next (--base only for
#       release/* and core master); resumes origin/<branch> if it already exists.
# next  the integrator worktree ~/proj/.wt/<repo>/next, detached at
#       origin/v2.3/next, or at the base before v2.3/next exists (android,
#       myitmoapi: master; web: main; backend: dev; core: always master).
# pin   re-pins an Android worktree to gradle/myitmoapi.ref and removes the old pin.
# done  after the merge comment: refuses with uncommitted or unpushed work or an
#       open PR; deletes the remote branch from the worktree (the guard admits
#       only its own lane), removes worktree, build dirs, pin and local branch.
# list  worktree, lane, branch, PR state, age; pins nobody uses.
#
# Tracked as scripts/lane.sh (L01 G-13b). ~/proj/.wt/bin/lane fetches origin and runs
# the copy on origin/v2.3/next, so callers never run a lane branch's copy. Paths
# derive from ITMO_WT (default ~/proj/.wt; its parent holds the main checkouts),
# never from $0: guards-test points ITMO_WT at its replica. Never creates or reads
# local.properties.

set -u
PATH="$PATH:/usr/bin:/bin"

me=lane
die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

WT=$(cd "${ITMO_WT:-$HOME/proj/.wt}" 2>/dev/null && pwd -P) || die "no lane root ${ITMO_WT:-$HOME/proj/.wt}"
PROJ=$(dirname "$WT")
PINS="$WT/myitmoapi"
LANES_DIR="$PROJ/ITMO.Widgets/vibe/v2.3/lanes"
REPOS="android backend core myitmoapi web"
LANE_RE='^L[0-9][0-9]-[a-z0-9]+(-[a-z0-9]+)*$'
SLUG_RE='^[a-z0-9][a-z0-9.-]*$'
SHA_RE='^[0-9a-f]{40}$'

lower() { printf '%s' "$1" | tr 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' 'abcdefghijklmnopqrstuvwxyz'; }
physical() { (cd "$1" 2>/dev/null && pwd -P); }

main_dir() {
  case $1 in
    android) printf '%s' "$PROJ/ITMO.Widgets" ;;
    backend) printf '%s' "$PROJ/itmo-widgets-backend" ;;
    core) printf '%s' "$PROJ/itmo-widgets-core" ;;
    myitmoapi) printf '%s' "$PROJ/MyItmoApi" ;;
    web) printf '%s' "$PROJ/itmo-widgets-web" ;;
    *) return 1 ;;
  esac
}

gh_slug() {
  case $1 in
    android) printf 'alllexey-dev/ITMO.Widgets' ;;
    backend) printf 'alllexey-dev/itmo-widgets-backend' ;;
    core) printf 'alllexey-dev/itmo-widgets-core' ;;
    myitmoapi) printf 'alllexey-dev/my-itmo-api' ;;
    web) printf 'alllexey-dev/itmo-widgets-web' ;;
  esac
}

# Base of v2.3/next before it exists; B's GitHub default master is never the base.
next_base() {
  case $1 in
    android | myitmoapi | core) printf 'master' ;;
    web) printf 'main' ;;
    backend) printf 'dev' ;;
  esac
}

check_repo() {
  case " $REPOS " in *" $1 "*) ;; *) die "unknown repo '$1' ($REPOS)" ;; esac
  local m
  m=$(main_dir "$1")
  [ -d "$m/.git" ] || die "main checkout $m is missing"
}

# Lane worktrees live only under ~/proj/.wt/<repo>/: never /tmp, a harness
# worktree or a main checkout (master plan section 7.1).
check_place() {
  local r m
  case $1 in
    /tmp | /tmp/* | /private/tmp | /private/tmp/*) die "refusing $1: temporary directories vanish on reboot" ;;
    */.claude/worktrees/* | */.codex/worktrees/*) die "refusing $1: agent harness worktree" ;;
    "$WT"/*/?*) ;;
    *) die "refusing $1: lane worktrees live under $WT/<repo>/" ;;
  esac
  for r in $REPOS; do
    m=$(main_dir "$r")
    case $1 in "$m" | "$m"/*) die "refusing $1: inside the main checkout $m" ;; esac
  done
}

git_dir_of() { git -C "$1" rev-parse --absolute-git-dir 2>/dev/null; }

write_marker() { # worktree content
  local gd
  gd=$(git_dir_of "$1") || die "$1 is not a git worktree"
  printf '%s\n' "$2" > "$gd/itmo-lane" || die "cannot write the marker in $gd"
}

read_marker() { # worktree -> marker or empty
  local gd
  gd=$(git_dir_of "$1") || return 0
  [ -f "$gd/itmo-lane" ] && head -n 1 "$gd/itmo-lane" | tr -d ' \t\r'
  return 0
}

open_prs() { # repo branch -> open PR numbers; fails when gh fails
  gh pr list --repo "$(gh_slug "$1")" --head "$2" --state open --limit 20 --json number --jq '.[].number'
}

# Resolves a worktree argument (path, <repo>/<name> or a unique <name>) to
# ~/proj/.wt/<repo>/<name>; sets wt_path, wt_repo, wt_name.
resolve_worktree() {
  local arg=$1 p="" hits="" d
  if [ -d "$arg" ]; then
    p=$(physical "$arg")
  elif [ -d "$WT/$arg" ]; then
    p=$(physical "$WT/$arg")
  else
    for d in "$WT"/*/"$arg"; do
      [ -d "$d" ] || continue
      hits="$hits $d"
    done
    set -- $hits
    [ $# -eq 1 ] || die "'$arg' matches $# worktrees under $WT; pass a path"
    p=$(physical "$1")
  fi
  [ -n "$p" ] || die "cannot resolve $arg"
  case $p in "$WT"/*/?*) ;; *) die "$p is not under $WT/<repo>/" ;; esac
  local rel=${p#"$WT"/}
  wt_repo=${rel%%/*}
  wt_name=${rel#*/}
  wt_path=$p
  [ "$wt_name" = "${wt_name%%/*}" ] || die "$p is not a direct child of $WT/$wt_repo"
  case " $REPOS " in *" $wt_repo "*) ;; *) die "$p is not under a repo directory of $WT" ;; esac
  case $wt_name in pin-*) die "$p is a MyItmoApi pin; lane done of its worktree removes it" ;; esac
  local top
  top=$(git -C "$p" rev-parse --show-toplevel 2>/dev/null) && top=$(physical "$top")
  [ "${top:-}" = "$p" ] || die "$p is not a git worktree root"
}

# ---- MyItmoApi pins -------------------------------------------------------------

remove_pin() { # pin path
  local pin=$1 m
  case $pin in "$PINS"/pin-?*) ;; *) note "not removing $pin: not a pin under $PINS"; return 0 ;; esac
  m=$(main_dir myitmoapi)
  if [ -e "$pin" ]; then
    git -C "$m" worktree remove --force "$pin" 2>/dev/null || rm -rf "$pin"
  fi
  git -C "$m" worktree prune 2>/dev/null
  note "removed pin $pin"
}

pin_mode=hard
pin_fail() {
  if [ "$pin_mode" = hard ]; then die "$*"; fi
  note "warning: $*; building without a pin until \`lane pin $pin_target\`"
  return 1
}

# One detached MyItmoApi checkout per Android worktree at gradle/myitmoapi.ref.
pin_worktree() { # worktree
  local path=$1 gd dir_file ref_file old="" sha name pin m
  pin_target=$path
  gd=$(git_dir_of "$path") || die "$path is not a git worktree"
  dir_file="$gd/itmo-myitmoapi-dir"
  ref_file="$path/gradle/myitmoapi.ref"
  [ -f "$dir_file" ] && old=$(head -n 1 "$dir_file")
  if [ ! -f "$ref_file" ]; then
    if [ -n "$old" ]; then
      remove_pin "$old"
      rm -f "$dir_file"
    fi
    note "no gradle/myitmoapi.ref in $path: no MyItmoApi pin needed"
    return 0
  fi
  sha=$(head -n 1 "$ref_file" | tr -d ' \t\r')
  [[ $sha =~ $SHA_RE ]] || { pin_fail "gradle/myitmoapi.ref does not hold a 40-hex commit"; return 1; }
  name=$(basename "$path")
  pin="$PINS/pin-$name-${sha:0:7}"
  m=$(main_dir myitmoapi)
  [ -d "$m/.git" ] || { pin_fail "MyItmoApi main checkout $m is missing"; return 1; }
  if [ "$old" = "$pin" ] && [ "$(git -C "$pin" rev-parse HEAD 2>/dev/null)" = "$sha" ]; then
    note "pin $pin is current"
    return 0
  fi
  if [ -e "$pin" ]; then
    if [ "$(git -C "$pin" rev-parse HEAD 2>/dev/null)" != "$sha" ]; then
      pin_fail "$pin exists but is not at ${sha:0:7}"
      return 1
    fi
  else
    git -C "$m" fetch --quiet origin || { pin_fail "git fetch origin failed in $m"; return 1; }
    git -C "$m" cat-file -e "$sha^{commit}" 2>/dev/null || { pin_fail "MyItmoApi has no commit ${sha:0:7} after the fetch"; return 1; }
    mkdir -p "$PINS" || { pin_fail "cannot create $PINS"; return 1; }
    git -C "$m" worktree add --quiet --detach "$pin" "$sha" || { pin_fail "cannot create the pin $pin"; return 1; }
  fi
  printf '%s\n' "$pin" > "$dir_file.tmp.$$" && mv -f "$dir_file.tmp.$$" "$dir_file" || { pin_fail "cannot write $dir_file"; return 1; }
  if [ -n "$old" ] && [ "$old" != "$pin" ]; then remove_pin "$old"; fi
  note "pinned MyItmoApi ${sha:0:7} at $pin"
  return 0
}

# ---- commands -------------------------------------------------------------------

cmd_new() {
  [ $# -ge 3 ] || die "usage: lane new <repo> <lane-id> <slug> [--base <ref>]"
  local repo=$1 lane_id=$2 slug=$3 base=""
  shift 3
  while [ $# -gt 0 ]; do
    case $1 in
      --base) [ $# -ge 2 ] || die "--base needs a ref"; base=$2; shift 2 ;;
      *) die "unknown argument '$1'" ;;
    esac
  done
  check_repo "$repo"
  [[ $lane_id =~ $LANE_RE ]] || die "lane id '$lane_id' is not L<nn>-<name> (the lane file name, e.g. L11-sport)"
  [[ $slug =~ $SLUG_RE ]] || die "slug '$slug' must match $SLUG_RE (<card-id lowercased>-<short-slug>)"
  case $slug in *..* | *. | *.lock | *-) die "slug '$slug' is not a valid ref component" ;; esac

  local lane_lc name path branch main start
  lane_lc=$(lower "$lane_id")
  name="$lane_lc-$slug"
  path="$WT/$repo/$name"
  branch="v2.3/$lane_lc/$slug"
  main=$(physical "$(main_dir "$repo")")
  check_place "$path"
  [ ! -e "$path" ] || die "$path already exists (lane list; lane done when finished)"

  if [ -n "$base" ]; then
    base=${base#origin/}
    case "$repo:$base" in
      *:release/?*) ;;
      core:master) ;;
      *) die "--base is only for PRs into release/* or core master, not '$base'" ;;
    esac
  else
    [ "$repo" != core ] || die "core has no v2.3/next: L01-CF uses --base master"
    base=v2.3/next
  fi

  git -C "$main" fetch --quiet origin || die "git fetch origin failed in $main"
  start="refs/remotes/origin/$base"
  git -C "$main" rev-parse -q --verify "$start^{commit}" > /dev/null || die "origin/$base does not exist yet"
  if git -C "$main" show-ref --verify -q "refs/heads/$branch"; then
    die "local branch $branch already exists: lane done --branch $repo $branch, or pick another slug"
  fi
  if git -C "$main" rev-parse -q --verify "refs/remotes/origin/$branch^{commit}" > /dev/null; then
    note "origin/$branch exists: resuming it"
    start="refs/remotes/origin/$branch"
  fi

  mkdir -p "$WT/$repo" || die "cannot create $WT/$repo"
  git -C "$main" worktree add --quiet --no-track -b "$branch" "$path" "$start" || die "git worktree add failed"
  write_marker "$path" "$lane_id"
  if [ "$repo" = android ]; then
    pin_mode=soft
    pin_worktree "$path" || true
  fi

  local lane_file="$LANES_DIR/$lane_id.md"
  [ -f "$lane_file" ] || note "warning: $lane_file does not exist; check the lane id"
  printf 'worktree  %s\n' "$path"
  printf 'branch    %s (from origin/%s at %s)\n' "$branch" "${start#refs/remotes/origin/}" "$(git -C "$path" rev-parse --short HEAD)"
  printf 'lane      %s\n' "$lane_file"
  printf 'recipe    %s\n' "$PROJ/ITMO.Widgets/vibe/v2.3/recipes/lane-workflow.md"
  printf 'first push: git -C %s push -u origin %s\n' "$path" "$branch"
}

cmd_next() {
  [ $# -eq 1 ] || die "usage: lane next <repo>"
  local repo=$1 path main start base
  check_repo "$repo"
  path="$WT/$repo/next"
  main=$(physical "$(main_dir "$repo")")
  check_place "$path"
  if [ -e "$path" ]; then
    [ "$(read_marker "$path")" = integrator ] || die "$path exists without the integrator marker"
    note "$path already exists"
    if [ "$repo" = android ]; then pin_worktree "$path"; fi
    printf 'worktree  %s\n' "$path"
    return 0
  fi
  git -C "$main" fetch --quiet origin || die "git fetch origin failed in $main"
  base=$(next_base "$repo")
  start="refs/remotes/origin/$base"
  if [ "$repo" != core ] && git -C "$main" rev-parse -q --verify "refs/remotes/origin/v2.3/next^{commit}" > /dev/null; then
    start="refs/remotes/origin/v2.3/next"
  fi
  git -C "$main" rev-parse -q --verify "$start^{commit}" > /dev/null || die "${start#refs/remotes/} does not exist"
  mkdir -p "$WT/$repo" || die "cannot create $WT/$repo"
  git -C "$main" worktree add --quiet --detach "$path" "$start" || die "git worktree add failed"
  write_marker "$path" integrator
  if [ "$repo" = android ]; then
    pin_mode=soft
    pin_worktree "$path" || true
  fi
  printf 'worktree  %s (integrator, detached at %s %s)\n' "$path" "${start#refs/remotes/}" "$(git -C "$path" rev-parse --short HEAD)"
}

cmd_pin() {
  [ $# -eq 1 ] || die "usage: lane pin <worktree>"
  resolve_worktree "$1"
  [ "$wt_repo" = android ] || die "only Android worktrees carry gradle/myitmoapi.ref"
  [ -n "$(read_marker "$wt_path")" ] || die "$wt_path has no itmo-lane marker; create worktrees with lane new|next"
  pin_mode=hard
  pin_worktree "$wt_path"
}

ensure_pushed() { # dir rev
  local extra
  extra=$(git -C "$1" rev-list --max-count=1 "$2" --not --remotes=origin) || die "cannot inspect $2"
  [ -z "$extra" ] || die "$2 has commits on no origin branch (${extra:0:7}): push them, or keep the worktree"
}

ensure_no_open_pr() { # repo branch
  local prs
  prs=$(open_prs "$1" "$2") || die "cannot list PRs for $2 (gh failed); refusing without that check"
  [ -z "$prs" ] || die "$2 has an open PR (#$(printf '%s' "$prs" | head -n 1)); wait for the merge comment"
}

cmd_done() {
  [ $# -ge 1 ] || die "usage: lane done <worktree> | lane done --branch <repo> <branch>"
  if [ "$1" = --branch ]; then
    shift
    cmd_done_branch "$@"
    return
  fi
  [ $# -eq 1 ] || die "usage: lane done <worktree>"
  resolve_worktree "$1"
  local path=$wt_path repo=$wt_repo marker lane_lc branch gd main dirty pin=""
  [ "$wt_name" != next ] || die "the integrator worktree $path stays"
  marker=$(read_marker "$path")
  [[ $marker =~ $LANE_RE ]] || die "$path has no lane marker (found '${marker:-none}')"
  lane_lc=$(lower "$marker")
  branch=$(git -C "$path" symbolic-ref -q --short HEAD) || branch=""
  if [ -n "$branch" ]; then
    case $branch in "v2.3/$lane_lc/"?*) ;; *) die "$path is on $branch, not a v2.3/$lane_lc/* branch" ;; esac
  fi
  gd=$(git_dir_of "$path")
  if [ -d "$gd/rebase-merge" ] || [ -d "$gd/rebase-apply" ] || [ -f "$gd/MERGE_HEAD" ] || [ -f "$gd/CHERRY_PICK_HEAD" ]; then
    die "$path has a rebase, merge or cherry-pick in progress"
  fi
  dirty=$(git -C "$path" status --porcelain --untracked-files=all) || die "git status failed in $path"
  [ -z "$dirty" ] || die "$path has uncommitted changes:
$dirty"
  git -C "$path" fetch --quiet origin || die "git fetch origin failed"
  ensure_pushed "$path" HEAD
  if [ -n "$branch" ]; then
    ensure_no_open_pr "$repo" "$branch"
    if git -C "$path" rev-parse -q --verify "refs/remotes/origin/$branch" > /dev/null; then
      git -C "$path" push --quiet origin --delete "$branch" || die "deleting origin/$branch failed (see the guard output)"
      note "deleted origin/$branch"
    fi
  fi
  [ -f "$gd/itmo-myitmoapi-dir" ] && pin=$(head -n 1 "$gd/itmo-myitmoapi-dir")
  main=$(physical "$(main_dir "$repo")")
  git -C "$main" worktree remove --force "$path" || die "git worktree remove failed for $path"
  case $path in "$WT/$repo"/?*) [ ! -e "$path" ] || rm -rf "$path" ;; esac
  [ -z "$pin" ] || remove_pin "$pin"
  if [ -n "$branch" ] && git -C "$main" show-ref --verify -q "refs/heads/$branch"; then
    git -C "$main" branch --quiet -D "$branch" || die "deleting local $branch failed"
  fi
  git -C "$main" worktree prune
  [ ! -d "$(main_dir myitmoapi)/.git" ] || git -C "$(main_dir myitmoapi)" worktree prune
  note "done: removed $path${branch:+ and $branch}"
}

cmd_done_branch() {
  [ $# -eq 2 ] || die "usage: lane done --branch <repo> <branch>"
  local repo=$1 branch=${2#refs/heads/} main lane_lc lane_id tmp have_local=no have_remote=no rc
  check_repo "$repo"
  [[ $branch =~ ^v2\.3/(l[0-9][0-9]-[a-z0-9]+(-[a-z0-9]+)*)/[a-z0-9][a-z0-9.-]*$ ]] || die "'$branch' is not a v2.3/<lane>/<slug> branch"
  lane_lc=${BASH_REMATCH[1]}
  lane_id="L${lane_lc#l}"
  main=$(physical "$(main_dir "$repo")")
  if git -C "$main" worktree list --porcelain | grep -qx "branch refs/heads/$branch"; then
    die "$branch is checked out in a worktree: lane done <worktree>"
  fi
  git -C "$main" fetch --quiet origin || die "git fetch origin failed in $main"
  git -C "$main" show-ref --verify -q "refs/heads/$branch" && have_local=yes
  git -C "$main" rev-parse -q --verify "refs/remotes/origin/$branch" > /dev/null && have_remote=yes
  if [ $have_local = no ] && [ $have_remote = no ]; then
    note "$branch exists neither locally nor on origin"
    return 0
  fi
  ensure_no_open_pr "$repo" "$branch"
  [ $have_local = no ] || ensure_pushed "$main" "refs/heads/$branch"
  if [ $have_remote = yes ]; then
    tmp="$WT/$repo/$lane_lc-cleanup-$$"
    check_place "$tmp"
    [ ! -e "$tmp" ] || die "$tmp already exists"
    git -C "$main" worktree add --quiet --no-checkout --detach "$tmp" "refs/remotes/origin/$branch" || die "cannot create $tmp"
    write_marker "$tmp" "$lane_id"
    git -C "$tmp" push --quiet origin --delete "$branch"
    rc=$?
    git -C "$main" worktree remove --force "$tmp" 2>/dev/null || rm -rf "$tmp"
    [ $rc -eq 0 ] || die "deleting origin/$branch failed (see the guard output)"
    note "deleted origin/$branch"
  fi
  if [ $have_local = yes ]; then
    git -C "$main" branch --quiet -D "$branch" || die "deleting local $branch failed"
    note "deleted local $branch"
  fi
  git -C "$main" worktree prune
}

age_of() { # file -> 3d / 5h / 12m
  local t now s
  t=$(stat -f %m "$1" 2>/dev/null || stat -c %Y "$1" 2>/dev/null) || { printf '?'; return; }
  now=$(date +%s)
  s=$((now - t))
  if [ $s -ge 86400 ]; then printf '%sd' $((s / 86400))
  elif [ $s -ge 3600 ]; then printf '%sh' $((s / 3600))
  else printf '%sm' $((s / 60)); fi
}

cmd_list() {
  local only="" offline=no
  while [ $# -gt 0 ]; do
    case $1 in
      --offline) offline=yes ;;
      *) check_repo "$1"; only=$1 ;;
    esac
    shift
  done
  local r d gd top marker branch pr pin used="" age
  printf '%-48s %-18s %-44s %-14s %-5s %s\n' WORKTREE LANE BRANCH PR AGE PIN
  for r in ${only:-$REPOS}; do
    for d in "$WT/$r"/*; do
      [ -d "$d" ] || continue
      case $(basename "$d") in pin-*) continue ;; esac
      top=$(git -C "$d" rev-parse --show-toplevel 2>/dev/null) && top=$(physical "$top")
      if [ "${top:-}" != "$(physical "$d")" ]; then
        printf '%-48s %s\n' "${d#"$WT"/}" "(not a worktree)"
        continue
      fi
      gd=$(git_dir_of "$d")
      marker=$(read_marker "$d")
      branch=$(git -C "$d" symbolic-ref -q --short HEAD) || branch="detached@$(git -C "$d" rev-parse --short HEAD)"
      pr=-
      case $branch in
        v2.3/*)
          if [ $offline = no ]; then
            pr=$(gh pr list --repo "$(gh_slug "$r")" --head "$branch" --state all --limit 1 \
              --json number,state --jq '.[] | "#\(.number) \(.state)"' 2>/dev/null) || pr="?"
            [ -n "$pr" ] || pr=none
          fi
          ;;
      esac
      if [ -f "$gd/itmo-lane" ]; then age=$(age_of "$gd/itmo-lane"); else age=$(age_of "$d"); fi
      pin=-
      if [ -f "$gd/itmo-myitmoapi-dir" ]; then
        pin=$(head -n 1 "$gd/itmo-myitmoapi-dir")
        used="$used
$pin"
        [ -d "$pin" ] || pin="$pin (missing)"
        pin=${pin#"$WT"/}
      fi
      printf '%-48s %-18s %-44s %-14s %-5s %s\n' "${d#"$WT"/}" "${marker:-none}" "$branch" "$pr" "$age" "$pin"
    done
  done
  if [ -z "$only" ] || [ "$only" = myitmoapi ] || [ "$only" = android ]; then
    for d in "$PINS"/pin-*; do
      [ -d "$d" ] || continue
      printf '%s\n' "$used" | grep -Fqx "$d" || printf 'unused pin: %s (remove with git -C %s worktree remove %s)\n' \
        "${d#"$WT"/}" "$(main_dir myitmoapi)" "$d"
    done
  fi
}

[ $# -ge 1 ] || die "usage: lane new|next|pin|done|list ... (lane --help)"
cmd=$1
shift
case $cmd in
  new) cmd_new "$@" ;;
  next) cmd_next "$@" ;;
  pin) cmd_pin "$@" ;;
  done) cmd_done "$@" ;;
  list) cmd_list "$@" ;;
  -h | --help | help) awk 'NR > 1 && !/^#/ { exit } NR > 1' "$0" ;;
  *) die "unknown command '$cmd' (new, next, pin, done, list)" ;;
esac
