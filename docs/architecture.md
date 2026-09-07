# ITMO.Widgets Android architecture

## Purpose and precedence

This document describes how the ITMO.Widgets Android application is structured:
package layout, layering, the dependency rule, cross-cutting technical decisions,
and the conventions that new code is expected to follow.

It is scoped to *structure*. It deliberately does not repeat what already lives in
`AGENTS.md`:

- product/release contract, system boundaries, and source-of-truth rules;
- UI/UX design language, Material 3/You, spacing, motion, accessibility;
- data privacy, security, secrets, and server-side authorization;
- build, verification, server environments, and deployment.

Read it together with `AGENTS.md` (engineering constraints and design language) and
`vibe/itmo-widgets-social-learning-plan.md` (delivery order). Where the two overlap
on dependency direction they must stay consistent; this file is the detailed version.
The user-facing v2.1 settings surface and product invariants are defined separately
in `docs/settings.md`.

Unless stated otherwise, everything below is **implemented**, not planned. Rules
marked as enforced are checked by `ArchitectureTest` and fail the build when broken.

### The single-module decision

The application is a single Gradle module (`:app`). Multi-module is a build/scale
tool, not an architectural principle, and its one relevant benefit here —
compile-time enforcement of layer boundaries — is achieved far more cheaply by the
Konsist suite. Reuse across applications is already served by the separate
`itmo-widgets-core` and `MyItmoApi` repositories.

Revisit only on a concrete trigger: painful incremental build times, a second
developer, or a second in-repo reuse site.

## Guiding principles

1. **Package-by-feature, all the way down.** A feature owns its `ui`,
   `presentation`, `domain`, and `data` together.
2. **The dependency rule points inward.** `ui -> presentation -> domain <- data`.
3. **Domain is DTO-free.** No `api.myitmo.*` and no transport models from
   `core.model.*` inside a `domain` package.
4. **Minimum ceremony.** A `UseCase` is introduced only for real orchestration or
   reused logic, never as a one-line passthrough.
5. **Boundaries are held by a test, not by a module.**

## Package structure

Paths are relative to `app/src/main/java/dev/alllexey/itmowidgets/`.

```text
app/                    Application, MainActivity, NavHost, bottom-nav wiring
core/                   cross-cutting; knows nothing about features
  debug/                BuildConfig.DEBUG fixtures (provider/controller/store)
  friend/               FriendRepository — contract shared by several features
  model/                transport DTOs + UserSummary (the one shared identity model)
  navigation/           navigation contracts between features
  network/              WidgetsClient, AppErrorMapper, serialization adapters
  result/               AppError, AppResult
  schedule/             Schedule refresh and widget-update cross-feature contracts
  services/             CustomServicesRepository — the backend opt-in
  session/              SessionTokenStore, SessionDataCleaner, CurrentUserProvider,
                        BackendIdentitySync
  storage/              DataStore wrappers, encrypted token storage, AppSettingsStorage
  text/                 UiText
  time/                 AcademicTimeProvider, WallClock
  ui/                   AvatarView, CircularProgressBar, AppErrorText, UiTextResolver
  util/                 small shared helpers
di/                     Hilt modules, one per feature or concern
feature/
  <name>/
    ui/                 Fragment, adapters, ViewBinding
    presentation/       ViewModel, UI state, one-shot events
    domain/             models, repository interfaces, use-cases
    data/               repository implementations, data sources, mappers
```

Current features: `debug`, `friendselector`, `home`, `me`, `qr`, `recordbook`,
`schedule`, `settings`, `sport`, `widget`. Not every feature needs all four layers —
`home` is presently UI only, and `me` has no data layer of its own.

Placement rules:

- **Shared contracts go to `core`, not to another feature.** When two features need
  the same repository, the interface moves into a `core` package
  (`core/friend`, `core/schedule`, `core/services`) and the implementation stays in
  the feature that owns the data. This is why `CustomServicesRepository` lives in
  `core/services` while `CustomServicesRepositoryImpl` lives in
  `feature/settings/data`.
- **`*RepositoryImpl` must live in a `data` package** and implement the matching
  contract — enforced. Helper implementations that are not repositories use the
  `Default*` prefix instead (`DefaultBackendIdentitySync`).
- **Shared models** move into `core` only once two or more features genuinely share
  them. `UserSummary` is the only transport model `domain` may import.

## Layers and the dependency rule

| Layer | Lives in | May depend on | Must not touch |
|---|---|---|---|
| `ui` | `feature/*/ui` | own `presentation`, `core/ui`, `core/text` | `data`, `core/network`, `core/storage`, `api.myitmo.*` |
| `presentation` | `feature/*/presentation` | own `domain`, `core` contracts | Android views, `data`, transport DTOs, raw `Throwable` |
| `domain` | `feature/*/domain` | pure Kotlin, `core/result`, `core/time` | Android, `androidx`, Gson, `api.myitmo.*`, transport DTOs |
| `data` | `feature/*/data`, `core/network`, `core/storage` | own `domain`, `MyItmoApi`, Core API | `ui`, `presentation` |

Two hard rules on top of the table:

- **Inside a feature:** `ui -> presentation -> domain <- data`.
- **Across features:** `feature/X` must not import `feature/Y`. Cross-feature
  navigation goes through the Nav graph; shared contracts go through `core`.

## Cross-cutting decisions

### UI state and events

Every screen exposes one state through a `StateFlow`, modelled as a sealed interface
when the screen has distinct modes:

```kotlin
sealed interface ScheduleUiState {
    data class Loading(val selectedUser: SelectedUser?) : ScheduleUiState
    data class Content(val schedule: List<DaySchedule>, ...) : ScheduleUiState
    data class Empty(val selectedUser: SelectedUser?) : ScheduleUiState
    data class Error(val error: AppError, ...) : ScheduleUiState
}
```

One-shot effects — navigation, toasts, dialogs — are **not** part of the state. They
go through a `Channel` exposed as a `Flow` and are collected with the view lifecycle.

**Derive state, never strand it.** A ViewModel must not write a transient value such
as `Loading` into a state that is otherwise driven by a repository flow: `StateFlow`
conflates equal values, so a refresh ending on the value the repository already held
emits nothing and the screen stays stuck. Combine the repository flow with an
explicit in-flight flag instead — see `FriendSelectorViewModel`.

### Errors

Exceptions are mapped to `AppError` at the `data` boundary by
`Throwable.toAppError()`; the UI renders them through `AppError.messageRes()`. Raw
exception messages must never reach the UI — `presentation` is forbidden from
referencing `Throwable` or `.message` at all, and that is enforced.

`AppError.CustomServicesDisabled` exists so that a refusal caused by the backend
opt-in reads as an actionable instruction rather than a generic "forbidden".

### Threading

Data sources own their dispatcher; callers must not have to know. Disk and network
work runs under `withContext(Dispatchers.IO)`, and cold flows that read the disk are
wrapped so that the read happens on collection, not at call time:

```kotlin
return flow { emitAll(combineFlows(keys).map { ... }) }.flowOn(Dispatchers.IO)
```

`viewModelScope` runs on `Dispatchers.Main.immediate`, so anything a ViewModel calls
directly executes on the main thread until it suspends.

### Persistence

| Data | Store |
|---|---|
| Settings, flags, one-off values | DataStore (`AppSettingsStorage`, `UtilityStorage`) |
| ITMO.ID tokens | Encrypted file via Android Keystore (`MyItmoStorage`) |
| Schedule and QR caches | Files under `cacheDir`, observed through flows |

`SharedPreferences` and `PreferenceManager` are banned from production code —
enforced. This is also why settings screens are plain Fragments rather than
`PreferenceFragmentCompat`.

Anything that caches user-scoped data implements `SessionDataCleaner`, so a session
change can drop it. **Any future sign-out or account-switch flow must invoke the
cleaners**; cache keys do not carry account identity on their own.

### Session and identity

- `SessionTokenStore` — token storage contract.
- `CurrentUserProvider` — decodes `isu`, `name` and `picture` from the locally
  stored ITMO.ID token. Identity therefore works offline and without the backend
  opt-in. The token is not verified: it is our own token and the result is used only
  for display, never for authorization.
- `BackendIdentitySync` — publishes the ID token to Backend, which derives the
  profile other users see from its claims.

### The two-backend seam

`MyItmoApi` and Core's `ItmoWidgetsApi` are touched **only** inside a feature's
`data` layer. A repository may combine both sources; `domain` stays source-agnostic.

Everything that reaches the project backend is gated on the custom-services opt-in,
and the gate belongs in the repository — the layer that chooses the source — so no
data source can be reached without passing it.

### Dependency injection

`@Binds` with constructor injection is the default; `@Provides` is for types the
project does not construct. Modules are split per feature or concern rather than one
growing module. Workers use `@HiltWorker`. The backend base URL comes from
`BuildConfig.WIDGETS_BASE_URL`, set per build type.

### Navigation

A single Nav graph. Root destinations keep their own back stacks; contextual screens
(settings, debug tools, subject, and later user/teacher/review) are ordinary
destinations with an in-layout back button, because the application has no app bar.

`ScheduleFragment` snapshots its list position before destroying its view. State
saving must also work for viewless back-stack Fragments; pending scroll restoration
waits for list data, and asynchronous adapter callbacks cannot touch an old view.

## Settings as data

Settings screens are **declarative**: a ViewModel emits `List<SettingSection>` built
from `SettingItem.Toggle | Choice | Navigation | Action | Info`, and `SettingsRenderer` inflates it.
Adding a section means writing a list, not a Fragment and a layout. Screens therefore
look identical by construction and can be asserted in plain JVM tests.

`SettingsPage` is a typed presentation destination. Each page uses the same
`SettingsFragment` with a `settings_page` navigation argument, read by its own
ViewModel through `SavedStateHandle`. The root lists categories; only the privacy
page requests remote sharing values. Section footers hold shared explanations,
and dynamic rows do not save view-hierarchy values over repository state.
The privacy page holds its bounded loading area for at least 300 ms on entry and
retry; network work and the minimum duration run concurrently in the ViewModel.
Only final content or error rows replace the loader, avoiding a transient set of
unknown switches. Disabling services bypasses the delay immediately.
`localSettingsLoaded` is independent of privacy refresh. Settings postpone their
enter transition until persisted sections and any initial QR image are ready,
then start on pre-draw; offline pages never show a progress indicator. Shared-axis
transitions use explicit forward/backward directions and a 220 ms duration.
Profile transition grouping lives in its XML root, not in the outgoing click
handler: Navigation recreates that view on return. Cached profile identity is
bound before transition capture, and reenter motion is configured in `onCreate`.

Widget settings values and `WidgetPreviewSettings` live in `core/settings`.
`SettingsViewModel.previewSettings` emits only persisted values for QR/schedule
pages. `core/ui/widget.WidgetPreviewFactory` is the UI contract; app-layer
`DefaultWidgetPreviewFactory` composes the feature-owned implementations without
cross-feature imports. Previews are scoped to the Fragment view and release bitmap
work/animations on teardown. The schedule preview saves its page and example time
through the Fragment's saved state.

The app factory warms QR sample images while the root settings page is open.
`QrPreviewBitmapCache` retains at most four bitmap pairs, keyed by resolved palette,
custom-spoiler revision, and invalidation generation. Disk reads and rendering stay
off the main thread; the cache retains no Activity or view. Atomic image save/reset
increments the shared image-store revision, so stale custom images are not reused.

Schedule preview data is deterministic and goes through `ScheduleWidgetSelector`,
`ScheduleWidgetRenderer`, and `ScheduleListRowRenderer`, shared with the real
widgets. QR uses its production bitmap/animation renderers with a sample payload.
Previews never register an AppWidget host, create PendingIntents, request network
data, write widget snapshots, or display a real QR pass.

After an actual sport booking succeeds, `SportBookingDelegate` requests a schedule
widget update through `core/schedule.ScheduleWidgetRefreshRequester` before awaiting
screen refreshes. The app-level `WidgetRefreshCoordinator` enqueues forced schedule
work, bypassing the routine throttle without touching QR widgets. Failed actions
and queue subscriptions do not signal an actual schedule change; the worker loads
fresh schedule data and updates both installed schedule-widget types.

`CustomSpoilerViewModel` owns local image save/reset operations and widget refresh.
`CustomSpoilerRepository` keeps URI strings at the domain boundary; its data
implementation performs disk work on IO through the shared `core/qr` image store.
The picker and crop UI return only a selected URI, cancellation, or a safe failure;
view recreation does not cancel an in-flight save.

Two conventions matter here:

- **A summary value on a row is only honest when the section collapses into a single
  state** (services on/off, signed-in account). Sections made of independent options
  use a static description of what is inside; showing one option out of three would
  misrepresent the section.
- **The renderer rebuilds views only when the set of rows changes**, and skips
  assigning a switch value that already matches. Re-inflating on every emission
  destroys the control the user just touched and swallows its animation.

## Boundary enforcement

`app/src/test/java/.../architecture/ArchitectureTest.kt` is a Konsist suite of 13
rules run as ordinary unit tests. It encodes:

- `domain` imports no Android, `androidx`, Gson, `api.myitmo.*`, `core.network`,
  `core.storage`, `core.ui`, or transport models except `UserSummary`;
- `ui` imports no `api.myitmo.*`, `core.network`, `core.storage`, or `data`;
- `presentation` imports none of the above and never references `Throwable`/`.message`;
- `data` imports no `ui` or `presentation`;
- no feature imports another feature; `core` imports no feature;
- the legacy global `data`/`domain` packages stay empty;
- ViewModels live in `presentation`; `*RepositoryImpl` lives in `data` and implements
  the matching contract;
- every Fragment with a nullable binding clears it in `onDestroyView()`;
- no `LocalDate.now()`, `OffsetDateTime.now()`, `Calendar.getInstance()` or
  `System.currentTimeMillis()` under `feature.*` or `core.storage`;
- no `SharedPreferences`/`PreferenceManager` in production code, and the settings,
  utility and friend-history stores use `DataStore`.

Extend this suite when a new invariant is agreed, rather than relying on review.

## Testing conventions

- JVM unit tests are the default. `unitTests.isReturnDefaultValues = true` lets
  classes that log through `android.util.Log` be tested directly.
- Time-dependent logic takes an injected `Clock` or `AcademicTimeProvider`; tests use
  a fixed clock.
- ViewModels are created **inside** the test body, not in a field: `MainDispatcherRule`
  installs the test dispatcher when the test starts, and a ViewModel that collects a
  flow in `init` will otherwise touch an uninstalled main dispatcher.
- Prefer extracting a small collaborator over faking six repositories. `InFlightLessons`
  exists so the double-tap guard is testable without constructing `SportSignViewModel`.
- Instrumented tests are reserved for what genuinely needs a device: Keystore and
  file-backed storage.

## Known gaps and debt

Functional gaps toward v2.0.1 parity: authentication, onboarding, FCM handling, the
three widgets with their workers and boot receiver, the QR screen, and error
diagnostics. The home feed is still a stub.

Structural debt, in rough priority order:

1. **Stale caches are dropped, not shown.** The schedule cache has a 24-hour TTL and
   filters expired entries out, so going offline for a day yields an empty schedule
   instead of stale data.
2. **Cache failures are silent.** Schedule and QR disk writes swallow exceptions
   without logging, so a full or corrupted cache is invisible.
3. **Token storage does crypto on the calling thread.** `MyItmoStorage` decrypts
   lazily under `@Synchronized`; a slow Keystore call blocks whichever thread asks.
4. **QR expiry is passive.** An expired entry simply stops being emitted, so a
   displayed code would outlive its TTL. Dormant until the widgets land.
5. **The bottom bar does not hide** on contextual destinations, contrary to the
   design language.
6. **An empty friend list from the backend is reported as an error** rather than an
   empty state; the Core contract should be confirmed.

## Architecture definition of done

Extends the `AGENTS.md` "Definition of done". A structural change is complete when:

1. New code sits in the correct feature and layer.
2. The touched feature's `domain` is free of Android and transport DTOs.
3. The Konsist suite passes, with any new invariant added to it.
4. Screen state uses the state/event contract, and no transient value can be stranded.
5. Errors surface as `AppError`, never as raw exception text.
6. New persistence follows the DataStore/file rules, and user-scoped caches implement
   `SessionDataCleaner`.
7. Anything reaching the project backend passes the custom-services gate.
