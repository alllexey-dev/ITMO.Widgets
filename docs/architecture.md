# Android architecture

Package layout, layering, the dependency rule and the cross-cutting conventions
new code follows. Everything here is implemented unless marked otherwise. Rules
marked *enforced* are checked by the Konsist suite in
`app/src/test/java/dev/alllexey/itmowidgets/architecture/ArchitectureTest.kt`
and fail the build when broken. Feature-specific behaviour lives in
[`features/`](features/); the visual language in [`design.md`](design.md).

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

Paths are relative to `app/src/main/java/dev/alllexey/itmowidgets/`.

```text
app/            Application, MainActivity, navigation coordinator, notifier, widget coordinator
core/           cross-cutting; knows nothing about features
  debug/        BuildConfig.DEBUG fixtures (provider / controller / store)
  diagnostics/  AppDiagnostics journal, sanitizer, crash handler
  location/     BuildingDirectory (res/raw/itmo_buildings.json), MapDestination geo URIs
  ui/           LessonTypes and LocationTitles shared by schedule and recordbook rows
  friend/       FriendRepository — the schedule picker's narrow view of friends
  home/         HomeCard model and the HomeCardSource contract every feature contributes to
  model/        transport DTOs, UserSummary, UserProfile, RelationshipState, UserData.toUserSummary
  navigation/   contracts between features (FriendSelectionContract, UserScreenArgs, WidgetProviders,
                LessonDetailsArgs, PendingSportDetailsArgs, SettingsScreenArgs)
  onboarding/   OnboardingRepository — whether the first-run flow was passed
  network/      WidgetsClient, error mapping, serialization adapters
  notification/ FCM receiver, WorkManager entry points, dispatcher, AppNotifier contract
  result/       AppError, AppResult
  schedule/     schedule preferences, widget-refresh and SubjectLessonsGateway contracts
  services/     CustomServicesRepository — the Backend opt-in
  settings/     WidgetAppearanceRepository and CustomSpoilerRepository — widget appearance
                for screens outside settings (the first-run flow)
  session/      token store, session repository, current user, device registration
  social/       SocialRepository, PeopleSearchRepository
  sport/        SportScoreRepository, PendingSportBookingsRepository
  storage/      DataStore wrappers, encrypted token storage
  text/         UiText
  time/         AcademicTimeProvider, WallClock
  qr/           CustomSpoilerManager
  ui/           AvatarView, state helpers, AppNavigator port, WidgetPinRequester, the spoiler crop screen,
                the details-sheet header (view_details_header.xml + DetailsHeader.kt), ConditionTone
di/             Hilt modules, one per feature or concern
feature/<name>/ ui | presentation | domain | data
```

Features: `auth`, `debug`, `friendselector`, `home`, `me`, `onboarding`, `qr`,
`recordbook`, `schedule`, `settings`, `social`, `sport`, `update`, `widget`. A
feature does not need all four layers.

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
refusal caused by the opt-in read as an instruction, not a generic error.

### Threading

Data sources own their dispatcher. Disk and network run under
`Dispatchers.IO`; cold flows that read disk do so on collection, not at call
time. `viewModelScope` is `Main.immediate`, so ViewModel code runs on the main
thread until it suspends.

### Persistence

| Data | Store |
|---|---|
| Settings, flags, one-off values | DataStore (`AppSettingsStorage`, `UtilityStorage`) |
| ITMO.ID tokens, BARS session | Encrypted files via Android Keystore |
| Schedule and QR caches | Files under `cacheDir`, observed through flows |

`SharedPreferences` is banned. *Enforced.* Anything caching user-scoped data
implements `SessionDataCleaner`; sign-out and account change invoke every
cleaner, and cache keys do not carry account identity themselves.

### Session and identity

- `SessionTokenStore` stores tokens; `SessionRepository` drives sign-in,
  sign-out and the lifecycle effects (widgets, notifications, device registration).
- `CurrentUserProvider` decodes ISU, name and picture from the local ID token,
  so identity works offline and without the opt-in. The token is not verified;
  the result is for display only.
- `BackendIdentitySync` publishes the ID token to Backend on sign-in and opt-in;
  `BackendDeviceSession` registers the FCM token, remembering the registered
  token and owner.

### The two-backend seam

MyItmoApi and Core's `ItmoWidgetsApi` are touched only inside `data`. A
repository may combine both. Every call to Backend passes the custom-services
gate inside the repository, so no data source can bypass it.

### Time

Feature and storage code take an injected `Clock` or `AcademicTimeProvider`.
*Enforced.* The production clock is `Europe/Moscow`; tests use fixed clocks; a
debug-only academic date override exists.

### Dependency injection

`@Binds` with constructor injection by default, `@Provides` for types the project
does not construct, one module per feature or concern, `@HiltWorker` for
workers, `@IntoSet` multibindings for open sets such as `SessionDataCleaner`,
`FcmPayloadHandler` and `HomeCardSource` (the home feed knows its sources only as
a set; each feature registers its own in its module).

### Navigation

One Activity, two surfaces. `main_nav_graph` holds authentication, the five
bottom destinations (recordbook, schedule, home, sport, profile) and the schedule
friend-picker dialog. Contextual screens (settings, debug tools, subject details,
update offer, friends, people search, public profile, another user's schedule
and sport) live in `overlay_nav_graph` inside a full-screen `AppOverlayHostFragment`
that slides above the unchanged root and bottom bar.

Features open contextual screens through `core/ui/navigation.AppNavigator`,
implemented by `MainActivity` and `MainNavigationCoordinator`. The same port
shows the lesson and pending-sport sheets (`openLessonDetails`,
`openPendingSportDetails`) on the Activity's FragmentManager, so a screen in
another feature can open them without importing `feature/schedule`. Selecting or
reselecting a root tab discards the whole overlay stack; Back pops one overlay
level; rotation restores the current level. Widget and notification intents are
parsed by `MainActivityIntentRouting`, queued until the session is signed in,
saved across recreation and consumed exactly once.

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
`DesignCardResourcesTest` pins the card style family from
[`design.md`](design.md). Add a rule when a new invariant is agreed instead of
relying on review.

## Testing conventions

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

Toward v2.0.1 parity: authentication polish.

Structural debt, in priority order:

1. Stale caches are dropped, not shown: the schedule cache has a 24-hour TTL.
2. Cache write failures are swallowed silently.
3. `MyItmoStorage` does Keystore crypto on the calling thread.
4. Widget QR expiry is passive; the full-screen QR pass actively hides expired codes.
5. The bottom bar does not hide on contextual screens.
6. The profile tab cannot show live privacy values or the BARS session state
   because those live inside other features; a `core` contract is needed.

## Architecture definition of done

1. New code sits in the correct feature and layer; `domain` is free of Android
   and transport types.
2. The Konsist suite passes, extended with any new invariant.
3. Screen state follows the state/event contract with nothing stranded.
4. Errors surface as `AppError`.
5. New persistence follows the DataStore/file rules and user-scoped caches
   implement `SessionDataCleaner`.
6. Anything reaching Backend passes the custom-services gate.
