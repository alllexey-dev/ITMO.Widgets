# shellcheck shell=bash
# source scripts/ios/env.sh
#
# The iOS toolchain (docs/ios.md). Sourced by scripts/ios/*.sh and by the app target's Run Script in
# iosApp/project.yml: the Xcode GUI passes no shell environment, so everything a build needs is set here.
#
# - The Xcode (version and build), XcodeGen, simulator device type and runtime the project is tested with. CI
#   selects exactly this Xcode; locally a different one only warns. The ITMO_* values may be preset in the
#   environment.
# - DEVELOPER_DIR: kept when set (Xcode sets it for its Run Scripts), else ITMO_DEVELOPER_DIR.
# - JAVA_HOME = JDK 21 and ANDROID_HOME on macOS; PATH gains Homebrew for XcodeGen.
# - MyItmoApi comes from Maven Central; set MYITMOAPI_DIR to a my-itmo-api checkout to build against it instead.

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
      printf 'env.sh: warning: tested with Xcode %s (%s), DEVELOPER_DIR=%s has: %s\n' \
        "$ITMO_XCODE_VERSION" "$ITMO_XCODE_BUILD" "$DEVELOPER_DIR" "${version:-no xcodebuild}" >&2
      ;;
  esac
  xcodegen=$(xcodegen --version 2> /dev/null)
  if [ "$xcodegen" != "Version: $ITMO_XCODEGEN_VERSION" ]; then
    printf 'env.sh: warning: tested with XcodeGen %s, found: %s\n' "$ITMO_XCODEGEN_VERSION" "${xcodegen:-none}" >&2
  fi
  return 0
}

itmo_ios_check_toolchain
