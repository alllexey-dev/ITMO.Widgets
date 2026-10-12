# One feature end to end: the schedule

The schedule touches every layer: it reads MyITMO through MyItmoApi, uploads
and reads lessons through the ITMO.Widgets Backend, caches on the device,
shows a Compose screen on both platforms and feeds the home-screen widgets.
Follow it to learn where code goes; copy it when you add a feature. Product
behaviour is in [features/schedule.md](features/schedule.md).

Kotlin paths below are relative to
`shared/feature-schedule/src/commonMain/kotlin/dev/alllexey/itmowidgets/feature/schedule/`
unless they start with a module directory.

## 1. Remote clients

| Client | Calls | Built in |
|---|---|---|
| `MyItmoClient` (MyItmoApi, Maven Central) | `schedule.getPersonalSchedule(start, end)`: the own schedule | Android `app/src/main/java/dev/alllexey/itmowidgets/di/NetworkModule.kt`, iOS `shared/core/src/iosMain/kotlin/dev/alllexey/itmowidgets/core/di/IosCoreModule.kt` |
| `ScheduleApi` (`shared/backend-client`, `client/schedule/ScheduleApi.kt`) | `syncLessons` uploads the own lessons, `userLessons` reads a friend's | the same modules, as `BackendClient.schedule` |

Both clients share one Ktor engine and the session's tokens. Neither checks
demo mode or the opt-in: the callers do.

## 2. Data source with the gates

`data/remote/ScheduleRemoteDataSourceImpl.kt` takes `MyItmoClient`,
`ScheduleApi`, `DemoMode`, `BackendGate`, `AcademicTimeProvider` and
`AppDispatchers`:

1. `demo.isActive()` first: the demo session answers from
   `data/demo/DemoSchedule.kt` and sends nothing.
2. The own schedule comes from MyITMO on `dispatchers.io`.
3. Only if `backend.mayCallBackend()` is true, the lessons are uploaded to
   Backend, best effort: a failure never hides MyITMO data.
4. A friend's schedule exists only on Backend.

`data/local/ScheduleLocalDataSourceImpl.kt` keeps days in
`cacheDir/schedule_cache` and exposes them as flows.

## 3. Repository

`data/repository/ScheduleRepositoryImpl.kt` implements the `domain` port
`domain/ScheduleRepository.kt`:

- `observeScheduleForRange` reads the local cache, so screens render cached
  days in the first frame.
- `refreshSchedule` fetches, then replaces the cached range. A friend's
  schedule without the opt-in (`CustomServicesRepository.isEnabled()`) fails
  with `AppError.CustomServicesDisabled` and clears that user's cache; a
  `Forbidden` answer clears it too, so revoked access leaves nothing offline.
- It is a `SessionDataCleaner`: sign-out empties the cache, and a late
  answer after sign-out is dropped.
- Exceptions become `AppError` here (`data/ScheduleErrors.kt`).

Koin wiring is `di/ScheduleDataModule.kt` (`scheduleDataModule`): one
repository instance for the screens, the home card, the widget and sign-out.

## 4. View model

`presentation/ScheduleViewModel.kt` takes the repository,
`AcademicTimeProvider`, `SavedStateHandle` and other domain ports. It exposes
`uiState: StateFlow<ScheduleUiState>` (`presentation/ScheduleUiState.kt`) and
`events`, built from the repository flow plus refresh state, and never reads
the wall clock directly. `di/ScheduleModule.kt` registers it with
`viewModelOf(::ScheduleViewModel)`.

## 5. Compose screen

- `ui/list/ScheduleRoute.kt`: `ScheduleRoute(...)` obtains the view model
  with `koinViewModel()`, collects state and events, and turns clicks into
  the `ScheduleRouteActions` lambdas the host passes in.
- `ui/list/ScheduleScreen.kt`: stateless, takes state and lambdas, built from
  the `:shared:designsystem` kit and `ItmoTheme` tokens.
- `ui/list/ScheduleScreenPreviews.kt`: previews with synthetic data; they are
  the screenshot baselines in `shared/feature-schedule/screenshots/`.
- Strings: `shared/feature-schedule/src/commonMain/composeResources/values/strings_schedule.xml`.

## 6. Android host

- The tab root is `ScheduleTabRoot` in
  `app/src/main/java/dev/alllexey/itmowidgets/app/shell/entries/ScheduleEntries.kt`.
  It renders `ScheduleRoute` and maps its actions to `Nav3AppNavigator`: a
  lesson opens `AppRoutes.LessonDetails` (a sheet), the friends button opens
  `AppRoutes.FriendSelector`, whose answer comes back through the result bus.
- The same file registers `AppRoutes.UserSchedule`, `AppRoutes.ScheduleChanges`
  and the two sheets with `EntryRegistry`.
- Android-only pieces (widget refresh, change notifications, schedulers) are
  bound in `app/src/main/java/dev/alllexey/itmowidgets/di/ScheduleModule.kt`
  (`androidScheduleModule`).

## 7. iOS host

- `iosApp/Sources/Features/Schedule/ScheduleScreen.swift` puts a `ComposeHost`
  in the schedule tab. Its controller comes from `scheduleRootScreen(open:pickFriend:)`
  in `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/screens/ScheduleScreens.kt`,
  which wraps the same `ScheduleRoute` and sends routes back to Swift.
- `iosApp/Sources/App/Router/Routes+Schedule.swift` maps the schedule keys:
  another user's schedule and the changes are Compose screens, the lesson and
  friend picker are SwiftUI sheets around Compose content.
- iOS-only ports (EventKit calendar, notifier, widget timeline) are in
  `shared/feature-schedule/src/iosMain` and registered in
  `shared/ios/src/iosMain/kotlin/dev/alllexey/itmowidgets/ios/di/IosKoinModules.kt`.

These screens move to native SwiftUI over the same view models
([ADR 0031](decisions/0031-swiftui-ios-screens.md)).

## 8. Widget snapshot

`data/widget/ScheduleWidgetDataProvider.kt` is shared: it refreshes the own
schedule through the repository, reads the cache and selects what the widget
shows (`domain/widget/ScheduleWidgetSelector.kt`).

- **Android.** `ScheduleWidgetWork` enqueues
  `ScheduleWidgetUpdateWorker` (`app/.../feature/schedule/work/`), which calls
  `load()`, writes `widgets/schedule_snapshot.json` through
  `ScheduleWidgetSnapshotStoreImpl` and renders RemoteViews with
  `ScheduleWidgetRenderer` for `DayScheduleWidgetProvider` and
  `SingleLessonWidgetProvider`. `WidgetRefreshCoordinator` asks for a refresh
  after actions that change the schedule.
- **iOS.** `ScheduleTimelineWriter` (`shared/feature-schedule/src/iosMain`)
  calls `loadTimeline(until)` and writes the timeline to the App Group;
  `iosApp/Shared/WidgetSnapshots/LessonTimeline.swift` reads it and
  `iosApp/Extensions/Widgets/Schedule/DayScheduleWidget.swift` draws it.

## 9. Tests at each layer

| Layer | Tests |
|---|---|
| Data source and gates | `ScheduleRemoteDataSourceImplTest` (MockEngine through the real clients), `ScheduleDemoGateTest` |
| Repository | `ScheduleRepositoryImplTest` |
| View model | `ScheduleViewModelTest` (`commonTest`) |
| Screen | `ScheduleScreenTest`, `ScheduleRouteTest`, `ScheduleScreenshotTest` |
| Koin | `ScheduleModuleTest`, `ScheduleIosModuleTest`, app `ScheduleGraphTest` |
| Widget | `ScheduleWidgetDataProviderTest`, `ScheduleTimelineWriterTest`, instrumented `ScheduleWidgetSnapshotStoreTest` and `ScheduleWidgetRenderingTest`, iOS `ScheduleTimelineTests.swift` |
| UI on device | iOS `ScheduleUITests.swift` |

## Adding a Backend call

1. **Backend first.** Decide who may call the route and what each viewer sees,
   implement it with a security test and golden fixtures in
   [itmo-widgets-backend](https://github.com/alllexey-dev/itmo-widgets-backend).
   Every wire change is additive: released apps cannot be updated by a
   Backend release.
2. **Client.** After the Backend merge, vendor the contract with
   `scripts/sync-backend-contract.sh <backend-sha>` and mirror the route in
   `shared/backend-client` as `shared/backend-client/CONTRACT.md` describes:
   the function in `<Area>Api`, its call in `Ktor<Area>Api`, DTOs with
   Backend's names, route cases and decode tests in `commonTest`.
3. **Repository.** In the feature's `data`, take the `*Api`, `DemoMode`,
   `BackendGate` and `AppDispatchers`. Check the demo first, then
   `mayCallBackend()` (otherwise `AppError.CustomServicesDisabled`), then call
   on `dispatchers.io`; convert errors to `AppError`. `GateRulesTest` fails a
   class that holds a client without the gates. Add a demo-gate test.
4. **Screen.** View model, route and screen as above; failures are `AppError`,
   never server text.

## Adding a MyITMO or BARS call

MyItmoApi is the only client of official ITMO endpoints. Add the call to
[my-itmo-api](https://github.com/alllexey-dev/my-itmo-api) with fixtures and
MockEngine tests, build the app against it with `MYITMOAPI_DIR` while it is in
review, then bump `myItmoApiKmp` in `gradle/libs.versions.toml` after the
release. The data source checks `DemoMode` (no `BackendGate`: MyITMO is not
Backend) and maps `MyItmoException` to `AppError` in the repository.

## Adding a screen

1. `presentation/<Screen>UiState.kt` and `<Screen>ViewModel.kt`; register it
   with `viewModelOf` in the feature's `di/` module and add new modules to
   `KoinModules.kt` (Android) and `IosKoinModules.kt` (iOS).
2. `ui/<Name>Route.kt` (takes navigation lambdas, `viewModel = koinViewModel()`)
   and a stateless `ui/<Name>Screen.kt` from the kit. Strings go to the
   module's `composeResources`, icons come from `docs/design/icons.tsv`.
3. Previews in `ui/<Name>ScreenPreviews.kt`, then
   `scripts/verify.sh shots feature-<x> --record` and look at every PNG.
4. A key in `AppRoutes`, registered in `app/shell/entries/<Area>Entries.kt`
   and mapped in `iosApp/Sources/App/Router/Routes+<Area>.swift`.
5. Host tests: the screen in every state, the view model with fakes and a
   fixed clock, `<X>ModuleTest`.

## Adding a background check

1. The check itself in the feature's `data` (like
   `data/changes/ScheduleChangesCheck.kt`): one run, returning a
   `CheckOutcome` (`shared/core/src/commonMain/kotlin/dev/alllexey/itmowidgets/core/work/BackgroundChecks.kt`);
   what to notify about is a pure `domain` object.
2. A tracking class implementing `BackgroundCheck` (`syncWork`, `stopWork`)
   that enrols work only with a session and the switch on; register it as a
   named `BackgroundCheck` in Koin so the app start and sign-in reach it.
3. **Android:** a thin `CoroutineWorker` in the app's `feature/<x>/work`
   returning `workResultOf(...)`, scheduled by `PeriodicCheckScheduler` with a
   `PeriodicCheckSpec`. Worker class names and unique work names are stable:
   add them to `StableIdentifiersTest`.
4. **iOS:** a step of the one app refresh task (`core/work/RefreshSteps.kt`
   in `shared/core/src/iosMain`, like `ScheduleChangesRefresh.kt`).
5. Store state under `AppDirectories`, implement `SessionDataCleaner`, exclude
   device-only files from backup, add a debug trigger to the debug tools.
