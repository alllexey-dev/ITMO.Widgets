# Schedule

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

`feature/schedule` shows the own academic schedule from MyITMO, a friend's
schedule chosen through the picker, and optional pending sport rows. The
background change check is in [schedule changes](schedule-changes.md), the
phone calendar and `.ics` export in [calendar](calendar.md).

## Modules

- `:shared:feature-schedule` (`commonMain`, package
  `dev.alllexey.itmowidgets.feature.schedule`): `domain` (lessons, changes,
  calendar events, widget selector and timeline), `presentation`
  (`ScheduleViewModel`, `ScheduleChangesViewModel`, `LessonDetailsViewModel`),
  `ui/list`, `ui/changes`, `ui/details`, `ui/home` (Compose Multiplatform) and
  `data` (cache, file stores, My ITMO and Backend sources, calendar sync,
  `DemoSchedule`). Koin: `scheduleModule`, `scheduleDataModule`. `androidMain`
  only supplies `ScheduleFileSystem`; `iosMain` adds the iOS change check and
  the widget timeline writer.
- My ITMO comes through MyItmoApi 2.x, Backend through Core 2.0's
  `ScheduleApi`: `syncLessons` (best-effort upload of own lessons),
  `userLessons` (another user's schedule) and `friendsOnLesson`, all behind
  `BackendGate`. Every network call checks `DemoMode` first (`DemoSchedule`).
- `:app` keeps only Android: map and link effects of the sheets
  (`feature/schedule/ui/details/DetailsEffects.kt`), the widgets and the list
  adapter service (`ui/widget`), workers, change notification and refresh
  receiver (`work`), `AndroidPhoneCalendars` and `IcsFileExport`
  (`data/calendar`), `ScheduleWidgetSnapshotStoreImpl`. Koin:
  `androidScheduleModule` (`di/ScheduleModule.kt`). `ScheduleRulesTest`
  (`:konsist`) keeps the widget providers, receiver, adapter service and
  workers in `:app` under their packages.

- Screens: `ScheduleTabRoot` (`app/shell/entries/ScheduleEntries.kt`) hosts
  `ScheduleRoute` and registers `AppRoutes.UserSchedule` and the sheets `AppRoutes.LessonDetails`,
  `AppRoutes.PendingSportDetails`. Screens take state and callbacks only; the
  entry performs effects (maps, links, profiles, picker, navigation).

### Files

All schedule files are kotlinx JSON (`ScheduleStoreJson`) with the keys 2.2
wrote, so 2.2 files read unchanged in both directions
(`app/src/androidTest/.../upgrade/stores/`). Stores write through
`AtomicTextFile` (`<name>.new`, sync, atomic move).

| File | Format |
|---|---|
| `cacheDir/schedule_cache/<isu or default>_<date>.json` | gzipped day; a file that does not decode is a cache miss |
| `filesDir/schedule_changes/state.json` | `format` 1 ([storage](schedule-changes.md#storage)) |
| `filesDir/teacher_lessons/weeks.json` | `format` 1 ([lessons with a teacher](#lessons-with-a-teacher)) |
| `filesDir/calendar_sync/state.json` | `format` 1 ([synchronization](calendar.md#synchronization)) |
| `noBackupFilesDir/widgets/schedule_snapshot.json` | top-level `formatVersion` 2 ([widgets](widgets.md#schedule-widgets)) |

## Data

- `ScheduleViewModel` reads an optional `ARG_USER_ISU`: without it the own
  schedule with the picker FAB, with it another user without the picker.
- Cache: a file per user and date, 24-hour TTL. A refresh keeps the loaded
  range (including pages fetched by scrolling) and replaces it with one
  snapshot after success; partial lists are never published. Dates missing
  from the response are removed; other ranges and users are kept.
- `Forbidden` for a foreign schedule removes that user's cache and content
  only. A generation check stops a late response from restoring a revoked
  cache. Ordinary network failures keep cached content.
- First frame from memory: `ScheduleRepository.peekScheduleForRange` returns
  a fully hydrated range without disk access; only a screen with nothing
  cached shows the skeleton.
- `scheduleDataModule` holds one instance of each repository, gateway, check
  and source per process. The platform supplies `ScheduleApi`,
  `ScheduleChangeNotifier`, `ScheduleChangesScheduler`, `AppNotifier`,
  `PhoneCalendars` and `CalendarSyncScheduler`. Workers, widgets and the list
  adapter reach Koin through `KoinStarter`. Change tracking and calendar sync
  are named `BackgroundCheck` bindings.
- Sign-out: four named `SessionDataCleaner` bindings (cache, change history,
  teacher weeks, calendar sync), read with `getAll()`.

## Lessons with a teacher

`core/schedule/TeacherLessonsGateway.taughtBy(teacherIsu)` feeds the
[review editor](reviews.md#editor): subjects of the viewer's own academic
lessons with that teacher (newest first) and their `flowId`s.
`TeacherLessonsGatewayImpl` samples the personal My ITMO schedule by
Monday-Sunday weeks (`StudyWeeks.sampled`): in the current and 3 previous
academic years the weeks containing 25 September, 3 December, 24 February and
5 March (not starting after today), plus the current week once. At most 17
requests, all in parallel. Keeps `flowTypeId == 2` lessons with that
`teacherId`. Read only: never uploaded, never touches the schedule cache.

- The result is a flow: after each week it emits everything so far, newer
  weeks first. A failed week is skipped and retried next time; only when all
  fail is the single emission an error.
- Past weeks never change: `TeacherWeeksFileStore` keeps them in
  `filesDir/teacher_lessons/weeks.json` (format 1, atomic, excluded from
  backup); weeks no longer sampled are dropped on write. The current week is
  always requested. A corrupt or foreign-format file is ignored and replaced.
- `clearSessionData()` deletes the file; an answer requested before the clear
  is not stored. The recordbook is not used (its teachers carry no ISU).

## Friend picker

`AppRoutes.FriendSelector` ([Friend selector](friend-selector.md)) returns a
`FriendSelection` to `ScheduleTabRoot` on the result bus (`ResultEffect`).

## Pending sport rows

`Автозапись на спорт` (`SchedulePreferencesRepository`, default off) adds the
own active sport queues to the schedule and both schedule widgets.

- `PendingSportBookingsRepository` (`core/sport`) excludes cancelled,
  terminal, started and already-signed entries; queues on the same lesson
  collapse into one row; unpublished predictions use prototype dates plus two
  weeks.
- Observed only when the preference is on and both the selected and the loaded
  schedule are the own one; never for friends or the official cache. The
  Backend opt-in stays an independent gate in the data layer.
- `ScheduleUiState.Content.schedule` stays official; `ScheduleDisplayDay`
  adds pending rows. They are never styled as confirmed and do not change the
  lesson count. An error or empty snapshot from this source only removes the
  rows.
- Days are keyed by date, so live changes do not reset the cache or scroll.

## Behaviour

- Shared with recordbook, home and widgets: `core/schedule/LessonTypeNames.kt`,
  `core/text/LocationTitles.kt`.
- `TabRequest.ScheduleToday` (`core/navigation/EntryRoute.kt`, the `Сегодня`
  shortcut, see [home](home.md#quick-settings-tile-and-app-shortcuts)) waits
  in the navigator (`pendingRequest`) until `ScheduleTabRoot` consumes it
  once; another user's schedule never reads it.
- A restored list position is never replaced by today. Switching own/friend
  anchors the visible day by date, paging in up to four pages, else today.

## Lesson details

- Ordinary lesson cards open `AppRoutes.LessonDetails` with a `@Serializable`
  `LessonDetailsArgs` (`core/navigation`) built from the `Lesson` and date;
  nothing is fetched for the lesson itself.
- Sport lessons (type 11) in the own schedule and pending sport rows go
  through `SportDetailsRedirect` (`app/shell/entries/SportEntries.kt`): it
  finds the booking by date and start in `SportBookingsHolder` (confirmed
  booking before a queue) and opens `SportRoutes.SportCommonDetails`;
  cancellation asks first (`AppRoutes.CancelBookingConfirm`). Without a match
  a sport lesson falls back to the lesson sheet, a pending row to
  `AppRoutes.PendingSportDetails`.
- Map: a known building from `core/location/BuildingDirectory`
  (`res/raw/itmo_buildings.json`), else the raw text, via a generic `geo:`
  intent (`core/ui/navigation/MapLauncher`). No map provider setting. The
  video link (`zoom_url`) is opened, never displayed.
- Teacher row opens the profile only with a usable ISU (48 dp target,
  accessibility action); without one it is inert and no name lookup happens.
- Teacher tone dot: only with the ITMO.Widgets opt-in and an ISU, from
  `TeacherLevelsRepository`, cached for a day
  ([teacher levels](reviews.md#teacher-levels)). Its place is reserved
  (`reserveTone`) so a late dot moves nothing. Never on sport sheets.
- `Друзья на паре` is the only place friends on a lesson appear:
  `GET /api/schedule/lessons/{pairId}/friends?date=`; Backend returns accepted
  friends who attend and share their schedule with the viewer. Absent without
  the opt-in.
- `Поток` is `LessonDetailsArgs.flowName` (the lesson's `groupName`, trimmed;
  blank hides it). Lesson sheet only.
- `Изменения` shows the newest [schedule change](schedule-changes.md) of the
  last 30 days whose occurrences contain this `pairId` and date
  (`LessonDetailsViewModel.change`, local store only).

## iOS

Same shared code ([iOS app](../ios.md)): `ScheduleRoute` hosted by
`ScheduleScreen` (`iosApp/Sources/Features/Schedule/`) through
`scheduleRootScreen` (`shared/ios`); `scheduleIosModule`,
`scheduleChangesIosModule`, `calendarIosModule` in `IosKoinModules`.
Differences: maps go to Apple Maps (`PlatformActions.openMap`), links to
Safari; a pending sport row opens the schedule's pending sheet
([sport](sport.md#ios)); the picker returns through the router
([friend selector](friend-selector.md#ios)). See also
[schedule changes](schedule-changes.md#ios), [calendar](calendar.md#ios),
[widgets](widgets.md#ios).
