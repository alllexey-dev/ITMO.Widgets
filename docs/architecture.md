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

The [design guide](design.md) consolidates the target component language,
intentional exceptions, and open UI consistency work; it does not imply those
changes are already implemented.

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
  sport/                SportScoreRepository, shared scores and own pending-booking projection
  services/             CustomServicesRepository — the backend opt-in
  session/              SessionTokenStore, SessionDataCleaner, CurrentUserProvider,
                        BackendIdentitySync
  storage/              DataStore wrappers, encrypted token storage, AppSettingsStorage
  text/                 UiText
  time/                 AcademicTimeProvider, WallClock
  ui/                   AvatarView, CircularProgressBar, AppErrorText, UiTextResolver
    navigation/         AppNavigator UI port for contextual screens
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

### Recordbook and sport

Recordbook reads official grades independently of sport progress. The shared
`core/sport/SportScoreRepository` returns period-specific score summaries;
`feature/sport/data/repository/SportScoreRepositoryImpl` owns the MyItmoApi calls and supplies
attendance history to the sport feature as well. Both screens use
`SportScoreSummary` for the bonus cap and total, but sports progress never changes
an official academic grade. PE rings display that period's sports progress even
before the official credit, while their grade marker and the passed-subject count
remain based on the recordbook. The PE detail card separates attendance/bonus
sectors from the official result and keeps API errors distinct from a zero score.
The resolver accepts only a unique year/season match,
not numeric equality between unrelated semester identifiers.

The compact root and scrollable subject details retain content during refresh and
cancel superseded period requests. Details reload the official subject rather than
trusting a navigation snapshot, preserve control hierarchy, and respect `have_tree`.
API observations and limitations are recorded in `docs/recordbook-api.md`.

### Pending sport in the schedule screen

The optional `Автозапись на спорт` switch is a persisted local preference, disabled
by default. `SchedulePreferencesRepository` is shared through `core/schedule`;
its settings-owned implementation forwards DataStore changes without enabling
community services. The same setting applies to the own in-app schedule and both
schedule widget types, not to friends' schedules, exported data or the official
schedule cache.

`PendingSportBookingsRepository` in `core/sport` exposes a read-only projection of
active own queues. Its sport-owned implementation combines official chosen-sport
bookings with the existing queue stream; friend enrichment is not a dependency.
Cancelled, terminal, already-started and already-signed entries are excluded.
Multiple queues resolving to the same sport lesson produce one pending row.
Unpublished predictions use prototype dates plus two weeks; a bound real lesson
uses its actual dates without a second shift. Confirmation uses sport lesson IDs,
not an assumed equality with academic `pairId` or a name/time heuristic.

Schedule observes this source only when the setting is enabled and both its
selected and loaded schedule belong to self. Backend opt-in remains an independent
data-layer gate. Both sport source caches participate in session cleanup, and
results started before cleanup cannot repopulate them afterward.

`ScheduleUiState.Content.schedule` remains official data. `ScheduleDisplayDay`
adds pending rows in the loaded date range, including dates with no official
lessons. The adapter compares the complete display day, so live additions and
cancellations render without clearing the cache or resetting scroll position.
After the first successful academic load, pending-only content also stays visible
during refresh and pagination. Initial academic loading/failure remains explicit;
later refresh failures use a snackbar while valid content remains on screen.
Pending rows are explicitly labelled as waiting or predicted, never styled as a
current confirmed lesson, and do not increase the confirmed lesson count. Optional
source loading/errors do not replace the academic screen. An error or successful
empty snapshot removes pending rows rather than keeping a possibly cancelled or
already-confirmed queue visible. The source refreshes independently of academic
refresh and receives subsequent queue/booking changes from the Sport screen.

No Backend/Core/MyItmoApi API or dependency version changes are required.

### Sport session cards and details

The My Sport and registration lists share the same restrained card language and
friend preview. Cards keep a compact title/time header, fixed-size metadata icons
and a small outlined 48 dp action target (36 dp visual button). Start time is the
scanning anchor and is the only card metadata drawn on `colorOnSurface`; the class
kind is a filled chip on `colorSurfaceContainerHighest` so it stays legible against
the card's own surface. A full-width 6 dp occupancy bar separates session
information from booking controls, and its label carries the same occupancy tone as
the bar. The
details use a 100 dp occupied-capacity ring beside a free-place stat. Each number is
stated once: the ring owns occupied and limit, the column beside it owns the free
count (`нет` when full), so no figure is repeated in a derived form. Bookings
retain their date tile beside the weekday, time and registration status, with the
tile centred against that column. A wider gap below it separates *when + status* from
the teacher/location rail, so the two groups read as groups. Weekday and month names
are capitalised on the way out: `DateTimeFormatter` renders Russian ones in lower
case, so every sport date string goes through the helpers in `SportCardPresentation`
rather than being formatted at the call site. Full names and queue history belong in
the details sheet.
Sport registration status and capacity are derived by the pure
presentation helpers in `feature/sport/presentation/common`. Capacity bars
represent **occupied** places, matching their label; invalid/unknown capacity and
predicted lessons never appear as zero-capacity real sessions.

The contextual bottom sheet is a separate layout, not an included list item. It
retains the full section title and separates date/duration, teacher/location,
registration, queue metrics/history, notices, comments and friends. Session facts
(when, who, where) share one icon rail directly under the title; registration state,
conditions, comment and friends follow as divider-separated sections. Rail rows carry
no visible category label — the icon already states it, and the label survives as the
value's `contentDescription`. Rail icons are a fixed dp size while the text line box
grows with the font scale, so `alignRailIcon` recentres them at bind time instead of
using a constant margin. Rail values are `colorOnSurfaceVariant`, matching the list
cards: the section title and the start time are the only text on `colorOnSurface`,
so the facts read as a hierarchy instead of a wall of same-sized lines. A wider gap,
not a divider, separates *when* from *who/where*. Queue history is a label/value
table without icons. It renders the
selected domain snapshot; opening it does not make another booking request.
Booking-only responses do not contain capacity or comments, so those fields are
not invented. Queue notification counts describe requests, not guaranteed
successful booking attempts. All timestamps use `AcademicTimeProvider.zoneId`.
Location hand-off uses a generic `geo:` intent; booking/cancellation continues
through the existing list actions and ViewModels.

`SportBookingConditions` is the shared, deterministic local offer policy for cards
and details. Academic schedule intersections only warn; official sport booking
conflicts, quotas, selection, credit and health-group restrictions block a new
offer. A full or unpublished lesson can be waited for, subject to the existing
community-services and future-auto-sign rolling-30-day checks at confirmation.
Explicit MyITMO denial (including debt-only classes) is a definite restriction,
even when its text has no known enum mapping. Only a missing explanation is
labelled unknown. The class type alone never invents personal ineligibility. Existing active queues can still
be cancelled when new restrictions appear. Starting time uses the academic clock,
both when rendering and when a card action is tapped.

The details distinguish permission, waiting, non-blocking warnings and restrictions
that auto-sign cannot bypass. This does **not** claim Backend rejects creating a
queue for every MyITMO restriction: the queue services do not preflight all those
rules. The official submission must still satisfy MyITMO. Predictions carry
inferred restrictions, explicitly marked as coming from the previous lesson.
Backend's `SportAutoSignEntryRepository.findMatchingWaitingEntries` matches section,
teacher, building ID, room ID, section/lesson level, type, time-slot ID and exact
start two weeks later, both for newly published lessons and notification retries.
Auto-sign does not substitute another teacher, time, building or room. Room names
are display metadata; matching uses `roomId`, so a rename does not prevent a
match. Building ID `0` cannot confirm a match:
the import also uses it as a fallback for unknown buildings. Such predictions
are left waiting rather than matched on two unknown locations. The details list
the building check among the prediction conditions without a separate
historical-location footnote.

Condition categories use stable semantic accents with deliberate day/night colors:
green permission, blue waiting, amber warning/unknown, red denial. Each also has a
label and icon; color is not the only cue. Registration status reuses those accents
for its label and icon through `SportRegistrationStatus.tone()`; statuses with no
outcome yet (not signed, cancelled) stay neutral instead of borrowing an accent.
Compact tinted condition panels replace
generic explanations plus duplicate reason rows. Neutral card outlines and the
sheet's lowest surface separate the remaining content without saturated slabs.
For real lessons the ordinary queue stops within one hour of start; the existing
force-sign confirmation only relaxes that deadline, not eligibility. The sheet
does not claim the account has available auto-sign quota without refreshing it.
The building check requires deploying the corresponding Backend revision before
releasing this copy to users. No public Backend/Core/MyItmoApi contract or Android
dependency version changes are required.

The My Sport score card collapses into a compact bar as the bookings list scrolls.
`SportScoreCollapseController` drives it from `computeVerticalScrollOffset`, which is
exact while the first row is visible — the whole collapse range — and only has to stay
large enough to clamp past it. The list owns its geometry: it fills the screen and
reserves the expanded card height as top padding, so a drag moves rows one to one.
Letting the card push the list instead would move rows twice per drag, once for the
scroll and once for the shrinking header. Before the first draw, padding changes
preserve the list anchor and wait for the corresponding layout; padding alone does
not move already laid-out rows. During scrolling, all measured sizes remain expanded:
only the card/detail drawing bounds, content translations and alpha change. Neither
layout parameters nor padding are animated, so the screen is not remeasured on each
frame. The details retreat under the header; title and status remain the same views,
with a small translation for compact vertical spacing. Rows share the card's
surface, so the collapsed card steps up a surface level and a backdrop fades in behind
its inset strip, reaching past the card bottom to keep the same gutter the expanded
card has. A header left half collapsed settles to the nearer edge once the list stops.
That snap scrolls the list, not the fraction: the fraction is derived from the scroll
offset, so moving it alone would leave the two disagreeing and the card would jump back
on the next scroll event. A list too short to reach either edge stays put rather than
chasing the snap. `SportScoreCollapseTest` drives the real layout and controller from a
debug host with synthetic bookings, asserting the reserved padding never moves while
collapsing, the first drawn booking is below the card, scrolling does not request a
new layout, and snapping resolves both ways without looping on a short list.

`SportCardsVisualTest` runs real adapters and the real bottom sheet in an isolated
debug host, with synthetic data and no network actions. It covers both themes,
two dynamic palettes, a 320 dp content width, font scale 1.0/1.3, queue states,
busy-action protection, re-binding, full detail text and recreation.

### Dependency injection

`@Binds` with constructor injection is the default; `@Provides` is for types the
project does not construct. Modules are split per feature or concern rather than one
growing module. Workers use `@HiltWorker`. The backend base URL comes from
`BuildConfig.WIDGETS_BASE_URL`, set per build type.

### Navigation

One Activity owns two navigation surfaces. `main_nav_graph` contains authentication,
the five bottom destinations, and the schedule friend-selector dialog. The root
host and bottom bar retain their geometry while a full-screen `AppOverlayHostFragment`
slides above them. `overlay_nav_graph` owns settings, debug tools, and subject details;
future contextual user/teacher/review screens belong there too. Features open these
screens through `core/ui/navigation.AppNavigator`, implemented in the app layer.

`MainNavigationCoordinator` makes the overlay host the primary navigation Fragment
in one reversible parent transaction. The covered root remains STARTED with its
view intact; only the overlay handles Back and accessibility. Nested Back pops one
level; Back at the overlay root removes the surface without animating profile children.
Root selection/reselection discards the entire overlay and any root dialog before
switching tabs. Overlay history is never saved as part of a bottom tab's back stack.
Activity recreation restores the current overlay level, unlike explicit root selection.

Widget intents use the same root-selection path. `MainActivity` queues an intent
received after state saving until `onResumeFragments`, saves that pending destination,
and consumes it once so rotation does not replay an old widget intent. Sign-out
removes overlays before revealing authentication. No navigation commits allow state loss.

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
The first settings page signals `ScreenTransitionHost` on ready pre-draw, releasing
the parent surface's postponed 220 ms slide. Only deeper settings levels use
shared-axis motion; the profile underneath never exits or animates its children.
Cached profile identity is bound before its first draw, including activity recreation.

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

Both schedule widgets use the same default-off `Автозапись на спорт` preference as
the own schedule screen. `ScheduleWidgetDataProvider` loads only the own academic
range and, with both the preference and community services enabled, refreshes the
pending source and reads its completed `getPendingBookings()` snapshot. This API
has no synthetic initial empty emission and performs no implicit network request.
Optional errors remove only pending rows; a failed initial academic request with
no cache remains unavailable rather than being disguised as a pending-only day.

`ScheduleWidgetSelector` builds a separate widget-only timeline, never synthetic
academic `Lesson` objects. Today/tomorrow selection, teacher visibility and smart
refresh boundaries include pending times. Every pending row has a persisted
`WAITING` or `PREDICTED` marker, rendered in both widget types with a short explicit
label and an outlined indicator. It never becomes a confirmed/current lesson.
Queue-enabled widgets request the next refresh within seven minutes (or sooner
at a lesson boundary); Android may defer background execution, so this is not a
wall-clock delivery guarantee. The atomic presentation snapshot includes an exact official-only
fallback, so dropping optional rows also restores the correct single next lesson
and remaining count. Pending data expires at its earliest start or after seven
minutes; snapshot reads re-check both settings gates and authentication. Process
recreation preserves the explicit marker only within that validity window.
Refresh failure never restores old pending rows from the presentation snapshot.
The store is a shared singleton session cleaner; cleanup invalidates in-flight
worker generation tickets before clearing disk, preventing an old account's
completed snapshot from being written into a new session.

After an actual sport booking or queue mutation succeeds, `SportBookingDelegate`
requests a schedule widget update through
`core/schedule.ScheduleWidgetRefreshRequester` before awaiting screen refreshes.
The app-level `WidgetRefreshCoordinator` enqueues forced schedule work, bypassing
the routine throttle without touching QR widgets. Failed actions do not enqueue
updates. Queue mutations update only the optional projection and do not invalidate
the official schedule cache. Explicit changes to the shared display preference
or community-services gate also enqueue widget refresh after persistence.

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
