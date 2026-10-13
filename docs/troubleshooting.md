# Troubleshooting

Problems people hit when building and testing, and the fix for each.

## Android SDK not found

Gradle fails with "SDK location not found". Set `ANDROID_HOME`, or let
Android Studio write `sdk.dir=...` into `local.properties`. The scripts
(`scripts/verify.sh`, `scripts/ios/env.sh`) default `ANDROID_HOME` to
`~/Library/Android/sdk` on macOS. compileSdk is 37: install that platform in
the SDK Manager if the build asks for it.

## Wrong JDK

The Gradle daemon runs on JetBrains JDK 21 from
`gradle/gradle-daemon-jvm.properties` and downloads it from foojay when it is
not installed. Without network, install JDK 21 and point Gradle at it:
`org.gradle.java.installations.paths=<jdk>` in `~/.gradle/gradle.properties`,
as the CI jobs do. Launching Gradle with a JDK older than 17 fails.

## Xcode or XcodeGen version warning

```text
env.sh: warning: tested with Xcode 27.0 (27A266a), DEVELOPER_DIR=... has: ...
env.sh: warning: tested with XcodeGen 2.46.0, found: ...
```

`scripts/ios/env.sh` pins the toolchain. Locally another version only warns
and the build continues; CI fails instead. If the build breaks, install the
pinned versions, or select the pinned Xcode with `DEVELOPER_DIR`. When the
pin moves, update `scripts/ios/env.sh`, `.github/workflows/ios.yml` and
`.github/workflows/ios-nightly.yml` together (CI checks that the XcodeGen
versions match).

## Xcode project out of date

`iosApp/ITMOWidgets.xcodeproj` is generated and ignored. After a pull that
changes `iosApp/project.yml`, or after adding or removing Swift files, run
`xcodegen generate --spec iosApp/project.yml` (or `scripts/ios/test.sh`).

## The Kotlin framework does not build from Xcode

The app target's Run Script calls
`./gradlew :shared:ios:embedAndSignAppleFrameworkForXcode` after sourcing
`scripts/ios/env.sh`, because Xcode passes no shell environment. It needs a
JDK 21 that `/usr/libexec/java_home -v 21` finds and
`ENABLE_USER_SCRIPT_SANDBOXING = NO` (set in `iosApp/Config/Base.xcconfig`).
Run `./gradlew verifyIosKlibs` from a terminal to see Kotlin errors without
Xcode.

## Kotlin/Native cache

The first iOS build downloads Kotlin/Native into `~/.konan` (about 1 GB per
Kotlin version), which takes minutes. After a Kotlin version bump the next
build downloads again. If the toolchain there is broken (an interrupted
download), delete the affected directory under `~/.konan` and build again.

## MyItmoApi composite build

With `MYITMOAPI_DIR` set, Gradle prints `MyItmoApi: including <dir>` and builds
the library from that checkout instead of Maven Central.

- `MYITMOAPI_DIR=... has no Gradle build`: the path is not a my-itmo-api
  checkout (the build uses `<checkout>/kmp` or the root). Fix the path or
  unset the variable.
- Unexpected API errors after switching branches: the variable is still set
  in your shell or IDE run configuration, so the build uses your checkout.
  Unset it to go back to the published artifact.
- Android Studio does not see variables exported in a terminal started after
  it; restart the IDE from that shell or set the variable in its settings.

## Screenshot tests fail

- A real diff: open `<module>/build/outputs/roborazzi/<name>_compare.png`
  (reference, diff, new). If the change is deliberate, re-record with
  `scripts/verify.sh shots <module> --record`, look at the PNGs and commit
  them with the code.
- "stale baseline" or a preview without a baseline: a preview was renamed,
  added or removed. Re-record the module and delete the orphaned PNG.
- Passes locally, fails in CI: baselines were recorded on Linux or with
  another font; record on a Mac.
- `--record and --gallery exclude each other`, or the gallery is not empty:
  pass a new, empty directory to `--gallery`.

## Instrumented tests refuse to run

- `verify.sh ui` needs `ANDROID_SERIAL` naming an emulator
  (`ANDROID_SERIAL=emulator-5554`). It refuses physical devices because the
  tests clear app data.
- `--managed-device` runs only with `CI=true`; locally it would create an AVD.
- `scripts/ship-check.sh` device stages also require an emulator and refuse a
  worktree with `keystore.properties` (release outputs stay unsigned there).

## Generated files are stale

| Symptom | Fix |
|---|---|
| `check-sources.sh` reports string tables or `AppSymbol.swift` out of date | `./gradlew :app:exportAppleStrings` |
| `check-tokens.sh` reports `Tokens.generated.swift` stale | `python3 scripts/ios/gen-tokens.py` |
| `DesignTokensExportTest` fails | `./gradlew :shared:designsystem:exportDesignTokens` |
| The vendored Backend contract differs | `scripts/sync-backend-contract.sh <backend-sha>` |

## Koin fails at start

The graph starts with `allowOverride(false)`, so a type defined twice fails at
start instead of silently replacing a binding. A missing definition shows up in
the module's `<X>ModuleTest` and in `KoinGraphTest`; run
`./gradlew :app:testGithubDebugUnitTest --tests '*KoinGraphTest'`.

## Docs check fails

`scripts/check-docs.sh` reports backticked paths that do not exist, dead
relative links and anchors, and docs not linked from `docs/README.md`. Fix the
path or link. A true false positive goes into `scripts/check-docs.known` with
a reason.
