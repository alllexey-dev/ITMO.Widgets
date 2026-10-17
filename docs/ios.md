# iOS app

The iOS client lives in `iosApp/` and links one Kotlin umbrella framework, `Shared`, built from `shared/ios/`
([ADR 0023](decisions/0023-ios-client.md)). It has three bundles: the app, a WidgetKit extension and a notification
service extension. The app is a SwiftUI shell with five tabs (recordbook, schedule, today, sport, profile). System screens
(sign-in, the first-run flow, settings, diagnostics, the web sign-in, My ITMO web, the update offer, the BARS sign-in),
Сегодня with the bell's sheet, the pass sheet, the schedule with its sheets (the lesson sheet, the month sheet, the
friend picker), the recordbook, the subject page with `Мои баллы`, the sport tab with its sections and `Откуда баллы`, the profile tab,
Друзья, the person card with the review report and another user's friends are SwiftUI over shared ViewModels; the
other content screens (another user's sport, the review editor, links) are still shared Compose
Multiplatform screens hosted through
`ComposeUIViewController`. They move to
SwiftUI screen by screen ([ADR 0031](decisions/0031-swiftui-ios-screens.md), see Moving to SwiftUI).

## Prerequisites

| Tool | Pin | Where it is pinned |
|---|---|---|
| Xcode | 27.0 (27A266a) at `/Applications/Xcode.app` | `scripts/ios/env.sh` |
| Simulator | iPhone 17, iOS 27.0 runtime | `scripts/ios/env.sh` |
| XcodeGen | 2.46.0 (`brew install xcodegen`) | `scripts/ios/env.sh` |
| JDK | 21 for Gradle | `scripts/ios/env.sh` |
| Deployment target | iOS 26.1 | `iosApp/Config/Base.xcconfig` |

`scripts/ios/env.sh` warns when the local Xcode or XcodeGen differs from the pin; CI selects exactly the pinned Xcode.
A CI image with another Xcode presets the `ITMO_*` variables instead of editing the file.

## Layout

| Path | Contents |
|---|---|
| `iosApp/project.yml` | XcodeGen spec: targets, the Kotlin Run Script, the `ITMOWidgets` scheme; the `.xcodeproj` is generated and ignored |
| `iosApp/Config/` | `Base.xcconfig` (identifiers, versions, signing defaults) and one xcconfig per target |
| `iosApp/Resources/` | `Info/` plists and per-target entitlements, unsigned and `.signed` |
| `iosApp/Sources/` | the app target: `App/` (entry, shell, router, Debug fixtures), `Bridge/` (Swift glue to Kotlin), `DesignSystem/`, `Features/<Feature>/`, `Intents/`, `Background/`, `Push/`, `Support/` |
| `iosApp/Extensions/Widgets/` | the widget extension (WidgetKit, Controls), no Kotlin |
| `iosApp/Extensions/NotificationService/` | the notification service extension, no Kotlin |
| `iosApp/Shared/` | sources of all three targets: generated string tables and `AppSymbol.swift`, brand symbol images, `WidgetSnapshots/` (App Group readers), `Intents/` |
| `iosApp/Strings/` | `strings_ios*.xml`: copy only iOS shows (Android resource syntax, Russian) |
| `iosApp/Tests/` | `UnitTests/` and `SnapshotTests/` (hosted in the app), `UITests/` (XCUITest) |
| `shared/ios/` | the static framework `Shared` with SKIE: the Koin start, `IosPlatform`, the ViewModel store, the Compose screen factories |
| `scripts/ios/` | `env.sh`, `test.sh`, `check-*.sh`, `gen-tokens.py`, `screenshots.sh`, `archive.sh` |

Targets use directory globs: a new Swift file needs no `project.yml` edit. The app target also compiles the extension
sources (without their entry points), so hosted tests reach them.

## Targets and processes

Each bundle runs in its own process; they share only the App Group container and the Keychain group.

| Target | Kotlin | Does |
|---|---|---|
| `ITMOWidgets` (app) | `Shared` | every screen and network request, DataStore, the Keychain session, the App Group writers, background refresh, local notifications, push registration |
| `ITMOWidgetsWidgets` | none | QR, lesson and day widgets and the QR Control; reads App Group files, never calls the network |
| `ITMOWidgetsNotificationService` | none | the sport booking of a push and its text; Swift micro-client for MyITMO and Backend |

- Only the app has DataStore and a Koin graph. An extension that needs a fact reads a versioned JSON file the app
  wrote; session-v1.json carries the session state and the demo flag, never a token.
- The widget extension has about 30 MB, which the Kotlin runtime alone would use up, so it links no Kotlin. The
  notification service stays Swift-only for the same reason.
- Extensions compute no academic time: the app writes timelines with their instants and WidgetKit picks the entry.

## Shell and routes

The root is the SwiftUI shell in `iosApp/Sources/App/Shell/`.

- Session gate. `ShellSession` maps the shared `SessionRepository.state` and the first-run flag onto the loading gate,
  the sign-in screen (`AuthScreen`), the first-run flow (`OnboardingScreen`) or the tabs. The demo session shows the
  tabs with `ItmoDemoBanner` below each tab's stack.
- Tabs. `ShellTabs` is the glass `TabView` of iOS 26 in the bar order of the shared `AppTab` (recordbook, schedule,
  today, sport, profile), with its names and icons; a tab shows only while `PlatformCapabilities` offers its
  feature. `ShellTab`'s raw values are the frozen route ids, so today is `home` and profile is `me`. Tabs switch by
  the tab bar only, no swipe ([Tab switching](design.md#tab-switching-and-back)); a pushed screen keeps the bar.
- Pass accessory. While today is selected, `tabViewBottomAccessory` shows `QR-пропуск` (`ShellPassAccessory`), which
  opens the pass sheet (`AppRoutes.QrPass`), the same sheet widgets, the Control and shortcuts open. `QrPassSheet`
  (`Features/Qr/`) is a content-height `ItmoSheet` over `QrPassViewModel`: holder, the code from `QrCodeGenerator`
  drawn as the QR widget draws it, and the line with `Обновить`; full brightness while it is on screen.
- Root header. A SwiftUI root takes `.itmoRootHeader(title:subtitle:titleMenu:titleMenuLabel:actions:)`: title and
  subtitle with the actions in one row, static (owner, 2026-10-13); with `titleMenu` the title is a button with a
  chevron (the schedule's month sheet). The system's `.inlineLarge` title collapses to `.inline` on scroll, so the
  root hides the navigation bar and draws the header in a `safeAreaBar` on the grouped background that the content
  scrolls under and never shows through; the navigation title stays for a pushed screen's back button. Today's root (`TodayScreen`,
  `Features/Home/`) has it with today's academic date and the bell (`ItmoBellButton` with the count of unseen events,
  today only) over `TodayContent` (`TodayViewModel`: the hero, the day as `ItmoLessonTimeline` from the shared
  `LessonTimelines` rows, `Support/LessonUi+Kit.swift`, the things to do); the bell opens `BellSheet`, a tall
  `ItmoSheet` over `BellViewModel` ([the bell](features/home.md#the-bell)). The other roots are still Compose screens
  with their own top bar.
- Stacks and sheets. Each tab has one `NavigationStack` whose path `AppRouter` holds. One router sheet shows at a
  time; `replaceSheet(with:)` swaps it. Sheets take one of three heights (`ItmoSheet`, `ItmoSheetHeight`): the
  content's, 0.82 of the screen, or full; none goes under the status bar.
- Router (`Sources/App/Router/`) on the shared route model of `:shared:core`. Every URL, App Intent and notification
  becomes an `EntryRoute` (`RouteURL` over `EntryRouteParser`) and waits in the shared `RouteQueue` until the tabs are
  on screen; a tap opens an `AppRoute` key with `AppRouter.open(_:)`. Results return through `open(_:onResult:)` and
  `deliver(_:from:)`.
- Route map. `Routes.target(for:)` switches over `RouteFeature` and delegates to the feature's `Routes+<Feature>.swift`,
  which returns `.swiftUI` (native bar), `.compose` (the route draws its own bar), `.sheet`, `.swiftUISheet`, `.composeSheet`, a tab,
  the gate or `.notOnIOS`. `RouterTests` fails for a key no feature claims.
- Debug launch arguments replace Android's debug tools: `-itmoDemo`, `-itmoSignedOut`, `-itmoShellSession <state>`,
  `-itmoOnboarding`, `-itmoRunRefresh`, `-itmoNotificationFixture <kind>`, `-itmoWebLoginFixture`, `-itmoBarsLogin`.

| Route id | URL | Opens |
|---|---|---|
| `home` (today), `schedule`, `recordbook`, `sport`, `me` (profile) | `itmowidgets://route/<root>` | that root |
| `qr_pass` | `itmowidgets://route/qr_pass` | the pass sheet over today |
| `today` | `itmowidgets://route/today` | the schedule root on today |
| `lesson` | `itmowidgets://route/lesson?args=<base64url LessonDetailsArgs JSON>` | the lesson sheet over the schedule (without readable `args`, the schedule) |
| `sport` with `lesson` | `itmowidgets://route/sport?lesson=<id>[&predicted=1]` | Sport on that lesson (`TabRequest.SportLesson`) |
| any other id | `itmowidgets://route/<id>` | the damaged-link sheet over today |

The scheme is the build setting `APP_URL_SCHEME`. Placed widgets and Controls keep their URLs, so the scheme, the route
ids and the query names `args`, `lesson`, `predicted` are frozen (`StableIdentifiersTests`). Widgets build them in
`WidgetRoute` (`Extensions/Widgets/Common/`); `RouteURL` reads them through `IosRoutes.lessonRoute` and `sportRoute`.

## Swift bridge and screens

Swift reaches Kotlin through `Shared` and SKIE (`shared/ios/build.gradle.kts`): a sealed type switches through
`onEnum(of:)`, a suspend function is `async throws`, a `Flow` is an `AsyncSequence`, a `StateFlow` has `value`.

- Exports. `:shared:core`, `:shared:feature-account`, `:shared:feature-settings`, `:shared:feature-recordbook`,
  `:shared:feature-qr` and `:shared:feature-home` are exported, so their types keep their names in Swift. A feature whose ViewModel SwiftUI owns exports its module.
- Koin. `App.init` calls `IosStrings.installAppLocale()`, then `startKoinIos(platform: AppPlatform())`: one graph
  over `IosKoinModules.all(platform)`, `allowOverride(false)`. iOS bindings live in
  `shared/feature-<x>/src/iosMain/.../di/<Area>IosModule.kt`. Swift reads the graph with
  `IosKoin.shared.get(protocol:)` or `get(type:)`.
- `IosPlatform` is the one Kotlin interface Swift implements (`Sources/Bridge/AppPlatform.swift`): WidgetKit reloads,
  the top view controller, WebKit clearing and cookies, the hidden BARS web view, pairing's CryptoKit HPKE and
  LocalAuthentication (`PairingHost`).
- SwiftUI screens. `ObservableViewModel<Model, State>` (`Sources/Bridge/ObservableViewModel.swift`) is `@Observable`,
  owns a `ScreenViewModelStore`, resolves the ViewModel from Koin on first use, follows `uiState` with
  `.observing(model)` and delivers events with `.onEvents(of:_:perform:)`. Its `deinit` clears the store, which
  cancels `viewModelScope`. Arguments are Koin parameters; there is no `SavedStateHandle` on this path.
- Compose screens. Only `shared/ios` builds a `ComposeUIViewController`, through `screenController { }` in
  `screens/ScreenControllers.kt`, which wraps `ItmoTheme` and `LocalPlatformActions`; each feature has
  `screens/<Feature>Screens.kt` with the factories Swift calls. `ComposeHost { factory() }`
  (`Sources/Bridge/ComposeHost.swift`) makes the controller once and ignores the safe area; the factory pads
  `WindowInsets.safeDrawing`. A Compose sheet is hosted inside a SwiftUI `.sheet`, never a Compose
  `ModalBottomSheet`. A SwiftUI `safeAreaInset` never reaches a hosted controller.
- `UiText` resolves in Swift with `text.resolved` (`Sources/Support/UiText+Resolve.swift`), icons with
  `AppIcon.symbol` and `AppSymbol.image`.

## Settings and diagnostics

Settings are SwiftUI: `Routes+Settings.swift` maps each `AppRoutes.Settings(page)` key to `SettingsScreen`, which owns
the page's shared `SettingsViewModel` and renders it with `SettingsForm` (toggle, menu, navigation, action and value
rows). Page providers hide what `PlatformCapabilities` does not offer. `SettingsIosCopy` replaces texts that name
Android with `strings_ios*.xml` rows; the background work row is Background App Refresh. The screen reports the
notification permission and Background App Refresh to the ViewModel on appear and on every return to the app. The
calendar switch asks EventKit for access in Swift (`Features/Calendar/`). `DiagnosticsScreen` lists `AppDiagnostics`
records and shares the journal.

## Strings and icons

The catalog is the Android resource files ([ADR 0028](decisions/0028-strings-and-icons.md)); Swift holds no Russian.

- `./gradlew :app:exportAppleStrings` writes `iosApp/Shared/Strings/*.xcstrings`, one table per catalog file;
  `strings_platform.xml` and `iosApp/Strings/strings_ios*.xml` go to `Localizable` (APNs resolves `loc-key` only
  there); `InfoPlist` and `AppShortcuts` rows come from `build-logic/strings/apple-tables.properties`. Commit the
  result; never edit a table by hand.
- Every bundle has only the `ru` localization. Compose Multiplatform takes plural rules from the preferred languages,
  so `installAppLocale()` puts `ru` first for the app process.
- Swift uses generated symbols: `Text(.StringsCommon.scheduleLessonCount(5))`; App Intents take a key literal.
- Icons: `iosApp/Shared/Symbols/AppSymbol.swift` is generated from `docs/design/icons.tsv` and names an SF Symbol per
  row; `custom.*` names are symbol images in `Brands.xcassets`.
- `scripts/ios/check-sources.sh` fails on Cyrillic in Swift, on App Group or team ID literals, and on stale tables.

## Design system

SwiftUI screens use native controls with the shared tokens; the kit is `iosApp/Sources/DesignSystem/`, ported from
the design lab's prototype (`proto/kit/ios`, DECISIONS.md) and described in [design.md](design.md#swiftui-screens-ios).

- `scripts/ios/gen-tokens.py` turns `shared/designsystem/tokens/itmo-tokens.json` into `Tokens.generated.swift`:
  colour roles, shapes, spacing, type roles and motion. `check-tokens.sh` fails while the file is stale.
- Type roles map onto the nearest Dynamic Type text style (`Font.itmo(.bodyLarge)`), so text scales. Touch targets
  are at least `ItmoMetrics.touchTarget`. `ItmoMotion.animation(_:reduceMotion:)` respects Reduce Motion.
- Frames: `.itmoRootHeader(title:subtitle:)`, `ItmoScreenStack` (the scrolling column), `ItmoSheet` with
  `ItmoSheetHeight` (`.content`, `.tall` at 0.82, `.full`), `ItmoBellButton`.
- Blocks: `ItmoCard` (one hero per screen), `ItmoSectionHeader`, `ItmoListGroup` with `ItmoRow`, `ItmoIconTile` and
  `ItmoGroupNote`; `ItmoStatusPill` over `ItmoPill`, `ItmoAvatar`, `ItmoFlow`; `ItmoSegmentedChoice` (the native
  segmented picker) for two to four options and in-screen tabs. Tones (`ItmoTone`) colour cards, pills and tiles.
- Lessons: `ItmoLesson` is what a lesson says, resolved by the caller; a day's list is `ItmoLessonTimeline` (rail,
  now marker, gap rows, change and overlap notes), a single lesson `ItmoLessonCard`; `ItmoWeekStrip` (in
  `ItmoFilters.swift`) is one week of the schedule's and the sport tab's strip.
- States: `ItmoLoadingView`, `ItmoEmptyView` and `ItmoErrorView` (the system's unavailable view with one glass
  button), `ItmoGateCard`, `ItmoActionCard`, `ItmoLockNote`, `ItmoStale`; settings pages keep `ItmoFormSection` and
  the form rows. `ItmoProgressButton`, `ItmoDemoBanner`.
- Texts reach the kit resolved, so the kit holds no copy; icons come only from `AppSymbol`.

## Identifiers and signing

Frozen from the first TestFlight build; `iosApp/Tests/UnitTests/StableIdentifiersTests.swift` pins them
([ADR 0016](decisions/0016-parity-rewrite.md)).

| Identifier | Value |
|---|---|
| Bundle IDs | `dev.alllexey.itmowidgets`, `.widgets`, `.notification-service` |
| App Group | `group.dev.alllexey.itmowidgets` (all three) |
| Keychain access group | `$(AppIdentifierPrefix)dev.alllexey.itmowidgets.shared` (app and notification service) |
| Widget kinds | `dev.alllexey.itmowidgets.widget.qr`, `.widget.single-lesson` (next lesson and Lock Screen), `.widget.day-schedule`, `.widget.sport` |
| Control kind | `dev.alllexey.itmowidgets.control.qr` |
| Quick action types | `dev.alllexey.itmowidgets.qr_pass`, `dev.alllexey.itmowidgets.today` |
| Background task | `dev.alllexey.itmowidgets.refresh` |

App Group and Keychain group are build settings (`APP_GROUP_ID`, `KEYCHAIN_GROUP` in `Base.xcconfig`), expanded into
each bundle's Info.plist (`AppGroupID`, `KeychainGroup`) and entitlements; code reads them from its own
`Bundle.main`. `BACKEND_BASE_URL` is dev for Debug and production for Release.

Builds are unsigned by default, which is all the simulator and CI need. The ignored
`iosApp/Config/Signing.local.xcconfig` (included by `Base.xcconfig`) sets `DEVELOPMENT_TEAM`,
`ITMO_CODE_SIGN_IDENTITY = Apple Development` and `ITMO_ENTITLEMENTS_VARIANT = .signed`, which switches every target to
its `.signed` entitlements, the only place for push and associated domains. No team ID is committed.

## Data sharing

Kotlin platform classes live in `shared/core/src/iosMain/` and run only in the app. App files, cache and no-backup
files are in the app container (`IosAppDirectories`); DataStore never leaves it.

App Group files (`AppGroupDirectory`; an unsigned build without the group falls back to a private directory and the
extensions see nothing). Snapshots are `{"version": N, ...}`, written to a temporary file and renamed, then the
writer reloads the widget kinds through `WidgetReloader`. A reader treats a missing, corrupt or newer file as its
placeholder state (`AppGroupSnapshot`, `SnapshotReaderTests`).

| File | Written by | Read by |
|---|---|---|
| session-v1.json | `SessionSnapshotWriter` on every session change | widgets, notification service |
| qr-pass-v1.json | `QrPassSnapshotWriter` (`:shared:feature-qr`) | QR widget |
| qr-widget-v1.json | `RevealQrIntent` in the widget extension | QR widget |
| schedule-timeline-v2.json | `ScheduleTimelineWriter` (`:shared:feature-schedule`) on the session, widget options, foreground, cached schedule and after a booking | next-lesson and day widgets |
| sport-widget-v1.json | `SportWidgetWriter` (`:shared:feature-sport`) on the session and widget theme, after a booking and as the `sport-widget` refresh step | sport widget |
| `locks/<name>.lock` | `FileCrossProcessLock` (`flock`), held only around the guarded call | app, notification service |
| `sport-results/<lesson>-<millis>.json` | `SportResultInbox` in the notification service, one per booked or refused queue lesson, moved in place whole | `AppGroupSportResultInbox` (`:shared:feature-sport`), which moves each into the bell's `SportResultLog` and deletes it |

Keychain (`KeychainSecureStore`, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, never synchronised or restored
from a backup on another device): `myitmo_tokens` (app and
notification service; refreshed inside the `myitmo-refresh` lock), `bars_tokens.enc` and `itmo_id_cookies` (app).
Sign-out clears the Keychain items, the App Group files (keeping `locks` and system metadata) and WebKit's data.

## Widgets

The widget extension reads App Group files on each timeline request and makes no network call.

- QR widget (`Extensions/Widgets/Qr/`): signed out, spoiler, revealed and expired states; the spoiler is a
  `Button(intent: RevealQrIntent())` that shows the code for 30 s. Options come from qr-pass-v1.json.
- Next-lesson widget (`NextLessonWidget`, kind `.widget.single-lesson`): small, medium (two more lessons on the
  right), and on the Lock Screen the line beside the date, the rectangle and the countdown ring. The day widget
  (`DayWidget`, large only) draws the day to scale (`DayAxis`: linear time, every lesson at least one readable row),
  gaps, and the now line. Both read schedule-timeline-v2.json in `Extensions/Widgets/Schedule/`: Swift picks the entry
  whose `validFrom` has passed and, inside it, the last `nowTicks` minute; countdowns and progress are the system's
  live views on `until` and `since` (`WidgetCountdown`, `WidgetLessonProgress`), so Swift computes no time.
- Sport widget (`Extensions/Widgets/Sport/`, medium and small): the score ring, the remaining points and the nearest
  bookings or queues from sport-widget-v1.json.
- The pass widget and the pass Control (`Extensions/Widgets/Controls/`) are titled `settings_widget_pass_title`; the
  Control opens the pass sheet through `OpenRouteIntent`.
- Every widget shows a calm placeholder signed out, in the demo, without a file or a container (unsigned builds), and
  for the loading and error snapshots. The app's palette (`settings_widgets_theme_title`) reaches every file as
  `palette`.
- The widget sources (minus `WidgetsBundle.swift`) compile into the app too: the widgets gallery
  (`Sources/Features/Settings/WidgetGalleryForm.swift`) draws the real entry views on the shared sample, which
  `IosWidgetPreviews` (`shared/ios`) writes as the App Group files the writers would leave for the gallery's settings
  and theme, frozen at 12:22 on the sample Monday. The gallery counts placed widgets per kind through
  `WidgetCenter.currentConfigurations()` and offers how to add one, the Lock Screen widgets and the Control.

## System entries

The QR Control, the App Shortcuts (`ITMOWidgetsShortcuts`, `Sources/Intents/`) and the static quick actions open a
route id (`qr_pass`, `today`) through `RouteInbox` (`Shared/Intents/`), which the app connects to the router.

## Background refresh

One app refresh task, `dev.alllexey.itmowidgets.refresh`. `BackgroundRunner` (`shared/ios`) is the entry point of the
task, of every launch and every return to the foreground. It runs the bound `RefreshStep`s in `RefreshStepKeys.ORDER`
(widget snapshots, the sport widget at most every 30 minutes, schedule changes, marks with the BARS cookie renewal,
calendar sync), keeps Android's periods in
`UserDefaultsRefreshStepLog`, and stops after 25 s. `IosAppNotifier` posts local notifications; anything found between
00:00 and 06:00 Moscow time is scheduled for 06:00.

## Notifications

- Registration. `PushDeviceRegistration` registers the device token with Backend (`platform = IOS`, `alertsAllowed`,
  app version) only with the services opt-in, a signed-in ISU and a token. Each return to the foreground refreshes
  the notification settings into session-v1.json.
- Taps. `NotificationTaps` reads the route from `userInfo` (`NotificationTapRoutes`) and hands it to the router, so a
  cold-start tap waits for the shell.
- Notification service (Swift micro-client). `PushHandler` drops pushes for another ISU, signed out or the demo;
  shows friendship pushes as Backend sent them; books sport pushes through `SportPushBooker` on MyITMO and reports
  the outcome to Backend. `MyItmoAccess` refreshes the token under the `myitmo-refresh` lock. Tests replay the Kotlin
  golden fixtures (`NotificationServiceTests`), since `simctl push` never runs an extension.

## Degradations

| Surface | On iOS | Why |
|---|---|---|
| Tabs | native tab bar only, no swipe | paging between tabs is not an iOS convention |
| Pass entry | the `QR-пропуск` tab bar accessory instead of the `Пропуск` FAB | the accessory is the iOS 26 place for it |
| Colours | static brand scheme | iOS has no wallpaper colours |
| Placing a widget | the app shows how to add one | iOS lets no app place a widget |
| QR widget | fade instead of the circle reveal; no custom spoiler image | WidgetKit animates only between entries |
| Widget countdowns | the minutes in the system's full words (`48 минут`), not `48 мин` | the live offset text has no abbreviated style |
| Widget gallery of the system | the user's own data or a placeholder, no sample | the sample comes from Kotlin, which the extension does not link |
| QR tile | the Control opens the pass, cannot draw the code | a Control shows only a symbol and a title |
| Widget refresh | no seven-minute pending sport refresh | WidgetKit allows about 40 to 70 reloads a day |
| Background checks | when iOS wakes the app, at launch and on return | iOS has no WorkManager |
| BARS in the background | only through the Keychain cookie copy | a background run has about 30 s and no window |
| Notification channels | one switch | iOS has no channels |
| Push, Universal Links, dropped-push filtering | not available in unsigned builds | they need the Apple developer account |
| Update offer | App Store page only, nothing while `APP_STORE_ID` is empty | no other channel on iOS |
| Debug tools | launch arguments instead | the debug tools are Android-only (ADR 0016) |

## Build and test

```bash
scripts/ios/test.sh                                   # checks, xcodegen, build, unit and snapshot tests
scripts/ios/test.sh --only ITMOWidgetsTests/StableIdentifiersTests
scripts/ios/test.sh --record --only SnapshotTests/SettingsSnapshotTests   # re-record references
scripts/ios/test.sh ui SmokeUITests                   # XCUITest classes (all without a class)
scripts/ios/test.sh kn core                           # iosSimulatorArm64Test of a shared module
scripts/ios/screenshots.sh                            # review screenshots, light and dark
scripts/ios/test.sh --cleanup                         # delete this worktree's simulator
./gradlew verifyIosKlibs                              # Kotlin/Native klibs only
```

- The app target's Run Script builds `Shared` with `./gradlew :shared:ios:embedAndSignAppleFrameworkForXcode`.
- `test.sh` runs `xcodebuild build-for-testing`, then `test-without-building`, so the scheme (and Gradle) builds
  once. The simulator boots only for the test step: on the 3-vCPU CI runner a boot during the build slowed the
  build by as much as it saved.
- To open the project in Xcode run `xcodegen generate --spec iosApp/project.yml` first.
- MyItmoApi comes from Maven Central; `MYITMOAPI_DIR` builds against a local checkout.
- CI: `.github/workflows/ios.yml` runs `ios-check` (`scripts/ios/test.sh --ci`) on PRs that change iOS inputs and on
  pushes to `master` that change `shared/` (see [testing.md](testing.md#ci)); `ios-nightly.yml` runs every module's
  `kn` tests and all `UITests`.
- Release builds: `scripts/ios/archive.sh --unsigned` (an `.ipa` for self-signing) or `--signed [--upload]` with
  `Signing.local.xcconfig`. The build number is `git rev-list --count HEAD`. Uploads are manual steps.

## Visual verification

- SwiftUI screens and widget entry views have swift-snapshot-testing references in `__Snapshots__/` beside each test:
  `assertAppearances(of:named:)` renders light and dark at Dynamic Type L and AX1, 375 pt wide (iPhone SE, the
  narrowest iPhone of iOS 26). `test.sh --ci` never records; when CI disagrees with a local recording, the CI images
  win. The system spinner starts on a frame picked by the time it appears, so layer renders draw an indeterminate
  `ProgressView` with a still stand-in (`StillSpinnerStyle` in `AssertAppearances.swift`).
- Whole screens with system chrome (the glass tab bar, navigation bars, presented sheets) use
  `assertScreenAppearances(of:named:)`, which draws the view in a window of its own at 375 x 667 pt with UIKit
  animations off; a layer render misses that chrome. `ShellSnapshotTests` covers the tabs, the root header, a pushed
  screen and the sheet heights on kit fixtures, `KitSnapshotTests` the kit components, `TodaySnapshotTests` and
  `BellSheetSnapshotTests` Сегодня and the bell on the states the shared presenter builds from `TodaySamples`,
  `ScheduleSnapshotTests` the schedule and its sheets on `ScheduleSamples`.
- CMP-hosted routes draw into a Metal layer those snapshots miss, so they are checked by XCUITest screenshots and
  their goldens stay Roborazzi's on the JVM until their SwiftUI screens replace them
  ([ADR 0022](decisions/0022-jvm-screenshot-tests.md)).

## Moving to SwiftUI

[ADR 0031](decisions/0031-swiftui-ios-screens.md): every iOS screen becomes native SwiftUI over a shared,
Compose-free view model. A route moves in one change: the SwiftUI screen in `Sources/Features/<Feature>/`, its
snapshot tests, the `.swiftUI` target in `Routes+<Feature>.swift`, and the deletion of its Compose factory in
`shared/ios/.../screens/` and its `ComposeHost` wrapper. How: [Adding a SwiftUI screen](recipes/ios-host.md). Open
questions (minimum iOS version, how view models reach Swift, screen order) are listed in the ADR.
