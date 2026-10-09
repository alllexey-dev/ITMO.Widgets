# Schedule

`feature/schedule` shows the own academic schedule from MyITMO, a friend's
schedule chosen through the picker, and optional pending sport rows. It also
checks the own schedule for changes in the background
([schedule changes](schedule-changes.md)) and puts it into the phone's calendar
or a `.ics` file ([calendar](calendar.md)).

## Modules

- `:shared:feature-schedule` holds the feature in `commonMain`, under the
  `dev.alllexey.itmowidgets.feature.schedule` packages of 2.2: `domain`
  (lessons, changes, calendar events, the widget selector and timeline),
  `presentation` (`ScheduleViewModel`, `ScheduleChangesViewModel`,
  `LessonDetailsViewModel`), `ui/list`, `ui/changes`, `ui/details` and
  `ui/home` (Compose Multiplatform), and `data` (the cache, the file stores,
  the My ITMO and Backend sources, the calendar sync, `DemoSchedule`). Koin
  builds all of it from `scheduleModule` and `scheduleDataModule` in `di`.
  `androidMain` only supplies `ScheduleFileSystem`; `iosMain` adds the iOS
  change check (`ScheduleChangesRefresh`, `IosScheduleChangeNotifier`) and the
  widget timeline writer (`ScheduleTimelineWriter`). The module compiles for
  iOS, and its strings live in its `composeResources`
  (`strings_schedule.xml`).
- My ITMO comes through MyItmoApi 2.x, Backend through Core 2.0's
  `ScheduleApi`: `syncLessons` uploads the own lessons best-effort behind
  `BackendGate`, `userLessons` reads another user's schedule and
  `friendsOnLesson` the friends on a lesson, also behind `BackendGate`. Every
  network call checks `DemoMode` first (`DemoSchedule`).
- `:app` keeps only what is Android: the Fragment and sheet hosts
  (`feature/schedule/ui`), the two widgets, their renderers and the legacy
  list adapter (`ui/widget`), the workers, the change notification and the
  refresh receiver (`work`), `AndroidPhoneCalendars` and `IcsFileExport`
  (`data/calendar`) and the widget snapshot file
  (`data/widget/ScheduleWidgetSnapshotStoreImpl`). Hilt's side is
  `di/ScheduleModule.kt`; `di/bridge/ScheduleBridge.kt` hands it to Koin and
  back. `ScheduleRulesTest` keeps the shared code free of Fragments, Views and
  `R`, and the widget providers, the receiver, the adapter service and the
  workers in `:app` under their packages.

### Screens

Every screen and sheet is a stateless composable of
`:shared:feature-schedule` drawn by its unchanged host in `:app`, which keeps
the class name, the factories, the `TAG`, the arguments and the Fragment
results:

| Host | Compose body | Route |
|---|---|---|
| `ScheduleFragment` | `ScheduleScreen` | `ScheduleRoute` |
| `UserScheduleFragment` | `UserScheduleScreen` around `ScheduleScreen` | `ScheduleRoute` |
| `ScheduleChangesFragment` | `ScheduleChangesScreen` | `ScheduleChangesRoute` |
| `LessonDetailsBottomSheet` | `LessonDetailsContent` | `LessonDetailsSheetRoute` |
| `PendingSportDetailsBottomSheet` | `PendingSportDetailsContent` | - |

The screens take state and callbacks only; the ViewModels come from Koin in
the route or its host, and the host performs the effects (maps, links,
profiles, the picker, navigation). The sheets sit on the design
system's `ItmoBottomSheetFragment`. The XML layouts, adapters and View tests
of these screens are gone: JVM host tests (`ScheduleScreenTest`,
`ScheduleRouteTest`, `UserScheduleScreenTest`, `ScheduleChangesScreenTest`,
`ScheduleChangesRouteTest`, `LessonDetailsSheetTest`,
`PendingSportDetailsSheetTest`) cover the behaviour, and
`ScheduleScreenshotTest` keeps the goldens in four appearances under
`shared/feature-schedule/screenshots/`.

### Files

All schedule files are kotlinx JSON (`ScheduleStoreJson`: absent keys read
as null or the default, nulls are omitted, defaults are written), with the
keys 2.2 wrote through Gson, so 2.2 files read unchanged and either build
reads the other's files. The `UpgradeFrom22Test` checkers in
`app/src/androidTest/java/dev/alllexey/itmowidgets/upgrade/stores/` read 2.2's captured files.

| File | Format |
|---|---|
| `cacheDir/schedule_cache/<isu or default>_<date>.json` | gzipped day, no format field; a file that does not decode is a cache miss |
| `filesDir/schedule_changes/state.json` | `format` 1 ([storage](schedule-changes.md#storage)) |
| `filesDir/teacher_lessons/weeks.json` | `format` 1 ([lessons with a teacher](#lessons-with-a-teacher)) |
| `filesDir/calendar_sync/state.json` | `format` 1 ([synchronization](calendar.md#synchronization)) |
| `noBackupFilesDir/widgets/schedule_snapshot.json` | top-level `formatVersion` 2 ([widgets](widgets.md#schedule-widgets)) |

The stores write through `AtomicTextFile` (`<name>.new`, sync, atomic move).

## Data

- `ScheduleFragment` takes an optional `ARG_USER_ISU`. Without it the screen
  shows the signed-in user and offers the friend picker FAB; with it the same
  screen shows another user and hides the picker. `UserScheduleFragment` wraps
  it with a titled header when opened from a public profile.
- The cache is a file per user and date range with a 24-hour TTL. A refresh keeps
  the loaded range, including pages fetched by scrolling, and replaces it with one
  snapshot after a successful response; intermediate partial lists are never
  published. Dates missing from the response are removed; other ranges and users
  are kept.
- A definitive denial of a foreign schedule (`Forbidden`) removes that user's
  cache and visible content without touching own or other users' data. A
  generation check prevents a late concurrent response from restoring a revoked
  cache. Ordinary network failures keep cached content.
- The first frame comes from memory: `ScheduleRepository.peekScheduleForRange`
  returns the range without touching the disk when every date is already
  hydrated, so the first load publishes `Content(loadingMore = true)` at
  once and only a screen with nothing cached shows the skeleton (three
  placeholder cards) until the cache flow answers.
- The schedule data lives in `commonMain` of `:shared:feature-schedule` and is
  constructed by Koin (`scheduleDataModule`), one instance of each per
  process: the cache and remote sources, `ScheduleRepositoryImpl` (also the
  `ScheduleRefreshGateway`), `LessonFriendsRepositoryImpl`, the subject and
  teacher lesson gateways, `ScheduleChangesRepositoryImpl`,
  `DefaultScheduleChangeTracking`, `ScheduleChangesCheck`, both home card
  sources, `ScheduleWidgetDataProvider` and `DemoSchedule`. The platform
  supplies Core 2.0's `ScheduleApi`, the change notification
  (`ScheduleChangeNotifier`), the check's scheduler
  (`ScheduleChangesScheduler`) and `AppNotifier`; on Android they are Hilt's and
  reach Koin through `di/bridge/ScheduleBridge.kt` and `CoreBridge`, and the
  same bridge hands Koin's change tracking, change check, widget data provider
  and preview scenario to the Hilt-built workers, widget and debug tools. The
  phone calendar sync lives there too (`CalendarSyncRepositoryImpl`,
  `DefaultCalendarSync`, `CalendarSyncFileStore`, `MyItmoOwnScheduleSource`);
  the platform supplies the `PhoneCalendars` port and the
  `CalendarSyncScheduler`, and the bridge hands the sync to
  `CalendarSyncWorker` and the background check set and the source to
  `IcsFileExport`. The workers, the widgets and the list adapter read Koin
  through `KoinStarter`. The
  four Koin cleaners (the cache, the change history, the teacher weeks, the
  calendar sync) join sign-out's set through `SessionCleanersBridge`.

## Lessons with a teacher

`core/schedule/TeacherLessonsGateway.taughtBy(teacherIsu)` feeds the
[review editor](reviews.md#editor): the subjects of the viewer's own academic
lessons with that teacher (suggestions, newest first) and their `flowId`s
(candidate ISU flows for Backend's check). `TeacherLessonsGatewayImpl` in
`feature/schedule/data` samples the personal My ITMO schedule by Monday–Sunday
weeks (`StudyWeeks.sampled`): in the current academic year and the 3 previous
ones the weeks containing 25 September, 3 December, 24 February and 5 March,
without weeks that start after today, plus the current week, which is asked
once even when it is a sampled one. That is at most 17 requests; all of them
run at once. It keeps academic lessons (`flowTypeId == 2`) whose `teacherId` is
the teacher. It is read only: these lessons are never uploaded to Backend and
do not touch the schedule cache.

The result is a flow: after each week answers it emits everything collected so
far, newer weeks first, and it completes when every week has answered. A failed
week is left out and asked again next time; only when every week fails is the
single emission an error.

Weeks that ended before today never change. `TeacherWeeksFileStore` keeps them
in `filesDir/teacher_lessons/weeks.json` (format 1: per week's Monday, the
teacher ISU, flow and subject of each academic lesson with a teacher), with
atomic writes, excluded from backup and device transfer; weeks no longer
sampled are dropped on write. They are emitted at once without a request; the
current week is requested every time. A corrupt file or one of another format
is ignored and replaced by the next write. The
file belongs to the signed-in account: `clearSessionData()` deletes it, and an
answer requested before the clear is not stored. The recordbook is not used,
since its teachers carry no ISU.

## Friend picker

The FAB opens the friend picker (`FriendSelectorDialogFragment`, body
`FriendSelectorSheetRoute` from `:shared:feature-social`); its result arrives
through `FriendSelectionContract` fragment results. The sheet, its states and
the recent chips are in [Friend selector](friend-selector.md).

## Pending sport rows

The `Автозапись на спорт` preference (`SchedulePreferencesRepository` in
`core/schedule`, default off, DataStore) adds the user's own active sport queues
to the schedule and both schedule widgets. It never applies to friends' schedules
or the official cache.

- `PendingSportBookingsRepository` (`core/sport`, implemented in sport data)
  projects active own queues: cancelled, terminal, started and already-signed
  entries are excluded; several queues resolving to the same lesson produce one
  row; unpublished predictions use prototype dates plus two weeks, a bound real
  lesson its actual dates.
- The schedule observes the projection only when the preference is on and both
  the selected and loaded schedule belong to self. The Backend opt-in remains an
  independent gate in the data layer.
- `ScheduleUiState.Content.schedule` stays official data; `ScheduleDisplayDay`
  adds pending rows, including dates without official lessons. Rows are labelled
  waiting or predicted, never styled as a confirmed lesson, and do not change the
  lesson count. An error or empty snapshot from the optional source removes the
  pending rows; it never replaces the academic screen.
- The list keys its days by date and recomposes the whole display day, so
  live additions and cancellations render without clearing the cache or
  resetting scroll.
- A pending row opens `PendingSportDetailsBottomSheet`
  (`feature/schedule/ui/details`) with `core/navigation/PendingSportDetailsArgs`;
  the home feed opens the same sheet through `AppNavigator`.

## Behaviour

- Timeline markers and day alpha follow [`design.md`](../design.md).
- Lesson type names (`lessonTypeName` in `:shared:core`
  `core/schedule/LessonTypeNames.kt`) and the short building and room titles
  (`core/text/LocationTitles.kt`) are shared with the recordbook, the home
  cards and the widgets; type colours are the design system's extended colours
  (`ItmoTheme.extendedColors`).
- `core/navigation/ScheduleTodayRequest` is the Fragment result `MainActivity`
  sends to show today in the own schedule (the `Сегодня` shortcut, see
  [home](home.md#quick-settings-tile-and-app-shortcuts)).
- The list keeps its position across recreation (saved list state); a
  restored position is never replaced by today, and it waits for late data.
- Switching between own and a friend's schedule keeps the reader on the day and
  offset they were reading: the visible day is anchored by date, the closest later
  day is used when that date has no lessons, and a day past the initial range is
  paged in (up to four pages) before the list is shown; meanwhile the list is
  laid out but not drawn. An unreachable day opens the schedule on today
  instead.
- After the first successful load, later refresh failures show a snackbar while
  content stays; the initial failure is an explicit error state.

## Lesson details

- Every ordinary lesson card opens `LessonDetailsBottomSheet` (body
  `LessonDetailsContent` in `ui/details` of `:shared:feature-schedule`) from
  the schedule fragment's child fragment manager, in the own and in a friend's
  schedule alike. Pending sport rows never open it. A sport lesson (type 11) in the own schedule goes through
  `AppNavigator.openLessonDetails` to `MainActivity`, which finds the booking
  with the same date and start in the sport tab's data (a confirmed booking
  before a queue, then the matching section name) and opens the sport sheet
  with `Отменить`; without a match the lesson sheet appears. The sheet gets a
  `@Serializable` `LessonDetailsArgs` (`core/navigation`) built from the
  `Lesson` plus the day's date; nothing is fetched for the lesson itself.
- The sheet starts with the design system's `DetailsHeader`, which every
  details sheet shares: subject,
  type and format with the type colour, the weekday and date with the time
  range and duration, teacher, flow, room with the full building name and
  `Открыть на карте`. Then, each only when present: the `Изменения` block, the
  meeting info and password when MyITMO sends them, note. Buttons:
  `Открыть на карте` (a known building from `core/location/BuildingDirectory`,
  read from `res/raw/itmo_buildings.json`, else the raw building text) through
  the generic `geo:` intent of a `core/location/MapDestination` in
  `core/ui/navigation/MapLauncher`, and `Открыть видеозвонок` when the lesson
  carries a link (MyITMO calls the field `zoom_url`, but lessons run on any
  platform, so the link itself is not shown). No map provider setting. A card
  whose lesson carries a link shows a small camera icon next to the type.
- A pending sport row (queue or auto-sign prediction) goes through
  `AppNavigator.openPendingSportDetails`. `MainActivity` looks the lesson up in
  the sport tab's `SportBookingRepository` (refreshing once when nothing is
  cached) and opens the sport tab's own `SportCommonDetailsBottomSheet` with its
  queue position, history and `Отменить`; the cancellation runs through the
  shared `SportMyViewModel` after the usual confirmation. Only when the sport
  data has nothing about the queue does the schedule's own
  `PendingSportDetailsBottomSheet` appear: the shared header, the status as
  the kind line, one `Условия записи` card and `Открыть в спорте`.
- A teacher in the lesson or pending-sport header opens the person profile when
  the lesson carries a usable ISU. The row shows a chevron, has a 48 dp touch
  target and a localized accessibility click action. The sheet dismisses first.
  Without an ISU, including event teachers, the row remains informational with
  no chevron, ripple or click action; no name lookup is attempted.
- With `Подключение к ITMO.Widgets` and a teacher ISU the lesson sheet shows the
  tone of the teacher's AI summary as a 10 dp dot between the name and the
  chevron (`DetailsTeacher.tone`, its place kept by `reserveTone`).
  `LessonDetailsViewModel` gets it
  from `TeacherLevelsRepository` (`ARG_TEACHER_ISU`), cached for a day
  ([teacher levels](reviews.md#teacher-levels)). The place of the dot is
  reserved while the level loads or when there is none, so a late dot moves
  neither the name nor the chevron; TalkBack reads `, тон отзывов: …` after the
  teacher. Without an ISU the dot is gone, and sport sheets never show it.
- `Друзья на паре` is the only place friends on a lesson appear.
  `LessonDetailsViewModel` asks `LessonFriendsRepository` for
  `GET /api/schedule/lessons/{pairId}/friends?date=`; Backend answers with the
  viewer's accepted friends who attend that occurrence and share their schedule
  with the viewer. Without the ITMO.Widgets opt-in the block is absent; while
  loading it shows a spinner; an error offers `Повторить`; an empty answer says
  so. A friend row (`UserRow`) opens the person profile.
- `Поток` (`ic_group`) is the My ITMO flow of the lesson, for
  example `ФИЗ ПИИКТ 3.2`: `LessonDetailsArgs.flowName` is the lesson's
  `groupName`, trimmed, and a blank one hides the row. It is informational,
  without a chevron or a click action; TalkBack reads `Поток: …`. The lesson
  type is already in the kind line, so the row does not repeat it. It exists
  only in the lesson sheet: schedule cards, `PendingSportDetailsBottomSheet`
  and the sport sheets have no flow.
- `Изменения` (after the header and before the meeting info)
  shows the newest [schedule change](schedule-changes.md) of the last 30 days
  whose occurrences contain this lesson's `pairId` and date
  (`LessonDetailsViewModel.change`, from the local store only). A divider, the
  title and the change's `detailLines()`: one `было → стало` line per changed
  field of an updated lesson, or the single line of an added or cancelled one
  (`Добавлена: пн, 7 сентября, 08:20`). Without a change the block is gone;
  the header above it never moves.

## iOS

The iOS app ([iOS app](../ios.md)) shows the same schedule on the same shared
code: `ScheduleRoute` is the root of the schedule tab's stack, hosted by
`ScheduleScreen` (`iosApp/Sources/Features/Schedule/`) through
`scheduleRootScreen` (`shared/ios`, `screens/ScheduleScreens.kt`), with
`scheduleModule`, `scheduleDataModule`, `scheduleIosModule`,
`scheduleChangesIosModule` and `calendarIosModule` in `IosKoinModules`. The
route draws its own chrome and keeps clear of the bars and the demo banner.

- A lesson opens the lesson sheet (`AppRoutes.LessonDetails`) from the
  schedule, another user's schedule and the home card, as a full-height
  SwiftUI sheet around `LessonDetailsSheetRoute`, with the teacher's tone dot
  and `Друзья на паре` as on Android. «Открыть на карте» hands the place to
  Apple Maps (`PlatformActions.openMap`): the pin of a known building from the
  same `itmo_buildings.json` (bundled by path), else the building text. The
  meeting link opens in Safari; when nothing takes either, the sheet says
  `schedule_map_unavailable` or `link_open_failed`.
- A queued or predicted sport row opens the schedule's pending sport sheet,
  whose sport button selects the sport tab ([sport](sport.md#ios)).
- «Расписание друзей» opens the friend picker as a sheet; its choice comes back
  to the schedule through the router ([friend selector](friend-selector.md#ios)).
- The `today` entry route (the App Shortcut and the quick action) puts the own
  schedule back on today, as `ScheduleTodayRequest` does on Android; a lesson
  widget's tap opens the schedule tab as it is.
- Another user's schedule (`AppRoutes.UserSchedule`, from a profile) and the
  changes (`AppRoutes.ScheduleChanges`, from the home card) open on the
  selected tab's stack ([schedule changes](schedule-changes.md#ios)).
- The calendar sync and the `.ics` export are on ([calendar](calendar.md#ios)).
- The lesson and day widgets read the timeline the app writes
  ([widgets](widgets.md#ios)).
- Tests: `ScheduleIosModuleTest` (the graph resolves with no request, the
  bundled buildings; `scripts/ios/test.sh kn :shared:feature-schedule`) and
  `UITests/ScheduleUITests` on the demo session (days from today, the lesson
  sheet with friends and the Maps hand-off, a drag down closing it, the picker
  round trip and a profile from it, the `today` route, changes from home, a
  lesson from home, another user's schedule, AX1).
