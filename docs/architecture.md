# Android architecture

Package layout, layering, the dependency rule and the cross-cutting conventions
new code follows. Everything here is implemented unless marked otherwise. Rules
marked *enforced* are checked by the Konsist suite, the `*RulesTest.kt` files in
`app/src/test/java/dev/alllexey/itmowidgets/architecture/`, and fail the build
when broken. Feature-specific behaviour lives in [`features/`](features/); the
visual language in [`design.md`](design.md).

## Principles

1. **Package-by-feature.** A feature owns its `ui`, `presentation`, `domain` and
   `data` together.
2. **The dependency rule points inward.** `ui -> presentation -> domain <- data`.
3. **Domain is DTO-free.** No `api.myitmo.*` and no `core.model.*` transport
   types inside a `domain` package; `UserSummary` is the one shared identity model.
4. **Minimum ceremony.** A use case exists only for real orchestration, never as
   a one-line passthrough.
5. **Boundaries are held by tests and modules.** The Konsist suite holds the
   package rules; the shared modules ([Modules](#modules)) hold the feature and
   platform boundaries ([0018](decisions/0018-module-graph-and-toolchain.md)).

## Package structure

Paths are relative to `app/src/main/java/dev/alllexey/itmowidgets/`. The list
names each package's purpose; the classes of a single feature are listed in
that feature's document.

```text
app/            Application, MainActivity and the app-level coordinators: navigation, notifier,
                widget refresh, the pending route queue (MainRouteQueue) and app shortcuts
core/           cross-cutting contracts and helpers; knows nothing about features
  coroutines/   ApplicationScope (work that outlives a screen) and the injected dispatchers
  debug/        BuildConfig.DEBUG fixtures (provider / controller / store) and debug probes
  demo/         DemoMode, the gate of the demo session, and its shared fictional data
  diagnostics/  AppDiagnostics journal, sanitizer, crash handler
  friend/       the friends contract the schedule picker reads
  home/         HomeCard and the HomeCardSource contract every feature contributes to
  location/     ITMO building directory and map destinations
  model/        transport DTOs and the shared identity models (UserSummary, UserProfile,
                UserData.toUserSummary)
  navigation/   contracts between features: screen arguments, Fragment results, shared-link parsing
                and building; AppEntryIntents holds the main screen's entry actions and its class
                name for widgets, the tile, shortcuts and notifications (core/ui/navigation/
                AppEntryIntentFactory builds the intents without importing the activity)
  network/      WidgetsClient, PublicWebClient (cookie-free, for public pages), error mapping
                (an IOException anywhere in the cause chain is AppError.Network), serialization adapters
  notification/ FCM receiver and dispatch, notification channels, the AppNotifier contract
  onboarding/   whether the first-run flow was passed
  presentation/ the ViewModel kit: EventQueue, RefreshMode, RefreshTracker, BusyKeys, StableOrder
  qr/           custom QR spoiler images shared by settings and the QR widget
  recordbook/   mark-check and BARS sign-in contracts shared with settings and home
  resources/    subject link contracts and models shared by resources and the recordbook
  result/       AppError, AppResult (built with appResultOf {}), LoadState
  reviews/      teacher review and teacher level contracts and models
  schedule/     schedule contracts shared with other features: preferences, widget refresh, lessons
                by subject and by teacher, schedule changes, calendar sync and the `.ics` export
  services/     BackendGate, the one reader of the Backend opt-in (isConnected, mayCallBackend,
                isOptedIn); CustomServicesRepository, the opt-in switch
  session/      token store, session repository, current user, device registration, SessionDataCleaner
  settings/     widget appearance settings for screens outside settings (the first-run flow)
  social/       social and people-search contracts
  sport/        sport score and pending booking contracts
  storage/      per-concern DataStore preference stores, encrypted token storage, atomic files
  text/         UiText; core/ui `resolve()` resolves UiText arguments first, so a format takes
                localized names
  time/         AcademicTimeProvider, WallClock
  ui/           shared views, texts and UI helpers, the AppNavigator port, widget preview and
                pinning, the spoiler crop screen
  util/         HttpsNavigationPolicy and TelegramLinks
  weblogin/     the contract for approving a browser's sign-in to the web version
  work/         rules shared by the background checks: QuietHours, CheckOutcome, outcomeOf, workResultOf
di/             Hilt modules, one per feature or concern
feature/<name>/ ui | presentation | domain | data
```

Features (one per directory of `feature/`): `auth`, `debug`, `friendselector`,
`home`, `me`, `onboarding`, `qr`, `recordbook`, `resources`, `reviews`,
`schedule`, `settings`, `social`, `sport`, `update`, `web`, `weblogin`. A
feature does not need all four layers. The features that read the network keep
their fictional data for the demo session in `data/demo`
([demo session](features/demo.md)). `qr`, `schedule` and `recordbook` also have
`work` for their WorkManager workers, schedulers and entry points. A feature
reaches another only through a `core` contract implemented by the feature that
owns the data, or through `AppNavigator`, so the features never import each
other.

Placement rules:

- A contract shared by two features moves to `core/<topic>`; the implementation
  stays in the feature that owns the data (`CustomServicesRepository` in
  `core/services`, its `Impl` in `feature/settings/data`).
- `*RepositoryImpl` lives in a `data` package and implements the matching
  contract. *Enforced.* Non-repository helpers use the `Default*` prefix.
- Models move to `core` only once two features genuinely share them.

### Modules

`settings.gradle.kts` includes these projects; `build-logic/` is an included
build with the conventions (`itmowidgets.kmp.library`, `itmowidgets.cmp.ui`,
`itmowidgets.testing`, `itmowidgets.strings`, the Android app convention).
Pins and the toolchain live in [ADR 0018](decisions/0018-module-graph-and-toolchain.md)
and the version catalog.

| Module | Convention | Holds | Depends on |
|---|---|---|---|
| `:app` | Android app | what stays Android-only (below), Hilt | every shared module; `:shared:testing` in tests |
| `:shared:core` | kmp-library | the `core/*` contracts and models, common strings | nothing |
| `:shared:designsystem` | cmp-ui | theme, tokens, component kit, icons | `:shared:core` |
| `:shared:testing` | cmp-ui | test kit, fake `Clock`, preview and screenshot harness | nothing (core-free) |
| `:shared:backend-client` | kmp-library | the typed Backend client ([0026](decisions/0026-core-2-backend-client.md)) | nothing |
| `:shared:feature-<x>` | cmp-ui | one feature's `domain`, `presentation`, `ui`, `data` | `:shared:core`, `:shared:designsystem`; `:shared:testing` in `commonTest` |
| `:shared:ios` | Kotlin Multiplatform | the static framework `Shared` for the iOS app | every shared module but `:shared:testing` |
| `:konsist` | Kotlin JVM | the architecture rules over the sources of `app/` and `shared/` | nothing (reads sources) |

The ten feature modules are `:shared:feature-qr`, `:shared:feature-home`,
`:shared:feature-schedule`, `:shared:feature-sport`, `:shared:feature-recordbook`,
`:shared:feature-social` (with `friendselector`), `:shared:feature-settings`,
`:shared:feature-resources`, `:shared:feature-reviews` and
`:shared:feature-account` (`auth`, `onboarding`, `me`, `weblogin`, `web`,
`update`). The `debug` feature stays in `app/`.

- **Targets and source sets.** Shared modules target `android`, `iosArm64`
  and `iosSimulatorArm64`: `commonMain`, `androidMain`, `iosMain`, `commonTest`,
  `androidHostTest` (Robolectric and Roborazzi, [0022](decisions/0022-jvm-screenshot-tests.md))
  and `iosSimulatorArm64Test`. `:shared:core` and the features also compile the
  core test fakes from `shared/core/src/testFixtures/kotlin` into `commonTest`.
- **Allowed dependencies.** Only the edges in the table. A feature never
  depends on another feature; `commonMain` has no `android.*`, `java.*`, `R`,
  Hilt or `javax.inject`. The `Res` class of a cmp-ui module lives in its
  namespace (`dev.alllexey.itmowidgets.shared.<path>`) and is public only in
  `:shared:core` and `:shared:designsystem`.
- **Packages are preserved.** Moving a class into a module is a `git mv`
  without a package change, so the package structure above stays valid in every
  module and the stable identifiers keep their names.
- **What stays in `app/`.** Activities and WebView screens, the RemoteViews
  widgets and `ScheduleWidgetRemoteViewsService`, workers and schedulers, FCM
  (`MyFirebaseMessagingService`, `DefaultFcmTokenSync`, the Android notifier),
  `QrTileService`, shortcuts and channel creation, the Keystore cipher,
  calendar sync, the Play code scanner, the `github` and `play` update actions,
  debug tools, the exported `strings_platform.xml`, and Hilt until it leaves
  ([0019](decisions/0019-koin-per-lane.md)).

## Layers

| Layer | May depend on | Must not touch |
|---|---|---|
| `ui` | own `presentation`, `core/ui`, `core/text` | `data`, `core/network`, `core/storage`, `api.myitmo.*` |
| `presentation` | own `domain`, `core` contracts | Android views, `data`, transport DTOs, `Throwable` |
| `domain` | pure Kotlin, `core/result`, `core/time` | Android, `androidx`, Gson, `api.myitmo.*`, transport DTOs |
| `data` | own `domain`, MyItmoApi, Core API | `ui`, `presentation` |

Across features: `feature/X` never imports `feature/Y`. *Enforced.* Navigation
between features goes through the navigation graphs and `AppNavigator`; shared
contracts go through `core`.

## Cross-cutting conventions

### Screen state and events

A ViewModel exposes one `uiState: StateFlow<S>`, at most one `events: Flow<E>`
backed by an `EventQueue<E>` (`core/presentation`), `refresh(mode: RefreshMode)`
when the screen refreshes, and plain action functions; no other public flow or
value. `S`, `E` and row models live in `<Screen>UiState.kt` beside the
ViewModel, never in the ViewModel file. They hold no `R`, `android.*` or
`Context`: text is `UiText`, failures are `AppError`, and events carry typed
values, never text. `SavedStateHandle` is allowed; only the route or the
Fragment host obtains a ViewModel (decision
[0020](decisions/0020-navigation-3.md)).

`RefreshMode` says who asked: `Silent` (entry, resume; no indicator), `Pull`
(pull-to-refresh) or `Force` (retry, manual reload; may bypass caches).
`RefreshTracker` runs one refresh at a time: other requests join it, a `Pull`
raises `refreshing`, and only `Force` replaces a running non-forced refresh.
`BusyKeys<K>` marks per-row actions busy until they complete, however they
complete. Repositories hand cached collections to the screen as
`core/result/LoadState` (`Loading`, `Disabled`, `Content(value, error)`,
`Error`); a one-shot or replayed single source answers `AppResult`. The kit
never catches.

Derive state, never strand it: `uiState` combines the repository flow with
`RefreshTracker.refreshing` and `BusyKeys.busy`; a ViewModel never writes a
transient `Loading` into a state otherwise driven by a repository flow, because
`StateFlow` conflates equal values and a refresh ending on the same value emits
nothing. One-shot effects (navigation, snackbars, dialogs) go through `events`
and are collected with the view lifecycle; an event sent while no view collects
is delivered once to the next collector. The test-only `ReferenceViewModel`
(`app/src/test/java/dev/alllexey/itmowidgets/core/presentation/ReferenceViewModel.kt`)
is the worked example; `FriendSelectorViewModel` is the first production one.

### Errors

Exceptions become `AppError` at the `data` boundary via `Throwable.toAppError()`;
the UI renders `AppError.messageRes()`. `presentation` never references
`Throwable` or `.message`. *Enforced.* `AppError.CustomServicesDisabled` makes a
refusal caused by the opt-in read as an instruction, not a generic error;
`AppError.Restricted` is a Backend `restricted` answer (a moderation
restriction); `AppError.DemoUnavailable` is a write or an outside page the demo
session does not offer (`Недоступно в демо`). A Backend error body is decoded
once (`backendErrorCode`); server messages are never shown as UI text.

### Threading

Data sources own their dispatcher. Disk and network run on the injected
`AppDispatchers.io` (bound to `Dispatchers.IO` in `di/CoroutinesModule.kt`); no
`Dispatchers.IO` outside `core/coroutines` and `di`. Cold flows that read disk
do so on collection, not at call time. `viewModelScope` is `Main.immediate`, so
ViewModel code runs on the main thread until it suspends.

### Persistence

| Data | Store |
|---|---|
| Settings, flags, one-off values | DataStore through the per-concern `*Preferences` stores in `core/storage` (`WidgetSettingsPreferences`, `MarkSourcePreferences`, `DemoPreferences`, ...) and `UtilityStorage`; `demo_active` marks the demo session |
| ITMO.ID tokens, BARS session | Encrypted files via Android Keystore |
| Schedule and QR caches | Files under `cacheDir`, observed through flows |
| Device-only subject links and the last links answer per subject period | `filesDir/subject_links/cache.json`, atomic writes, excluded from backup and device transfer |
| Finished weeks of the personal schedule for review suggestions | `filesDir/teacher_lessons/weeks.json`, atomic writes, excluded from backup and device transfer |
| Tones of teachers' AI summaries, a day per answer | `filesDir/teacher_levels/levels.json`, atomic writes, excluded from backup and device transfer |
| The last snapshot of the own schedule for the change check and the changes of the last 30 days | `filesDir/schedule_changes/state.json`, one atomic write for both, excluded from backup and device transfer |
| The last My ITMO and BARS mark snapshots of the current half-year and the unread subjects (30 days, at most 100) | `filesDir/marks/state.json`, bound to the owner's ISU, one atomic write for all, excluded from backup and device transfer |
| Connections to public Google Sheets with the last own total of each subject period | `filesDir/sheet_scores/state.json`, format 1, bound to the owner's ISU, atomic writes, excluded from backup and device transfer |
| Calendar synchronization: the switch, the calendar and the ids and content of the app's events in it | `filesDir/calendar_sync/state.json`, format 1, atomic writes, excluded from backup and device transfer |
| The latest exported `.ics` file | `cacheDir/ics/`, shared only through the `FileProvider` `${applicationId}.files` (`res/xml/file_paths.xml`) |

`SharedPreferences` is banned. *Enforced.* Anything caching user-scoped data
implements `SessionDataCleaner`; sign-out and account change invoke every
cleaner, and cache keys do not carry account identity themselves. Repository
implementations that are session cleaners are `@Singleton`, so cleaning and
screen reads target the same cache instance. *Enforced.* Social and review
Backend caches are unavailable while the opt-in is off or unknown, and their
local generations invalidate late publications after opt-out or session clear.

### Session and identity

- `SessionTokenStore` stores tokens; `SessionRepository` drives sign-in,
  sign-out and the lifecycle effects (widgets, notifications, device registration).
- `CurrentUserProvider` decodes ISU, name and picture from the local ID token,
  so identity works offline and without the opt-in. The token is not verified;
  the result is for display only.
- `BackendIdentitySync` publishes the ID token to Backend on sign-in and opt-in;
  `BackendDeviceSession` registers the FCM token, remembering the registered
  token and owner.
- The demo session is `SessionState.SignedIn(user, demo = true)`, started by
  `SessionRepository.startDemo()` and kept in `demo_active`. It has no tokens;
  `DemoCurrentUserProvider` answers the fictional user, and identity sync,
  device registration, FCM and background work never run for it
  ([demo session](features/demo.md)).

### The two-backend seam

MyItmoApi and Core's `ItmoWidgetsApi` are touched only inside `data`. A
repository may combine both. Every call to Backend passes
`BackendGate.mayCallBackend()` inside the repository, so no data source can
bypass it. Before that, every class holding a network client (`ItmoWidgetsApi`,
`MyItmo`, `MyItmoApi`, `Bars`, the `@PublicWebClient` `OkHttpClient`) checks
`DemoMode` where it calls the network: the demo session reads the feature's `data/demo` and refuses writes
with `AppError.DemoUnavailable`. *Enforced* for the constructor parameter; the
demo gate tests cover the behaviour.

### Distribution variants

The flavor dimension `distribution` has `github` and `play`, with one
`applicationId` and one signing key, so either install updates the other.
Each sets `BuildConfig.DOWNLOAD_URL`: the latest GitHub release or the Google
Play card; the invite text of people search shares it. Only «Обновить» of the
[update offer](features/update.md) differs: `feature/update/ui/UpdateAction`
(`start(activity, unsupported, onFailed)`) and `InstallStateWatcher` are bound
by `di/UpdateActionModule` in `app/src/github` (`GithubUpdateAction` opens
`DOWNLOAD_URL` through `ReleasePageOpener`; the watcher never reports) and in
`app/src/play` (`PlayUpdateAction` and `PlayInstallStateWatcher` on Play In-App
Updates, `com.google.android.play:app-update-ktx`, a `playImplementation`
dependency). Nothing else lives in the variant source sets.

### Stable identifiers

Some names outlive an app update: the launcher, SystemUI, WorkManager, placed
widgets, pending intents and files on disk hold them. These are the manifest
component class names, the App Link hosts and paths, the shortcut ids and
actions, the FileProvider authority and its `cacheDir/ics/` path, the worker
class names and unique work names, the `dev.alllexey.itmowidgets.action.*` intent
actions, the notification channel ids and the deletion of
`fcm_default_channel`, the DataStore file, the token files, their Keystore
alias and format prefix, the widget snapshot, the debug override files, the
`filesDir` and `cacheDir` directories with their inner files, the backup
exclusions, the application id and the `github` and `play` flavors.

`StableIdentifiersTest` holds them as literal values and is the source of
truth; this list only summarises it. A class may move between files or modules
as long as its package stays the same. A package or value change fails the
test. The list only grows. Changing an entry needs an ADR (see decisions
[0016](decisions/0016-parity-rewrite.md) and
[0030](decisions/0030-release-lines-and-data-continuity.md)) and a dual read of
the old value, so installed apps keep their work, widgets, settings and
sessions.

### Strings

User-visible text is Russian and lives in
`app/src/main/res/values/strings_<file>.xml`, split by owner, not by prefix.
`strings_platform.xml` holds every id a system surface reaches: the manifest,
`res/xml*`, widget and launcher-preview layouts, widget renderers, notifiers and
push handlers, the QS tile and the device calendar. It also holds every id the
shared builders `core/ui/{ScheduleChangeTexts,LocationTitles,LessonTypes,MarkTexts}.kt`
use. These keys are frozen in `scripts/strings-frozen-keys.txt` (append-only)
and are always exported to Android `res` and the Apple catalog.
`strings_common.xml` holds ids used by two or more modules or by `core/**`.
Every other id lives in its module's `strings_<module>.xml`: `app`, `debug`,
`qr`, `home`, `schedule`, `sport`, `recordbook`, `social`, `settings`,
`resources`, `reviews`, and one file per account package (`auth`,
`onboarding`, `me`, `weblogin`, `web`, `update`).
`scripts/strings-owners.py --where <id|path>` places a new string, and
`--report` lists id, file and users. Only L05 writes `strings_common.xml` and
`strings_platform.xml`; other lanes hand rows in as `## Catalog hand-in`.
`StringOwnershipTest` keeps the split honest.

### Time

Feature and storage code take an injected `Clock` or `AcademicTimeProvider`.
*Enforced.* The production clock is `Europe/Moscow`; tests use fixed clocks; a
debug-only academic date override exists.

### Dependency injection

`@Binds` with constructor injection by default, `@Provides` for types the project
does not construct, one module per feature or concern, `@IntoSet` multibindings
for open sets such as `SessionDataCleaner`, `FcmPayloadHandler` and
`HomeCardSource` (the home feed knows its sources only as a set; each feature
registers its own in its module).

Workers are built by WorkManager and take their dependencies through an
`@EntryPoint` (`QrWidgetEntryPoint`, `ScheduleWidgetEntryPoint`,
`ScheduleChangesEntryPoint`, `CalendarSyncEntryPoint`, `MarksEntryPoint`, `BarsCookieProbeEntryPoint`,
the FCM workers in `core/notification/FcmWork.kt`),
not `@HiltWorker`: an entry point needs no `androidx.hilt` processor next to
Hilt's own (which runs on KSP) and no custom `WorkManager` configuration.

### Navigation

One Activity, two surfaces. `main_nav_graph` holds authentication, the five
bottom destinations (recordbook, schedule, home, sport, profile) and the schedule
friend-picker dialog. Contextual screens (settings, debug tools, subject details,
update offer, friends, people search, person profile, another user's schedule
and sport) live in `overlay_nav_graph` inside a full-screen `AppOverlayHostFragment`
that slides above the unchanged root and bottom bar.

Features open contextual screens through `core/ui/navigation.AppNavigator`,
implemented by `MainActivity` and `MainNavigationCoordinator`. The same port
shows the lesson and pending-sport sheets (`openLessonDetails`,
`openPendingSportDetails`) and the subject link sheets (`openSubjectLinks`,
`openLinkEditor`, `openLinkActions`), the own sheet total (`openSheetScores`,
with `SheetScoresArgs`), the web sign-in sheet (`openWebLogin`)
and the review editor and report dialog (`openReviewEditor`,
`openReviewReport`, with `TeacherReviewArgs`) on the Activity's
FragmentManager, so a screen in another feature can open them without
importing `feature/schedule`, `feature/resources`, `feature/weblogin` or
`feature/reviews`. Selecting or
reselecting a root tab discards the whole overlay stack; Back pops one overlay
level; rotation restores the current level.

Every root keeps its own state: `NavigationUI` selects a tab with
`popUpTo(start, saveState = true)` and `restoreState = true`, so scroll
positions and sub-tabs survive switches, recreation and process death. Home is
the graph's start; when the first-run flow ends, `MainActivity` makes home the
start before leaving `onboarding`, otherwise tabs would pile up in the stack.
Back from another root leads home, Back from home leaves the app. Reselecting
the current tab closes overlays and the friend-picker dialog and keeps the
scroll. The bottom bar stays under a full-screen overlay instead of hiding:
the overlay container is above it (`translationZ`), opaque and clickable, and
the root and the bar are hidden from TalkBack, so the root never changes size.

Widget, notification, tile, shortcut and App Link intents are parsed by
`MainActivityIntentRouting` and wait in `MainRouteQueue` until the session is
signed in and the first-run flow is passed. The queue keeps one route (a newer
one replaces it), hands it out once its root is selected, and is saved across
recreation; a route runs exactly once. An intent carrying
`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` (Recents replaying the original intent on
Android 8–11) opens no route.

| Action | Root | Above it | Shortcut reported |
|---|---|---|---|
| `ACTION_OPEN_QR_PASS` (tile, shortcut) | home | `QR_PASS` | `qr_pass` |
| `ACTION_OPEN_TODAY` (shortcut) | schedule, scrolled to today (`ScheduleTodayRequest`) | — | `today` |
| `ACTION_OPEN_SCHEDULE` (widget) | schedule, at the reading place | — | — |
| `ACTION_OPEN_SPORT` (notification) | sport | — | — |
| `ACTION_OPEN_USER_PROFILE` (friendship) | profile | `USER_PROFILE` | — |
| `ACTION_OPEN_SCHEDULE_CHANGES` | schedule | `SCHEDULE_CHANGES` | — |
| `ACTION_OPEN_RECORDBOOK`, `ACTION_OPEN_RECORDBOOK_SUBJECT` | recordbook | `RECORDBOOK_SUBJECT` with valid arguments | — |
| `ACTION_OPEN_BARS_LOGIN` | recordbook | `BarsLoginActivity` | — |
| `ACTION_VIEW` `/u/{isu}` (App Link) | profile | `USER_PROFILE` | — |
| `ACTION_VIEW` `/sport/{id}`, `/sport/p/{id}` | sport, `Запись` (`SportLessonRequest`) | the lesson card or `Занятие недоступно` | — |
| `ACTION_VIEW`, malformed link | home | `Ссылка не открывается` | — |

Back after a route: an overlay first (QR pass, profile, changes, subject page,
the BARS sign-in), then home, then out of the app. On Android 12+ the root
launcher activity only moves the task to the background, below it
`MainActivity` finishes; reopening from Recents does not repeat the route.
`ScheduleTodayRequest` is a Fragment result on the Activity's FragmentManager:
it is kept, also in the saved state, until the root schedule is `STARTED`.
`SportLessonRequest` works the same way for the root sport screen, which hands
it to its sign page. Links and sharing: [features/app-links.md](features/app-links.md).

Every Fragment opens a person profile with `Fragment.openUserProfile(isu)` in
`core/ui/navigation/AppNavigator.kt`. A sheet dismisses before invoking it.
`UserScreenArgs.profileIsu(Long?)` accepts only `1..Int.MAX_VALUE`; absent or
out-of-range teacher IDs leave rows informational. The friendship notification
entry point stays in `MainActivity` and uses `navigation.openScreen` directly.

### Settings as data

Settings screens are declarative: a ViewModel emits `List<SettingSection>` of
`Toggle | Choice | Navigation | Action | Info` items and `SettingsRenderer`
inflates them. Each page is the same `SettingsFragment` with a `settings_page`
argument. The renderer rebuilds views only when the set of rows changes and
skips assigning a switch value that already matches. A row shows a summary value
only when the section collapses into one state. See [`settings.md`](settings.md)
for the contract itself.

## Boundary enforcement

The Konsist suite encodes: the layer table above; no feature-to-feature imports;
`core` imports no feature; the legacy global `data`/`domain` packages stay empty;
ViewModels live in `presentation`; `*RepositoryImpl` lives in `data`; every
Fragment with a nullable binding clears it in `onDestroyView()`; no direct
`now()` calls under `feature.*` or `core.storage`; no `SharedPreferences`.
Core library wire types from `core.model.reviews`, `core.model.resources`,
`core.model.social` and `core.model.fcm` cannot be imported by `ui` or
`presentation`; data mappers alias those imports when names overlap with local
models. Session-cleaning `*RepositoryImpl` classes must be `@Singleton`. Every
class outside `core/network` whose constructor takes a network client also
takes `DemoMode` (`network clients are gated by demo mode`). The debug
override stores, the debug refresh-token controller and the debug tools screen
check `BuildConfig.DEBUG` themselves (`debug code is gated`); no production
source names the GitHub releases page, which only the `github` variant's
`BuildConfig.DOWNLOAD_URL` holds (`distribution variants take the download
address from BuildConfig`). The rules are split by topic:
`LayerRulesTest`, `FeatureIsolationRulesTest`, `TimeRulesTest`,
`GateRulesTest`, `StorageRulesTest`, `DebugRulesTest`,
`DistributionRulesTest` and `UiRulesTest`, over the shared `ArchitectureScope`,
whose scopes must stay above non-empty floors so a rule cannot pass on nothing.
The scope filters agent worktrees by the project path, so the suite also runs
inside one.
`DesignCardResourcesTest` pins the card style family from
[`design.md`](design.md). Add a rule when a new invariant is agreed instead of
relying on review.

## Testing conventions

- The app has two distribution variants (`flavorDimensions` `distribution`):
  `github` and `play`, with the same `applicationId` and key. Every task name
  carries the variant: `testGithubDebugUnitTest`, `testPlayDebugUnitTest`,
  `lintGithubDebug`, `connectedGithubDebugAndroidTest`; the debug APK is
  `app/build/outputs/apk/github/debug/app-github-debug.apk`. Variant-only code
  lives in `app/src/{github,play}`, its tests in `app/src/testGithub` and
  `app/src/androidTestPlay` (Play's `FakeAppUpdateManager` needs real
  `PendingIntent`s, so the Play update tests run on a device).
  `scripts/check-play-policy.sh` checks the release manifests, `targetSdk`, the
  16 KB alignment and that the `play` bundle carries no GitHub update link.
- JVM unit tests are the default; `unitTests.isReturnDefaultValues = true` lets
  code that logs through `android.util.Log` run.
- ViewModels are created inside the test body after `MainDispatcherRule` is
  installed, never in a field.
- Fakes over mocks: small in-memory fakes, a `Proxy`-based `ItmoWidgetsApi`
  fake per endpoint group, and `myItmoStub` that answers real Retrofit calls
  with synthetic JSON. A fake of a `core` contract lives once in
  `app/src/test/java/dev/alllexey/itmowidgets/core/testing/`
  (`FakeSocialRepository`, `FakeCustomServicesRepository`,
  `FixedAcademicTime`, ...); a fake of a feature contract lives in that
  feature's `*Fakes.kt`. No test imports another feature's test code. Two fakes
  of one contract differ in purpose and say so in their names.
  `SportCardFixtures` lives in the debug source set, shared by debug unit
  tests, instrumented tests and the debug hosts. Prefer extracting a small
  collaborator over faking six repositories.
- Instrumented tests cover what needs a device: Keystore, file storage, real
  layouts in an isolated debug host (`SportDetailsSheetVisualTest`,
  `RecordbookVisualTest`, `SelectionRowsTest`, `SportScoreCollapseTest`).
- One instrumented run is one app process, so Hilt singletons outlive a test
  class. `SessionRepositoryImpl.initialize()` reads the token store once and
  returns early afterwards, so a token file written directly is invisible to a
  session another class already started: sign in through
  `TestSession.seedActiveSession()` (the real `completeItmoIdLogin`) and sign
  out afterwards. A class that fails in a full run is re-run alone before the
  change is blamed.
- Visual tests share `app/src/androidTest/java/dev/alllexey/itmowidgets/testing/`:
  `Appearances` (the light/dark/dynamic/narrow matrix; `Appearances.Spec.toPreview()`
  builds the one `core/debug/PreviewAppearance` every debug host takes, and each
  feature's mappers live in `testing/<Feature>Appearances.kt`),
  `Screenshots` (write-only PNGs), `TestUi` (`settle`, `eventually`,
  `awaitFrameCommit`) and `ViewChecks` (`assertTextFits`, `assertTouchTargets`,
  `descendants`). By default a visual test runs one appearance and takes no
  screenshots; the full matrix and the PNGs are opt-in through instrumentation
  arguments, see [Running the visual tests](design.md#running-the-visual-tests). Debug
  hosts that implement `AppNavigator` delegate to the debug `NoOpAppNavigator`.
- `UpgradeFrom22Test` (androidTest, pool emulator) reads the 2.2 data directory
  captured in `app/src/androidTest/assets/upgrade-2.2/` through head stores; a
  change that moves or reformats persisted data updates its checker in
  `upgrade/stores/`, never the assets.

## Known gaps

Structural debt, in priority order:

1. Stale caches are dropped, not shown: the schedule cache has a 24-hour TTL.
2. Cache write failures are swallowed silently.
3. `MyItmoStorage` does Keystore crypto on the calling thread.
4. Widget QR expiry is passive; the full-screen QR pass actively hides expired codes.
5. The profile tab cannot show live privacy values or the BARS session state
   because those live inside other features; a `core` contract is needed.

## Architecture definition of done

1. New code sits in the correct feature and layer; `domain` is free of Android
   and transport types.
2. The Konsist suite passes, extended with any new invariant.
3. Screen state follows the state/event contract with nothing stranded.
4. Errors surface as `AppError`.
5. New persistence follows the DataStore/file rules and user-scoped caches
   implement `SessionDataCleaner`.
6. Anything reaching Backend passes `BackendGate.mayCallBackend()`; anything
   reaching the network passes the demo gate.
