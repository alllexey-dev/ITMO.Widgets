#!/usr/bin/env bash
# ci-emulator.sh start                        GitHub Actions only: KVM, the API 35 image, the AVD, boot in the background
# ci-emulator.sh wait [--cutout tall|none]    wait for the boot, unlock, apply the cutout; prints ANDROID_SERIAL=...
# ci-emulator.sh logcat <file>                capture `logcat -v threadtime` into <file> in the background
#
# The emulator of android-ui.yml and android-ship.yml (L04 TC-CI1). Local runs use scripts/emulator.sh and its pool.
#
# - Runs only with CI=true and GITHUB_ACTIONS=true: it writes a udev rule with sudo and installs SDK packages.
# - The AVD mirrors the pool AVD of scripts/emulator.sh (Pixel 7 geometry: 1080x2400 at 420 dpi, 2 GB RAM, 4 cores,
#   hardware keyboard, no skin and so no cutout) on system-images;android-35;google_apis;x86_64, the x86_64 twin of
#   the pool's arm64 image. Animations stay as the image ships them, as on the pool emulators.
# - `start` returns right after the launch, so the job builds while the emulator boots; every boot is cold
#   (-no-snapshot), so no state leaks between runs. The port is 5560, a pool port: verify.sh ui refuses 5554.
# - `wait --cutout tall` enables com.android.internal.display.cutout.emulation.tall, the tall cutout overlay,
#   because the integrator's emulator has a cutout and the shell's insets tests must see one.
# - Exit code: 0 ok, 1 the emulator failed or did not boot in time, 2 refused (usage, not in GitHub Actions).

set -u

me=ci-emulator.sh
API=35
IMAGE="system-images;android-$API;google_apis;x86_64"
AVD=itmo-ci-api$API
PORT=5560
SERIAL=emulator-$PORT
BOOT_TIMEOUT=600

die() { printf '%s: %s\n' "$me" "$1" >&2; exit "${2:-2}"; }
note() { printf '%s: %s\n' "$me" "$*" >&2; }

[ "${CI:-}" = true ] && [ "${GITHUB_ACTIONS:-}" = true ] ||
  die "GitHub Actions only (CI=true, GITHUB_ACTIONS=true); locally use scripts/emulator.sh"
[ -n "${ANDROID_HOME:-}" ] || die "ANDROID_HOME is not set"

adb="$ANDROID_HOME/platform-tools/adb"
avd_home="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
state_dir="${RUNNER_TEMP:-/tmp}/ci-emulator"

sdkmanager() {
  local bin
  for bin in "$ANDROID_HOME"/cmdline-tools/latest/bin/sdkmanager "$ANDROID_HOME"/cmdline-tools/*/bin/sdkmanager; do
    [ -x "$bin" ] && { "$bin" "$@"; return; }
  done
  die "no sdkmanager under $ANDROID_HOME/cmdline-tools" 1
}

config_ini() {
  cat << EOF
AvdId=$AVD
PlayStore.enabled=false
abi.type=x86_64
avd.ini.displayname=$AVD
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.forceColdBoot=yes
fastboot.forceFastBoot=no
hw.accelerometer=yes
hw.audioInput=no
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.dPad=no
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
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
image.sysdir.1=system-images/android-$API/google_apis/x86_64/
showDeviceFrame=no
tag.display=Google APIs
tag.id=google_apis
target=android-$API
vm.heapSize=228
EOF
}

cmd_start() {
  local t0
  t0=$(date +%s)
  mkdir -p "$state_dir" "$avd_home/$AVD.avd" || die "cannot create $state_dir or the AVD directory" 1
  # The KVM rule of the android-emulator-runner documentation: the runner user may open /dev/kvm.
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' |
    sudo tee /etc/udev/rules.d/99-kvm4all.rules > /dev/null
  sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm || die "cannot enable KVM" 1
  note "installing emulator, platform-tools, $IMAGE"
  yes 2> /dev/null | sdkmanager --licenses > /dev/null 2>&1
  sdkmanager --install emulator platform-tools "$IMAGE" > "$state_dir/sdkmanager.log" 2>&1 || {
    tail -n 20 "$state_dir/sdkmanager.log" >&2
    die "sdkmanager failed" 1
  }
  printf 'avd.ini.encoding=UTF-8\npath=%s/%s.avd\npath.rel=avd/%s.avd\ntarget=android-%s\n' \
    "$avd_home" "$AVD" "$AVD" "$API" > "$avd_home/$AVD.ini"
  config_ini > "$avd_home/$AVD.avd/config.ini"
  "$adb" start-server > /dev/null 2>&1
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
    -no-snapshot -gpu swiftshader_indirect -accel on > "$state_dir/emulator.log" 2>&1 &
  echo $! > "$state_dir/pid"
  note "emulator $AVD launched on $SERIAL (pid $!) after $(($(date +%s) - t0))s; log $state_dir/emulator.log"
}

dev_sh() { "$adb" -s "$SERIAL" shell "$@" 2> /dev/null | tr -d '\r'; }

cmd_wait() {
  local cutout=none deadline t0 pid
  while [ $# -gt 0 ]; do
    case "$1" in
      --cutout)
        cutout=${2:-}
        shift 2 || die "--cutout needs tall or none"
        ;;
      *) die "unknown argument '$1'" ;;
    esac
  done
  case "$cutout" in tall | none) ;; *) die "--cutout takes tall or none, got '$cutout'" ;; esac
  t0=$(date +%s)
  deadline=$((t0 + BOOT_TIMEOUT))
  pid=$(cat "$state_dir/pid" 2> /dev/null)
  until [ "$(dev_sh getprop sys.boot_completed)" = 1 ] && dev_sh pm path android | grep -q '^package:'; do
    if [ -z "$pid" ] || ! kill -0 "$pid" 2> /dev/null; then
      tail -n 40 "$state_dir/emulator.log" >&2
      die "the emulator exited before it booted" 1
    fi
    [ "$(date +%s)" -lt "$deadline" ] || { tail -n 40 "$state_dir/emulator.log" >&2; die "no boot in ${BOOT_TIMEOUT}s" 1; }
    sleep 3
  done
  note "booted after $(($(date +%s) - t0))s of waiting"
  dev_sh input keyevent 82 > /dev/null
  dev_sh wm dismiss-keyguard > /dev/null
  if [ "$cutout" = tall ]; then
    dev_sh cmd overlay enable com.android.internal.display.cutout.emulation.tall > /dev/null
    until dev_sh cmd overlay list | grep -q '\[x\] com.android.internal.display.cutout.emulation.tall'; do
      [ "$(date +%s)" -lt "$deadline" ] || die "the tall cutout overlay did not turn on" 1
      sleep 1
    done
    note "tall cutout overlay on"
  fi
  printf 'ANDROID_SERIAL=%s\n' "$SERIAL"
}

cmd_logcat() {
  [ $# -eq 1 ] || die "usage: logcat <file>"
  mkdir -p "$(dirname "$1")" || die "cannot create the directory of $1" 1
  nohup "$adb" -s "$SERIAL" logcat -v threadtime > "$1" 2>&1 &
  note "logcat of $SERIAL into $1 (pid $!)"
}

cmd=${1:-}
[ $# -gt 0 ] && shift
case "$cmd" in
  start) [ $# -eq 0 ] || die "start takes no arguments"; cmd_start ;;
  wait) cmd_wait "$@" ;;
  logcat) cmd_logcat "$@" ;;
  *) sed -n '2,4p' "$0" | sed 's/^# //' >&2; exit 2 ;;
esac
