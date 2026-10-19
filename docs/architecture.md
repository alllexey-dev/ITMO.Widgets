# Architecture

How the code is split, which way dependencies point and how the platform
pieces (DI, navigation, storage, widgets, background work, push) fit together.
Feature behaviour is in [`features/`](features/); one feature traced end to end
is [feature-flow.md](feature-flow.md); tests are [testing.md](testing.md).

## Overview

ITMO.Widgets is a Kotlin Multiplatform app for Android and iOS.

- `shared/*` holds almost everything: domain models, repositories, view
  models and Compose Multiplatform screens, in `commonMain`.
- `app/` is the Android app: the Navigation 3 shell around the shared screens
  and everything only Android has (RemoteViews widgets, the quick-settings
  tile, WorkManager, FCM, WebView sign-in pages, Koin wiring of Android ports).
- `iosApp/` is the iOS app: a SwiftUI shell built with XcodeGen. Today it hosts
  the shared Compose screens through the `Shared` framework, plus Swift-only
  widgets and a notification service extension.

Direction: iOS screens move to native SwiftUI over the shared Kotlin domain,
data and view models, and the shared code is refactored substantially during
that move; Android keeps Compose. See
[ADR 0031](decisions/0031-swiftui-ios-screens.md). The iOS side of this
document describes the current state.

## Modules

`settings.gradle.kts` includes the projects below. `build-logic/` is an
included build with the convention plugins (`itmowidgets.kmp.library`,
`itmowidgets.cmp.ui`, `itmowidgets.testing`, `itmowidgets.strings`,
`itmowidgets.android.app`); versions live in `gradle/libs.versions.toml`.

| Module | Holds |
|---|---|
| `:app` | Android app: shell, Android-only surfaces, Koin start, Android ports |
| `:shared:core` | Contracts and models every feature shares (`core/*` packages), common strings; no Compose |
| `:shared:compose-strings` | The Compose side of the catalog: one CMP `Res` over a copy of every module's strings, the `UiText` resolvers |
| `:shared:designsystem` | `ItmoTheme`, tokens, the component kit, the Android-only M3 Expressive kit of the redesign (`designsystem.kit`, which draws `core/lesson`), icons, preview helpers |
| `:shared:backend-client` | Typed Ktor client of the ITMO.Widgets Backend (`BackendClient`, one `*Api` per area) |
| `:shared:testing` | Test kit: Robolectric and screenshot harness, semantics checks, fake clocks |
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
| `:shared:ios` | Umbrella that builds the static framework `Shared` for Xcode, iOS Koin start, iOS screen factories |
| `:konsist` | Architecture rules over the sources of `app/` and `shared/` |
| `:baselineprofile` | Device-only: generates `:app`'s baseline and startup profiles and runs the nightly frame-timing benchmarks over the demo session (docs/testing.md) |

Allowed edges: a feature module depends on `:shared:core`,
`:shared:designsystem` and `:shared:compose-strings` only (`:shared:testing` in
tests). Features never depend on each other. `:shared:compose-strings` depends
on `:shared:core` only; `:shared:designsystem` on `:shared:core` and `:shared:compose-strings`. `:app` and `:shared:ios` depend on every shared module.
`:shared:backend-client` depends on nothing in this repository.

Shared modules target `android`, `iosArm64` and `iosSimulatorArm64`. Source
sets: `commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidHostTest`
(JVM with Robolectric) and `iosTest`. `commonMain` has no `android.*`,
`java.*` or `R`.

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
  `app/src/main/java/dev/alllexey/itmowidgets/app/shell/`: `ShellHost`,
  `AppShell` (one `NavDisplay`), `Nav3AppNavigator` (open and close keys,
  per-tab back stacks in `ShellBackStack`) and `EntryRegistry`. Every key is
  registered in `app/shell/entries/<Area>Entries.kt`, which renders the
  feature's `<Name>Route` and turns its lambdas into navigator calls. Each tab
  root renders inside its frame (`entries/TabRootFrames.kt`): the kit's
  `ItmoRootScreen` with the static root header, and on Сегодня the date
  (`TodayHeaderViewModel`), the bell and the `Пропуск` button that opens the
  `QrPass` sheet ([design](design.md#shell)). `NavDisplay` shows
  `ShellBackStack.entries`: the start tab's root, the selected tab's root above
  it, the pushed screens and the sheets and dialogs, and does every transition
  and the entries' lifecycles itself (`ShellMotion.kt`: fade through between tab
  roots, shared axis X for pushed screens). The predictive Back is the shell's
  (`ShellBack.kt`): it takes the system gesture, draws Material's back preview
  on a pushed screen (entries with `backPreview()` metadata) and hands
  `NavDisplay` a gesture held at 0 through a private dispatcher, so the release
  always gets the whole dissolve; Nav3 1.3's gesture and release handoff can
  replace that once it is stable. Per-tab state follows the Navigation 3 `multiplestacks` recipe:
  each tab's stack and the contextual stack are decorated on their own
  (saveable state, `ViewModelStore`, route arguments), so a hidden tab keeps its
  state and view models. `NavDisplay` disposes a tab root under a pushed screen and
  caps it at STARTED under a sheet, so a root keeps its scroll in saveable
  state and starts no work from composition or lifecycle effects: the first
  load runs in its view model, and revalidation waits for the app's return to
  the foreground (`AppForeground` in core, `ProcessLifecycleOwner` on Android,
  `UIApplicationDidBecomeActiveNotification` on iOS). The `ShortNavigationBar` and the demo banner are laid
  out under `NavDisplay` (`AppShell.kt`); tab roots end above them, pushed
  screens cover them. Sheets and dialogs are overlay scenes (`ShellScenes.kt`
  sizes a sheet by its `SheetPolicy`). Only `LoginActivity`, `BarsLoginActivity` and
  `SpoilerCropActivity` remain separate activities (`app/shell/ActivityRoutes.kt`).
- **iOS.** `iosApp/Sources/App/Router/AppRouter.swift` keeps a SwiftUI
  `NavigationStack` per tab and the sheets; `Routes+<Area>.swift` map each
  shared key to a target (a Compose screen through `ComposeHost`, a SwiftUI
  screen or a sheet). URLs and App Intents arrive through `RouteURL.swift`
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
| Tokens and the BARS session | `SecureStore`: `FileSecureStore` on Android (files in `noBackupFilesDir` sealed with an Android Keystore key), `KeychainSecureStore` on iOS |
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

User-visible text is Russian and lives in catalog files:
`shared/<module>/src/commonMain/composeResources/values/strings_<file>.xml`.
`strings_common.xml` (`:shared:core`) holds text two modules share;
`strings_platform.xml` holds text system surfaces read (manifest, widgets,
notifications, the tile), with frozen keys in `scripts/strings-frozen-keys.txt`.

- Code outside Compose names a string by its key: `UiText.Res(key, arguments)`
  and `UiText.Plural(key, count, arguments)` (`core/text/UiText.kt`). The keys
  are the `const val`s of `<Area>Strings` and `<Area>Plurals`, which
  `itmowidgets.strings` generates in each module's namespace
  (`dev.alllexey.itmowidgets.shared.core.CoreStrings`,
  `dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals`).
- `:shared:core` compiles no CMP resources. `:shared:compose-strings` compiles
  a copy of every module's catalog files into one public CMP `Res` and
  resolves `UiText` in Compose (`asString()`) and in suspend code
  (`resolve()`); screens read core strings from that `Res`. Feature modules
  still compile their own catalog for their screens, so a string sits twice in
  the app until the screens leave `commonMain`.
- Android Views, widgets and notifications resolve `UiText` with
  `resolve(context)` through the generated key -> `R` table
  (`ExportedStringIds`), Swift with `UiText.resolved` from the `.xcstrings`
  tables, iOS Kotlin outside Compose through the `UiTextResolver` port.
- `:shared:core` and the `data`, `domain` and `presentation` packages import no
  CMP resource, `Res` or Compose resolver (`UiTextRulesTest`).

The `itmowidgets.strings` plugin exports what Android `res` still needs, and
`./gradlew :app:exportAppleStrings` writes the `.xcstrings` tables and
`AppSymbol.swift` under `iosApp/Shared/`. `scripts/strings-owners.py --where <id>`
tells where a new id belongs. Keys are never renamed: one key names a string
on every platform.

## Distribution variants

The Android flavor dimension `distribution` has `github` (APK on GitHub
Releases) and `play` (AAB for Google Play), with one `applicationId` and one
signing key. They differ only in `BuildConfig.DOWNLOAD_URL` and the update
action bound in `app/src/github` and `app/src/play` (`UpdateActionModule.kt`).
Debug and `minifiedSmoke` builds call `https://dev.widgets.alllexey.dev`,
release builds `https://widgets.alllexey.dev`. On iOS the Backend origin is
`BACKEND_BASE_URL` in `iosApp/Config/Base.xcconfig` (dev for Debug,
production for Release). iOS has no build variants; `IosClientVersion` reads
the channel from the installed bundle for `X-App-Version` and `X-Client`: `dev`
for a debug binary, `sideload` with an embedded provisioning profile (Xcode, a
free Apple ID, AltStore), `testflight` with a sandbox receipt, else `appstore`.
Both apps also send `X-Client` (ADR 0034) with the OS version and a random
install id kept in `app_preferences` (`InstallIdPreferences`) and replaced on
every session change ([contract](../shared/backend-client/CONTRACT.md#request-headers)).

## Enforced rules

The Konsist suite (`konsist/src/test/`) fails the build when a rule breaks.
Topics: layers (`LayerRulesTest`), feature isolation, modules and KMP source
sets, time, threading, gates (demo and opt-in), storage, DI, presentation,
UI text, Compose, gestures, debug-only code and distribution variants. Add a
rule when a new invariant is agreed instead of relying on review.

## Other repositories

| Repository | Role |
|---|---|
| [itmo-widgets-backend](https://github.com/alllexey-dev/itmo-widgets-backend) | Users, friendships, privacy, sport queues, links, reviews, push delivery. Backend changes first; `:shared:backend-client` mirrors its contract (`scripts/sync-backend-contract.sh`, `shared/backend-client/CONTRACT.md`) |
| [my-itmo-api](https://github.com/alllexey-dev/my-itmo-api) | The only client of official MyITMO and BARS endpoints, a KMP library on Maven Central (`dev.alllexey:my-itmo-api-kmp`). Extend it instead of adding a second client here |
| [itmo-widgets-web](https://github.com/alllexey-dev/itmo-widgets-web) | Web version and landing; reads the design tokens from `shared/designsystem/tokens/itmo-tokens.json` |
