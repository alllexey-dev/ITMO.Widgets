#!/usr/bin/env bash
# Checks the release builds against the Google Play requirements the app has to keep:
# no restricted permissions or foreground service in the merged manifests, targetSdk 36,
# 16 KB aligned native libraries, and no GitHub update link in the play variant.
# Signing is not needed: without keystore.properties the release outputs are unsigned.
#
# Usage: scripts/check-play-policy.sh            builds what it checks, then checks
#        SKIP_BUILD=1 scripts/check-play-policy.sh  checks the existing outputs
set -euo pipefail

cd "$(dirname "$0")/.."

TARGET_SDK=36
FORBIDDEN_MANIFEST=(
  android.permission.REQUEST_INSTALL_PACKAGES
  android.permission.QUERY_ALL_PACKAGES
  android.permission.SCHEDULE_EXACT_ALARM
  android.permission.USE_EXACT_ALARM
  android.permission.FOREGROUND_SERVICE
  com.google.android.gms.permission.AD_ID
  androidx.work.impl.foreground.SystemForegroundService
)
GITHUB_RELEASES="github.com/alllexey-dev/ITMO.Widgets/releases"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew -q :app:processPlayReleaseManifest :app:processGithubReleaseManifest \
    :app:assembleGithubRelease :app:bundlePlayRelease
fi

failures=0
fail() {
  echo "FAIL: $*"
  failures=$((failures + 1))
}

# Bash 3 (macOS) has no ${var^}, so the task names are spelled out.
for pair in playRelease:processPlayReleaseManifest githubRelease:processGithubReleaseManifest; do
  variant="${pair%%:*}"
  manifest="app/build/intermediates/merged_manifests/$variant/${pair#*:}/AndroidManifest.xml"
  [[ -f "$manifest" ]] || { fail "$variant: no merged manifest at $manifest"; continue; }
  for name in "${FORBIDDEN_MANIFEST[@]}"; do
    # FOREGROUND_SERVICE also matches FOREGROUND_SERVICE_<TYPE>, which is just as forbidden here.
    if grep -q "android:name=\"$name" "$manifest"; then
      fail "$variant: $name in the merged manifest"
    fi
  done
  grep -q "android:targetSdkVersion=\"$TARGET_SDK\"" "$manifest" ||
    fail "$variant: targetSdkVersion is not $TARGET_SDK"
done

sdk_dir="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"
sdk_dir="${sdk_dir:-${ANDROID_HOME:-}}"
zipalign="$(ls -d "$sdk_dir"/build-tools/*/zipalign 2>/dev/null | tail -1 || true)"
github_apk="$(ls app/build/outputs/apk/github/release/app-github-release*.apk 2>/dev/null | head -1 || true)"
if [[ -z "$zipalign" ]]; then
  fail "zipalign not found under the SDK build-tools"
elif [[ -z "$github_apk" ]]; then
  fail "no github release APK under app/build/outputs/apk/github/release"
elif ! "$zipalign" -c -P 16 -v 4 "$github_apk" >/dev/null; then
  # The AAB carries the same native libraries, so the APK stands for both.
  fail "$github_apk is not 16 KB aligned (zipalign -c -P 16 -v 4)"
fi

play_bundle="app/build/outputs/bundle/playRelease/app-play-release.aab"
if [[ ! -f "$play_bundle" ]]; then
  fail "no play bundle at $play_bundle"
elif unzip -p "$play_bundle" | LC_ALL=C grep -aqF "$GITHUB_RELEASES"; then
  fail "$play_bundle contains $GITHUB_RELEASES"
fi

if ((failures > 0)); then
  echo "$failures Play policy check(s) failed"
  exit 1
fi
echo "Play policy checks passed"
