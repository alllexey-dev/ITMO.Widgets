# iOS app

The iOS client is a SwiftUI shell around the shared Compose Multiplatform screens, with WidgetKit extensions and a
notification service extension ([ADR 0023](decisions/0023-ios-client.md)). It lives in `iosApp/` and links one Kotlin
umbrella framework, `Shared`, built from `shared/ios/`. Today the app is a skeleton: it shows a string from the
framework, the widget bundle is empty and the notification service passes notifications through unchanged.

## Prerequisites

Only the owner installs the toolchain (it needs an Apple ID and `sudo`); agents check it.

| Tool | Pin | Where it is pinned |
|---|---|---|
| Xcode | 27.0 (27A266a) at `/Applications/Xcode.app` | `scripts/ios/env.sh` (gate T10: Kotlin 2.4.20 works with it) |
| Simulator | iPhone 17, iOS 27.0 runtime | `scripts/ios/env.sh` |
| XcodeGen | 2.46.0 (`brew install xcodegen`) | `scripts/ios/env.sh` |
| JDK | 21 for Gradle, as `scripts/verify.sh` | `scripts/ios/env.sh` |
| Deployment target | iOS 18.0 | `iosApp/Config/Base.xcconfig` |

`scripts/ios/env.sh` refuses to build when `xcodebuild -version` or `xcodegen --version` differs from the pin. A CI
image with another Xcode presets the `ITMO_*` pins in its environment instead of editing the file.

Check the machine (read-only):

```bash
xcode-select -p && xcodebuild -version
xcrun simctl list runtimes
xcodegen --version
```

## Layout

| Path | Contents |
|---|---|
| `iosApp/project.yml` | XcodeGen spec: targets, the Kotlin Run Script, the `ITMOWidgets` scheme. The generated `.xcodeproj` is ignored |
| `iosApp/Config/` | `Base.xcconfig` (identifiers, versions, signing defaults) and one xcconfig per target |
| `iosApp/Resources/` | `Info/` plists and the entitlements of each target, unsigned and `.signed` |
| `iosApp/Sources/` | the app target `ITMOWidgets` (SwiftUI) |
| `iosApp/Extensions/Widgets/` | the widget extension `ITMOWidgetsWidgets` (WidgetKit, Controls; no Kotlin) |
| `iosApp/Extensions/NotificationService/` | the notification service extension `ITMOWidgetsNotificationService` (no Kotlin) |
| `iosApp/Tests/UnitTests/` | `ITMOWidgetsTests`, hosted in the app |
| `shared/ios/` | the umbrella framework `Shared` (static) over every shared module |
| `scripts/ios/` | `env.sh` (pins), `test.sh` (build and test) |

Targets use directory globs: a new Swift file in a target directory needs no `project.yml` edit. Only the app links
`Shared`; the extensions stay Swift-only (memory limits, ADR 0023).

## Identifiers

Frozen from the first TestFlight build; `iosApp/Tests/UnitTests/StableIdentifiersTests.swift` pins each one that
exists in the build.

| Identifier | Value |
|---|---|
| App bundle ID | `dev.alllexey.itmowidgets` |
| Widget extension bundle ID | `dev.alllexey.itmowidgets.widgets` |
| Notification service bundle ID | `dev.alllexey.itmowidgets.notification-service` |
| App Group | `group.dev.alllexey.itmowidgets` (app, widget, notification service) |
| Keychain access group | `$(AppIdentifierPrefix)dev.alllexey.itmowidgets.shared` (app and notification service) |
| Marketing version | 2.3.0 for all three bundles |

The App Group and Keychain group are build settings (`APP_GROUP_ID`, `KEYCHAIN_GROUP` in `Base.xcconfig`). Xcode
expands them into each bundle's Info.plist (keys `AppGroupID`, `KeychainGroup`) and entitlements; code reads them
from its own process's `Bundle.main` and never writes them as literals. `$(AppIdentifierPrefix)` is the team ID and
a dot on a device, `FAKETEAMID.` on the simulator and empty in a team-less device build.

Signing: builds are unsigned by default ("Sign to Run Locally", no team), which is all the simulator and CI need.
The ignored `iosApp/Config/Signing.local.xcconfig`, written by the integrator after gate T13, sets
`DEVELOPMENT_TEAM`, `ITMO_CODE_SIGN_IDENTITY = Apple Development` and `ITMO_ENTITLEMENTS_VARIANT = .signed`; the
last switches every target to its `.signed` entitlements, the only place for push and associated domains. No
team ID is committed.

## Build and test

Gradle and `xcodebuild` run only through `scripts/ios/test.sh` (or `scripts/verify.sh run|klibs`), which hold the
`kn` build slot and pass the worktree's MyItmoApi pin.

```bash
scripts/ios/test.sh                                          # checks, xcodegen, build and all tests
scripts/ios/test.sh --only ITMOWidgetsTests/StableIdentifiersTests
scripts/ios/test.sh kn core                                  # iosSimulatorArm64Test of shared/core
scripts/ios/test.sh --cleanup                                # delete this worktree's simulator
```

- `test.sh` runs every `scripts/ios/check-*.sh` first, then `xcodegen generate` and `xcodebuild build test` with
  DerivedData in `iosApp/build/` and the `.xcresult` in `iosApp/build/test-results/`. Its last line is
  `VERIFY A ios|ios-only|ios-kn PASS|FAIL <secs>s <sha7>`.
- Each worktree gets one simulator, `itmo-<worktree directory>`, created on first use; delete it with `--cleanup`
  at the end of a card. `--ci` runs without slots, uses `itmo-ci` and deletes it afterwards.
- The app target's Run Script "Build Kotlin framework" runs before Compile Sources:
  `scripts/slot.sh kn -- ./gradlew :shared:ios:embedAndSignAppleFrameworkForXcode` with the pin, after sourcing
  `env.sh` because the Xcode GUI passes no shell environment. Inside `test.sh` the slot is already held, so the
  nested `slot.sh` runs Gradle directly. It needs `ENABLE_USER_SCRIPT_SANDBOXING = NO`.
- Opening the project in Xcode: run `xcodegen generate --spec iosApp/project.yml` first, then open
  `iosApp/ITMOWidgets.xcodeproj`; the Run Script still takes the `kn` slot.
- `CADisableMinimumFrameDurationOnPhone = YES` is set in the app's Info.plist; Compose Multiplatform refuses to
  start without it.
- The app link prints one known warning: the ICU data object in Compose Multiplatform 1.12.1 targets iOS 18.5,
  above the 18.0 deployment target. It is harmless; the minimum stays iOS 18.0 (ADR 0023).

## CI

`.github/workflows/ios.yml` runs the job `ios-check` (the required check's name; never renamed) on every PR into
and every push to `v2.3/next` and `master`, and on `workflow_dispatch`.

- Runner: the GitHub `xcode-27` image (arm64, 3 vCPU, 7 GB), the image with the Xcode pinned in
  `scripts/ios/env.sh`. The job selects `/Applications/Xcode_<pin>.app` and fails when the image lacks it; it never
  falls back to another Xcode. XcodeGen comes from its GitHub release at the pinned version, checked by sha256.
- Path filter: a first step ends the job green when nothing under `iosApp/`, `shared/`, `scripts/ios/`,
  `scripts/slot.sh`, `gradle/`, `build-logic/`, `app/src/main/assets/`, the `*.gradle.kts` files,
  `gradle.properties` or `.github/workflows/ios*.yml` changed. The filter is never on the trigger, so the check
  always reports.
- The build is `scripts/ios/test.sh --ci` with MyItmoApi checked out at `gradle/myitmoapi.ref` (`MYITMOAPI_DIR`, as
  in `android-ci.yml`), one Gradle worker (`ITMO_MAX_WORKERS=1`, no parallel K/N), the Gradle cache and `~/.konan`
  cached; only `v2.3/next` writes the caches. No secrets and no signing.
- On failure the `.xcresult` from `iosApp/build/test-results/` and the snapshot diffs from
  `iosApp/build/snapshot-artifacts/` (`SNAPSHOT_ARTIFACTS` for the test runner) are uploaded as `ios-check-results`.
- The step summary records the duration of `test.sh --ci` and the peak used memory (active, wired and compressed
  pages, sampled every 5 s).
- `.github/workflows/ios-nightly.yml` runs on a schedule against `v2.3/next`: `test.sh --ci kn` over every shared
  module with the testing convention, and `test.sh --ci ui` once `test.sh` has a `ui` mode. It is never a required
  check.

Measured on the first runs (no caches): the job takes about 18.5 minutes, `test.sh --ci` 1076 s, with a peak of
6.3 GB used of 7 GB. If the build runs out of memory, split it into a framework job and an `xcodebuild` job.
