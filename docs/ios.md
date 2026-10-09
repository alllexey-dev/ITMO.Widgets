# iOS app

The iOS client is a SwiftUI shell around the shared Compose Multiplatform screens, with WidgetKit extensions and a
notification service extension ([ADR 0023](decisions/0023-ios-client.md)). It lives in `iosApp/` and links one Kotlin
umbrella framework, `Shared`, built from `shared/ios/`. The app is the shell with a Compose root in each of the five
tabs (recordbook, schedule, home, sport, me), gated on the shared session with the sign-in screen and the first-run
flow (see Shell and routes, Sign-in). Its Compose screens are the shared ones Android shows: the QR pass, the
recordbook with BARS, its subject page, sheets and mark tracking, the schedule with its changes and the friend
picker, the home feed, the sport tab, the Me tab, the social screens, the teacher reviews and the subject links.
Settings, sign-in, the first-run flow, the web sign-in, My ITMO and the update offer are SwiftUI screens over the
shared ViewModels. The widget bundle holds the QR, lesson and day widgets and the QR Control (see Widgets; App
Shortcuts and quick actions in System entries); one background refresh task runs the widget snapshots, the
schedule change and mark checks and the calendar sync (Background refresh); the notification service passes
notifications through unchanged. What iOS does differently from Android, and why, is in Degradations.

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
| `iosApp/Sources/` | the app target `ITMOWidgets` (SwiftUI); `App/` the app entry, the shell, the router and the Debug fixtures (Shell and routes), `Bridge/` the Kotlin side's Swift glue (Swift bridge), `DesignSystem/` the SwiftUI kit (Design system), `Features/<Feature>/` the Swift screen or host of each route, `Intents/` the App Shortcuts and the quick actions (System entries), `Background/` the app refresh task (Background refresh), `Push/` the notification taps and the permission refresh (Notifications), `Support/` string and icon resolution |
| `iosApp/Extensions/Widgets/` | the widget extension `ITMOWidgetsWidgets` (WidgetKit, Controls; no Kotlin); `<Widget>/` per widget, `Controls/` the Controls. The app target compiles these sources too, without `WidgetsBundle.swift`, so the hosted tests reach them |
| `iosApp/Extensions/NotificationService/` | the notification service extension `ITMOWidgetsNotificationService` (no Kotlin) |
| `iosApp/Shared/` | sources of all three targets: the generated string tables and `AppSymbol.swift`, the custom symbol images, `WidgetSnapshots/` (readers of the App Group snapshots), `Intents/` (App Intents of widget buttons and Controls, `RouteInbox`; not in the notification service) |
| `iosApp/Strings/` | `strings_ios*.xml`: catalog files with copy only iOS shows |
| `iosApp/Tests/UnitTests/` | `ITMOWidgetsTests`, hosted in the app; `Fixtures/` holds the App Group JSON the Kotlin writers' tests produce |
| `iosApp/Tests/SnapshotTests/` | `SnapshotTests`, hosted in the app: SwiftUI and widget entry view snapshots (swift-snapshot-testing), references in `__Snapshots__/` |
| `iosApp/Tests/UITests/` | `UITests` (XCUITest): smoke tests and review screenshots |
| `shared/ios/` | the umbrella framework `Shared` (static, with SKIE) over every shared module; it exports `:shared:core`; the Koin start, `IosPlatform`, the ViewModel store and the Compose screen hosts |
| `scripts/ios/` | `env.sh` (pins), `test.sh` (build and test), `check-*.sh` (source checks), `screenshots.sh` (review screenshots), `archive.sh` (device archives, Release) |

Targets use directory globs: a new Swift file in a target directory needs no `project.yml` edit. Only the app links
`Shared`; the extensions stay Swift-only (memory limits, ADR 0023).

## Targets and processes

Each bundle runs in a process of its own; they share only the App Group container and the Keychain group (Data
sharing), never memory or a Koin graph.

| Target | Bundle | Kotlin | Does |
|---|---|---|---|
| `ITMOWidgets` | the app | `Shared` | every screen, every network request, DataStore, the Keychain session, the App Group writers, the background refresh task, local notifications, the push registration |
| `ITMOWidgetsWidgets` | the widget extension | none | the QR, lesson and day widgets and the QR Control; reads the App Group files on each timeline request and makes no network call; writes only qr-widget-v1.json (a reveal) |
| `ITMOWidgetsNotificationService` | the notification service | none | delivers every notification unchanged |
| `ITMOWidgetsTests`, `SnapshotTests` | hosted in the app | through the app | unit and snapshot tests (Build and test, Visual verification) |
| `UITests` | the XCUITest runner | none | drives the app from outside |

- The app process is the only one with DataStore and a Koin graph; an extension that needs a fact reads a
  versioned JSON file the app wrote (session-v1.json for the session and the demo, never a token).
- Memory: the widget extension has about 30 MB (SP-16a), which the Kotlin runtime alone would use up, so it links
  no Kotlin; the app's own footprint is in Memory.
- Time: the extensions compute no academic time. The app writes timelines with their instants, and WidgetKit
  picks the entry of the moment.
- Demo: the extensions see the demo only as `"demo": true` in session-v1.json and the snapshots; nothing in them
  reaches the network.

## Strings and icons

The catalog is the Android resource files ([ADR 0028](decisions/0028-strings-and-icons.md)); iOS reads generated
copies and never holds Russian text in Swift.

- Tables. `scripts/verify.sh run -- :app:exportAppleStrings` writes `iosApp/Shared/Strings/*.xcstrings`, one per
  catalog file name; `strings_platform.xml` and `iosApp/Strings/strings_ios*.xml` go to `Localizable` (APNs resolves a
  `loc-key` only there), `InfoPlist` and `AppShortcuts` rows come from `build-logic/strings/apple-tables.properties`.
  Run it after changing a catalog file and commit the result; never edit a table by hand.
- iOS-only copy (widget and Control texts, the Background App Refresh row, usage descriptions) lives in
  `iosApp/Strings/strings_ios.xml`, Android syntax; later cards add `strings_ios_<area>.xml`
  (`strings_ios_settings.xml`: the settings texts where the shared ones name Android). An Info.plist value is an
  `InfoPlist.<key>` row, a shortcut phrase an `AppShortcuts.<phrase>` row.
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
- Compose screens follow the same settings through the Compose kit's iOS actuals (`shared/designsystem/src/iosMain`):
  `rememberReducedMotion()` reads Reduce Motion (`UIAccessibility`) and follows its changes, the colour scheme is
  the static one (iOS has no wallpaper colours, so `ColorSource.Platform` falls back), and the text size follows
  Dynamic Type (`QrPassUITests` checks the QR pass at AX1).

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

The app's root is the SwiftUI shell in `iosApp/Sources/App/` (master A5). Its session gate and demo banner follow
the shared `SessionRepository` (see Core graph). The recordbook root is L12's Compose recordbook
(`RecordbookTabScreen`, IO-09d2), the home root LH-2's Compose feed (`HomeScreen`, IO-09a), the sport root LP-6's
Compose sport tab (`SportTabScreen`, IO-09c), the me root LA-3's Compose Me tab (`MeTabScreen`, IO-09e) with the
entry to settings and the sign-out (the route's confirmation, `SessionRepository.signOut()`), the schedule root L10's
Compose schedule (`ScheduleScreen`, IO-09b); no tab has a placeholder root any more.

- Tabs. `ShellTab` holds the roots of Android's `res/menu/bottom_nav.xml` in its order: recordbook, schedule, home,
  sport, me. A tab shows only while iOS offers its feature (`ShellTab.isAvailable`: the recordbook follows
  `PlatformCapabilities.recordbook`, on since IO-09d2), so no placeholder reaches App Review; home is selected at
  launch. The container is one type, `Shell/ShellTabs.swift` (a `TabView`). Tabs switch by the native
  tab bar only, with no swipe between them (owner decision 2026-10-06; IO-SW1 dropped, see design.md "Tab swipe").
- Stacks and sheets. Each tab has one `NavigationStack` whose path the router holds; the shell's own sheets open at
  the medium detent and drag to large. A sheet key of a feature (`RouteTarget.composeSheet`: the lesson, pending
  sport and friend picker sheets, IO-09b; the recordbook's period picker and `Мои баллы`, IO-09d2) is one
  `ShellSheet.route` above the shell whose SwiftUI view hosts the Compose sheet content and sets the detents (`.large`
  for Android's tall sheets, `.medium` and `.large` for its default ones, with the system's drag indicator);
  the content draws its own title and close button. One sheet at a time; a screen opened from a sheet replaces it.
  A screen's own sheet (the sport details, IO-09c) is a SwiftUI `.sheet` of its Swift host hosting the shared sheet
  content, never a Compose `ModalBottomSheet`: Android's tall sheet is the large detent, the system drag indicator
  sits over the content's header, and the controller is transparent (`screenController(opaque = false)`) over
  `presentationBackground`, so the sheet's grouped background runs under the home indicator.
- Session gate. `ShellSession` maps `SessionRepository.state` and `OnboardingGateViewModel`'s flag through
  `SessionGateway` onto `ShellGate.surface`, as Android's shell: `Initializing` and `SigningOut` show the loading
  gate, `SignedOut` and `ReauthenticationRequired` the sign-in screen (see Sign-in), a signed-in session the
  first-run flow while the flag is `Required` (the loading gate while it is still unread) and the tabs after it, the
  demo session the tabs at once with the demo banner (`ItmoDemoBanner`) above the tab bar, below the tab's stack.
  The banner's sign-in is `SessionRepository.signOut()`, which leaves the demo for the sign-in screen as on Android.
  A Debug build launched with `-itmoShellSession loading|signed-out|demo|signed-in` runs a fixture session instead,
  whose signed-out gate signs in with one tap and whose flow counts as passed; UI tests pass it through
  `XCUIApplication.itmo(session:)`, and the scheme's test action passes `signed-in` to the host app of the hosted
  tests, so they never open the sign-in screen.
- Chrome rule (the owner may veto at T12). A CMP route draws its own DS-03 top bar and hides the SwiftUI navigation
  bar (`shellChrome(.compose)`); a SwiftUI screen keeps the native bar (`.native`). Hiding the bar turns UIKit's
  edge swipe back off, so the compose chrome turns it on again for the stack above its root;
  `ShellUITests.testEdgeSwipeGoesBackFromComposeChrome` fails without it.
- Router (`Sources/App/Router/`) on the shared route model of `:shared:core` (SH-1a2). Every URL, App Intent,
  link and notification becomes an `EntryRoute` (`RouteURL`, which uses `EntryRouteParser` and `AppLinks`) and goes
  to `AppRouter.open(entry:)`; a tap opens an `AppRoute` key with `AppRouter.open(_:)`. Entry routes wait in the
  shared `RouteQueue`: a route runs once, only when `ShellGate` reports the tabs (a ready session, the first-run flow
  passed or the demo) and its tab could be selected (the tab bar is on screen); a newer route replaces a waiting
  one, and a route to a hidden tab is dropped. A tap passes `ShellGate.check` first (keys that need a real account
  are refused in the demo). Leaving the tabs clears the stacks, the sheet, the tab requests and the result
  callbacks.
- Route map. `Routes.target(for:)` switches exhaustively over `RouteFeature` (`shared/ios`, `IosRoutes`) and
  delegates each key to its feature's `Routes+<Feature>.swift`, which returns a Compose screen, a SwiftUI screen, a
  shell sheet, a Compose sheet, a tab, the gate, or "not on iOS". A feature card changes only its own file; a key no feature claims fails
  `RouterTests.testEveryRegisteredRouteHasAFeature`, and `testEveryRouteKindHasItsTarget` pins the target of every
  key. "Not on iOS" keys (the `.ics` export, which the settings screen presents itself; the link and review reports,
  Compose dialogs inside what asks for them (IO-09f); the debug tools for good; and every screen of a feature whose
  IO card has not merged) have no entry point and open nothing.
- Route results. A screen that answers its opener (the friend picker answers the schedule, the period picker the
  recordbook) is opened with `open(_:onResult:)` and answers with `deliver(_:from:)`; the router holds the callback
  until then.
- Sheets that lead on. One router sheet shows at a time. A links sheet that opens the next one in its place, as
  Android's close themselves first, calls `replaceSheet(with:)` (SwiftUI dismisses the one and presents the other);
  all links show a link's actions and the editor as their own nested sheet over the list, as Android stacks them
  (IO-09f).

| Route id | URL | Opens |
|---|---|---|
| `recordbook`, `schedule`, `home`, `sport`, `me` | `itmowidgets://route/<root>` | that root as it is |
| `qr_pass` | `itmowidgets://route/qr_pass` | the QR pass above home (QR widget, Control, quick action) |
| `today` | `itmowidgets://route/today` | the schedule root on today (App Shortcut, quick action) |
| any other id | `itmowidgets://route/<id>` | the damaged-link sheet above home (`app_link_unavailable_*`) |

The URL scheme is the build setting `APP_URL_SCHEME` (the app target in `project.yml`), registered in the app's
Info.plist as `CFBundleURLTypes`. Placed widgets and Controls keep their URLs, so the scheme and the route ids are
frozen; `StableIdentifiersTests.testRouteUrls` pins them.

## Settings and diagnostics

Settings are SwiftUI screens over the shared page model (IO-08a): `Routes+Settings.swift` maps each
`AppRoutes.Settings(page)` key to `Features/Settings/SettingsScreen`, which owns the page's `SettingsViewModel`
(`settingsPageParameters(page:)` hands it the page as Android's navigation argument), and `AppRoutes.Diagnostics` to
`Features/Diagnostics/DiagnosticsScreen`. Android's Compose pages are not used on iOS. The Me tab's settings row opens
the root page, its privacy row the privacy page.

- Rendering. `SettingsForm` draws the provider's sections in a `Form`, one row type per `SettingItem`: a toggle, a
  menu picker, a navigation row that pushes the next page's key onto the tab's stack, an action row, a read-only
  value. Rows carry `settings.row.<SettingRowId.key>`. Nothing renders until the page is `loaded`, so stored values
  never animate in.
- Platform rows. The page providers hide what `PlatformCapabilities` does not offer: the quick settings tile, the
  spoiler animation and the custom spoiler image. The recordbook page (mark tracking) is on since IO-09d3, the
  calendar rows since IO-15b. The QR widget page keeps its spoiler and dynamic colour switches, which the iOS
  widget does not follow yet (Degradations).
- iOS copy. `SettingsIosCopy` replaces the shared texts that name Android with `strings_ios*.xml` rows: the
  notification values and the background work row, which is Background App Refresh on iOS
  (`IosBackgroundWorkAccess` reads `UIApplication.backgroundRefreshStatus`) and opens the app's page in Settings.
- System state. On appear and on every return to the app the screen reports the notification permission and
  Background App Refresh to the ViewModel. The notification row asks for the permission while iOS has never asked,
  then opens the app's notification settings.
- The schedule change switch and the calendar sync are the schedule data graph's (`scheduleDataModule`, IO-09b):
  the background runner runs the change check and the calendar sync (see Background refresh). The calendar switch
  asks EventKit for full access in Swift (`Features/Calendar/CalendarAccess`, the system prompt once, then a
  rationale that opens the app's page in Settings); `calendarIosModule` gives the sync the phone's calendars over
  EventKit and the `.ics` file, which `IcsExportSheet` shares with a `ShareLink`
  ([calendar](features/calendar.md#ios)). Mark tracking is the recordbook graph's (IO-09d1) with its `Зачётка`
  page since IO-09d3: the switches run the mark check of the background runner
  ([mark tracking](features/marks-tracking.md#ios)). "Пройти знакомство заново" resets the shared flag,
  which the shell's gate reads (IO-07b). Widget pages draw no preview above their rows.
- Diagnostics. The journal lists `AppDiagnostics`' records, newest first, with a stack trace folded under its
  record; the share sheet takes the plain-text journal (and copies it), clearing asks first.
- Tests. `SnapshotTests/SettingsSnapshotTests` (the root, schedule, recordbook and QR widget pages from the
  providers in the app's graph, and every row type with long values), `ITMOWidgetsTests/SettingsTests`,
  `UITests/SettingsUITests` (a switch survives a relaunch), and `SettingsIosModuleTest` on the simulator.

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
| Widget kinds | `dev.alllexey.itmowidgets.widget.qr`, `dev.alllexey.itmowidgets.widget.single-lesson`, `dev.alllexey.itmowidgets.widget.day-schedule` |
| Control kinds | `dev.alllexey.itmowidgets.control.qr` |
| Quick action types | `dev.alllexey.itmowidgets.qr_pass`, `dev.alllexey.itmowidgets.today` (`$(PRODUCT_BUNDLE_IDENTIFIER).<route id>`) |
| Background task | `dev.alllexey.itmowidgets.refresh` (`BGTaskSchedulerPermittedIdentifiers`, a literal as in `AppRefreshScheduler`) |
| App Group file names | `<name>-v<N>.json`, listed in Data sharing |

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

The iOS platform classes live in `shared/core/src/iosMain/` and run only in the app process; the extensions read
files and never link Kotlin (Targets and processes).

| Store | Where | Class |
|---|---|---|
| App files (`AppDirectories.files`), DataStore `app_preferences` included | `Library/Application Support/files` in the app container, backed up | `IosAppDirectories` |
| Cache (`AppDirectories.cache`) | `Library/Caches`, cleared by the system | `IosAppDirectories` |
| No-backup files (`AppDirectories.noBackup`) | `Library/Application Support/no-backup`, excluded from backup | `IosAppDirectories` |
| Files the extensions read | the App Group container | `AppGroupDirectory` |
| Secrets | the Keychain | `KeychainSecureStore` |
| When each background step is next due (`backgroundRefresh.*`) | the app's `NSUserDefaults`, cleared on sign-out | `UserDefaultsRefreshStepLog` |

DataStore stays in the app container: it is single-process, so no extension opens it.

App Group container. `AppGroupDirectory.resolve` uses the group in `AppGroupID`, then any group AltStore lists in
`ALTAppGroups` (SP-23). When none resolves (an unsigned build) it falls back to `no-backup/app-group` in the app
container, logs one warning per process and carries on; the extensions then see nothing.

| File | Written by | Read by | Notes |
|---|---|---|---|
| `locks/<name>.lock` | `FileCrossProcessLock` | app, notification service | `flock(2)`; empty files, never deleted; `myitmo-refresh` guards the token refresh |
| session-v1.json | `SessionSnapshotWriter`, kept on the session state by the account module's `SessionSnapshotSync` | widget extension (`SessionFile.swift` in `iosApp/Shared/WidgetSnapshots/`), notification service | `{"isu": Int?, "demo": Bool, "alertsAllowed": Bool}`; written for every signed-in session, demo included; missing means signed out; no token; `alertsAllowed` is the last notification settings answer (see Notifications) |
| qr-pass-v1.json | `QrPassSnapshotWriter` (`:shared:feature-qr`, iosMain), on every new valid pass; reloads `dev.alllexey.itmowidgets.widget.qr` | widget extension (`QrPassSnapshot.swift` in `iosApp/Shared/WidgetSnapshots/`) | `{"generatedAt": ISO 8601, "expiresAt": ISO 8601, "demo": Bool, "matrix": [String], "spoiler": Bool?}`: one string per row from the top, `1` a dark module, from the shared `QrCodeGenerator` (version 1, ECC LOW), so the widget encodes nothing; no file while there is no valid pass; the fixture `iosApp/Tests/UnitTests/Fixtures/qr-pass-v1.json` is what the writer writes for the demo pass (`QrPassSnapshotWriterTest`, `QrPassSnapshotTests`); `spoiler` is the global QR widget option, absent (as the writer leaves it today) means on, the Android default |
| qr-widget-v1.json | `RevealQrIntent` in the widget extension, on a tap on the spoiler | widget extension (`QrWidgetReveal.swift`) | `{"revealedUntil": ISO 8601}`: the tap's time plus 30 s, Android's auto-hide delay; one file for every placed QR widget; never read by the app |
| schedule-timeline-v1.json | `ScheduleTimelineWriter` (`:shared:feature-schedule`, iosMain) on the session, the schedule widget options, every return to the foreground, a changed cached schedule and `ScheduleWidgetRefreshRequester` (sport); reloads `dev.alllexey.itmowidgets.widget.single-lesson` and `.day-schedule` | widget extension (`LessonTimeline.swift`) | LS-3's `ScheduleWidgetTimeline` from `ScheduleWidgetDataProvider.loadTimeline` to the end of tomorrow (academic zone): `{"version": 1, "generatedAt", "validUntil", "entries": [{"validFrom", "snapshot"}]}`, the snapshot in the keys of Android's widget snapshot, nulls omitted, defaults written; rooms and buildings are already the short titles Android's widget shows; an unavailable schedule keeps the previous file; the envelope around `shared/feature-schedule/fixtures/schedule-widget-timeline-v1.json` is what the writer writes for it (`ScheduleTimelineWriterTest`, `ScheduleTimelineTests`) |
| `<name>-v<N>.json` | `AppGroupSnapshotWriter` | widget extension, notification service | each card that adds a snapshot adds its row |

- Snapshots hold `{"version": N, "value": ...}`. A write goes to a temporary file of its own and is renamed over
  the old one, so a reader sees the old or the new snapshot, never a partial one; then the writer asks WidgetKit to
  reload the given kinds through `WidgetReloader`, which Swift implements (WidgetKit has no Objective-C API). A
  reader rejects a `version` above its own.
- Swift reads them with `AppGroupSnapshot` (`iosApp/Shared/WidgetSnapshots/`), in any process and without Kotlin:
  the container from the process's own `AppGroupID`, then `ALTAppGroups`; a missing container or file, a corrupt
  file and a newer `version` all read as nil, and the reader shows its placeholder, never crashes
  (`ITMOWidgetsTests/SnapshotReaderTests`).
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
| `bars_tokens.enc` | `BarsTokenStore` (`"<isu>\n<header>"`, the BARS sign-in and every renewal) | app |
| `itmo_id_cookies` | `KeychainItmoIdCookies` (the `id.itmo.ru` cookies of WebKit, merged with each replay's `Set-Cookie`) | app |

Each card that stores a secret adds its row. Values are never logged.

Session. `KeychainTokenStorage` is both MyItmoApi 2.x's `TokenStorage` and the session's `SessionTokenStore` over
the one `myitmo_tokens` item, serialised as Android's `MyItmoStorage` serialises `myitmo_tokens.enc` before sealing
it: five lines, each token base64url without padding (`~` for none), each expiry in epoch milliseconds. It keeps no
copy, so each read sees what another process wrote. The client's `TokenManager` is the only refresher; it refreshes
inside `FileCrossProcessLock("myitmo-refresh")` and re-reads the item once it holds the lock, so when the app and the
notification service find the same expired token only the first refreshes. A value that does not parse is dropped
(signed out); a Keychain failure is thrown and drops nothing.

Sign-in (IO-07a, IO-07b, SP-21 path (a)). The signed-out gate is `AuthScreen` (`Sources/Features/Auth/`), Android's
`AuthFragment` in SwiftUI over the shared `AuthViewModel`: the shared `auth_logo` (bundled by path from
`shared/feature-account/src/commonMain/composeResources/drawable`, no copy), the app name, the three feature lines,
`auth_login_itmo_id`, which opens `ItmoSignInScreen` as a full-screen cover, and `auth_login_refresh_token`, an alert with a secure field that calls
`signInWithRefreshToken`. Five taps on the logo, each within 1.5 s (`DemoEntryTaps`, timed on the shared
`TimeSource.Monotonic`), start the demo with a success haptic and a VoiceOver announcement of `demo_entered`;
the logo stays hidden from VoiceOver, as on Android.

`ItmoSignInScreen` is Android's `LoginActivity`: my.itmo.ru in a `WKWebView` on `WKWebsiteDataStore.default()` under
a close button and `auth_web_title`, where the user types the credentials on ITMO.ID's own pages. Its page state is
the shared `InteractiveLoginViewModel` (`ItmoSignInModel` forwards the browser's reports to it and counts the
reloads). Android's `app/src/main/assets/token_refresh_interceptor.js` is bundled by path (`project.yml`, no copy)
and runs at document start in the main frame, after a bridge script that defines `window.ItmoAuthBridge` only on
`https://my.itmo.ru/login/callback`; its `postTokens` goes to the `postTokens` script message handler, which takes a
string from the main frame of `https://my.itmo.ru` only, and the ViewModel hands it to
`SessionRepository.completeItmoIdLogin` only while the page is the callback (`ItmoAuthUrlPolicy.isTokenCallback`),
one hand-over at a time; `Completed` closes the cover. The main frame stays on https pages (`HttpsNavigationPolicy`,
as on Android, for VK and other providers); no new windows. A failed page or sign-in shows an error with a retry
(`auth_web_error`, Android's `auth_error_*` mapping); a retry reloads the sign-in page. The website data is not
cleared before the page loads, unlike Android: sign-out has cleared it, and an expired session keeps ITMO.ID's SSO
cookies for the re-sign-in. Path (b) (own PKCE with `decidePolicyFor`, SP-21's recommendation) needs the code
exchange in shared Kotlin first.

First-run flow (IO-07b). `OnboardingScreen` (`Sources/Features/Onboarding/`) is Android's flow in SwiftUI over the
shared `OnboardingViewModel`, which gets a fresh `SavedStateHandle` as a Koin parameter (`OnboardingIosParameters`):
`ScreenViewModelStore` has no saved-state registry, so a relaunch starts at the first step. The steps and their order
are Android's; a widget step shows how to add the widget (`ios_onboarding_widget_howto_*`; iOS lets no app place one,
so `pinSupported` is false) beside its appearance rows from `WidgetAppearanceRepository`, without the custom spoiler
image row; the services step is the shared opt-in; the notifications step asks with
`UNUserNotificationCenter.requestAuthorization` and then opens the app's notification settings
(`ios_onboarding_notifications_configure` once allowed). The answer is the step's status; the foreground refresh
after the dialog carries it into `alertsAllowed` (see Notifications). A back button above the step replaces Android's system back. A Debug build launched with
`-itmoOnboarding` shows the flow over the demo session, which otherwise skips it, until the flow ends (UI tests,
`XCUIApplication.itmoOnboarding()`); finishing it there stores the flag as a real account would.

BARS session (IO-09d1, SP-21). `recordbookModule` runs on `recordbookIosModule`
(`shared/feature-recordbook/src/iosMain/`): one `BarsClient`, `BarsTokenStore` and `OwnerBoundBarsStorage` per
process, BARS on a Darwin engine of its own (no cookies, cache or redirects, never the MyITMO engine), and ITMO.ID's
`bars` sign-in (`BarsLogin`) over it.

- Cookies. `KeychainItmoIdCookies` is the iOS `ItmoIdCookies`: the `id.itmo.ru` cookies (WebKit's domain match also
  returns `.itmo.ru` analytics cookies, which stay out) in the `itmo_id_cookies` item as
  `{"version": 1, "cookies": [...]}`; a higher version or a value that does not parse reads as no cookies.
  `ItmoIdCookieExport` replaces the copy with WebKit's cookies after every WebView session: an interactive ITMO.ID
  sign-in (the session turns signed in from signed out or a required re-sign-in, never on launch or in the demo), the
  BARS sign-in sheet and each hidden renewal. The background replay (`BarsCookieSilentLogin`, common) sends the copy
  as the `Cookie` header of the authorization URL only, matched by domain, path, `Secure` and expiry as RFC 6265
  does, and merges the answer's `Set-Cookie` back, the last of a name winning.
- Foreground renewal. `WebViewBarsSilentLogin` asks Swift's `HiddenBarsBrowser` (`Sources/Features/Bars/`, through
  `IosPlatform`'s `BarsWebHost`) for a `WKWebView` that is never shown, on `WKWebsiteDataStore.default()`: Kotlin's
  `BarsWebNavigation` lets ITMO.ID pages load, cancels the exact BARS callback and hands it over, and stops on any
  other page; a page that finishes loading is a sign-in form, so no code; 20 s at most. Only the callback with the
  same `state` yields a code.
- Sign-in sheet. `BarsLoginSheet` is Android's `BarsLoginActivity` over the shared `BarsLoginViewModel`, whose
  `SavedStateHandle` is a Koin parameter (`BarsLoginParameters.fresh()`), so each sheet keeps one `state`. A retry
  also clears WebKit's cookies, as Android clears `CookieManager`. The recordbook list and the subject page open
  it from their BARS snackbar and load again after a completed sign-in (IO-09d2); in a Debug build
  `-itmoBarsLogin` presents it, and `-itmoBarsRenew foreground|background` replaces the saved header with one BARS
  rejects and renews it through the hidden view or the cookie copy (`BarsSessionCheck`, which logs only the
  header's length and expiry).
- The mark check (IO-09d3) reads BARS in the background through the cookie renewal only (`BarsCookieSilentLogin`
  over the Keychain cookies), never the hidden view; no cookies or ITMO.ID's `LOGIN_REQUIRED` end the session with
  one `Войдите в БАРС` ([mark tracking](features/marks-tracking.md#ios)).

Sign-out. Three `SessionDataCleaner`s run with the shared ones, on sign-out and before every sign-in or demo start:
the Keychain (every item of the service), the App Group container and WebKit's website data, which Swift removes
through `IosCoreHost.clearWebsiteData` (`WKWebsiteDataStore`, as Android clears its WebView data). The App Group
cleaner removes the app's files (snapshots and a writer's leftover `.tmp` file) and keeps `locks`, `Library` and
every other hidden file: the system's .com.apple.mobile_container_manager.metadata.plist is the container's
record, and without it the system drops the container as stale and gives the next process a new, empty one, so the
widgets would read a container the app no longer writes (`ITMOWidgetsTests/WidgetSnapshotsTests`). The Keychain
cleaner takes `bars_tokens.enc` and `itmo_id_cookies` with the session; the recordbook's own cleaners run as on
Android.

Web sign-in (IO-08b). `WebLoginSheet` (`Sources/Features/WebLogin/`) is Android's `WebLoginBottomSheet` in SwiftUI
over the shared `WebLoginViewModel` (a fresh `SavedStateHandle` as its Koin parameter, `WebLoginIosParameters`), the
shell sheet of `AppRoutes.WebLogin` (`ShellSheet.webLogin`); a Universal Link's `code` is checked as soon as it
opens. `web_login_scan` opens VisionKit's `DataScannerViewController` (QR only) full screen; unlike Android's Play
services scanner it needs the camera, so the app's Info.plist has `NSCameraUsageDescription`
(`ios_web_login_camera_usage` through `InfoPlist.xcstrings`). A refused camera, a device without the scanner (the
simulator) or a scanner that stops reads as `web_login_scanner_unavailable`, and typing still works. The demo refuses
the route (`ShellGate`), so the Me tab's row says `error_demo_unavailable` there. A Debug build launched with
`-itmoWebLoginFixture` opens the sheet on the tabs, answers from `WebLoginIosFixture` (code `ABCD2345`, Chrome on
macOS, no Backend) and hands the fixture's link to the scan button (`UITests/WebLoginUITests`).

My ITMO web (IO-08b). `MyItmoWebScreen` (`Sources/Features/MyItmoWeb/`) is a SwiftUI screen under the native bar on
the tab's stack: `my.itmo.ru` in a `WKWebView` on `WKWebsiteDataStore.default()`, every navigation decided by the
shared `MyItmoWebPolicy`, no token injection; see [My ITMO in the app](features/my-itmo-web.md#ios).

Update offer (IO-08b). `AppUpdateOffer` (`Sources/Features/Update/`) runs the shared `AppUpdateGateViewModel` once per
process on the tabs of a real session (`ShellGate.checksForUpdate`), and a newer iOS release opens `AppUpdateScreen`
over the shared `AppUpdateViewModel` as a sheet. The only channel is the App Store page
`https://apps.apple.com/app/id$(APP_STORE_ID)` (`AppStoreListing`; `APP_STORE_ID` in `Base.xcconfig`, `AppStoreID` in
the app's Info.plist): while the ID is empty, until the App Store record exists (T13), the offer never checks or shows.
No GitHub or Play channel.

## Notifications

Push plumbing before the Apple account (IO-13a); the APNs and FCM token arrive with IO-13b, the notification
service's work with IO-12a.

- Registration. `PushDeviceRegistration` (`shared/core/src/commonMain/.../core/notification/`) is both the
  session's `FcmTokenSync` and its `BackendDeviceSession`: it registers the token with `platform = IOS`,
  `alertsAllowed` and `appVersion` (`CFBundleShortVersionString`) and the device name `Apple <model>` (never the
  user's own device name). Nothing reaches Backend in the demo, without the opt-in (`BackendGate.mayCallBackend()`),
  without a signed-in ISU or without a token; `IosPushDevice` has none until Swift passes one to `updateToken`, so
  today nothing is registered. `PushRegistrationPreferences` keeps what Backend last accepted (token, owner,
  alerts); sign-out and turning the services off unregister that token and forget it.
- Foreground refresh. Each return to the foreground (`scenePhase == .active`, so also after the permission dialog)
  runs `PushForegroundRefresh` (`PushRefresh.run()`): it reads the notification settings (authorized, provisional
  or ephemeral count as allowed), which session-v1.json's `alertsAllowed` follows, then syncs, which registers
  again only when the token, the owner or the alerts answer changed. A failure is logged, never thrown into Swift.
- Taps. `ITMOWidgetsAppDelegate` (`@UIApplicationDelegateAdaptor`) runs `PushLaunch`, which makes `NotificationTaps`
  the notification center's delegate before launch ends. A tap reads the `data` envelope from `userInfo`
  (`NotificationTapRoutes`) and hands the route to `AppRouter.open(entry:)`; a tap that launched the app waits until
  the shell attaches the router. A notification that arrives in the foreground shows as a banner.
- Local notifications (`IosAppNotifier`: schedule changes, marks, the BARS reminder) carry Android's entry action
  in `userInfo["action"]`, but the tap handler routes only the push `data` envelope, so a tap on one opens the app
  where it was (Degradations).

| Payload type | Opens |
|---|---|
| `FRIENDSHIP_EVENT_PAYLOAD` | the me tab with the actor's profile above it |
| `SPORT_FREE_SIGN_LESSONS_PAYLOAD`, `SPORT_AUTO_SIGN_LESSONS_PAYLOAD` | the sport tab with the lesson's request; with several lessons the sport tab |
| anything else | nothing |

- A Debug build launched with `-itmoNotificationFixture friendship|sport` asks for permission and posts a local
  notification shaped like the push two seconds later (`NotificationFixtures`); `NotificationTapUITests` taps its
  banner. `ITMOWidgetsTests/DeviceRegistrationTests` checks the graph and the tap routes, `PushDeviceRegistrationTest`
  (`scripts/ios/test.sh kn core`) the Backend cases on a MockEngine.

## Widgets

The widget extension links no Kotlin (its limit is about 30 MB, SP-16a): each widget reads App Group files on every
timeline request and makes no network call. The app's writers reload a widget's kind when its files change.

QR widget (`Extensions/Widgets/Qr/`, kind `dev.alllexey.itmowidgets.widget.qr`, small, `StaticConfiguration`).
Android keeps the widget options global, so they come from qr-pass-v1.json, not from a per-widget configuration.

| State | When | Shows | Tap |
|---|---|---|---|
| Signed out | no session-v1.json (or no container) | QR symbol, `schedule_widget_signed_out` | the QR pass in the app |
| Spoiler | a valid pass, no reveal running | noise, `ios_widget_qr_reveal` | `RevealQrIntent`: the code for 30 s |
| Revealed | a reveal running, or the spoiler option off | the code, dark on white in both themes; in the demo with `demo_entered` | the QR pass in the app |
| Expired | signed in, no pass or past its `expiresAt` | refresh symbol, `ios_widget_qr_expired` | the QR pass in the app |

- `QrWidgetTimeline` turns the files into entries: the current state, the spoiler when the reveal ends, the
  expired state at the pass's deadline; a new timeline at least every hour, Android's update period.
- The tap URL is `itmowidgets://route/qr_pass` (`widgetURL`); the spoiler alone is a `Button(intent:)`.
- Degradations: no circle animation on reveal (WidgetKit animates only between entries); a custom spoiler image is
  v2.4; the code is dark on white whatever `Динамические цвета` says; `QrPassSnapshotWriter` does not write
  `spoiler` yet, so the spoiler stays on although settings and the first-run flow show its switch (Degradations).
- `SnapshotTests/WidgetSnapshotTests` holds each state at the small family size (170 x 170 pt on the pinned
  iPhone, `WidgetSizes`) in the four appearances; `ITMOWidgetsTests/QrWidgetTimelineTests` the timeline.

Schedule widgets (`Extensions/Widgets/Schedule/`, `StaticConfiguration`, options from the timeline file). The lesson
widget (kind `dev.alllexey.itmowidgets.widget.single-lesson`: small, medium, Lock Screen `accessoryRectangular` and
`accessoryInline`) shows the current or next lesson: type and marker, time, subject, place and teacher, now or next
and how many follow. The day widget (kind `dev.alllexey.itmowidgets.widget.day-schedule`: medium, large) shows the
day's rows, tomorrow's once today is over when that option is on. A tap opens `itmowidgets://route/schedule`.

| State | When | Shows |
|---|---|---|
| Signed out | no session-v1.json, or the snapshot's signed-out kind | sign-in symbol, `schedule_widget_signed_out` |
| Demo | the demo session (no ITMO.ID token, so no schedule, docs/features/demo.md) | `demo_entered`, `schedule_widget_open_app_hint` |
| Unavailable | signed in, no timeline or past its `validUntil` | refresh symbol, `schedule_widget_error`, `schedule_widget_open_app_hint` |
| Lessons | the timeline's entry of the moment | Android's texts by key; completed rows faded, pending sport rows with an outlined marker |
| No lessons | `EMPTY_TODAY`, `NO_MORE_TODAY`, `EMPTY_TODAY_AND_TOMORROW` | the catalog message |

- `LessonWidgetTimeline` makes one entry per `validFrom` after now, the unavailable state at `validUntil`, and asks
  `.after(validUntil)` (within an hour without a timeline). Swift takes no time-zone or academic decision; the day
  header's date is the file's calendar day.
- WidgetKit cannot scroll: the day widget shows the rows that fit its family and text size, dropping completed
  lessons first, then rows from the end (`DayScheduleRows`).
- The widget extension compiles the design tokens (`Tokens.generated.swift`, `ItmoTheme.swift`) by path for the
  surface, text and lesson type colours.
- Degradation: no seven-minute pending sport refresh (WidgetKit allows about 40 to 70 reloads a day); a pending row
  stays until the app writes again or its entry ends.
- The writer starts with the graph (`scheduleWidgetIosModule`) and resolves the provider and the cached schedule at
  each write from the schedule data graph (IO-09b) and its pending sport rows from the sport graph (IO-09c).
- `ITMOWidgetsTests/ScheduleTimelineTests` decodes LS-3's fixture and picks Kotlin's `entryAt` entry at sampled
  instants; `SnapshotTests/WidgetSnapshotTests` holds lesson, break, pending sport, empty, tomorrow, signed out,
  demo and unavailable states, light and dark (AX1 for the small and the large family), long names at the narrowest
  medium size (321 pt, `WidgetSizes.narrowMedium`).

## System entries

The Control, the App Shortcuts and the quick actions open the app on a route id (`IntentRoute`: `qr_pass`,
`today`, Android's shortcut ids). Each hands the id to `RouteInbox` (`Shared/Intents/`), which the app connects to
`AppRouter.open(id:)` in `App.init`; an id that arrives first waits there. From the router on, the route runs as
any URL does (Shell and routes): it waits for a ready session and the tab bar, then runs once.

| Entry | Where | Opens |
|---|---|---|
| QR Control (`QrControl`, kind `dev.alllexey.itmowidgets.control.qr`) | Control Center, Lock Screen, Action button | `OpenRouteIntent(route: .qrPass)`: the QR pass above home |
| App Shortcut `shortcut_qr_short` (`OpenQrPassIntent`) | Siri, Spotlight, Shortcuts | the QR pass above home |
| App Shortcut `shortcut_today_short` (`OpenTodayIntent`) | Siri, Spotlight, Shortcuts | the schedule root on today |
| Quick action `shortcut_qr_long` (type `<bundle ID>.qr_pass`, symbol `qrcode`) | the app icon's menu | the QR pass above home |
| Quick action `shortcut_today_long` (type `<bundle ID>.today`, symbol `calendar`) | the app icon's menu | the schedule root on today |

- The Control's `OpenRouteIntent` is an `OpenIntent` compiled into the app and the widget extension, so the system
  runs `perform()` in the app after bringing it forward; the extension never routes. Like Android's tile it cannot
  draw the code. It is hidden from Shortcuts, which lists the App Shortcuts instead.
- `ITMOWidgetsShortcuts` (`Sources/Intents/`) declares the App Shortcuts; their intents set `openAppWhenRun`. Each
  phrase is an ASCII key of `AppShortcuts.xcstrings` with `${applicationName}` once; the Russian texts are
  `ios_shortcut_*` in `iosApp/Strings/strings_ios_shortcuts.xml`, mapped by `AppShortcuts.<phrase>` rows.
- Quick actions are static `UIApplicationShortcutItems` in the app's Info.plist; the title is a catalog key that
  `InfoPlist.xcstrings` resolves (`InfoPlist.shortcut_*_long` rows). `ITMOWidgetsAppDelegate` takes the one that
  launched the app from the connection options and installs `QuickActionSceneDelegate` for the ones chosen while
  it runs; SwiftUI keeps the window.
- Tests: `ITMOWidgetsTests/IntentsTests` runs each intent's `perform()` and the quick-action handler with a
  synthetic `UIApplicationShortcutItem` against a router; XCUITest does not drive Siri, Control Center or the
  springboard reliably, so these surfaces are checked by hand on the simulator.

## Background refresh

iOS has no WorkManager: the app gets one app refresh task, `dev.alllexey.itmowidgets.refresh` (`UIBackgroundModes`
`fetch`, listed in `BGTaskSchedulerPermittedIdentifiers`), and the system decides when it runs, from how the app is
used, often hours apart, never with Background App Refresh off or in Low Power Mode. `BackgroundRunner`
(`shared/ios/src/iosMain/.../ios/background/`, IO-14) is the one entry point of every run:

- Triggers. The task (`.backgroundRefresh()` on the scene, `Sources/Background/BackgroundRefresh.swift`, SwiftUI's
  `.backgroundTask(.appRefresh(_:))`), the app's launch and every return to the foreground (`App.init` calls
  `IosBackgroundRefresh.start()` after the graph), and `CheckScheduler.runOnce` (the debug tools' "check now"). Runs
  never overlap; a second one waits.
- Steps. Each feature binds its `RefreshStep` (`shared/core`, iosMain, `core/work/RefreshSteps.kt`) under its key in
  its iOS module; the runner takes them in `RefreshStepKeys.ORDER`, where each later check adds one line: widget
  snapshots (`scheduleWidgetIosModule`: `ScheduleTimelineWriter` publishes the schedule timeline), schedule changes
  (`scheduleChangesIosModule`: `ScheduleChangesRefresh`, LT-1's `ScheduleChangesCheck`, then the quiet hours), marks
  with the BARS renewal (`recordbookIosModule`: `MarksRefresh`, KM-11b2's `MarksCheck`, then the quiet hours; every
  three hours, a switch turned on makes the step due at once), then the calendar sync (`calendarIosModule`:
  `DefaultCalendarSync.run` every two hours; turning it on makes the step due at once). A bound step without a place
  in the order fails the start.
- Deadline. The system gives the task about 30 s and cancels the Swift task when it expires, which cancels the
  Kotlin run; the runner stops its steps after 25 s (`BackgroundRunner.DEADLINE`), cancels the one running and
  leaves the rest for the next run, then lets the posted notifications reach the system and asks for the next
  wake, also when cut or cancelled.
- Periods. A check keeps its Android period (schedule changes and the calendar sync: two hours, marks: three) in
  `UserDefaultsRefreshStepLog`: a launch, a foreground or an early wake within it leaves the check out, so My ITMO gets no more requests from an iPhone than
  from an Android phone. A retry (a network failure, or a step that threw) comes back after 15 minutes at most twice,
  then waits for the next period, Android's backoff; a skipped check (signed out, switched off) is tried on the next
  run. The widget snapshots run every time.
- Scheduling. `AppRefreshScheduler` (`shared/core`, iosMain) is the `CheckScheduler` of every check: `ensurePeriodic`
  submits the task for an hour on at the earliest (each run does it at its end), `runOnce` asks the running app for
  a run, `cancel` does nothing (one task for all checks; a check whose switch is off skips itself). The simulator
  refuses the request (logged).
- Notifications. `IosAppNotifier` posts through `UNUserNotificationCenter` (`docs/features/notifications.md`, iOS):
  thread = the channel, identifier `<channel>-<id>`, texts from the catalog. A schedule change, new marks or the
  BARS reminder found between 00:00 and 06:00 Moscow time are handed to the system for 06:00 at once (a calendar
  trigger) and count as delivered; Android keeps them for its first run after 06:00, which iOS might not give for
  hours.
- The schedule change step resolves the check and its repository from the graph at each run (`scheduleDataModule`,
  in the graph since IO-09b); `scheduleChangesIosModule` binds the check's iOS ports (`IosScheduleChangeNotifier`,
  the scheduler).
- Debug. A Debug build launched with `-itmoRunRefresh` runs the entry point once more after the session is read,
  prints `Refresh: <step>=<result> ...`, and posts a fixture schedule change notification (`ScheduleChangeFixture`)
  and a fixture marks digest (`MarksFixture`), both with catalog texts, since tests cannot force the scheduler. On
  the simulator Xcode's
  `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"dev.alllexey.itmowidgets.refresh"]`
  also starts the task while the app is paused in the debugger.
- Tests: `BackgroundRunnerTest` (`shared/ios`, `scripts/ios/test.sh kn :shared:ios`: order, deadline, period skip,
  retries, overlapping runs, cancellation, the mark check after the schedule changes) with fake steps,
  `ScheduleChangesRefreshTest`, `MarksRefreshTest`, `IosAppNotifierTest`, `AppRefreshSchedulerTest`, and the hosted
  `ITMOWidgetsTests/BackgroundRunnerTests` (the app graph's steps in order, the fixture posts by catalog key, the
  Info.plist keys).

## Core graph

Koin is the only dependency graph on iOS. `iosCoreModule(host)` (`shared/core/src/iosMain/.../core/di/`) defines
each core type once, what `:app`'s Hilt modules and `CoreBridge` give Android: `AppLog` (`OsLogAppLog`),
`AppDiagnostics`, the wall `Clock`, `AppDispatchers`, `AcademicTimeProvider` (Moscow time, no debug override),
`AppDirectories`, the App Group directory and snapshot writer, `SessionSnapshotWriter`, `CrossProcessLock`,
`SecureStore`, the `app_preferences` DataStore with the core preference stores and `UtilityStorage` (the bundle's
`CFBundleShortVersionString` as the app version), `BackendGate`, the session storage, the Darwin engine,
`MyItmoClient`, Core 2.0's `BackendClient` and its `UsersApi`, `PlatformActions`, `PlatformCapabilities` and the
sign-out cleaners.

- The session. `authDataModule` (KM-11h1: `SessionRepository`, `DemoMode`) runs on `accountIosModule`
  (`shared/feature-account/src/iosMain/.../auth/di/`), the iOS side of Android's `CoreBridge` and
  `AccountAuthBridge`: `DemoPreferences`, the current user from the ID token (`DemoMode`'s fictional user in the
  demo), every `SessionDataCleaner` of the graph (`getAll()`, read on each transition), and `SessionSnapshotSync`,
  which writes session-v1.json for every signed-in state. The lifecycle effects do nothing yet (no background
  work, notifications or widgets to stop); push token sync and device registration are one shared
  `PushDeviceRegistration` (see Notifications), the Backend identity upload a no-op until an iOS card turns custom
  services on. `AppNotifier` is `IosAppNotifier` (`iosBackgroundModule`, see Background refresh), also a sign-out
  cleaner of every shown and scheduled notification.
- Launch. `App.init` starts the graph, builds the shell's session, then calls `SessionRepository.initialize()`. A
  Debug build launched with `-itmoDemo` first opens the demo session unless `DemoMode` is already on
  (`startDemo()`), so the gate never passes the sign-in screen; most UI tests pass it through
  `XCUIApplication.itmo()`. One launched with `-itmoSignedOut` signs out first (the demo or a stored session), so it
  opens on the sign-in screen (`XCUIApplication.itmoSignedOut()`, the five taps' UI test).
- The first-run flow and the sign-in screens. `authModule`, `onboardingDataModule` and `onboardingModule` of
  `:shared:feature-account`, and `settingsDataModule` of `:shared:feature-settings` for the opt-in and the widget
  appearance the flow writes, on `settingsIosModule` (`shared/feature-settings/src/iosMain/.../settings/di/`):
  `WidgetRefreshRequester` reloads the timelines of every widget kind, and `CustomSpoilerRepository` has no image on
  iOS. `iosCoreModule` adds `WidgetSettingsPreferences` and `UtilityStorage` (the first-run flag; the version is
  `CFBundleShortVersionString`). `Shared` exports `:shared:feature-account`, whose ViewModels SwiftUI owns.
- The web sign-in and the update offer. `webLoginDataModule` and `webLoginModule` of `:shared:feature-account`
  on Core 2.0's users area from `iosCoreModule`, and `updateModule` on `updateIosModule`
  (`shared/feature-account/src/iosMain/.../update/di/`): the installed version is `CFBundleShortVersionString`, the
  platform `DevicePlatform.IOS`, so Backend answers the iOS release (`version-info?platform=IOS`, BK-17).
- The application `CoroutineScope` (as Android's `CoroutinesModule`) and `ShareLinkFactory` over the Backend origin,
  the site the share links name (as Android's `WIDGETS_BASE_URL`), are core bindings (IO-09e).
- Sport. `sportModule` on `sportIosModule` (`shared/feature-sport/src/iosMain/.../sport/di/`): Core 2.0's sport
  area from the one `BackendClient`, empty debug ports and `SportShares`; `scheduleDataModule` gives the schedule
  refresh after a booking and `friendSelectorModule` the lessons' friends (`FriendRepository` over social's
  repository). `sportModule` replaces the schedule's empty pending sport rows, and the sport card joins the home feed
  (IO-09c).
- Recordbook screens. `recordbookModule`'s four ViewModels and the subject page's loaders run on the schedule data
  graph's own lessons and refresh, the sport graph's score, the reviews graph's teacher tones and the links graph's
  subject links (IO-09d2).
- Reviews and subject links. `reviewsModule` on `reviewsIosModule` and `resourcesModule` on `resourcesIosModule`
  (`shared/feature-{reviews,resources}/src/iosMain/.../di/`): Core 2.0's reviews and links areas from the one
  `BackendClient`; the editor's teacher lessons come from `scheduleDataModule`. They replace the stand-ins the
  social, schedule and recordbook graphs had before (IO-09f).
- `IosCoreHost` is what the graph needs from Swift: `WidgetReloader`, `clearWebsiteData` and the top view
  controller for the share sheet.
- Backend origin: `BackendBaseURL` in the app's Info.plist, from `BACKEND_BASE_URL` in `Base.xcconfig`; dev
  (`https://dev.widgets.alllexey.dev`) in every configuration until Backend 1.8.0 is in production (gate R).
- `AppDiagnostics` (`IosAppDiagnostics`) keeps this launch's records in memory and writes each to unified logging;
  an error contributes only its type until the journal's sanitiser is shared. `startKoinIos` installs
  `IosCrashHook` (Kotlin/Native's `setUnhandledExceptionHook`): an uncaught Kotlin exception becomes a crash record
  with its type and stack frames, written at once to the JSON file diagnostics-crash-v1.json in the no-backup directory, which
  the next launch shows first and deletes. Swift crashes never pass Kotlin and are not recorded.
- `PlatformCapabilities` is `IosPlatformCapabilities`: everything off until the IO card that ships a feature turns
  it on (calendar export on since IO-15b, recordbook since IO-09d2, marks since IO-09d3, reviews and subject links
  since IO-09f); the quick settings tile, Android's battery and Xiaomi screens, the update channel, the animated QR widget
  and the custom spoiler image stay off.
- `PlatformActions`: the share sheet (the title is not shown: iOS's sheet has none), links (t.me in Telegram when
  installed), Apple Maps (`maps.apple.com`, the pin or the address) and the app's pages in Settings.
- `IosCoreGraph` runs the module in a Koin application of its own for hosted tests (`ITMOWidgetsTests/SessionTests`);
  `IosCoreModuleTest` resolves every definition on the simulator, since Koin's `verify()` is JVM-only.

Network and log. `darwinHttpEngine()` is the Ktor engine of every iOS client: no cookie storage, no cookie
handling, no URL cache (SP-15a, SP-15b); clients read cookies with Ktor's `setCookie()` and set
`followRedirects = false` where a redirect must surface. `OsLogAppLog` writes `AppLog` to unified logging, subsystem
= the bundle ID, category = the tag.

## Swift bridge

Swift reaches Kotlin through the umbrella `Shared` and SKIE 0.10.15 (`shared/ios/build.gradle.kts`): a sealed class
or interface switches as a Swift enum through `onEnum(of:)`, a Kotlin enum is a Swift enum (`AppIcon.allCases`), a
suspend function is `async throws`, a `Flow` is an `AsyncSequence` (`SkieSwiftFlow`, `SkieSwiftStateFlow` with
`value`). SKIE's analytics upload is off.

- Exports. A type of an exported module has its own name in Swift (`UiText`, `AppIcon`); any other keeps a module
  prefix (`Lifecycle_viewmodelViewModel`, typealiased as `SharedViewModel`). `:shared:core`,
  `:shared:feature-account` (IO-07b), `:shared:feature-settings` (IO-08a) and `:shared:feature-recordbook` (IO-09d1:
  `BarsLoginViewModel`, the BARS WebKit ports) are exported; the IO card of a SwiftUI-owned ViewModel exports its
  feature module. Each export grows the header and the link.
- Koin start. `App.init` calls `startKoinIos(platform: AppPlatform())` after the app locale: one global graph over
  `IosKoinModules.all(platform)` (`shared/ios/src/iosMain/.../ios/di/`), `allowOverride(false)`. A second call keeps
  the running graph and returns false. `IosKoin` keeps the graph `startKoin` returned; iOS code never reads Koin's
  global context. Each feature adds one line to `IosKoinModules`; its bindings live in
  `shared/feature-<x>/src/iosMain/.../di/<Area>IosModule.kt`.
- `IosPlatform` is the Kotlin interface Swift implements once, `iosApp/Sources/Bridge/AppPlatform.swift`: the core
  graph's `IosCoreHost` (WidgetKit reloads, the top view controller, WebKit clearing), the BARS session's
  `BarsWebHost` (WebKit's cookies, the hidden BARS view) and `installedWidgetKinds` (WidgetKit's current
  configurations).
- Reading the graph. Koin's `get` is reified, so Swift names the type: `IosKoin.shared.get(protocol: X.self)` for an
  interface, `get(type: X.self)` for a class. A missing definition crashes.
- SwiftUI-owned ViewModels. `ObservableViewModel<Model, State>` (`iosApp/Sources/Bridge/`) is `@Observable` and owns
  a `ScreenViewModelStore`. `ObservableViewModel(X.self, state: \.uiState)` resolves `X` from Koin on first use; a
  view holds it in `@State`; `.observing(model)` follows `uiState` in the view's task;
  `.onEvents(of: model, \.events) { event in ... }` delivers the one-shot events. Its `deinit` clears the store,
  which runs `onCleared` and cancels `viewModelScope`; nothing else does on iOS. These ViewModels get no
  `SavedStateHandle`: arguments are Koin parameters (`ScreenViewModelStore.resolve(type:parameters:)`).
- Compose screens. Only `shared/ios` builds a `ComposeUIViewController`: `screens/ScreenControllers.kt` wraps the
  content in `ItmoTheme` and the host's `LocalPlatformActions`, and each feature's IO card adds
  `screens/<Feature>Screens.kt` with the factories Swift calls (`qrPassViewController(onBack:)`). A CMP screen's
  `koinViewModel()` uses the store Compose Multiplatform gives each controller, whose `SavedStateHandle` is empty: a
  route whose ViewModel reads its key's arguments gets them from its feature's iOS route, which resolves the Koin
  definition in that store with a `SavedStateHandle` of the arguments (`UserProfileIosRoute`, IO-09e;
  `UserScheduleIosRoute`, `LessonDetailsIosRoute`, `FriendSelectorIosRoute`, IO-09b; `UserSportIosRoute`, IO-09c;
  `RecordbookSubjectIosRoute`, `SheetScoresIosRoute`, IO-09d2). The links and review sheets and the report dialogs
  resolve theirs with `hostedViewModel` (`shared/ios`, `screens/HostedViewModel.kt`) in a store cleared when they
  leave the composition (IO-09f).
  A Swift host that keeps state for its route across the route's life holds a Kotlin object the factory reads
  (`SportTabState`: the sport tab's shared lessons and the details sheet's results).
- Requests into a hosted route. A tab root that takes requests from the shell returns a Kotlin handle with its
  controller (`ScheduleRootScreen`: `showToday()` for the `today` entry route, `showFriend(user:)` for the friend
  picker's answer; `RecordbookPage`: `send(request:)` for the period picker's answer and `barsSignedIn()` after the
  BARS sign-in sheet), backed by an unlimited channel, as Android's Fragment results.
- Hosting. `ComposeHost { factory() }` (`Sources/Bridge/ComposeHost.swift`) makes the controller once and ignores
  the safe area, so the surface runs under the status bar and the tab bar and Compose's `WindowInsets` report them;
  the factory pads its content by `WindowInsets.safeDrawing` (the kit's top bar draws no insets). A SwiftUI
  `safeAreaInset` never reaches a hosted controller (nor a pushed screen), so the demo banner sits below each tab's
  stack instead of in an inset. A route's Swift screen (`Sources/Features/<Feature>/`) wraps the host with what only UIKit can do:
  the QR pass sets the screen to full brightness while it is visible and the scene is active and restores the
  user's level otherwise (master P8); the home feed opens the widget instruction sheet, asks for notifications and
  says `error_demo_unavailable` when the router refuses a key in the demo, as the Me tab and the social screens do
  (`SocialMessages`, which also puts a copied ISU on the clipboard with `person_isu_copied`), and the schedule's
  hosts say `schedule_map_unavailable` and `link_open_failed` when no app takes a map or a link (`ScheduleMessages`).
  `onBack` and other callbacks are Swift closures (`dismiss()`, `router.open(_:)`). Compose maps `testTag` to the
  accessibility identifier, so UI tests find a route's parts by its test tags.
- Sheets. A Compose sheet's content is hosted the same way inside a SwiftUI `.sheet`, never as a Compose
  `ModalBottomSheet` in a controller: the factory builds a see-through controller (`screenController(opaque =
  false)`) and pads only the sides and the home indicator (Compose still reports the status bar the sheet starts
  below); the kit's `SheetScaffold` draws its iOS header on the grouped sheet background, and the Swift view sets the
  same background (`systemGroupedBackground`), the detents and the drag indicator and passes `dismiss()` as the
  content's close. The shell's sheet gets the router in its environment, so a sheet opens further keys and answers
  its opener (`deliver(_:from:)`).
- `ITMOWidgetsTests/BridgeTests` checks the graph start, a `StateFlow` update re-rendering a hosted SwiftUI view,
  the ViewModel cleared when the view leaves the hierarchy, and events as Swift enums, over `BridgeProbeViewModel`, a
  probe in `shared/ios` that no screen uses.

## Degradations

Where iOS does less than Android, or does it differently, and why. Everything else is Android's behaviour on the
same shared code (ADR 0016: full parity; only the debug tools stay Android-only). An open gap is a defect still to
fix, not a decision.

| Surface | On iOS | Why |
|---|---|---|
| Tabs | switch by the native tab bar only, no swipe between them | owner decision 2026-10-06: paging between tabs is not an iOS convention |
| Colours | the static brand scheme on every screen | iOS has no wallpaper colours for `ColorSource.Platform` |
| Placing a widget | the first-run flow and the home hint show how to add one | iOS lets no app place a widget |
| QR widget reveal | the code appears without the circle animation | WidgetKit animates only between timeline entries |
| QR widget custom spoiler image | the standard spoiler only; the settings rows are hidden | deferred to v2.4: it needs an iOS picker and crop screen and the image in the App Group |
| QR widget spoiler and dynamic colour switches | shown in settings and the first-run flow, not followed: the spoiler stays on, the code dark on white | open gap: `QrPassSnapshotWriter` writes neither option into qr-pass-v1.json |
| QR tile | the QR Control opens the pass; it cannot draw the code | a Control shows only a symbol and a title |
| Pending sport rows in widgets | no seven-minute refresh; a row stays until the app writes again or its entry ends | WidgetKit allows about 40 to 70 reloads a day |
| Day widget | shows the rows that fit, completed lessons leave first | widget views do not scroll |
| Background checks | the schedule change, mark and calendar steps run when iOS wakes the app, at launch and on every return to it; no fixed rhythm | iOS has no WorkManager: the system picks the moments from how the app is used, never with Background App Refresh off or in Low Power Mode |
| Quiet hours | a change or mark found between 00:00 and 06:00 is handed to the system for 06:00 at once | iOS might not wake the app soon after 06:00 |
| BARS in the background | renewal only through the Keychain cookie copy, never the hidden WebKit view | a background run has about 30 s and no window |
| Notification channels | one switch for all the app's notifications | iOS has no channels |
| Tap on a local notification | opens the app without a route | open gap: the tap handler reads only the push `data` envelope, not `userInfo["action"]` |
| Push | no device token, so nothing is registered with Backend; the notification service passes notifications through | APNs needs the paid Apple account (gate T13) |
| App Links (`https`) | not opened; only `itmowidgets://route/<id>` | Universal Links need associated domains, which only a signed build after T13 has |
| Web sign-in scanner | VisionKit's scanner, which asks for the camera; without it (or on the simulator) the code is typed | iOS has no Play services scanner |
| Update offer | only the App Store page, and nothing while `APP_STORE_ID` is empty | no GitHub or Play channel on iOS; the App Store record arrives after T13 |
| Background work settings | Background App Refresh instead of Android's battery and Xiaomi screens | those screens do not exist on iOS |
| First-run flow | a relaunch in the middle starts at the first step | `ScreenViewModelStore` has no saved-state registry |
| Diagnostics | Kotlin crashes show on the next launch; Swift crashes are not recorded | Swift crashes never pass Kotlin's hook |
| Debug tools | none; Debug builds take launch arguments instead (`-itmoDemo`, `-itmoRunRefresh` and the others in this document) | ADR 0016 keeps the debug tools Android-only |

## Memory

The app's physical footprint (`footprint -p <pid>`, `phys_footprint`) on the pinned simulator, Debug build, demo
session, measured with the method in recipe `ios-cmp-host` when a route of a new kind arrives. The widget extension
stays under its about 30 MB limit by linking no Kotlin; these figures are the app process only.

| State | Footprint | Measured |
|---|---|---|
| Launch on home (fixture roots, Koin graph and the shared session started) | 72 MB | IO-21, 2026-10-07 |
| After visiting the 4 tabs and opening the QR pass (the first Compose screen) | 79 to 87 MB | IO-21, 2026-10-07 |
| Launch on home (the Compose home feed with its hints, other roots fixtures) | 81 MB | IO-09a, 2026-10-07 |
| Launch on home, then the Compose schedule tab and the `today` route (schedule data graph loaded) | 81 MB (peak 89 MB) | IO-09b, 2026-10-08 |
| The sport tab open from home, both pages loaded (Compose `SportRoute`, launched by XCUITest) | 106 MB (peak 108 MB) | IO-09c, 2026-10-08 |
| The recordbook tab open from home through `itmowidgets://route/recordbook` (Compose `RecordbookRoute`, `simctl launch`); home and the sport tab measured the same way in the same session: 162 MB and 154 MB | 149 to 154 MB (peak 153 to 192 MB) | IO-09d2, 2026-10-08 |

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

Tiers (the iOS side of `docs/process/workflow.md`, Verification tiers):

| Tier | Run | When |
|---|---|---|
| Card | `scripts/ios/test.sh`, plus `test.sh kn <module>` for each shared module whose `iosMain` changed and `test.sh ui <Class>` for the screens the card hosts or changes | every iOS card; a card that also touches `app/` or a `commonMain` runs `scripts/verify.sh quick` |
| PR | `ios-check` (CI) | every PR into and push to `v2.3/next` and `master` |
| Nightly | `ios-nightly.yml`: every shared module's `kn` tests and all of `UITests` | each night on `v2.3/next` |
| Release | the snapshot matrix, `scripts/ios/screenshots.sh` in light and dark for review, `archive.sh` of the case that applies (Release) | before a TestFlight build or the F3 prerelease |

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

## Release

How iOS ships in v2.3 depends on when the paid Apple Developer account arrives (gate T13). One case applies:

| Case | When | What ships | Build |
|---|---|---|---|
| F0 | T13 by about 2026-11-10 | MVP and stretch on TestFlight external at T16; App Store submission after Android GA, on the owner's word | `archive.sh --signed --upload` |
| F1 | T13 by about 2026-12-01 | MVP on TestFlight internal at T16, external about a week later; the NSE may slip to 2.3.1 | `archive.sh --signed --upload` |
| F2 | T13 later, account expected | Android ships on time; iOS stays simulator-verified and CI-built on `master`; TestFlight 2.3.x 1 to 2 weeks after T13 | `test.sh --ci` (CI), then as F1 |
| F3 | no account in sight at T16 | on the owner's approval, an "iOS developer preview": the unsigned `.ipa` on a GitHub prerelease for self-signing with a free Apple ID (AltStore, SideStore; re-signed every 7 days), no push and no NSE | `archive.sh --unsigned` |

```bash
scripts/ios/archive.sh --unsigned          # F3: iosApp/build/archive/ITMOWidgets-2.3.0-<build>-unsigned.ipa
scripts/ios/archive.sh --signed            # after T13: a signed archive and an App Store .ipa in archive/export/
scripts/ios/archive.sh --signed --upload   # the integrator only, on the owner's word: to App Store Connect
```

- Everything goes to `iosApp/build/archive/` (ignored): the `.xcarchive`, the `.ipa`, for F3 the staging
  directory `iosApp/build/archive/unsigned`. The script never publishes; a GitHub prerelease of the F3 `.ipa`
  and every upload are the owner's word, each time.
- Versions: `MARKETING_VERSION` 2.3.0 from `Base.xcconfig`; the build number (`CURRENT_PROJECT_VERSION`, the
  committed value 1 is for simulator builds) is `git rev-list --count HEAD` of the archived commit, monotonic along
  `v2.3/next` and `master`. A signed build refuses uncommitted changes; an unsigned one of a dirty tree carries
  `-dirty` in its file name.
- The archive is the `ITMOWidgets` scheme, Release, `generic/platform=iOS`, run by `xcodebuild` inside the `kn`
  slot; the Run Script builds the iosArm64 Kotlin framework in Release. That link runs out of the Gradle daemon's
  4 GB heap, so the script runs the Kotlin/Native compiler in a process of its own with 8 GB
  (`kotlin.native.disableCompilerDaemon`, `kotlin.native.jvmArgs`, passed as `ORG_GRADLE_PROJECT_` variables
  through `xcodebuild` to the Run Script). An unsigned archive with the iosArm64 klibs already built takes about
  6 minutes.
- F3 package (SP-23). `CODE_SIGNING_ALLOWED=NO` archives without signatures, so the bundles carry no entitlements,
  and Xcode cannot ad-hoc sign a device build with entitlements itself. The script removes the notification
  service (no push on a free team, and a third App ID), expands the unsigned entitlements of the widget and the app
  with the `AppGroupID` and `KeychainGroup` of the built Info.plist (a team-less device build has an empty
  `$(AppIdentifierPrefix)`), signs ad hoc (`codesign -s -`) the nested code, the widget, then the app, and zips
  the `Payload` directory. AltStore and SideStore re-sign from the entitlements they find, so this is what keeps
  the App Group. App and widget use 2 of the free account's App IDs and 1 active-app slot.
- F3 widget data. The script prints whether the widgets get real data: yes when app and widget request one App
  Group. AltStore renames it to `<group>.<TEAMID>` and lists it under `ALTAppGroups`, which `AppGroupDirectory` and
  `AppGroupSnapshot` try after `AppGroupID` (Data sharing). Before an F3 prerelease the owner's free-team device
  session confirms it (SideStore issue 1437 reports a failure); if no container resolves there, the widgets stay
  in the signed-out state and the prerelease notes say so.
- Signed (after T13): needs the ignored `iosApp/Config/Signing.local.xcconfig` (Identifiers), archives with
  automatic signing and `-allowProvisioningUpdates`, keeps the notification service, and exports with method
  `app-store-connect`; `--upload` sends the export to App Store Connect instead of writing the `.ipa`.

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
