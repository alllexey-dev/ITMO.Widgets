# iOS app

The iOS client is a SwiftUI shell around the shared Compose Multiplatform screens, with WidgetKit extensions and a
notification service extension ([ADR 0023](decisions/0023-ios-client.md)). It lives in `iosApp/` and links one Kotlin
umbrella framework, `Shared`, built from `shared/ios/`. Today the app is the shell with placeholder roots, gated on
the shared session with the sign-in screen and the first-run flow (see Shell and routes, Sign-in), the QR pass is
its first Compose screen, the widget bundle holds the QR widget (see Widgets) and the notification service passes
notifications through unchanged.

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
| `iosApp/Sources/` | the app target `ITMOWidgets` (SwiftUI); `Bridge/` holds the Kotlin side's Swift glue (Swift bridge), `Features/<Feature>/` the Swift screen of each route (the QR pass's host) |
| `iosApp/Extensions/Widgets/` | the widget extension `ITMOWidgetsWidgets` (WidgetKit, Controls; no Kotlin); `<Widget>/` per widget. The app target compiles these sources too, without `WidgetsBundle.swift`, so the hosted tests reach them |
| `iosApp/Extensions/NotificationService/` | the notification service extension `ITMOWidgetsNotificationService` (no Kotlin) |
| `iosApp/Shared/` | sources of all three targets: the generated string tables and `AppSymbol.swift`, the custom symbol images, `WidgetSnapshots/` (readers of the App Group snapshots), `Intents/` (App Intents of widget buttons; not in the notification service) |
| `iosApp/Strings/` | `strings_ios*.xml`: catalog files with copy only iOS shows |
| `iosApp/Tests/UnitTests/` | `ITMOWidgetsTests`, hosted in the app; `Fixtures/` holds the App Group JSON the Kotlin writers' tests produce |
| `iosApp/Tests/SnapshotTests/` | `SnapshotTests`, hosted in the app: SwiftUI and widget entry view snapshots (swift-snapshot-testing), references in `__Snapshots__/` |
| `iosApp/Tests/UITests/` | `UITests` (XCUITest): smoke tests and review screenshots |
| `shared/ios/` | the umbrella framework `Shared` (static, with SKIE) over every shared module; it exports `:shared:core`; the Koin start, `IosPlatform`, the ViewModel store and the Compose screen hosts |
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
the shared `SessionRepository` (see Core graph); until the IO-09x cards host the tab roots, the roots are
placeholders, and the me root holds the sign-out (Android's confirmation, `SessionRepository.signOut()`).

- Tabs. `ShellTab` holds the roots of Android's `res/menu/bottom_nav.xml` in its order: recordbook, schedule, home,
  sport, me. The recordbook is declared but hidden until IO-09d2 (no placeholder reaches App Review); home is
  selected at launch. The container is one type, `Shell/ShellTabs.swift` (a `TabView`). Tabs switch by the native
  tab bar only, with no swipe between them (owner decision 2026-10-06; IO-SW1 dropped, see design.md "Tab swipe").
- Stacks and sheets. Each tab has one `NavigationStack` whose path the router holds; sheets open at the medium
  detent and drag to large.
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
  sheet, a tab, the gate, or "not on iOS". A feature card changes only its own file; a key no feature claims fails
  `RouterTests.testEveryRegisteredRouteHasAFeature`, and `testEveryRouteKindHasItsTarget` pins the target of every
  key. "Not on iOS" keys (recordbook, reviews, resources, calendar until IO-09d2, IO-09f and IO-15b map them, the
  debug tools for good, and every screen of a feature whose IO card has not merged) have no entry point and open
  nothing.
- Route results. A screen that answers its opener (the friend picker answers the schedule) is opened with
  `open(_:onResult:)` and answers with `deliver(_:from:)`; the router holds the callback until then.

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
| Widget kinds | `dev.alllexey.itmowidgets.widget.qr` |
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
| session-v1.json | `SessionSnapshotWriter`, kept on the session state by the account module's `SessionSnapshotSync` | widget extension (`SessionFile.swift` in `iosApp/Shared/WidgetSnapshots/`), notification service | `{"isu": Int?, "demo": Bool, "alertsAllowed": Bool}`; written for every signed-in session, demo included; missing means signed out; no token; `alertsAllowed` false until IO-13a |
| qr-pass-v1.json | `QrPassSnapshotWriter` (`:shared:feature-qr`, iosMain), on every new valid pass; reloads `dev.alllexey.itmowidgets.widget.qr` | widget extension (`QrPassSnapshot.swift` in `iosApp/Shared/WidgetSnapshots/`) | `{"generatedAt": ISO 8601, "expiresAt": ISO 8601, "demo": Bool, "matrix": [String], "spoiler": Bool?}`: one string per row from the top, `1` a dark module, from the shared `QrCodeGenerator` (version 1, ECC LOW), so the widget encodes nothing; no file while there is no valid pass; the fixture `iosApp/Tests/UnitTests/Fixtures/qr-pass-v1.json` is what the writer writes for the demo pass (`QrPassSnapshotWriterTest`, `QrPassSnapshotTests`); `spoiler` is the global QR widget option, absent (as the writer leaves it today) means on, the Android default |
| qr-widget-v1.json | `RevealQrIntent` in the widget extension, on a tap on the spoiler | widget extension (`QrWidgetReveal.swift`) | `{"revealedUntil": ISO 8601}`: the tap's time plus 30 s, Android's auto-hide delay; one file for every placed QR widget; never read by the app |
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

Each card that stores a secret adds its row. Values are never logged.

Session. `KeychainTokenStorage` is both MyItmoApi 2.x's `TokenStorage` and the session's `SessionTokenStore` over
the one `myitmo_tokens` item, serialised as Android's `MyItmoStorage` serialises `myitmo_tokens.enc` before sealing
it: five lines, each token base64url without padding (`~` for none), each expiry in epoch milliseconds. It keeps no
copy, so each read sees what another process wrote. The client's `TokenManager` is the only refresher; it refreshes
inside `FileCrossProcessLock("myitmo-refresh")` and re-reads the item once it holds the lock, so when the app and the
notification service find the same expired token only the first refreshes. A value that does not parse is dropped
(signed out); a Keychain failure is thrown and drops nothing.

Sign-in (IO-07a, IO-07b, SP-21 path (a)). The signed-out gate is `AuthScreen` (`Sources/Features/Auth/`), Android's
`AuthFragment` in SwiftUI over the shared `AuthViewModel`: Android's `auth_logo` (bundled by path from
`app/src/main/res/drawable-nodpi`, no copy), the app name, the three feature lines, `auth_login_itmo_id`, which opens
`ItmoSignInScreen` as a full-screen cover, and `auth_login_refresh_token`, an alert with a secure field that calls
`signInWithRefreshToken`. Five taps on the logo, each within 1.5 s (`DemoEntryTaps`, times from
`ProcessInfo.systemUptime`), start the demo with a success haptic and a VoiceOver announcement of `demo_entered`;
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
(`ios_onboarding_notifications_configure` once allowed). The answer is the step's status; IO-13a feeds it into
`alertsAllowed`. A back button above the step replaces Android's system back. A Debug build launched with
`-itmoOnboarding` shows the flow over the demo session, which otherwise skips it, until the flow ends (UI tests,
`XCUIApplication.itmoOnboarding()`); finishing it there stores the flag as a real account would.

Sign-out. Three `SessionDataCleaner`s run with the shared ones: the Keychain (every item of the service), the App
Group container (every file but the `locks` directory) and WebKit's website data, which Swift removes through
`IosCoreHost.clearWebsiteData` (`WKWebsiteDataStore`, as Android clears its WebView data).

## Widgets

The widget extension links no Kotlin (its limit is about 30 MB, SP-16a): each widget reads App Group files on every
timeline request and makes no network call. The app's writers reload a widget's kind when its files change.

QR widget (`Extensions/Widgets/Qr/`, kind `dev.alllexey.itmowidgets.widget.qr`, small, `StaticConfiguration`).
Android keeps the widget options global, so they come from `qr-pass-v1.json`, not from a per-widget configuration.

| State | When | Shows | Tap |
|---|---|---|---|
| Signed out | no `session-v1.json` (or no container) | QR symbol, `schedule_widget_signed_out` | the QR pass in the app |
| Spoiler | a valid pass, no reveal running | noise, `ios_widget_qr_reveal` | `RevealQrIntent`: the code for 30 s |
| Revealed | a reveal running, or the spoiler option off | the code, dark on white in both themes; in the demo with `demo_entered` | the QR pass in the app |
| Expired | signed in, no pass or past its `expiresAt` | refresh symbol, `ios_widget_qr_expired` | the QR pass in the app |

- `QrWidgetTimeline` turns the files into entries: the current state, the spoiler when the reveal ends, the
  expired state at the pass's deadline; a new timeline at least every hour, Android's update period.
- The tap URL is `itmowidgets://route/qr_pass` (`widgetURL`); the spoiler alone is a `Button(intent:)`.
- Degradations: no circle animation on reveal (WidgetKit animates only between entries); a custom spoiler image is
  v2.4; the spoiler option has no iOS setting yet, so the spoiler is always on.
- `SnapshotTests/WidgetSnapshotTests` holds each state at the small family size (170 x 170 pt on the pinned
  iPhone, `WidgetSizes`) in the four appearances; `ITMOWidgetsTests/QrWidgetTimelineTests` the timeline.

## Core graph

Koin is the only dependency graph on iOS. `iosCoreModule(host)` (`shared/core/src/iosMain/.../core/di/`) defines
each core type once, what `:app`'s Hilt modules and `CoreBridge` give Android: `AppLog` (`OsLogAppLog`),
`AppDiagnostics`, the wall `Clock`, `AppDispatchers`, `AcademicTimeProvider` (Moscow time, no debug override),
`AppDirectories`, the App Group directory and snapshot writer, `SessionSnapshotWriter`, `CrossProcessLock`,
`SecureStore`, the `app_preferences` DataStore and the core preference stores, `BackendGate`, the session storage,
the Darwin engine, `MyItmoClient`, Core 2.0's `BackendClient`, `PlatformActions` and the sign-out cleaners.

- The session. `authDataModule` (KM-11h1: `SessionRepository`, `DemoMode`) runs on `accountIosModule`
  (`shared/feature-account/src/iosMain/.../auth/di/`), the iOS side of Android's `CoreBridge` and
  `AccountAuthBridge`: `DemoPreferences`, the current user from the ID token (`DemoMode`'s fictional user in the
  demo), every `SessionDataCleaner` of the graph (`getAll()`, read on each transition), and `SessionSnapshotSync`,
  which writes session-v1.json for every signed-in state. The lifecycle effects do nothing yet (no background
  work, notifications or widgets to stop); push token sync and device registration are no-ops until IO-13a, the
  Backend identity upload until an iOS card turns custom services on. `AppNotifier` is bound by the card that needs
  it.
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

## Swift bridge

Swift reaches Kotlin through the umbrella `Shared` and SKIE 0.10.15 (`shared/ios/build.gradle.kts`): a sealed class
or interface switches as a Swift enum through `onEnum(of:)`, a Kotlin enum is a Swift enum (`AppIcon.allCases`), a
suspend function is `async throws`, a `Flow` is an `AsyncSequence` (`SkieSwiftFlow`, `SkieSwiftStateFlow` with
`value`). SKIE's analytics upload is off.

- Exports. A type of an exported module has its own name in Swift (`UiText`, `AppIcon`); any other keeps a module
  prefix (`Lifecycle_viewmodelViewModel`, typealiased as `SharedViewModel`). Only `:shared:core` is exported; the IO
  card of a SwiftUI-owned ViewModel exports its feature module. Each export grows the header and the link.
- Koin start. `App.init` calls `startKoinIos(platform: AppPlatform())` after the app locale: one global graph over
  `IosKoinModules.all(platform)` (`shared/ios/src/iosMain/.../ios/di/`), `allowOverride(false)`. A second call keeps
  the running graph and returns false. `IosKoin` keeps the graph `startKoin` returned; iOS code never reads Koin's
  global context. Each feature adds one line to `IosKoinModules`; its bindings live in
  `shared/feature-<x>/src/iosMain/.../di/<Area>IosModule.kt`.
- `IosPlatform` is the Kotlin interface Swift implements once, `iosApp/Sources/Bridge/AppPlatform.swift`: the core
  graph's `IosCoreHost` (WidgetKit reloads, the top view controller, WebKit clearing) and `installedWidgetKinds`
  (WidgetKit's current configurations).
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
  `koinViewModel()` uses the store Compose Multiplatform gives each controller.
- Hosting. `ComposeHost { factory() }` (`Sources/Bridge/ComposeHost.swift`) makes the controller once and ignores
  the safe area, so the surface runs under the status bar and the tab bar and Compose's `WindowInsets` report them;
  the factory pads its content by `WindowInsets.safeDrawing` (the kit's top bar draws no insets). A SwiftUI
  `safeAreaInset` never reaches a hosted controller (nor a pushed screen), so the demo banner sits below each tab's
  stack instead of in an inset. A route's Swift screen (`Sources/Features/<Feature>/`) wraps the host with what only UIKit can do:
  the QR pass sets the screen to full brightness while it is visible and the scene is active and restores the
  user's level otherwise (master P8). `onBack` and other callbacks are Swift closures (`dismiss()`). Compose maps
  `testTag` to the accessibility identifier, so UI tests find a route's parts by its test tags.
- `ITMOWidgetsTests/BridgeTests` checks the graph start, a `StateFlow` update re-rendering a hosted SwiftUI view,
  the ViewModel cleared when the view leaves the hierarchy, and events as Swift enums, over `BridgeProbeViewModel`, a
  probe in `shared/ios` that no screen uses.

## Memory

The app's physical footprint (`footprint -p <pid>`, `phys_footprint`) on the pinned simulator, Debug build, demo
session, measured with the method in recipe `ios-cmp-host` when a route of a new kind arrives. The widget extension
stays under its about 30 MB limit by linking no Kotlin; these figures are the app process only.

| State | Footprint | Measured |
|---|---|---|
| Launch on home (fixture roots, Koin graph and the shared session started) | 72 MB | IO-21, 2026-10-07 |
| After visiting the 4 tabs and opening the QR pass (the first Compose screen) | 79 to 87 MB | IO-21, 2026-10-07 |

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
