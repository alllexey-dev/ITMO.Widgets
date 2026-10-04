#!/usr/bin/env bash
# emulator.sh up [--api 35|30] [--timeout <s>] [--boot-timeout <s>]   start a pool emulator, print ANDROID_SERIAL=...
# emulator.sh up --integrator [--timeout <s>] [--boot-timeout <s>]   Pixel_7 on 5554 (integrator worktree only)
# emulator.sh down                                                    stop the emulator this worktree started
# emulator.sh list                                                    pool AVDs, emulator slots, running emulators
# emulator.sh init [--dry-run]                                        owner only: create the pool AVDs once
#
# The emulator pool for `scripts/verify.sh ui` (master plan section 7.5, L04 TC-13; recipe emulator-ui-run).
#
# - Pool AVDs: itmo-pool-api35 (system-images;android-35;google_apis_playstore;arm64-v8a) and itmo-pool-api30
#   (system-images;android-30;google_apis;arm64-v8a) in ~/.android/avd, Pixel 7 geometry. `init` writes their .ini
#   and config.ini itself (no avdmanager) and cold-boots each once to create its data partition. It needs an
#   interactive terminal and a typed `yes`; `--dry-run` only prints. Existing AVDs, Pixel_7 above all, are never
#   modified.
# - `up` holds one `scripts/slot.sh emulator` slot for the emulator's lifetime: a detached slot holder runs the
#   emulator in the foreground inside the slot. Emulator slot <n> listens on port 5558 + 2n (5560, 5562, ...;
#   never 5554 or 5556). Pool emulators run headless with -read-only -no-snapshot-save, so every boot is clean.
#   `up` waits for sys.boot_completed and prints `ANDROID_SERIAL=emulator-<port>` as its only stdout line.
# - `up --integrator` (only when the worktree's itmo-lane marker reads `integrator`) starts Pixel_7 on 5554 without
#   -read-only, or adopts a running emulator-5554; either way it holds an emulator slot until `down`.
# - `down` stops only what this worktree started (state file in the worktree's git dir); an adopted emulator-5554
#   keeps running and only its slot is released.
# - The emulator binary is $ANDROID_HOME/emulator/emulator (not on PATH); ANDROID_HOME defaults to
#   ~/Library/Android/sdk. ITMO_SLOT_SH overrides the slot script, ITMO_SLOTS_CONF its configuration (tests).
# - Exit code: 0 ok, 1 failed (emulator died or did not boot), 2 refused (usage, no AVD, unsafe request),
#   75 no emulator slot within --timeout (nothing was started).

set -u

me=emulator.sh
POOL_APIS="35 30"
INTEGRATOR_AVD=Pixel_7
INTEGRATOR_PORT=5554
FIRST_POOL_PORT=5560

die() { printf '%s: %s\n' "$me" "$*" >&2; exit 2; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }
usage() {
  sed -n '2,6p' "$self" | sed 's/^# //' >&2
  exit 2
}

script_dir=$(cd "$(dirname "$0")" 2> /dev/null && pwd -P) || die "cannot resolve the script directory"
self="$script_dir/$(basename "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
emulator_bin="$ANDROID_HOME/emulator/emulator"
adb="$ANDROID_HOME/platform-tools/adb"
avd_root="$HOME/.android/avd"
slot_sh=${ITMO_SLOT_SH:-$script_dir/slot.sh}

pool_avd() { printf 'itmo-pool-api%s' "$1"; }

sysdir_rel() { # api -> system image directory relative to ANDROID_HOME
  case "$1" in
    35) printf 'system-images/android-35/google_apis_playstore/arm64-v8a/' ;;
    30) printf 'system-images/android-30/google_apis/arm64-v8a/' ;;
    *) return 1 ;;
  esac
}

tag_id() { case "$1" in 35) printf google_apis_playstore ;; *) printf google_apis ;; esac; }

state_paths() { # sets state_file, holder_file, log_file for this worktree
  local git_dir
  git_dir=$(git -C "$script_dir" rev-parse --absolute-git-dir 2> /dev/null) || die "not inside a git worktree"
  state_file="$git_dir/itmo-emulator.state"
  holder_file="$git_dir/itmo-emulator.holder"
  log_file="$git_dir/itmo-emulator.log"
}

lane_marker() {
  local git_dir
  git_dir=$(git -C "$script_dir" rev-parse --absolute-git-dir 2> /dev/null) || return 0
  head -n 1 "$git_dir/itmo-lane" 2> /dev/null | tr -d ' \t\r'
}

state_get() { # key -> value from the state file
  sed -n "s/^$1=//p" "$state_file" 2> /dev/null | head -n 1
}

alive() { [ -n "${1:-}" ] && kill -0 "$1" 2> /dev/null; }

adb_state() { # serial -> device|offline|... (empty when unknown)
  "$adb" -s "$1" get-state 2> /dev/null | tr -d ' \t\r'
}

port_busy() { # port -> 0 when something listens on 127.0.0.1:<port>
  /usr/bin/nc -z 127.0.0.1 "$1" > /dev/null 2>&1
}

running() { # "<port> <avd>" per running emulator, from the qemu command lines (no adb needed)
  ps -axo command= 2> /dev/null | awk '/qemu-system-[^ ]* / {
      avd = ""; port = ""
      for (i = 1; i < NF; i++) { if ($i == "-avd") avd = $(i + 1); if ($i == "-port") port = $(i + 1) }
      if (avd != "") print (port == "" ? "5554" : port), avd
    }' | sort -n
}

# ---- the slot holder (internal: emulator.sh __hold <state file> <mode> <avd>) ---------------------------
#
# Runs inside `slot.sh emulator`, so the slot is held exactly as long as this process lives. Modes: pool
# (read-only pool AVD), firstboot (init's one cold boot that creates the data partition), integrator (Pixel_7 on
# 5554), adopt (keeps the slot while a running emulator-5554 stays online).

hold() {
  local state=$1 mode=$2 avd=$3 slot port
  slot=${ITMO_SLOT_ID#emulator.}
  if ! [[ $slot =~ ^[0-9]+$ ]]; then
    printf 'error=no emulator slot id (ITMO_SLOT_ID=%s)\n' "${ITMO_SLOT_ID:-}" > "$state"
    exit 2
  fi
  case "$mode" in
    integrator | adopt) port=$INTEGRATOR_PORT ;;
    *) port=$((FIRST_POOL_PORT - 2 + 2 * slot)) ;;
  esac
  if [ "$mode" = adopt ]; then
    printf 'pid=%s\nport=%s\navd=%s\nmode=%s\nslot=%s\n' "$$" "$port" "$avd" "$mode" "$ITMO_SLOT_ID" > "$state"
    trap 'exit 0' TERM INT HUP
    while [ "$(adb_state "emulator-$port")" = device ]; do sleep 15 & wait $!; done
    exit 0
  fi
  if [ ! -f "$avd_root/$avd.ini" ]; then
    printf 'error=no AVD %s in %s (the owner runs `scripts/emulator.sh init` once)\n' "$avd" "$avd_root" > "$state"
    exit 2
  fi
  if port_busy "$port" || port_busy $((port + 1)); then
    printf 'error=port %s or %s is in use by a process outside the pool\n' "$port" $((port + 1)) > "$state"
    exit 1
  fi
  local -a args=(-avd "$avd" -port "$port" -no-window -no-audio -no-boot-anim)
  case "$mode" in
    pool) args+=(-read-only -no-snapshot-save) ;;
    firstboot) args+=(-no-snapshot) ;;
  esac
  # exec keeps this pid, so the recorded pid is the emulator's own.
  printf 'pid=%s\nport=%s\navd=%s\nmode=%s\nslot=%s\n' "$$" "$port" "$avd" "$mode" "$ITMO_SLOT_ID" > "$state"
  exec "$emulator_bin" "${args[@]}"
}

# ---- up ----------------------------------------------------------------------------------------------

# start <mode> <avd> <slot timeout> <boot timeout>: prints the serial on stdout, returns 0/1/2/75.
start() {
  local mode=$1 avd=$2 slot_timeout=$3 boot_timeout=$4 holder rc waited=0 port serial booted err
  rm -f "$state_file" "$holder_file"
  : > "$log_file"
  # setsid: the holder outlives this shell and its session; stdin from /dev/null, output to the log.
  /usr/bin/perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV or die "exec: $!\n"' \
    "$slot_sh" emulator ${slot_timeout:+--timeout "$slot_timeout"} -- "$self" __hold "$state_file" "$mode" "$avd" \
    < /dev/null >> "$log_file" 2>&1 &
  holder=$!
  printf '%s\n' "$holder" > "$holder_file"
  note "waiting for an emulator slot (holder pid $holder, log $log_file)"
  while [ ! -s "$state_file" ]; do
    if ! alive "$holder"; then
      wait "$holder"
      rc=$?
      rm -f "$holder_file"
      sed 's/^/  /' "$log_file" >&2
      [ "$rc" -eq 75 ] && note "no emulator slot within ${slot_timeout}s; nothing was started"
      [ "$rc" -eq 0 ] && rc=1
      return "$rc"
    fi
    sleep 1
  done
  err=$(state_get error)
  if [ -n "$err" ]; then
    wait "$holder"
    rc=$?
    rm -f "$holder_file" "$state_file"
    note "$err"
    [ "$rc" -eq 0 ] && rc=1
    return "$rc"
  fi
  port=$(state_get port)
  serial="emulator-$port"
  note "slot $(state_get slot): $(state_get avd) on port $port ($(state_get mode)); waiting for boot"
  "$adb" start-server > /dev/null 2>&1
  while :; do
    if ! alive "$(state_get pid)"; then
      note "the emulator exited before it booted; log:"
      sed 's/^/  /' "$log_file" >&2
      stop
      return 1
    fi
    booted=$("$adb" -s "$serial" shell getprop sys.boot_completed 2> /dev/null | tr -d ' \t\r')
    [ "$booted" = 1 ] && break
    if [ "$waited" -ge "$boot_timeout" ]; then
      note "$serial did not boot within ${boot_timeout}s; stopping it"
      stop
      return 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  note "$serial booted"
  printf 'ANDROID_SERIAL=%s\n' "$serial"
  return 0
}

cmd_up() {
  local api=35 integrator=0 slot_timeout=1800 boot_timeout=300 mode avd port pid
  while [ $# -gt 0 ]; do
    case "$1" in
      --api) [ $# -ge 2 ] && sysdir_rel "$2" > /dev/null || die "--api takes 35 or 30"; api=$2; shift 2 ;;
      --integrator) integrator=1; shift ;;
      --timeout)
        [ $# -ge 2 ] && [[ $2 =~ ^[0-9]+$ ]] || die "--timeout needs whole seconds"
        slot_timeout=$2
        shift 2
        ;;
      --boot-timeout)
        [ $# -ge 2 ] && [[ $2 =~ ^[0-9]+$ ]] || die "--boot-timeout needs whole seconds"
        boot_timeout=$2
        shift 2
        ;;
      *) usage ;;
    esac
  done
  [ -x "$slot_sh" ] || die "no executable slot script at $slot_sh"
  [ -z "${ITMO_SLOT_HELD:-}" ] || die "up runs outside any slot (ITMO_SLOT_HELD=$ITMO_SLOT_HELD); it takes its own"
  state_paths
  pid=$(state_get pid)
  if alive "$pid"; then
    port=$(state_get port)
    if [ "$(adb_state "emulator-$port")" = device ]; then
      note "this worktree already runs $(state_get avd) on emulator-$port"
      printf 'ANDROID_SERIAL=emulator-%s\n' "$port"
      return 0
    fi
    die "this worktree's emulator (pid $pid, port $port) is not online; run \`$me down\` first"
  fi
  rm -f "$state_file" "$holder_file"
  if [ "$integrator" -eq 1 ]; then
    [ "$(lane_marker)" = integrator ] || die "--integrator only from a worktree whose itmo-lane marker reads integrator"
    if [ "$(adb_state "emulator-$INTEGRATOR_PORT")" = device ]; then
      mode=adopt
      note "emulator-$INTEGRATOR_PORT is already running; adopting it into an emulator slot"
    else
      mode=integrator
    fi
    avd=$INTEGRATOR_AVD
  else
    mode=pool
    avd=$(pool_avd "$api")
  fi
  [ -x "$emulator_bin" ] || [ "$mode" = adopt ] || die "no emulator at $emulator_bin (set ANDROID_HOME)"
  [ -x "$adb" ] || die "no adb at $adb (set ANDROID_HOME)"
  start "$mode" "$avd" "$slot_timeout" "$boot_timeout"
}

# ---- down --------------------------------------------------------------------------------------------

# stop: ends this worktree's emulator (or releases an adopted one) and waits for the slot holder to exit.
stop() {
  local pid port mode holder waited=0 cmd
  pid=$(state_get pid)
  port=$(state_get port)
  mode=$(state_get mode)
  holder=$(head -n 1 "$holder_file" 2> /dev/null)
  if alive "$pid"; then
    if [ "$mode" = adopt ]; then
      kill -TERM "$pid" 2> /dev/null
    else
      # The recorded pid is the emulator launcher (exec'd by the holder); qemu may run as its child.
      cmd=$(ps -p "$pid" -o command= 2> /dev/null)
      case "$cmd" in
        *emulator* | *qemu*) ;;
        *) note "pid $pid is no longer this worktree's emulator; not touching it"; pid="" ;;
      esac
      if [ -n "$pid" ]; then
        "$adb" -s "emulator-$port" emu kill > /dev/null 2>&1
        while alive "$pid" && [ "$waited" -lt 30 ]; do sleep 1; waited=$((waited + 1)); done
        if alive "$pid"; then
          /usr/bin/pkill -TERM -P "$pid" 2> /dev/null
          kill -TERM "$pid" 2> /dev/null
        fi
        waited=0
        while alive "$pid" && [ "$waited" -lt 15 ]; do sleep 1; waited=$((waited + 1)); done
        if alive "$pid"; then
          /usr/bin/pkill -KILL -P "$pid" 2> /dev/null
          kill -KILL "$pid" 2> /dev/null
        fi
      fi
    fi
  fi
  waited=0
  while alive "$holder" && [ "$waited" -lt 15 ]; do sleep 1; waited=$((waited + 1)); done
  alive "$holder" && note "slot holder $holder is still running; \`slot.sh status\` shows the emulator slots"
  rm -f "$state_file" "$holder_file"
  return 0
}

cmd_down() {
  [ $# -eq 0 ] || usage
  state_paths
  if [ ! -f "$state_file" ] && [ ! -f "$holder_file" ]; then
    note "this worktree runs no emulator"
    return 0
  fi
  note "stopping $(state_get avd) on emulator-$(state_get port) ($(state_get mode))"
  stop
  note "done"
}

# ---- list --------------------------------------------------------------------------------------------

cmd_list() {
  local api avd pid
  [ $# -eq 0 ] || usage
  state_paths
  printf 'pool AVDs (%s):\n' "$avd_root"
  for api in $POOL_APIS; do
    avd=$(pool_avd "$api")
    if [ -f "$avd_root/$avd.ini" ]; then
      printf '  %-16s present\n' "$avd"
    else
      printf '  %-16s missing (owner: scripts/emulator.sh init)\n' "$avd"
    fi
  done
  printf 'emulator slots:\n'
  if [ -x "$slot_sh" ]; then
    "$slot_sh" status 2> /dev/null | grep -E '^(emulator |  emulator\.)' | sed 's/^/  /'
  else
    printf '  no slot script at %s\n' "$slot_sh"
  fi
  printf 'running emulators (from the process table):\n'
  running | while read -r port name; do printf '  emulator-%-6s %s\n' "$port" "$name"; done
  pid=$(state_get pid)
  if alive "$pid"; then
    printf 'this worktree: %s on emulator-%s (%s, pid %s)\n' "$(state_get avd)" "$(state_get port)" \
      "$(state_get mode)" "$pid"
  else
    printf 'this worktree: none\n'
  fi
}

# ---- init (owner only) ---------------------------------------------------------------------------------

avd_ini() { # avd api
  printf 'avd.ini.encoding=UTF-8\npath=%s/%s.avd\npath.rel=avd/%s.avd\ntarget=android-%s\n' \
    "$avd_root" "$1" "$1" "$2"
}

config_ini() { # avd api
  local tag playstore=false display="Google APIs"
  tag=$(tag_id "$2")
  [ "$tag" = google_apis_playstore ] && playstore=true display="Google Play"
  cat << EOF
AvdId=$1
PlayStore.enabled=$playstore
abi.type=arm64-v8a
avd.ini.displayname=$1
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.forceColdBoot=yes
fastboot.forceFastBoot=no
hw.accelerometer=yes
hw.audioInput=no
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.dPad=no
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.initialOrientation=portrait
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2400
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=2048
hw.sdCard=no
hw.sensors.orientation=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=$(sysdir_rel "$2")
showDeviceFrame=no
tag.display=$display
tag.id=$tag
target=android-$2
vm.heapSize=228
EOF
}

cmd_init() {
  local dry=0 api avd sysdir answer serial rc=0 created=""
  case "${1:-}" in
    "") ;;
    --dry-run) dry=1; shift ;;
    *) usage ;;
  esac
  [ $# -eq 0 ] || usage
  if [ "$dry" -eq 0 ]; then
    [ -t 0 ] && [ -t 1 ] || die "init is run once by the owner in a terminal; agents use --dry-run"
  fi
  case "$avd_root" in
    /tmp/* | /private/tmp/* | /var/folders/* | /private/var/folders/*) die "AVD home $avd_root is wiped on reboot" ;;
  esac
  for api in $POOL_APIS; do
    avd=$(pool_avd "$api")
    sysdir="$ANDROID_HOME/$(sysdir_rel "$api")"
    printf '== %s (API %s)\n' "$avd" "$api"
    if [ -f "$avd_root/$avd.ini" ] || [ -d "$avd_root/$avd.avd" ]; then
      printf 'exists: %s/%s.ini; kept as is\n' "$avd_root" "$avd"
      continue
    fi
    if [ ! -f "$sysdir/system.img" ]; then
      printf 'missing system image %s\n' "$sysdir"
      printf '  install system-images;android-%s;%s;arm64-v8a (Android Studio > SDK Manager), then rerun init\n' \
        "$api" "$(tag_id "$api")"
      rc=1
      continue
    fi
    if [ "$dry" -eq 1 ]; then
      printf 'would write %s/%s.ini:\n' "$avd_root" "$avd"
      avd_ini "$avd" "$api" | sed 's/^/  /'
      printf 'would write %s/%s.avd/config.ini:\n' "$avd_root" "$avd"
      config_ini "$avd" "$api" | sed 's/^/  /'
      printf 'would cold-boot it once in an emulator slot (-no-snapshot) to create its data partition, then stop it\n'
      continue
    fi
    created="$created $api"
  done
  # A dry run only prints; a missing system image above is for the owner to install before the real run.
  [ "$dry" -eq 1 ] && return 0
  [ -n "$created" ] || return "$rc"
  printf 'Create%s in %s? Type yes: ' "$(for api in $created; do printf ' %s' "$(pool_avd "$api")"; done)" "$avd_root"
  read -r answer
  [ "$answer" = yes ] || die "not confirmed; nothing written"
  state_paths
  for api in $created; do
    avd=$(pool_avd "$api")
    mkdir -p "$avd_root/$avd.avd" || die "cannot create $avd_root/$avd.avd"
    avd_ini "$avd" "$api" > "$avd_root/$avd.ini"
    config_ini "$avd" "$api" > "$avd_root/$avd.avd/config.ini"
    printf 'wrote %s; first cold boot\n' "$avd"
    if serial=$(start firstboot "$avd" "" 600); then
      printf '%s booted as %s; stopping it\n' "$avd" "${serial#ANDROID_SERIAL=}"
      stop
    else
      printf '%s did not boot; see %s\n' "$avd" "$log_file"
      rc=1
    fi
  done
  return "$rc"
}

# ---- main ----------------------------------------------------------------------------------------------

[ $# -ge 1 ] || usage
cmd=$1
shift
case "$cmd" in
  __hold) [ $# -eq 3 ] || usage; hold "$@" ;;
  up) cmd_up "$@" ;;
  down) cmd_down "$@" ;;
  list) cmd_list "$@" ;;
  init) cmd_init "$@" ;;
  -h | --help) usage ;;
  *) usage ;;
esac
exit $?
