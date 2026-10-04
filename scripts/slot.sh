#!/usr/bin/env bash
# slot.sh <android|kn|backend|jvm|emulator> [--timeout <s>] -- <cmd...>
# slot.sh status
#
# Runs one heavy job under the machine-wide build slots (master plan §7.5, L04 TC-12).
#
# - Locks are /usr/bin/lockf files in ~/.cache/itmo-agents/slots: <kind>.<n>.lock (n = 1..count) and
#   global.<n>.lock (2 half-unit tokens per heavy unit of the global budget). flock does not exist on macOS.
# - The kind slot is taken first, then the kind's global tokens in ascending order, all with `lockf -t 0`;
#   on any miss everything is released and the script backs off (no hold-and-wait).
# - Counts and weights come from slots.conf next to this script, then ~/.config/itmo-agents/slots.conf
#   (owner's machine posture). ITMO_SLOTS_CONF (replaces both) and ITMO_SLOT_DIR are for tests only.
# - Android slots drop to `android.emulators_full` while every emulator slot is held.
# - A call made inside a held slot (ITMO_SLOT_HELD set) runs the command directly, so nested calls never wait.
# - Exports to the command: ITMO_SLOT_HELD, ITMO_MAX_WORKERS = max(2, 28 / global tokens held machine-wide),
#   ANDROID_HOME (android, kn, emulator) and JAVA_HOME = JDK 21 (android, kn) unless already set.
# - With CI=true or without /usr/bin/lockf (Linux) the command runs directly.
# - Exit code: the command's own (0, 1, 75 alike); 75 from slot.sh itself only when --timeout expires
#   (with a message on stderr); 2 for usage errors. Signals are forwarded to the job only.
# - Each run is logged to ~/.cache/itmo-agents/slots.log: kind, slot, tokens, wait, run time, exit, cwd.

set -u

me=slot.sh
KINDS="android kn backend jvm emulator"
LOCKF=/usr/bin/lockf
MAX_WORKERS_BUDGET=28 # half-units: 14 cores, one worker per core when a single Android job runs

die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }
usage() { die "usage: slot.sh <android|kn|backend|jvm|emulator> [--timeout <s>] -- <cmd...> | slot.sh status"; }

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd -P) || die "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
slot_dir=${ITMO_SLOT_DIR:-$HOME/.cache/itmo-agents/slots}
log_file="$(dirname "$slot_dir")/slots.log"

is_kind() { case " $KINDS " in *" $1 "*) return 0 ;; esac; return 1; }

# ---- configuration -----------------------------------------------------------------------------------

cfg_android=3 cfg_kn=1 cfg_backend=1 cfg_jvm=2 cfg_emulator=2 cfg_global=4 cfg_android_emulators_full=2
cfg_weight_android=2 cfg_weight_kn=4 cfg_weight_backend=2 cfg_weight_jvm=1 cfg_weight_emulator=0
conf_files=""

load_conf() { # file
  local line key val
  [ -f "$1" ] || return 0
  conf_files="$conf_files $1"
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%%#*}
    line=$(printf '%s' "$line" | tr -d ' \t\r')
    [ -n "$line" ] || continue
    key=${line%%=*}
    val=${line#*=}
    [[ $key =~ ^[a-z]+(\.[a-z_]+)?$ && $val =~ ^[0-9]+$ ]] || die "$1: bad line '$line'"
    key=${key//./_}
    printf -v "cfg_$key" '%s' "$val"
  done < "$1"
}

cfg() { # key -> value
  local v="cfg_${1//./_}"
  printf '%s' "${!v:-0}"
}

if [ -n "${ITMO_SLOTS_CONF:-}" ]; then
  [ -f "$ITMO_SLOTS_CONF" ] || die "ITMO_SLOTS_CONF=$ITMO_SLOTS_CONF does not exist"
  load_conf "$ITMO_SLOTS_CONF"
else
  load_conf "$script_dir/slots.conf"
  load_conf "$HOME/.config/itmo-agents/slots.conf"
fi
tokens_total=$(($(cfg global) * 2))

# ---- locks ---------------------------------------------------------------------------------------------

is_free() { # lock file -> 0 when nobody holds it (takes it for an instant)
  "$LOCKF" -s -k -t 0 "$1" true
}

effective_count() { # kind
  local count n held=0 emu
  count=$(cfg "$1")
  if [ "$1" = android ]; then
    emu=$(cfg emulator)
    if [ "$emu" -gt 0 ]; then
      for ((n = 1; n <= emu; n++)); do is_free "$slot_dir/emulator.$n.lock" || held=$((held + 1)); done
      if [ "$held" -ge "$emu" ] && [ "$(cfg android.emulators_full)" -lt "$count" ]; then
        count=$(cfg android.emulators_full)
      fi
    fi
  fi
  printf '%s' "$count"
}

global_held() { # [own tokens] -> number of global tokens held machine-wide (own tokens counted as held)
  local own=" ${1:-} " t held=0
  for ((t = 1; t <= tokens_total; t++)); do
    case "$own" in *" $t "*) held=$((held + 1)); continue ;; esac
    is_free "$slot_dir/global.$t.lock" || held=$((held + 1))
  done
  printf '%s' "$held"
}

log_run() { # kind slot tokens wait run exit
  mkdir -p "$(dirname "$log_file")" 2> /dev/null
  printf '%s kind=%s slot=%s tokens=%s wait=%ss run=%ss exit=%s cwd=%s\n' \
    "$(date '+%Y-%m-%dT%H:%M:%S')" "$1" "$2" "${3:-none}" "$4" "$5" "$6" "$PWD" >> "$log_file" 2> /dev/null || true
}

export_env() { # kind
  case "$1" in
    android | kn | emulator) export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ;;
  esac
  case "$1" in
    android | kn)
      if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
        JAVA_HOME=$(/usr/libexec/java_home -v 21 2> /dev/null) && export JAVA_HOME
      fi
      ;;
  esac
}

# ---- the job inside the locks (internal: slot.sh __run <acq> <kind> <slot> <tokens> -- <cmd...>) -------

run_inner() {
  local acq=$1 kind=$2 slot=$3 tokens=$4 held workers
  shift 4
  [ "${1:-}" = "--" ] && shift
  printf '%s\n' "$$" > "$acq"
  printf 'pid=%s since=%s cwd=%s cmd=%s\n' "$$" "$(date '+%Y-%m-%dT%H:%M:%S')" "$PWD" "$*" \
    > "$slot_dir/$kind.$slot.owner" 2> /dev/null || true
  held=$(global_held "${tokens//,/ }")
  [ "$held" -gt 0 ] || held=1
  workers=$((MAX_WORKERS_BUDGET / held))
  [ "$workers" -ge 2 ] || workers=2
  export ITMO_SLOT_HELD="$kind" ITMO_SLOT_ID="$kind.$slot" ITMO_MAX_WORKERS="$workers"
  # A handler (not an ignore) so the command still gets the signal with default handling, and this
  # shell survives to report the command's exit code instead of lockf's EX_SOFTWARE.
  trap ':' INT TERM HUP
  "$@"
  exit $?
}

# ---- status ------------------------------------------------------------------------------------------

show_status() {
  local kind count eff n file state owner
  mkdir -p "$slot_dir"
  printf 'slot dir: %s\nconfig:%s\nlog: %s\n' "$slot_dir" "${conf_files:- (defaults)}" "$log_file"
  for kind in $KINDS; do
    count=$(cfg "$kind")
    eff=$(effective_count "$kind")
    printf '%-8s count %s' "$kind" "$count"
    [ "$eff" != "$count" ] && printf ' (now %s: emulator slots full)' "$eff"
    printf ', weight %s half-units\n' "$(cfg "weight.$kind")"
    for ((n = 1; n <= count; n++)); do
      file="$slot_dir/$kind.$n.lock"
      if is_free "$file"; then
        state=free
      else
        state=held
        owner=$(head -n 1 "$slot_dir/$kind.$n.owner" 2> /dev/null || true)
        [ -n "$owner" ] && state="held $owner"
      fi
      printf '  %s.%s %s\n' "$kind" "$n" "$state"
    done
  done
  printf 'global   %s of %s tokens held\n' "$(global_held)" "$tokens_total"
}

# ---- one job -------------------------------------------------------------------------------------------

got_signal=""
child=""
forward() { # signal
  got_signal=$1
  [ -n "$child" ] && kill -"$1" -- "-$child" 2> /dev/null
}

signum() { case "$1" in INT) echo 2 ;; HUP) echo 1 ;; *) echo 15 ;; esac; }

run_job() { # kind timeout cmd...
  local kind=$1 timeout=$2 start now waited eff slot need picked t rc acq
  local -a chain
  local next_note=0 run_start backoff
  shift 2
  mkdir -p "$slot_dir" || die "cannot create $slot_dir"
  acq="$slot_dir/.acquired.$$"
  start=$(date +%s)
  while :; do
    eff=$(effective_count "$kind")
    slot=""
    for ((t = 1; t <= eff; t++)); do
      if is_free "$slot_dir/$kind.$t.lock"; then slot=$t; break; fi
    done
    picked=""
    need=$(cfg "weight.$kind")
    if [ -n "$slot" ]; then
      for ((t = 1; t <= tokens_total && need > 0; t++)); do
        if is_free "$slot_dir/global.$t.lock"; then picked="$picked,$t"; need=$((need - 1)); fi
      done
    fi
    picked=${picked#,}
    if [ -n "$slot" ] && [ "$need" -le 0 ]; then
      chain=("$LOCKF" -s -k -t 0 "$slot_dir/$kind.$slot.lock")
      for t in ${picked//,/ }; do chain+=("$LOCKF" -s -k -t 0 "$slot_dir/global.$t.lock"); done
      rm -f "$acq"
      run_start=$(date +%s)
      trap 'forward INT' INT
      trap 'forward TERM' TERM
      trap 'forward HUP' HUP
      set -m # own process group: forwarded signals reach the job and nothing else
      # stdin from /dev/null: a job outside the terminal's foreground group must never read the tty.
      "${chain[@]}" /bin/bash "$self" __run "$acq" "$kind" "$slot" "$picked" -- "$@" < /dev/null &
      child=$!
      set +m
      while :; do
        wait "$child"
        rc=$?
        kill -0 "$child" 2> /dev/null || break
      done
      trap - INT TERM HUP
      child=""
      if [ -f "$acq" ]; then
        rm -f "$acq"
        now=$(date +%s)
        log_run "$kind" "$slot" "$picked" "$((run_start - start))" "$((now - run_start))" "$rc"
        [ -n "$got_signal" ] && [ "$rc" -eq 0 ] && rc=$((128 + $(signum "$got_signal")))
        return "$rc"
      fi
      [ -n "$got_signal" ] && return $((128 + $(signum "$got_signal")))
      [ "$rc" -eq 75 ] || die "lockf failed with exit $rc on $kind.$slot"
    fi
    now=$(date +%s)
    waited=$((now - start))
    if [ -n "$timeout" ] && [ "$waited" -ge "$timeout" ]; then
      log_run "$kind" - - "$waited" 0 "75(timeout)"
      note "no $kind slot within ${timeout}s"
      return 75
    fi
    if [ "$waited" -ge "$next_note" ]; then
      note "$(date '+%H:%M:%S') waiting for the $kind slot (${waited}s so far; \`slot.sh status\` shows the holders)"
      next_note=$((waited + 300))
    fi
    backoff=$((1 + RANDOM % 3))
    # Never sleep past the deadline: a later attempt could take a slot that freed after it.
    if [ -n "$timeout" ] && [ "$backoff" -gt $((timeout - waited)) ]; then backoff=$((timeout - waited)); fi
    sleep "$backoff"
  done
}

# ---- main ----------------------------------------------------------------------------------------------

[ $# -ge 1 ] || usage
case "$1" in
  __run)
    shift
    [ $# -ge 5 ] || usage
    run_inner "$@"
    ;;
  status)
    [ -x "$LOCKF" ] || die "status needs /usr/bin/lockf"
    show_status
    exit 0
    ;;
esac

kind=$1
shift
is_kind "$kind" || usage
timeout=""
while [ $# -gt 0 ] && [ "$1" != "--" ]; do
  case "$1" in
    --timeout)
      [ $# -ge 2 ] && [[ $2 =~ ^[0-9]+$ ]] || die "--timeout needs whole seconds"
      timeout=$2
      shift 2
      ;;
    *) usage ;;
  esac
done
[ "${1:-}" = "--" ] && [ $# -ge 2 ] || usage
shift

export_env "$kind"

if [ -n "${ITMO_SLOT_HELD:-}" ]; then
  note "inside held slot '$ITMO_SLOT_HELD': running the $kind job directly"
  exec "$@"
fi

if [ "${CI:-}" = "true" ] || [ ! -x "$LOCKF" ]; then
  export ITMO_SLOT_HELD="$kind"
  exec "$@"
fi

run_job "$kind" "$timeout" "$@"
exit $?
