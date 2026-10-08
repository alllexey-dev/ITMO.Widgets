# shared/feature-schedule

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.schedule.*`: the schedule, its changes, calendar sync and
  the widget data. `data/` (`remote/`, `local/`, `repository/`, `mapper/`, `calendar/`, `changes/`, `widget/`,
  `home/`, `demo/`), `domain/` (with `calendar/`, `changes/`, `home/`, `model/`, `widget/`), `presentation/` (list,
  `changes/`, `details/`), `ui/` (`list/`, `changes/`, `details/`, `home/`, previews).
- `di/`: the Koin modules `scheduleModule` (screens, selectors) and `scheduleDataModule` (repositories, stores,
  checks); `iosMain`: `scheduleIosModule`, `scheduleChangesIosModule`, `scheduleWidgetIosModule` and `widget/`
  (`ScheduleTimelineWriter`); `androidMain`: the file system actual only.
- `strings_schedule.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `fixtures/schedule-widget-timeline-v1.json`: the widget timeline contract shared with the iOS widget.
- Tests: `androidHostTest` (repositories, checks, screens, `ScheduleModuleTest`, `ScheduleScreenshotTest`),
  `commonTest`, `iosTest`.
- Not here: the RemoteViews widgets, `ScheduleWidgetRefreshReceiver`, `ScheduleWidgetRemoteViewsService`, the
  workers and schedulers stay in `app/` under `feature/schedule/`; `di/bridge/ScheduleBridge.kt` bridges Koin and Hilt.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin and the KMP lifecycle.
- MyItmoApi 2.x for the own and teacher schedules; Backend's `schedule` area (behind `BackendGate`) for the own
  schedule's upload and other users' schedules.

## Verify
`scripts/verify.sh quick`, `scripts/verify.sh shots feature-schedule`; `klibs feature-schedule` after `iosMain`.

## Hot files
- Every file here: lane L10; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L10 writes, L04 reviews.
- `strings_schedule.xml`: L10; keys are never renamed.
- Stable identifiers held here: `cacheDir/schedule_cache`, `filesDir/schedule_changes/state.json`,
  `filesDir/calendar_sync/state.json`, `filesDir/teacher_lessons/weeks.json` and the serial names of the widget
  snapshot (`ScheduleWidgetModels`) the app stores in `noBackupFilesDir/widgets/schedule_snapshot.json`. Widgets,
  workers, unique work names and the `schedule_changes` channel are app-side entries of `StableIdentifiersTest`.
- `ScheduleRulesTest` (Konsist, lane L06): no Fragment, View or `R` here; widgets, receiver and workers stay in
  `app/`; the widget timeline always writes its version.

## Docs
- [Schedule](../../docs/features/schedule.md), [schedule changes](../../docs/features/schedule-changes.md),
  [calendar](../../docs/features/calendar.md), [schedule widgets](../../docs/features/widgets.md#schedule-widgets).
- [Background check](../../docs/recipes/background-check.md); ADRs
  [0013](../../docs/decisions/0013-schedule-changes-on-device.md), [0027](../../docs/decisions/0027-remoteviews-widgets.md).
