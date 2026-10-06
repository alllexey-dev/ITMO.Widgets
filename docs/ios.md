# iOS app

The iOS client is a SwiftUI shell around the shared Compose Multiplatform screens, with WidgetKit extensions and a
notification service extension ([ADR 0023](decisions/0023-ios-client.md)). It lives in `iosApp/` and links one Kotlin
umbrella framework, `Shared`, built from `shared/ios/`. Today the app is the shell on fixtures (tabs, router,
session gate, placeholder roots; see Shell and routes), the widget bundle is empty and the notification service
passes notifications through unchanged.

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
| `iosApp/Shared/` | sources of all three targets: the generated string tables and `AppSymbol.swift`, the custom symbol images |
| `iosApp/Strings/` | `strings_ios*.xml`: catalog files with copy only iOS shows |
| `iosApp/Tests/UnitTests/` | `ITMOWidgetsTests`, hosted in the app |
| `iosApp/Tests/SnapshotTests/` | `SnapshotTests`, hosted in the app: SwiftUI and widget entry view snapshots (swift-snapshot-testing), references in `__Snapshots__/` |
| `iosApp/Tests/UITests/` | `UITests` (XCUITest): smoke tests and review screenshots |
| `shared/ios/` | the umbrella framework `Shared` (static) over every shared module; it exports `:shared:core` |
| `scripts/ios/` | `env.sh` (pins), `test.sh` (build and test), `check-*.sh` (source checks), `screenshots.sh` (review screenshots) |

Targets use directory globs: a new Swift file in a target directory needs no `project.yml` edit. Only the app links
`Shared`; the extensions stay Swift-only (memory limits, ADR 0023).

## Strings and icons

The catalog is the Android resource files ([ADR 0028](decisions/0028-strings-and-icons.md)); iOS reads generated
copies and never holds Russian text in Swift.

- Tables. `scripts/verify.sh run -- :app:exportAppleStrings` writes `iosApp/Shared/Strings/*.xcstrings`, one per
  catalog file name; `strings_platform.xml` and `iosApp/Strings/strings_ios*.xml` go to `Localizable` (APNs resolves a
  `loc-key` only there), `InfoPlist` and `AppShortcuts` rows come from `build-logic/strings/apple-tables.properties`.
  Run it after changing a catalog file and commit the result; never edit a table by hand.
- iOS-only copy (widget and Control texts, the Background App Refresh row, usage descriptions) lives in
  `iosApp/Strings/strings_ios.xml`, Android syntax; later cards add `strings_ios_<area>.xml`. An Info.plist value
  is an `InfoPlist.<key>` row, a shortcut phrase an `AppShortcuts.<phrase>` row.
- Language. Every bundle has only the `ru` localization (`CFBundleDevelopmentRegion = ru`; XcodeGen also lists
  `Base` in `knownRegions`, which holds no files), so tables, Info.plist values and plural rules are Russian on an
  English iPhone. Compose Multiplatform instead takes plural rules from `NSLocale.preferredLanguages` ("5 пары"):
  `App.init` calls `IosStrings.installAppLocale()` first, which puts `ru` first in the process's `AppleLanguages`,
  launch arguments included (`designsystem/locale/AppLocale.kt`). Extensions link no Kotlin and need no override.
- Swift. `STRING_CATALOG_GENERATE_SYMBOLS` gives each table typed symbols:
  `Text(.StringsCommon.scheduleLessonCount(5))`, `.iosWidgetQrReveal` for `Localizable`. A shared `UiText` resolves
  with `text.resolved` (`Sources/Support/UiText+Resolve.swift`): `Localizable` first, then the file tables; a plural
  takes its count as argument 1, `%lld` an `Int64`, `%@` a `String`, a nested `UiText` is resolved first, and the
  Russian locale formats. `AppStrings.string`/`plural` do the same for a key.
- Compose resources. The Compose plugins on `shared/ios` copy every module's `composeResources` into
  `ITMOWidgets.app/compose-resources` during `embedAndSignAppleFrameworkForXcode`.
- Icons. `iosApp/Shared/Symbols/AppSymbol.swift` (generated from `docs/design/icons.tsv`) names an SF Symbol per
  `shared` row; a `custom.*` name is a symbol image in `Brands.xcassets`, drawn from the Android brand vector into
  an SF Symbols 3.0 template (Regular-M only). `AppIcon.symbol` maps the Kotlin icon, `AppSymbol.image` gives the
  SwiftUI `Image`.
- `scripts/ios/check-sources.sh` (run by `test.sh`) fails on Cyrillic in the Swift of `Sources`, `Extensions` and
  `Shared`, on an App Group or team ID literal in that Swift or in `shared/**/src/iosMain`, and on tables or
  `AppSymbol.swift` whose keys or cases differ from the catalog; `checkStringCatalog` compares the texts byte for
  byte in every Android build.
- `ITMOWidgetsTests/StringsTests` checks all of it on the English simulator: the tables and the Swift resolver with
  English preferred again, the CMP resolvers after the override, every `AppSymbol` image.

## Design system

SwiftUI-owned screens (the shell, settings, account, sign-in and other system screens) use native controls tinted
with the shared design tokens; Material stays inside the CMP screens. The kit lives in `iosApp/Sources/DesignSystem/`.

- Tokens. `scripts/ios/gen-tokens.py` turns `shared/designsystem/tokens/itmo-tokens.json` (written by
  `:shared:designsystem:exportDesignTokens`) into `Tokens.generated.swift`: the static light and dark colour roles
  and the extended colours (`ItmoColor.<role>` follows the environment's colour scheme), the corner scale and named
  shapes, spacing (`ItmoSpacing`), the type roles and motion (`ItmoMotion`). dp and sp become points 1:1, durations
  seconds. Run it after the tokens change and commit the result; never edit the file by hand.
- The generator refuses (exit 2) an unknown `schemaVersion`, a colour that is not `#RRGGBB`, a font family other
  than the system font, an unknown weight and a type role without a text style; a new schema needs a generator
  change. `scripts/ios/check-tokens.sh`, run by `test.sh`, calls `gen-tokens.py --check`, which fails while the
  committed file is stale.
- Type. Each M3 type role maps onto the Dynamic Type text style nearest its size at the default text size
  (`bodyLarge` is `body`, `titleLarge` `title2`, `labelLarge` `subheadline`; the display roles are `largeTitle`)
  with the role's weight: `Font.itmo(.bodyLarge)`, `Font.itmo(.titleMedium, emphasized: true)`. No fixed sizes, so
  every text scales with the user's text size.
- Touch targets are at least `ItmoMetrics.touchTarget` (the token's 48 pt, above the platform's 44 pt).
- Motion. `ItmoMotion.animation(_:reduceMotion:)` gives the kit's easing over a token duration, or none with
  Reduce Motion on.

| View | Use |
|---|---|
| `ItmoFormSection`, `ItmoToggleRow`, `ItmoValueRow`, `ItmoActionRow`, `ItmoRowLabel` | rows and sections of a `Form`; an `AppSymbol` in the primary role, titles that wrap, a destructive action in the error role |
| `ItmoLoadingView`, `ItmoEmptyView`, `ItmoErrorView` | full-width states with the same padding, so swapping them does not move the screen; the error view has a retry button (`common_retry`) |
| `ItmoProgressButton` | the prominent button of a screen; while in progress it shows a spinner, keeps its size and ignores taps |
| `ItmoDemoBanner` | the demo strip with the sign-in button (`demo_banner_text`, `demo_banner_sign_in`); the button moves under the text at accessibility sizes |

- Texts reach the kit resolved (`AppStrings`, `UiText.resolved`) and render verbatim; the kit's own defaults come
  from the catalog. Icons come only from `AppSymbol`.
- `SnapshotTests/DesignSystem/DesignSystemSnapshotTests` holds the references of every kit view in the four
  appearances.

## Shell and routes

The app's root is the SwiftUI shell in `iosApp/Sources/App/` (master A5). Until IO-21 binds the shared session it
runs on fixtures: placeholder roots, the session gate and the demo banner, without Kotlin.

- Tabs. `ShellTab` holds the roots of Android's `res/menu/bottom_nav.xml` in its order: recordbook, schedule, home,
  sport, me. The recordbook is declared but hidden until IO-09d2 (no placeholder reaches App Review); home is
  selected at launch. The container is one type, `Shell/ShellTabs.swift` (a `TabView` today), so IO-SW1 can swap it
  without touching the stacks or the router.
- Stacks and sheets. Each tab has one `NavigationStack` whose path the router holds; sheets open at the medium
  detent and drag to large.
- Session gate. No tab bar while the session is loading or signed out; the demo banner (`ItmoDemoBanner`) sits
  above the tab bar while the demo session is open. The fixture session comes from the launch argument
  `-itmoShellSession loading|signed-out|demo|signed-in` (signed in without it); UI tests pass it through
  `XCUIApplication.itmo(session:)`.
- Chrome rule (the owner may veto at T12). A CMP route draws its own DS-03 top bar and hides the SwiftUI navigation
  bar (`shellChrome(.compose)`); a SwiftUI screen keeps the native bar (`.native`). Hiding the bar turns UIKit's
  edge swipe back off, so the compose chrome turns it on again for the stack above its root;
  `ShellUITests.testEdgeSwipeGoesBackFromComposeChrome` fails without it.
- Router. Every input (an `itmowidgets://route/<id>` URL, an App Intent, a tap, a link) calls `AppRouter.open`.
  `RouteQueue` mirrors Android's `MainRouteQueue`: a route runs once, only when the session is ready and its root
  could be selected (the tab bar is on screen); a newer route replaces a waiting one. Leaving the session clears the
  stacks and the sheet. IO-06c moves the routes, the queue and the gate onto the shared route model.

| Route id | URL | Opens |
|---|---|---|
| `schedule`, `home`, `sport`, `me` | `itmowidgets://route/<root>` | that root as it is |
| `qr_pass` | `itmowidgets://route/qr_pass` | the QR pass above home (QR widget, Control, quick action) |
| `today` | `itmowidgets://route/today` | the schedule root on today (quick action) |
| any other id | `itmowidgets://route/<id>` | the damaged-link sheet above home (`app_link_unavailable_*`) |

The URL scheme is the build setting `APP_URL_SCHEME` (the app target in `project.yml`), registered in the app's
Info.plist as `CFBundleURLTypes`. Placed widgets and Controls keep their URLs, so the scheme and the route ids are
frozen; `StableIdentifiersTests.testRouteUrls` pins them.

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
| URL scheme and route ids | `itmowidgets://route/<id>`, ids in Shell and routes |

The App Group and Keychain group are build settings (`APP_GROUP_ID`, `KEYCHAIN_GROUP` in `Base.xcconfig`). Xcode
expands them into each bundle's Info.plist (keys `AppGroupID`, `KeychainGroup`) and entitlements; code reads them
from its own process's `Bundle.main` and never writes them as literals. `$(AppIdentifierPrefix)` is the team ID and
a dot on a device, `FAKETEAMID.` on the simulator and empty in a team-less device build.

Signing: builds are unsigned by default ("Sign to Run Locally", no team), which is all the simulator and CI need.
The ignored `iosApp/Config/Signing.local.xcconfig`, written by the integrator after gate T13, sets
`DEVELOPMENT_TEAM`, `ITMO_CODE_SIGN_IDENTITY = Apple Development` and `ITMO_ENTITLEMENTS_VARIANT = .signed`; the
last switches every target to its `.signed` entitlements, the only place for push and associated domains. No
team ID is committed.

## Data sharing

The iOS platform classes live in `shared/core/src/iosMain/` and run only in the app process (and, per SP-16, the
notification service's `SharedPush`); the widget extension reads files and never links Kotlin.

| Store | Where | Class |
|---|---|---|
| App files (`AppDirectories.files`), DataStore `app_preferences` included | `Library/Application Support/files` in the app container, backed up | `IosAppDirectories` |
| Cache (`AppDirectories.cache`) | `Library/Caches`, cleared by the system | `IosAppDirectories` |
| No-backup files (`AppDirectories.noBackup`) | `Library/Application Support/no-backup`, excluded from backup | `IosAppDirectories` |
| Files the extensions read | the App Group container | `AppGroupDirectory` |
| Secrets | the Keychain | `KeychainSecureStore` |

DataStore stays in the app container: it is single-process, so no extension opens it.

App Group container. `AppGroupDirectory.resolve` uses the group in `AppGroupID`, then any group AltStore lists in
`ALTAppGroups` (SP-23). When none resolves (an unsigned build) it falls back to `no-backup/app-group` in the app
container, logs one warning per process and carries on; the extensions then see nothing.

| File | Written by | Read by | Notes |
|---|---|---|---|
| `locks/<name>.lock` | `FileCrossProcessLock` | app, notification service | `flock(2)`; empty files, never deleted; `myitmo-refresh` guards the token refresh |
| session-v1.json | `SessionSnapshotWriter` | widget extension, notification service | `{"isu": Int?, "demo": Bool, "alertsAllowed": Bool}`; missing means signed out; no token |
| `<name>-v<N>.json` | `AppGroupSnapshotWriter` | widget extension, notification service | each card that adds a snapshot adds its row |

- Snapshots hold `{"version": N, "value": ...}`. A write goes to a temporary file of its own and is renamed over
  the old one, so a reader sees the old or the new snapshot, never a partial one; then the writer asks WidgetKit to
  reload the given kinds through `WidgetReloader`, which Swift implements (WidgetKit has no Objective-C API). A
  reader rejects a `version` above its own.
- Lock trap (0xdead10cc): iOS kills a suspended process that holds a lock on a file in a shared container. A
  `FileCrossProcessLock` is held only around the guarded call; a snapshot write takes no lock.

Keychain. `KeychainSecureStore` keeps one generic-password item per `SecureStore` name: service
`dev.alllexey.itmowidgets`, account = the name, access group = `KeychainGroup` from the process's own Info.plist,
`kSecAttrAccessibleAfterFirstUnlock` (the notification service and background refresh read while the device is
locked), never synchronised. Without the access-group entitlement the Keychain answers `errSecMissingEntitlement`
(-34018); the store then uses the process's default group and logs that once. A Kotlin/Native test binary has no
entitlements, so Keychain tests are the hosted `ITMOWidgetsTests/KeychainTests`.

| Item (account) | Written by | Read by |
|---|---|---|
| `myitmo_tokens` | `KeychainTokenStorage` (sign-in, and MyItmoApi's `TokenManager` on refresh) | app, notification service |

Each card that stores a secret adds its row. Values are never logged.

Session. `KeychainTokenStorage` is both MyItmoApi 2.x's `TokenStorage` and the session's `SessionTokenStore` over
the one `myitmo_tokens` item, serialised as Android's `MyItmoStorage` serialises `myitmo_tokens.enc` before sealing
it: five lines, each token base64url without padding (`~` for none), each expiry in epoch milliseconds. It keeps no
copy, so each read sees what another process wrote. The client's `TokenManager` is the only refresher; it refreshes
inside `FileCrossProcessLock("myitmo-refresh")` and re-reads the item once it holds the lock, so when the app and the
notification service find the same expired token only the first refreshes. A value that does not parse is dropped
(signed out); a Keychain failure is thrown and drops nothing.

Sign-out. Three `SessionDataCleaner`s run with the shared ones: the Keychain (every item of the service), the App
Group container (every file but the `locks` directory) and WebKit's website data, which Swift removes through
`IosCoreHost.clearWebsiteData` (`WKWebsiteDataStore`, as Android clears its WebView data).

## Core graph

Koin is the only dependency graph on iOS. `iosCoreModule(host)` (`shared/core/src/iosMain/.../core/di/`) defines
each core type once, what `:app`'s Hilt modules and `CoreBridge` give Android: `AppLog` (`OsLogAppLog`),
`AppDiagnostics`, the wall `Clock`, `AppDispatchers`, `AcademicTimeProvider` (Moscow time, no debug override),
`AppDirectories`, the App Group directory and snapshot writer, `SessionSnapshotWriter`, `CrossProcessLock`,
`SecureStore`, the `app_preferences` DataStore and the core preference stores, `BackendGate`, the session storage,
the Darwin engine, `MyItmoClient`, Core 2.0's `BackendClient`, `PlatformActions` and the sign-out cleaners.

- `DemoMode` and `SessionRepository` come from the account module; ports still in `:app` (`AppNotifier`,
  `FcmTokenSync`) are bound by the card that needs them.
- `IosCoreHost` is what the graph needs from Swift: `WidgetReloader`, `clearWebsiteData` and the top view
  controller for the share sheet.
- Backend origin: `BackendBaseURL` in the app's Info.plist, from `BACKEND_BASE_URL` in `Base.xcconfig`; dev
  (`https://dev.widgets.alllexey.dev`) in every configuration until Backend 1.8.0 is in production (gate R).
- `AppDiagnostics` keeps this launch's records in memory and writes each to unified logging; an error contributes
  only its type until the journal's sanitiser is shared (the crash hook and the diagnostics screen are IO-08a's).
- `PlatformActions`: the share sheet (the title is not shown: iOS's sheet has none), links (t.me in Telegram when
  installed), Apple Maps (`maps.apple.com`, the pin or the address) and the app's pages in Settings.
- `IosCoreGraph` runs the module in a Koin application of its own for hosted tests (`ITMOWidgetsTests/SessionTests`);
  `IosCoreModuleTest` resolves every definition on the simulator, since Koin's `verify()` is JVM-only.

Network and log. `darwinHttpEngine()` is the Ktor engine of every iOS client: no cookie storage, no cookie
handling, no URL cache (SP-15a, SP-15b); clients read cookies with Ktor's `setCookie()` and set
`followRedirects = false` where a redirect must surface. `OsLogAppLog` writes `AppLog` to unified logging, subsystem
= the bundle ID, category = the tag.

## Build and test

Gradle and `xcodebuild` run only through `scripts/ios/test.sh` (or `scripts/verify.sh run|klibs`), which hold the
`kn` build slot and pass the worktree's MyItmoApi pin.

```bash
scripts/ios/test.sh                                          # checks, xcodegen, build and all tests but UITests
scripts/ios/test.sh --only ITMOWidgetsTests/StableIdentifiersTests
scripts/ios/test.sh --record --only SnapshotTests/SampleSnapshotTests  # re-record snapshot references
scripts/ios/test.sh ui SmokeUITests                          # XCUITest classes (all of UITests without a class)
scripts/ios/test.sh kn core                                  # iosSimulatorArm64Test of shared/core
scripts/ios/test.sh --cleanup                                # delete this worktree's simulator
```

- `test.sh` runs every `scripts/ios/check-*.sh` first, then `xcodegen generate` and `xcodebuild build test` with
  DerivedData in `iosApp/build/` and the `.xcresult` in `iosApp/build/test-results/`. Its last line is
  `VERIFY A ios|ios-only|ios-ui|ios-kn PASS|FAIL <secs>s <sha7>`. The default run and `--only` skip `UITests`;
  `ui` runs only them.
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
- No toolchain download inside `xcodebuild`: `setup-java` installs JetBrains 21, the JDK
  `gradle/gradle-daemon-jvm.properties` asks for, and the job turns Gradle's toolchain auto-download off, so a
  criteria mismatch fails instead of reaching api.foojay.io. A step before `test.sh` runs `./gradlew help` with
  up to three attempts to fetch the wrapper distribution and the settings plugins.
- On failure the `.xcresult` from `iosApp/build/test-results/` and the snapshot diffs from
  `iosApp/build/snapshot-artifacts/` (`SNAPSHOT_ARTIFACTS` for the test runner) are uploaded as `ios-check-results`.
- The step summary records the duration of `test.sh --ci` and the peak used memory (active, wired and compressed
  pages, sampled every 5 s).
- `.github/workflows/ios-nightly.yml` runs on a schedule against `v2.3/next`: `test.sh --ci kn` over every shared
  module with the testing convention, and `test.sh --ci ui` (every class in `UITests`, `SmokeUITests` included). It
  is never a required check. It installs JetBrains 21 with toolchain auto-download off and resolves the Gradle
  plugins with retries before the build, as `ios.yml` and `android-ci.yml` do.

Measured on the first runs (no caches yet): the job takes 18.5 to 20 minutes, `test.sh --ci` 1076 to 1157 s, with
a peak of 6.3 to 6.4 GB used of 7 GB. If the build runs out of memory, split it into a framework job and an
`xcodebuild` job.

## Visual verification

SwiftUI screens, system surfaces and widget entry views are checked by snapshot tests; Compose Multiplatform
content is not. CMP draws into a Metal layer that a view-hierarchy snapshot of a `ComposeUIViewController` misses
(the image is blank), so CMP hosts are checked by XCUITest screenshots and their goldens stay Roborazzi's on the JVM.

- `SnapshotTests` uses [swift-snapshot-testing](https://github.com/pointfreeco/swift-snapshot-testing), pinned in
  `iosApp/project.yml`. `assertAppearances(of:named:)` renders a view in four appearances (light and dark, each at
  Dynamic Type L and AX1), 320 pt wide (narrower than any supported iPhone) at a fixed scale of 2, over the system
  background. Precision is set once in `SnapshotMatrix`: every pixel must match within a CIE Delta E of 2.
  `SampleSnapshotTests` proves that a one-pixel change fails.
- References live in `__Snapshots__/<test file>/<test>.<name>-<appearance>.png` beside the test file and are
  committed with the code behind them. A run without a reference records it and fails; `test.sh --record` re-records
  every reference it runs (and fails, by design); `test.sh --ci` never records. Failure images go to
  `iosApp/build/snapshot-artifacts/`.
- Record with the pinned Xcode, runtime and device from `scripts/ios/env.sh`. When CI disagrees with a local
  recording, the images from the CI artifact win.
- `UITests` launch the app in English (`-AppleLanguages (en) -AppleLocale en_US`) through `XCUIApplication.itmo()`.
  `scripts/ios/screenshots.sh [<Class>...]` runs them in light and dark under a fixed status bar (9:41, full battery)
  and exports the screenshots attached with `attachScreenshot(named:)` to `iosApp/build/screenshots/<appearance>/`
  for review; they are never committed.
