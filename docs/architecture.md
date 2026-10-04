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
5. **Boundaries are held by tests, not modules.** The app is a single Gradle
   module by decision ([0001](decisions/0001-single-module.md)).

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
  navigation/   contracts between features: screen arguments, Fragment results, widget and tile
                class names, shared-link parsing and building
  network/      WidgetsClient, PublicWebClient (cookie-free, for public pages), error mapping
                (an IOException anywhere in the cause chain is AppError.Network), serialization adapters
  notification/ FCM receiver and dispatch, notification channels, the AppNotifier contract
  onboarding/   whether the first-run flow was passed
  presentation/ view-model helpers: one-shot event queue, refresh tracking, busy keys
  qr/           custom QR spoiler images shared by settings and the QR widget
  recordbook/   mark-check and BARS sign-in contracts shared with settings and home
  resources/    subject link contracts and models shared by resources and the recordbook
  result/       AppError, AppResult, LoadState
  reviews/      teacher review and teacher level contracts and models
  schedule/     schedule contracts shared with other features: preferences, widget refresh, lessons
                by subject and by teacher, schedule changes, calendar sync and the `.ics` export
  services/     the Backend opt-in (CustomServicesRepository, BackendGate)
  session/      token store, session repository, current user, device registration, SessionDataCleaner
  settings/     widget appearance settings for screens outside settings (the first-run flow)
  social/       social and people-search contracts
  sport/        sport score and pending booking contracts
  storage/      DataStore wrappers, encrypted token storage, atomic files
  text/         UiText; core/ui `resolve()` resolves UiText arguments first, so a format takes
                localized names
  time/         AcademicTimeProvider, WallClock
  ui/           shared views, texts and UI helpers, the AppNavigator port, widget preview and
                pinning, the spoiler crop screen
  util/         small helpers: colors, schedule formatting, stable order, HTTPS and Telegram links
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

A screen exposes one `StateFlow` of a sealed state (`Loading`, `Content`,
`Empty`, `Error`). One-shot effects such as navigation, snackbars and dialogs go
through a `Channel` exposed as a `Flow` and are collected with the view lifecycle.

Derive state, never strand it: a ViewModel does not write a transient `Loading`
into a state otherwise driven by a repository flow, because `StateFlow` conflates
equal values and a refresh ending on the same value emits nothing. Combine the
repository flow with an explicit in-flight flag (`FriendSelectorViewModel`).

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

Data sources own their dispatcher. Disk and network run under
`Dispatchers.IO`; cold flows that read disk do so on collection, not at call
time. `viewModelScope` is `Main.immediate`, so ViewModel code runs on the main
thread until it suspends.

### Persistence

| Data | Store |
|---|---|
| Settings, flags, one-off values | DataStore (`AppSettingsStorage`, `UtilityStorage`); `demo_active` marks the demo session |
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
repository may combine both. Every call to Backend passes the custom-services
gate inside the repository, so no data source can bypass it. Before that, every
class holding a network client (`ItmoWidgetsApi`, `MyItmo`, `MyItmoApi`, `Bars`,
the `@PublicWebClient` `OkHttpClient`) checks `DemoMode` where it calls the
network: the demo session reads the feature's `data/demo` and refuses writes
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
not `@HiltWorker`: `androidx.hilt`'s processor cannot read Kotlin 2.0 metadata
under kapt, and an entry point needs no custom `WorkManager` configuration.

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
address from BuildConfig`). The suite filters agent worktrees by the project
path, so it also runs inside one.
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
- Fakes over mocks: small in-memory repositories (`FakeSocialRepository`), a
  `Proxy`-based `ItmoWidgetsApi` fake, and `myItmoStub` that answers real
  Retrofit calls with synthetic JSON. Prefer extracting a small collaborator over
  faking six repositories.
- Instrumented tests cover what needs a device: Keystore, file storage, real
  layouts in an isolated debug host (`SportCardsVisualTest`,
  `RecordbookVisualTest`, `SelectionRowsTest`, `SportScoreCollapseTest`).
- Visual tests share `app/src/androidTest/.../testing/`: `Appearances` (the
  light/dark/dynamic/narrow matrix mapped onto each debug host's `Appearance`),
  `Screenshots` (write-only PNGs), `TestUi` (`settle`, `eventually`,
  `awaitFrameCommit`) and `ViewChecks` (`assertTextFits`, `assertTouchTargets`,
  `descendants`). By default a visual test runs one appearance and takes no
  screenshots; the full matrix and the PNGs are opt-in through instrumentation
  arguments, see [Verification matrix](design.md#verification-matrix).

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
6. Anything reaching Backend passes the custom-services gate; anything reaching
   the network passes the demo gate.
