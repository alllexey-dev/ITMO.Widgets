# Architecture

How the code is split, which way dependencies point and how the platform
pieces (DI, navigation, storage, widgets, background work, push) fit together.
Feature behaviour is in [`features/`](features/); one feature traced end to end
is [feature-flow.md](feature-flow.md); tests are [testing.md](testing.md).

## Overview

ITMO.Widgets is a Kotlin Multiplatform app for Android and iOS.

- `shared/*` holds almost everything: domain models, repositories and view
  models in `commonMain`, the Compose screens in `androidMain` of the same
  module (iOS draws SwiftUI over the same view models).
- `app/` is the Android app: the Navigation 3 shell around the shared screens
  and everything only Android has (RemoteViews widgets, the quick-settings
  tile, WorkManager, FCM, WebView sign-in pages, Koin wiring of Android ports).
- `iosApp/` is the iOS app, built with XcodeGen: native SwiftUI screens over
  the shared view models through the `Shared` framework, plus Swift-only
  widgets and a notification service extension. iOS draws no Compose
  ([ADR 0031](decisions/0031-swiftui-ios-screens.md),
  [ADR 0035](decisions/0035-ios-without-compose.md)).

## Modules

`settings.gradle.kts` includes the projects below; the convention plugins are in
[`build-logic/`](../build-logic/README.md), versions in `gradle/libs.versions.toml`.
Every feature applies `itmowidgets.cmp.android`: Compose UI in `androidMain`,
strings from `:shared:compose-strings` only, so its iOS klib has no Compose
([ADR 0035](decisions/0035-ios-without-compose.md)).

| Module | Holds |
|---|---|
| `:app` | Android app: shell, Android-only surfaces, Koin start, Android ports |
| `:shared:core` | Contracts and models every feature shares (`core/*` packages), common strings; no Compose |
| `:shared:compose-strings` | Android only: the Compose side of the catalog, one CMP `Res` over a copy of every module's strings, the `UiText` resolvers |
| `:shared:designsystem` | Android only: `ItmoTheme`, tokens, the component kit, the Android-only M3 Expressive kit of the redesign (`designsystem.kit`, which draws `core/lesson`), icons, preview helpers |
| `:shared:backend-client` | Typed Ktor client of the ITMO.Widgets Backend (`BackendClient`, one `*Api` per area) |
| `:shared:testing` | Test kit: fake clocks, files and HTTP for every target; Robolectric, Compose UI test, semantics checks and the screenshot harness on Android |
| `:shared:feature-account` | Sign-in, first-run flow, profile tab, web sign-in, My ITMO web page, update offer |
| `:shared:feature-map` | Campus map: plan data, room mapping, the indoor router and trip planner |
| `:shared:feature-home` | Сегодня and the bell over every feature's `DayLessonsGateway`, `TodayTodoSource`s and `BellSource`s |
| `:shared:feature-qr` | QR pass, its cache and widget state |
| `:shared:feature-recordbook` | Record book, BARS overlay, subject page, marks tracking, sheet scores |
| `:shared:feature-resources` | Subject links |
| `:shared:feature-reviews` | Teacher reviews and AI summary tones |
| `:shared:feature-schedule` | Schedule, schedule changes, calendar sync, widget timeline |
| `:shared:feature-settings` | Settings pages, appearance, services opt-in, diagnostics |
| `:shared:feature-social` | Friends, people search, person profiles, friend picker |
| `:shared:feature-sport` | Sport catalog, bookings, queues, score |
| `:shared:ios` | Umbrella that builds the static framework `Shared` for Xcode, iOS Koin start, the Swift bridge |
| `:konsist` | Architecture rules over the sources of `app/` and `shared/` |
| `:baselineprofile` | Device-only: generates `:app`'s baseline and startup profiles and runs the nightly frame-timing benchmarks over the demo session (docs/testing.md) |

Allowed edges: a feature module depends on `:shared:core` (all targets) and on
`:shared:designsystem` and `:shared:compose-strings` from `androidMain` only
(`:shared:testing` in tests). Features never depend on each other. `:shared:compose-strings` depends
on `:shared:core` only; `:shared:designsystem` on `:shared:core` and `:shared:compose-strings`. `:app` depends on every shared module, `:shared:ios` on core, the features and
`:shared:backend-client`.
`:shared:backend-client` depends on nothing in this repository.

Shared modules target `android`, `iosArm64` and `iosSimulatorArm64`, except the
Android-only `:shared:designsystem` and `:shared:compose-strings`. Source
sets: `commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidHostTest`
(JVM with Robolectric) and `iosTest`. In the multiplatform modules `commonMain`
has no `android.*`, `java.*`, `R` or Compose (`KmpRulesTest`).

## Layers inside a feature

A feature module has the packages `feature/<x>/{data,domain,presentation,ui,di}`
under `dev.alllexey.itmowidgets`. Dependencies point inward:
`ui -> presentation -> domain <- data`.

| Package | Holds | Must not touch |
|---|---|---|
| `domain` | Models, repository interfaces, pure rules (selectors, digests, planners) | Platform APIs, wire DTOs, Ktor, Compose |
| `data` | `*RepositoryImpl`, remote and local data sources, mappers, `data/demo` fictional data | `ui`, `presentation` |
| `presentation` | `ViewModel`s and their `<Screen>UiState.kt` | `data`, wire DTOs, `Throwable`, Compose |
| `ui` | `<Name>Route` (owns the view model) and stateless `<Name>Screen` composables, previews | `data`, network, storage |
| `di` | The feature's Koin modules | - |

Not every feature has every layer. A contract two features share lives in
`:shared:core` (`core/<topic>`); the feature that owns the data implements it.
Wire types of MyItmoApi and the Backend client stop in `data`.

### Screen state and events

A view model exposes one `uiState: StateFlow<S>`, at most one `events: Flow<E>`
backed by `EventQueue` (`core/presentation`), `refresh(mode: RefreshMode)`
when the screen refreshes, and plain action functions. State carries no
platform text: labels are `UiText`, failures are `AppError` (`core/result`).
`RefreshTracker` joins concurrent refreshes, `BusyKeys` marks per-row actions
busy. Repositories hand cached data to screens as `LoadState` or `AppResult`.

Only the route obtains a view model (`koinViewModel()` or a passed instance);
the screen takes state and lambdas, so previews and tests call it directly.
Exceptions become `AppError` at the `data` boundary
(`core/network/BackendErrors.kt` decodes Backend errors); server messages never
reach the UI.

## Dependency injection

Koin is the only DI framework. Each shared feature exposes Koin modules in
`feature/<x>/di/` (for example `scheduleModule` and `scheduleDataModule`);
view models use `viewModelOf`, repositories `singleOf(...) { bind<...>() }`.
Open sets (session cleaners, bell and todo sources, background checks, push
handlers) are qualified definitions read with `getAll<T>()`.

- **Android.** `app/src/main/java/dev/alllexey/itmowidgets/di/KoinModules.kt`
  lists every module: the Android ones in the same `di/` package (platform
  ports such as `networkModule`, `storageModule`, `androidScheduleModule`)
  and the shared feature modules. `KoinStarter.ensureStarted()` starts the
  graph with `allowOverride(false)`. `ItmoWidgetsApplication` calls it first;
  workers, widget providers and the FCM service call it too, because Android
  can run them before `Application.onCreate()`. These components read
  dependencies as `KoinComponent`s (`by inject()`).
- **iOS.** `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/di/IosKoinModules.kt`
  lists the iOS graph: `iosCoreModule(platform)`, the shared feature modules
  and each feature's iOS ports from its `iosMain` (`<Area>IosModule.kt`).
  Swift calls `startKoinIos(platform:)` in `ITMOWidgetsApp.swift` with
  `AppPlatform` (Swift implementations of Kotlin platform protocols) and
  reads objects through `IosKoin.shared.get(...)`.
- **Tests.** Each feature has a `<X>ModuleTest` that runs Koin `verify()`;
  `app/src/test/java/dev/alllexey/itmowidgets/di/bridge/KoinGraphTest.kt`
  checks the whole Android graph.

## Navigation

Routes are shared: `@Serializable` keys in
`shared/core/src/commonMain/kotlin/dev/alllexey/itmowidgets/core/navigation/AppRoutes.kt`.
Each key has a kind (tab root, screen, sheet, dialog, gate); a sheet's `SheetPolicy` names one of the three sizes
(content height, `SheetPolicy.TALL_FRACTION` of the window, the whole height below the status bar), which iOS maps
to detents. `AppTab` is the tab model of both platforms: the five tabs in bar order (Зачётка, Расписание, Сегодня,
Спорт, Профиль; `HOME` is Сегодня and the start tab, `ME` Профиль) with each tab's label key and icons.
`EntryRouteParser` turns widget, shortcut, notification and App Link intents
into routes; `RouteQueue` holds one until the session and the first-run flow
allow it.

- **Android.** One activity, `MainActivity`, hosts the Navigation 3 shell in
  `app/src/main/java/dev/alllexey/itmowidgets/app/shell/`: `AppShell` (one
  `NavDisplay` over `ShellBackStack`, a back stack per tab), `Nav3AppNavigator`
  and `EntryRegistry`. Every key is registered in
  `app/shell/entries/<Area>Entries.kt`, which renders the feature's
  `<Name>Route`; tab roots render in `entries/TabRootFrames.kt`
  ([design](design.md#shell)). A hidden tab keeps its state and view models
  (the Nav3 `multiplestacks` recipe). A tab root is disposed under a pushed
  screen and capped at STARTED under a sheet, so roots start no work from
  composition or lifecycle effects: the first load runs in the view model and
  revalidation waits for `AppForeground` (`core/platform`). The bar and the demo
  banner lie under `NavDisplay`: tab roots end above them, pushed screens cover
  them. Transitions and the predictive Back are the shell's (`ShellMotion.kt`,
  `ShellBack.kt`). Only `LoginActivity`, `BarsLoginActivity` and
  `SpoilerCropActivity` are separate activities (`app/shell/ActivityRoutes.kt`).
- **iOS.** `iosApp/Sources/App/Router/AppRouter.swift` keeps a SwiftUI
  `NavigationStack` per tab and the sheets; `Routes+<Area>.swift` map each
  shared key to a target (a SwiftUI screen, a sheet or a tab). URLs and App Intents arrive through `RouteURL.swift`
  and `RouteInbox.swift`.

## Time

Academic logic takes time from `AcademicTimeProvider` (`core/time`) or an
injected `kotlin.time.Clock`; no direct `now()` in feature or storage code
(`TimeRulesTest`). The zone is `Europe/Moscow`. Debug builds can override the
academic date from the debug tools. Tests use fixed clocks.

## Demo mode

A hidden demo session lets reviewers use the app without an ITMO account.
`DemoMode` (`core/demo`) says whether it is on. Every class that takes a
network client (MyItmoApi, a Backend `*Api`, the public web client) checks it
before the call: reads answer from the feature's `data/demo`, writes fail with
`AppError.DemoUnavailable`. Demo data never reaches Backend. `GateRulesTest`
enforces the constructor parameter; `*DemoGateTest` and `DemoNetworkGateTest`
check behaviour. Details: [demo session](features/demo.md).

## Custom-services opt-in

Social features need the ITMO.Widgets Backend and are off until the user turns
on «Подключение к ITMO.Widgets». `BackendGate` (`core/services`) is the one
reader of that switch:

- `mayCallBackend()`: the stored opt-in outside the demo session. Every
  Backend call checks it inside the repository or data source, so no screen
  can bypass it.
- `isConnected()`: what settings and social screens show (true in the demo).
- `isOptedIn()`: the stored opt-in alone, for widgets and push.

`DefaultBackendGate` (Android) and `IosBackendGate` implement it;
`CustomServicesRepository` is the switch itself. Backend is authoritative for privacy and authorization; the app
never enforces privacy on its behalf.

## Storage

| Data | Where |
|---|---|
| Settings and flags | One DataStore file, `app_preferences`, read through per-concern stores in `core/storage` (`AppearancePreferences`, `WidgetSettingsPreferences`, ...) built on `DataStorePreferences` |
| Tokens, the BARS session and the Wi-Fi password | `SecureStore`: `FileSecureStore` on Android (files in `noBackupFilesDir` sealed with an Android Keystore key), `KeychainSecureStore` on iOS |
| Caches and feature state (schedule cache, QR cache, found changes, marks, queue results, sheet scores, calendar sync, links) | JSON files under `AppDirectories` written with `AtomicTextFile`; device-only state is excluded from backup |
| Widget snapshots | Android: `widgets/schedule_snapshot.json` and the QR state; iOS: JSON files in the App Group container that the widget extension reads |

`SharedPreferences` is not used. Every store of user-scoped data implements
`SessionDataCleaner`; sign-out and an account change run every cleaner.
Stored names (files, DataStore keys, worker names, intent actions, channel
ids, component class names) outlive app updates: `StableIdentifiersTest`
(Android) and `iosApp/Tests/UnitTests/StableIdentifiersTests.swift` (iOS) pin
them, and a change needs a migration.

## Widgets and background work

Widget data is shared, rendering is native.

- **Android widgets** are RemoteViews providers in `app/`: the QR widget
  (`QrCodeWidgetProvider`) and the schedule widgets (`SingleLessonWidgetProvider`,
  `DayTimelineWidgetProvider`, and `DayScheduleWidgetProvider` for the classic list). A worker loads data through the shared
  provider (`ScheduleWidgetDataProvider`), writes a snapshot and renders it.
  Rules: [widgets](features/widgets.md).
- **iOS widgets** are a WidgetKit extension in `iosApp/Extensions/Widgets/`
  that links no Kotlin. The app writes a timeline into the App Group
  (`ScheduleTimelineWriter`, the QR pass snapshot); the extension only picks
  the entry for the moment.
- **Background checks** (schedule changes, new marks, calendar sync) implement
  `BackgroundCheck` (`core/work`). The check logic is shared
  (`ScheduleChangesCheck`, `MarksCheck`); Android runs it from a WorkManager
  `CoroutineWorker` scheduled by `PeriodicCheckScheduler`, iOS from one
  `BGAppRefreshTask` (`iosApp/Sources/Background/BackgroundRefresh.swift`).
  The app start and the session lifecycle call `syncWork()` on every check.

## Push

Backend sends push messages for friendships and sport bookings.

- **Android.** `MyFirebaseMessagingService` hands each message to
  `FcmPayloadDispatcher`, which picks a `FcmPayloadHandler` by type
  (`FriendshipPushHandler`, `SportSignPushHandler`). `DefaultFcmTokenSync`
  registers the token with Backend behind `BackendGate`.
- **iOS.** `iosApp/Extensions/NotificationService/` decodes and shows pushes
  without Kotlin; the app registers the APNs token (`IosPushDevice`) and
  handles taps in `iosApp/Sources/Push/`.

Local notifications (schedule changes, marks) are posted by platform notifiers
behind shared ports. Details: [notifications](features/notifications.md).

## Strings

User-visible text is Russian and lives in catalog files
`shared/<module>/src/commonMain/composeResources/values/strings_<file>.xml`
([ADR 0028](decisions/0028-strings-and-icons.md),
[ADR 0035](decisions/0035-ios-without-compose.md)).
`strings_common.xml` (`:shared:core`) holds text two modules share;
`strings_platform.xml` holds text system surfaces read (manifest, widgets,
notifications, the tile), with frozen keys in `scripts/strings-frozen-keys.txt`.

- Keys are never renamed: one key names a string on every platform.
  `scripts/strings-owners.py --where <id>` tells where a new id belongs.
- Code outside Compose names a string as `UiText.Res` / `UiText.Plural`
  (`core/text/UiText.kt`) with the generated `<Area>Strings` / `<Area>Plurals`
  keys. Compose screens read the one CMP `Res` of `:shared:compose-strings`;
  feature modules compile no `Res` of their own.
- Android Views, widgets and notifications resolve `UiText` through the
  generated key -> `R` table; iOS (Swift and Kotlin) resolves it from the
  `.xcstrings` tables that `./gradlew :app:exportAppleStrings` writes under
  `iosApp/Shared/`. Nothing on iOS reads a CMP resource.
- `:shared:core` and the `data`, `domain` and `presentation` packages import no
  CMP resource or Compose resolver; common and iOS code imports no Compose
  (`UiTextRulesTest`, `KmpRulesTest`).

## Distribution variants

The Android flavor dimension `distribution` has `github` (APK on GitHub
Releases) and `play` (AAB for Google Play), with one `applicationId` and one
signing key; they differ only in `BuildConfig.DOWNLOAD_URL` and the update
action (`app/src/{github,play}/.../UpdateActionModule.kt`). Debug and
`minifiedSmoke` builds call `https://dev.widgets.alllexey.dev`, release builds
`https://widgets.alllexey.dev`; on iOS the origin is `BACKEND_BASE_URL` in
`iosApp/Config/Base.xcconfig`. Both apps send `X-App-Version` and `X-Client`
(ADR 0034): the build identity ([building](building.md#build-identity)), the
channel (iOS: `dev`, `sideload`, `testflight` or `appstore` from
`IosClientVersion`), the OS version and a random install id replaced on every
session change ([contract](../shared/backend-client/CONTRACT.md#request-headers)).
Release lines and version codes: [releases](product/releases.md).

## Enforced rules

The Konsist suite (`konsist/src/test/`) fails the build when a rule breaks.
Topics: layers (`LayerRulesTest`), feature isolation, modules and KMP source
sets, time, threading, gates (demo and opt-in), storage, DI, presentation,
UI text, Compose, gestures, debug-only code and distribution variants. Add a
rule when a new invariant is agreed instead of relying on review.

## Other repositories

Backend, MyItmoApi and the web version live in their own repositories
([docs/README.md](README.md#related-repositories)). Backend changes first and
`:shared:backend-client` mirrors its contract (`scripts/sync-backend-contract.sh`,
[CONTRACT.md](../shared/backend-client/CONTRACT.md)); MyItmoApi is the only client
of MyITMO and BARS, so extend it instead of adding a second one. ISU pages are read on the device by `IsuWebClient`
(`core/network`, [ADR 0037](decisions/0037-on-device-isu-client.md)) with the phone's ITMO.ID cookies.
