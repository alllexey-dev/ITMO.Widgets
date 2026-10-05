# shellcheck shell=bash
# source scripts/ios/env.sh
#
# The pinned iOS toolchain (L18 IO-02, docs/ios.md). Sourced by scripts/ios/test.sh and by the app target's Run Script
# in iosApp/project.yml: the Xcode GUI passes no shell environment, so everything a build needs is set here.
#
# - Pins Xcode (version and build, gate T10), XcodeGen, one simulator device type and runtime; sourcing returns 1
#   with a message when the selected Xcode or XcodeGen differs. The ITMO_* pins below may be preset in the environment
#   (a CI image with another Xcode), never edited per machine.
# - DEVELOPER_DIR: kept when set (Xcode sets it for its Run Scripts), else ITMO_DEVELOPER_DIR.
# - JAVA_HOME = JDK 21 and ANDROID_HOME as scripts/verify.sh sets them; PATH gains Homebrew for XcodeGen.
# - itmo_ios_pin_args prints the -PmyItmoApiDir argument exactly as scripts/verify.sh resolves it (ADR 0024, TC-05):
#   nothing when MYITMOAPI_DIR is set (CI) or the worktree has no pin record.

: "${ITMO_XCODE_VERSION:=27.0}"
: "${ITMO_XCODE_BUILD:=27A266a}"
: "${ITMO_DEVELOPER_DIR:=/Applications/Xcode.app/Contents/Developer}"
: "${ITMO_XCODEGEN_VERSION:=2.46.0}"
: "${ITMO_SIM_DEVICE_TYPE:=com.apple.CoreSimulator.SimDeviceType.iPhone-17}"
: "${ITMO_SIM_RUNTIME:=com.apple.CoreSimulator.SimRuntime.iOS-27-0}"
export ITMO_XCODE_VERSION ITMO_XCODE_BUILD ITMO_DEVELOPER_DIR ITMO_XCODEGEN_VERSION ITMO_SIM_DEVICE_TYPE ITMO_SIM_RUNTIME

ITMO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
export ITMO_ROOT

export DEVELOPER_DIR="${DEVELOPER_DIR:-$ITMO_DEVELOPER_DIR}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2> /dev/null) && export JAVA_HOME || unset JAVA_HOME
fi
case ":$PATH:" in *:/opt/homebrew/bin:*) ;; *) [ -d /opt/homebrew/bin ] && PATH="$PATH:/opt/homebrew/bin" ;; esac
export PATH

itmo_ios_check_toolchain() {
  local version xcodegen
  version=$(xcodebuild -version 2> /dev/null | tr '\n' ' ')
  case "$version" in
    "Xcode $ITMO_XCODE_VERSION Build version $ITMO_XCODE_BUILD "*) ;;
    *)
      printf 'env.sh: Xcode %s (%s) is pinned, DEVELOPER_DIR=%s has: %s\n' \
        "$ITMO_XCODE_VERSION" "$ITMO_XCODE_BUILD" "$DEVELOPER_DIR" "${version:-no xcodebuild}" >&2
      return 1
      ;;
  esac
  xcodegen=$(xcodegen --version 2> /dev/null)
  if [ "$xcodegen" != "Version: $ITMO_XCODEGEN_VERSION" ]; then
    printf 'env.sh: XcodeGen %s is pinned, found: %s\n' "$ITMO_XCODEGEN_VERSION" "${xcodegen:-none}" >&2
    return 1
  fi
}

itmo_ios_pin_args() {
  local git_dir pin
  [ -z "${MYITMOAPI_DIR:-}" ] || return 0
  git_dir=$(git -C "$ITMO_ROOT" rev-parse --absolute-git-dir 2> /dev/null) || return 0
  pin=$(head -n 1 "$git_dir/itmo-myitmoapi-dir" 2> /dev/null | tr -d '\r')
  if [ -n "$pin" ] && [ -d "$pin" ]; then
    printf '%s\n' "-PmyItmoApiDir=$pin"
  else
    printf 'env.sh: warning: no MyItmoApi pin for this worktree; run `~/proj/.wt/bin/lane pin %s`\n' "$ITMO_ROOT" >&2
  fi
}

itmo_ios_check_toolchain || return 1
