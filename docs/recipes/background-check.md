# Recipe: background check

How to add a check that runs on the device without the app open: periodic
(schedule changes, marks) or one-off (the QR widget refresh). Copy the worked
examples below; the [notifications](../features/notifications.md) and
[settings](../settings.md#background-work) docs describe their behaviour.

## Worked examples

| Check | Files | Shape |
|---|---|---|
| QR widget refresh | `feature/qr/work/QrWidgetWork.kt`, `feature/qr/work/QrWidgetUpdateWorker.kt` | one-off `REPLACE`, rate limited on `SystemClock.elapsedRealtime()`, no network constraint on purpose: offline the worker still shows the cached pass |
| Marks | `feature/recordbook/work/MarksWorker.kt` (with `MARKS_SPEC`), `feature/recordbook/data/marks/MarksCheck.kt`, `feature/recordbook/data/marks/DefaultMarkTracking.kt` | periodic 3 h plus a one-off, `CONNECTED`, exponential backoff |
| Schedule changes | `feature/schedule/work/ScheduleChangesWorker.kt` (with `SCHEDULE_CHANGES_SPEC`), `feature/schedule/data/changes/ScheduleChangesCheck.kt`, `feature/schedule/data/changes/DefaultScheduleChangeTracking.kt` | periodic 2 h plus a one-off, `CONNECTED`, exponential backoff |

Shared rules live in `:shared:core`'s `core/work/BackgroundChecks.kt`
(`QuietHours`, `CheckOutcome`, `outcomeOf`, `MAX_RETRIES`, tested by its
`commonTest` `BackgroundChecksTest`) and `core/work/BackgroundCheck.kt` (the
`BackgroundCheck` and `CheckScheduler` contracts). The Android parts stay in
the app's `core/work`: `WorkResults.kt` (`workResultOf`, `WorkResultsTest`)
and `PeriodicCheckScheduler.kt` (`PeriodicCheckSpec`, `PeriodicCheckScheduler`,
pinned by `PeriodicCheckSpecTest`). Use them instead of a feature copy.

## Pieces

1. **Worker** in `feature/<x>/work`. A thin `CoroutineWorker`: it gets the
   feature's `<X>Check` and returns `workResultOf(check.run(), runAttemptCount)`.
   `CancellationException` propagates. The class name is stable once shipped:
   add it to `WORKERS` in `architecture/StableIdentifiersTest.kt`.
2. **Unique work names** as string literals in the worker file's
   `PeriodicCheckSpec` (`periodicWork`, `oneOffWork`, `tag`, next to the
   period and the backoff), whose KDoc names `enqueueUniquePeriodicWork`
   (the context `StableIdentifiersTest` looks for), and in `WORK_NAMES` of
   `StableIdentifiersTest` and in `PeriodicCheckSpecTest`. WorkManager keeps
   them across updates; a rename needs an ADR and a cancel of the old name.
3. **Check** in `feature/<x>/data`: one run. It returns `CheckOutcome.SKIPPED`
   without a refresh token or with the switch off, collects `AppError`s, always
   delivers what waits (quiet hours hold notifications, not checks) and ends
   with `outcomeOf(errors)`: `Unauthorized` alone does not retry. Time comes
   from `AcademicTimeProvider` (`TimeRulesTest`); the decision of what to post
   is a pure `domain` object (`MarkDigests`, `ScheduleChangeDigests`).
4. **Scheduler**: an empty port in `domain` that extends `CheckScheduler`
   (`interface MarksScheduler : CheckScheduler`: `ensurePeriodic`, `runOnce`,
   `cancel`), so fakes and test entry points name the feature's type. There is
   no per-feature WorkManager class: the feature's Hilt module binds the port
   with an unscoped delegating `@Provides`,
   `object : MarksScheduler, CheckScheduler by PeriodicCheckScheduler(context, MARKS_SPEC) {}`
   (a `PeriodicCheckScheduler` is not a `MarksScheduler`). `ensurePeriodic`
   uses `ExistingPeriodicWorkPolicy.UPDATE`, so calling it on every start
   keeps the enrolment time; `runOnce` uses `ExistingWorkPolicy.REPLACE`;
   both require `CONNECTED`, the periodic one backs off exponentially.
5. **Switch and tracking**: a DataStore key in the concern's
   `core/storage/*Preferences` store, and a `core/<area>` contract
   (`MarkTracking`, `ScheduleChangeTracking`) that extends `BackgroundCheck`
   (`syncWork`, `stopWork`) and adds `setEnabled` and `checkNow`, implemented
   as a `@Singleton` `Default*` in `data`. `syncWork` enrols the work only with
   a session and the switch on, otherwise cancels it; turning the switch off
   also forgets the snapshot, so the next run is a baseline. Bind the impl
   `@Binds @IntoSet` as a `BackgroundCheck` in the feature's Hilt module
   (`BackgroundCheckGraphTest` counts the set). Callers: the settings
   ViewModel (the switch); `app/ItmoWidgetsApplication.kt` (after an update or
   a restore) and `app/AndroidSessionLifecycleEffects.kt` (`stopWork` before a
   session change, `syncWork` after sign-in) iterate the injected
   `Set<BackgroundCheck>` and need no edit. Document the switch in
   [settings](../settings.md) and the dialog in
   [Background work](../settings.md#background-work).
6. **`SessionDataCleaner`**: the repository that stores the snapshot
   implements it and is bound `@IntoSet` in the feature's module; it is a
   `@Singleton` (`StorageRulesTest`). Sign-out and an account change run every
   cleaner.
7. **Backup exclusions**: the snapshot lives in `filesDir/<check>/`; add that
   directory to both `res/xml/backup_rules.xml` and
   `res/xml/data_extraction_rules.xml` (cloud backup and device transfer), and
   add a row to the persistence table in
   [architecture](../architecture.md#persistence). The exclusions are stable
   identifiers too.
8. **Notification channel**: a constant in
   `core/notification/AppNotificationChannels.kt`, created in `create()`, its
   name in `strings_platform.xml` (a catalog hand-in), posted by an
   `Android<X>Notifier` in `work` behind a `domain` port. Channel ids are
   stable; list it in [Channels](../features/notifications.md#channels).
9. **Debug trigger**: a button in the checks card of
   `feature/debug/ui/DebugToolsScreen.kt`, wired through `DebugToolsActions`
   in `DebugToolsFragment.kt` to a
   `DebugToolsViewModel` method that calls `checkNow()`; list it in
   [debug tools](../features/debug.md#screen).
10. **Gates in the data call**: every class that takes a network client
    checks `DemoMode` first and answers from the feature's demo data
    (`feature/qr/data/remote/QrCodeRemoteDataSourceImpl.kt` of
    `:shared:feature-qr` returns `DemoQr.HEX`, `feature/schedule/data/changes/ScheduleChangesRepositoryImpl.kt`
    compares nothing). A Backend call also needs `BackendGate.mayCallBackend()`
    (`core/notification/FcmTokenSync.kt`). `GateRulesTest` enforces both. The
    demo session has no refresh token, so the checks skip it anyway.
11. **Layer rule**: `work` imports neither `presentation` nor another feature's
    `ui.widget` (`LayerRulesTest`, `work depends on neither presentation nor
    another feature's widgets`); features never import each other.

## Dependency injection

Workers are built by WorkManager, so they cannot take constructor injection.

**Now (Hilt and Koin side by side).** The worker reads a Hilt `@EntryPoint`
(`MarksEntryPoint` in `MarksWorker.kt`). `EntryPointAccessors` is allowed only
in `di/bridge` (`DiRulesTest`, `EntryPointAccessors is used only in di
bridge`); the existing workers sit on its ratchet and no new line may join it,
so a new worker's entry point and its `from(context)` accessor go to
`app/src/main/java/dev/alllexey/itmowidgets/di/bridge/`. When the check's
dependencies are built by Koin, the entry point returns them through an
unscoped `@Provides` in a `<Feature>KoinBridgeModule` there that calls
`KoinStarter.ensureStarted(context).get()`: a worker can run before
`Application.onCreate()`. No `@HiltWorker` and no custom `WorkManager`
configuration.

**After the Hilt removal (KM-12a).** The worker becomes a `KoinComponent`
with `by inject()`, keeps its class name and runs under WorkManager's default
`WorkerFactory`; the entry point and its ratchet line are deleted. That change
replaces this section.

## iOS

The check maps to a `BGAppRefreshTask` registered by the iOS app; iOS picks
the moment, as Android does. The decision objects (`MarkDigests`,
`ScheduleChangeDigests`, `QuietHours`, `CheckOutcome`) are shared, so both
platforms post the same digests; the scheduler, notifier and worker are
platform code.

## Traps

- An unconditional `enqueue` from a widget's `onUpdate` loops: running a worker
  toggles WorkManager's `RescheduleReceiver`, which the system answers with
  `APPWIDGET_UPDATE`. Rate limit it on the monotonic clock, as
  `QrWidgetWork.enqueueUpdate` does.
- QR expiry uses the wall clock, never `AcademicTimeProvider`; the debug
  academic date must not move it.
- `ExistingPeriodicWorkPolicy.REPLACE` on every start pushes the next run away
  forever; use `UPDATE`.
- MIUI and HyperOS cut a backgrounded app's network although `CONNECTED`
  holds: treat `IOException` as temporary (`outcomeOf`) and never prompt a
  sign-in for it.
