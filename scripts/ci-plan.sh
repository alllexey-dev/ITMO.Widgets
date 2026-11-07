#!/usr/bin/env bash
# ci-plan.sh [--full | --all] < changed-paths   what ci.yml runs, as GitHub step outputs
# ci-plan.sh --self-test                fixed path lists against their expected plan
#
# Reads the changed paths, one per line, and prints:
# - android=true|false  something Gradle builds for Android changed (always true with --all)
# - ios=true|false      something only the iOS build reads changed: iosApp/, scripts/ios/, the iOS source sets,
#                       the build files (a commonMain change is covered by verifyIosKlibs on Linux); with --full
#                       also any shared/ module, so a Kotlin API change that breaks Swift shows up on master; always
#                       true with --all
# - gradle=<json>       the Gradle jobs of ci.yml: on a PR the githubDebug unit tests, Konsist, the build-logic
#                       tests, the androidTest compile, the githubDebug APK and the shared host tests; with --full or
#                       --all verifyFull (both flavours, lintGithubDebug, lintPlayDebug). [] when android is false
# - shots=<json>        the screenshot jobs: [] when no screenshot module changed, one job with the changed modules,
#                       or `verify.sh shots all` in 3 shards (--full or --all with an Android change, more than 3
#                       modules, or a change every module's screenshots depend on: designsystem, core, testing, the
#                       build). Every job reads the build cache line shots-0: the shared modules a single module needs
#                       are in every shard's line.
#
# Markdown outside a source set (module READMEs, CONTRACT.md) changes nothing: such paths are skipped. Markdown under
# src/ stays a change, since tests may read or list their resource folders.
#
# --full: pushes to master. --all: manual runs, as if every path changed.
# Portable to macOS /bin/bash 3.2.

set -u

plan() { # [--full] < paths
  local full=false android=false ios=false shared=false all=false modules="" path module count=0
  case "${1:-}" in
    --full) full=true ;;
    --all) full=true android=true ios=true ;;
  esac
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    case "$path" in
      */src/*) ;;
      *.md) continue ;;
    esac
    case "$path" in
      app/* | shared/* | konsist/* | build-logic/* | gradle/* | gradle.properties | gradlew | settings.gradle.kts | \
        build.gradle.kts | scripts/verify.sh | .github/workflows/ci.yml | .github/actions/android-setup/* | \
        .github/actions/build-cache-save/*)
        android=true
        ;;
    esac
    case "$path" in
      iosApp/* | scripts/ios/* | shared/ios/* | shared/*/src/ios* | shared/*/build.gradle.kts | build-logic/* | \
        gradle/* | gradle.properties | settings.gradle.kts | build.gradle.kts | app/src/main/assets/* | \
        .github/workflows/ci.yml | .github/actions/ios-setup/*)
        ios=true
        ;;
    esac
    case "$path" in shared/*) shared=true ;; esac
    case "$path" in
      shared/*/src/ios* | shared/ios/* | app/src/androidTest*) ;;
      shared/designsystem/* | shared/core/* | shared/testing/* | build-logic/* | gradle/* | gradle.properties | \
        settings.gradle.kts | build.gradle.kts | scripts/verify.sh | .github/workflows/ci.yml | \
        .github/actions/android-setup/* | .github/actions/build-cache-save/*)
        all=true
        ;;
      app/*) add_module app ;;
      shared/*/*)
        module=${path#shared/}
        module=${module%%/*}
        [ ! -d "shared/$module/screenshots" ] || add_module "$module"
        ;;
    esac
  done
  for module in $modules; do count=$((count + 1)); done
  [ "$count" -le 3 ] || all=true
  [ "$full" = false ] || all=true
  [ "$android" = true ] || all=false
  [ "$full" = false ] || [ "$shared" = false ] || ios=true

  echo "android=$android"
  echo "ios=$ios"
  if [ "$android" = false ]; then
    echo "gradle=[]"
  elif [ "$full" = true ]; then
    echo 'gradle=[{"name":"app","tasks":"verifyQuickUnitApp verifyQuickAssemble","home":"true"},'`
      `'{"name":"unit-shared","tasks":"verifyQuickUnitShared","home":"false"},'`
      `'{"name":"lint-github","tasks":"verifyQuickLint","home":"false"},'`
      `'{"name":"lint-play","tasks":":app:lintPlayDebug","home":"false"}]'
  else
    echo 'gradle=[{"name":"app","tasks":":app:testGithubDebugUnitTest :konsist:test :build-logic:convention:test '`
      `':build-logic:strings:test :app:compileGithubDebugAndroidTestKotlin :app:assembleGithubDebug","home":"false"},'`
      `'{"name":"unit-shared","tasks":"verifyQuickUnitShared","home":"false"}]'
  fi
  if [ "$all" = true ]; then
    echo 'shots=[{"name":"0/3","args":"all --shard 0/3","cache":"shots-0"},'`
      `'{"name":"1/3","args":"all --shard 1/3","cache":"shots-1"},'`
      `'{"name":"2/3","args":"all --shard 2/3","cache":"shots-2"}]'
  elif [ -n "$modules" ]; then
    modules=${modules# }
    echo "shots=[{\"name\":\"$modules\",\"args\":\"$modules\",\"cache\":\"shots-0\"}]"
  else
    echo "shots=[]"
  fi
}

add_module() { # name
  case " $modules " in *" $1 "*) ;; *) modules="$modules $1" ;; esac
}

self_test() {
  local failed=0 got
  expect() { # description expected-line flags... (paths on stdin)
    local description=$1 line=$2
    shift 2
    got=$(plan "$@")
    if printf '%s\n' "$got" | grep -qxF "$line"; then
      echo "ok   $description"
    else
      echo "FAIL $description: no line '$line' in:"
      printf '%s\n' "$got" | sed 's/^/     /'
      failed=1
    fi
  }
  printf 'docs/testing.md\nREADME.md\n' | expect "docs only: no Android" "android=false"
  printf 'docs/testing.md\n' | expect "docs only: no shots" "shots=[]"
  printf 'docs/testing.md\n' | expect "docs only: no iOS" "ios=false"
  printf 'build-logic/README.md\niosApp/README.md\n' | expect "module README: no Android" "android=false"
  printf 'build-logic/README.md\niosApp/README.md\n' | expect "module README: no iOS" "ios=false"
  printf 'shared/backend-client/CONTRACT.md\n' | expect "--full, module doc: no iOS" "ios=false" --full
  printf 'shared/feature-sport/src/commonMain/kotlin/A.kt\n' | expect "--full, shared module: iOS" "ios=true" --full
  printf '.github/actions/ios-setup/action.yml\n' | expect "ios-setup: iOS" "ios=true"
  printf '.github/actions/ios-setup/action.yml\n' | expect "ios-setup: no Android" "android=false"
  printf '.github/actions/android-setup/action.yml\n' | expect "android-setup: no iOS" "ios=false"
  printf 'app/src/androidTest/kotlin/A.kt\n' | expect "androidTest: Android" "android=true"
  printf 'app/src/androidTest/kotlin/A.kt\n' | expect "androidTest: no shots" "shots=[]"
  printf 'app/src/androidTest/kotlin/A.kt\n' | expect "androidTest: compiled on a PR" \
    'gradle=[{"name":"app","tasks":":app:testGithubDebugUnitTest :konsist:test :build-logic:convention:test :build-logic:strings:test :app:compileGithubDebugAndroidTestKotlin :app:assembleGithubDebug","home":"false"},{"name":"unit-shared","tasks":"verifyQuickUnitShared","home":"false"}]'
  printf 'shared/backend-client/src/commonTest/resources/contract/README.md\n' |
    expect "Markdown in a source set: Android" "android=true"
  printf 'iosApp/Sources/App.swift\n' | expect "Swift only: no Android" "android=false"
  printf 'iosApp/Sources/App.swift\n' | expect "Swift only: iOS" "ios=true"
  printf 'shared/feature-sport/src/commonMain/kotlin/A.kt\n' | expect "feature: Android" "android=true"
  printf 'shared/feature-sport/src/commonMain/kotlin/A.kt\n' | expect "feature: no iOS app build" "ios=false"
  printf 'shared/feature-sport/src/commonMain/kotlin/A.kt\napp/src/main/kotlin/B.kt\n' |
    expect "feature and app: their shots" \
      'shots=[{"name":"feature-sport app","args":"feature-sport app","cache":"shots-0"}]'
  printf 'shared/feature-sport/src/iosMain/kotlin/A.kt\n' | expect "iOS source set: iOS" "ios=true"
  printf 'shared/feature-sport/src/iosMain/kotlin/A.kt\n' | expect "iOS source set: no shots" "shots=[]"
  printf 'shared/backend-client/src/commonMain/kotlin/A.kt\n' | expect "no-screenshot module: no shots" "shots=[]"
  printf 'shared/designsystem/src/commonMain/kotlin/A.kt\n' | expect "designsystem: every shot" \
    'shots=[{"name":"0/3","args":"all --shard 0/3","cache":"shots-0"},{"name":"1/3","args":"all --shard 1/3","cache":"shots-1"},{"name":"2/3","args":"all --shard 2/3","cache":"shots-2"}]'
  printf 'docs/a.md\n' | expect "--full, docs only: no Android" "gradle=[]" --full
  printf 'app/build.gradle.kts\n' | expect "--full: lint" \
    'gradle=[{"name":"app","tasks":"verifyQuickUnitApp verifyQuickAssemble","home":"true"},{"name":"unit-shared","tasks":"verifyQuickUnitShared","home":"false"},{"name":"lint-github","tasks":"verifyQuickLint","home":"false"},{"name":"lint-play","tasks":":app:lintPlayDebug","home":"false"}]' --full
  printf 'docs/a.md\n' | expect "--all, docs only: Android" "android=true" --all
  : | expect "--all, no paths: iOS" "ios=true" --all
  return "$failed"
}

if [ "${1:-}" = --self-test ]; then
  cd "$(dirname "$0")/.." || exit 2
  self_test
  exit
fi
case "${1:-}" in
  "" | --full | --all) ;;
  *) echo "usage: ci-plan.sh [--full | --all] < paths | --self-test" >&2; exit 2 ;;
esac
plan "$@"
