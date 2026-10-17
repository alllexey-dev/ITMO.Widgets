# Schedule

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

`feature/schedule` is the Расписание tab: an infinite feed of days with a week strip, the month sheet, the
friend picker and a friend's schedule in the same feed, and the lesson sheet (`Лист пары`) that every lesson list
opens. The spec is the design lab's (`vibe/design-lab/ux-native/DECISIONS.md`, "Расписание", "Лист пары"). The
background change check is in [schedule changes](schedule-changes.md), the phone calendar and `.ics` export in
[calendar](calendar.md).

## Modules

- `:shared:feature-schedule` (`commonMain`, package `dev.alllexey.itmowidgets.feature.schedule`): `domain`
  (lessons, `ScheduleDays`, changes, calendar events, widget selector and timeline), `presentation` (`feed`,
  `month`, `friends`, `lesson`: Compose-free view models with `UiText` keys, [ADR 0031](../decisions/0031-swiftui-ios-screens.md)),
  `ui` (the Android Compose screens on the kit, `ui/preview/ScheduleSamples` for both platforms' references) and
  `data` (cache, file stores, My ITMO and Backend sources, `ScheduleDaysImpl`, calendar sync, `DemoSchedule`). Koin:
  `scheduleModule`, `scheduleDataModule`. `androidMain` only supplies `ScheduleFileSystem`; `iosMain` adds the iOS
  change check, the widget timeline writer and the Koin parameters of the SwiftUI screens (`ScheduleIosParameters.kt`).
- My ITMO comes through MyItmoApi 2.x, Backend through Core 2.0's `ScheduleApi`: `syncLessons` (best-effort upload
  of own lessons), `userLessons` (another user's schedule) and `friendsOnLesson`, all behind `BackendGate`. Every
  network call checks `DemoMode` first (`DemoSchedule`).
- `:app` keeps only Android: the widgets and the list adapter service (`ui/widget`), workers, change notification
  and refresh receiver (`work`), `AndroidPhoneCalendars` and `IcsFileExport` (`data/calendar`),
  `ScheduleWidgetSnapshotStoreImpl`. Koin: `androidScheduleModule` (`di/ScheduleModule.kt`). `ScheduleRulesTest`
  (`:konsist`) keeps the widget providers, receiver, adapter service and workers in `:app` under their packages.
- Screens: `ScheduleTabRoot` (`app/shell/entries/ScheduleEntries.kt`) hosts `ScheduleFeedRoute` and registers
  `AppRoutes.UserSchedule`, the lesson sheet (`AppRoutes.LessonDetails`, `AppRoutes.PendingSportDetails`), the month
  sheet (`AppRoutes.ScheduleMonth`) and the friend picker (`AppRoutes.FriendSelector`). Screens take state and
  callbacks only; the entry performs effects (the meeting link, profiles, Спорт, navigation). iOS draws the same view
  models in SwiftUI ([iOS](#ios)).

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

- `ScheduleDaysImpl` joins the cache of one user with the found changes (the newest on each occurrence; a cancelled
  lesson of the own schedule stays on its old day, struck through) and, for the own schedule, the sport queues while
  their rows are on; for another user it marks `Вы` on the occurrences the own cache has too.
- Day projection reads the timestamp of an already hydrated cache entry through `peekTimestamp`, without
  reopening its gzip file or decoding JSON for every date on changes/queue updates. The 24-hour TTL still applies.
  Refreshing the own schedule skips sport requests while queue rows are disabled.
- Cache: a file per user and date, 24-hour TTL. A refresh keeps the loaded
  range (including pages fetched by scrolling) and replaces it with one
  snapshot after success; partial lists are never published. Dates missing
  from the response are removed; other ranges and users are kept.
- `Forbidden` for a foreign schedule removes that user's cache and content
  only. A generation check stops a late response from restoring a revoked
  cache. Ordinary network failures keep cached content.
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

## The feed

`ScheduleFeedViewModel` reads a window of whole weeks around the visible day from `ScheduleDays`
(`ScheduleDaysImpl`, the same source as Сегодня's `DayLessonsGateway`) and `ScheduleFeedPresenter` draws it, re-evaluated
every minute. Timelines and weeks are projected on the default dispatcher only when data, known dates,
refresh status or academic time changes; selecting another visible day reuses that body and those weeks:

- Every day of the window that a refresh answered for is a row: a day with lessons is a sticky header
  (`Понедельник, 7 сентября`, `сегодня`, `5 пар`) over the kit's timeline built by `core/lesson/LessonTimelines` (the
  rules of Сегодня: `идёт`, `далее`, `Окно 2 ч 10 мин`, change notes, `очередь 2 из 5`, `записаны`, `онлайн`); a weekday
  without lessons is one short `Вт, 15 сентября — пар нет` row; a free Saturday and Sunday are one `Выходные` row; a
  week starts with `2-я неделя, чётная` (MyITMO's week number). Days no refresh answered for yet are left out, so a
  missing day never reads as free.
- The root header is static: `Сентябрь ▾` (the visible month) over the visible week; the title opens the month sheet,
  the friends button the picker. Off the current week `Сегодня` shows (Android: a FAB; iOS: a header button).
- The week strip pages by weeks with a swipe (no arrows; Android hands the tab swipe over at its ends,
  `tabSwipeHandover`): settling on a week shows its Monday (today on the current week), a tap shows that day; a dot per
  lesson that takes place, the last one in the warning colour when a lesson of the day changed.
  Pages and settled callbacks identify a week by its Monday, never by a mutable index. Only a settled user gesture
  selects a week; programmatic synchronization and prepending pages do not select it again or reset its weekday.
  iOS animates programmatic week changes unless Reduce Motion is on. A new date jump wins over a stale settled
  callback; a later user gesture explicitly takes over from the old jump.
- The view reports the day at its top (`onVisibleDate`) and its ends (`onNearStart`, `onNearEnd`): the window grows by
  two weeks there and those weeks load. A jump (a strip day, the month sheet, `Сегодня`) brings the day's weeks in
  (or moves the window when it is far) and asks the view to scroll there once (`FeedJump`). Edge observation stays
  attached across row updates. Each direction has at most one expansion in flight, independent of refresh;
  identical range requests share their load within one owner generation. Switching away and back starts a new
  generation rather than reusing the old account's in-flight request.
  On iOS a lazy list positions a jump twice after layout; cancellation stops the old jump before its second pass
  and acknowledgement. Visible-day callbacks cannot overwrite a jump that is still pending in the ViewModel.
- The first show loads the window once; each return of the app to the foreground (`AppForeground`) reloads it
  silently, a tab switch, a pop or a sheet closing does not. A pull reloads it and, on the own schedule, brings the
  phone's calendar up to date when it is synced. Nothing cached and a failed load: `Не удалось загрузить` with `Повторить`; a failure behind
  content: a snackbar (Android) or a banner (iOS) with the error.
- A friend's schedule is the same feed with `Расписание` over the friend's name and group, `Вы` on the lessons the
  viewer's own cache has on the same occurrence, and `Вернуться к своему`. The friend survives process death
  (`SavedStateHandle`). A schedule closed to the viewer (`Forbidden`, `NotFound`, ...) is the error state.
- `AppRoutes.UserSchedule` (from a profile) is the same feed on a pushed screen, without the month sheet and picker.
- `TabRequest.ScheduleToday` (the `Сегодня` shortcut, the widget) waits in the navigator until `ScheduleTabRoot`
  consumes it once: the own schedule on today. Another user's schedule never reads it.

## The month sheet

`AppRoutes.ScheduleMonth(date, userIsu)`, content height: `ScheduleMonthViewModel` shows the month of the visible day
with a dot per lesson (at most four; cancelled lessons and queues do not count), today ringed, the chosen day filled,
and one `Открыть четверг, 17 сентября`. Each month shown is refreshed once in the background. No day summary and no
quick chips (the spec). `Открыть` answers the feed (`DatePick` on Android's result bus, iOS through the router) and
closes the sheet.

## Friend picker

`AppRoutes.FriendSelector(selectedIsu)`, content height ([details](friend-selector.md)): `FriendPickerViewModel` lists
the own schedule (`Моё`) and the friends from `FriendRepository` (social's, behind the opt-in) with their avatars and
where they are now from today's lessons of their schedule (`на паре до 13:10`, `окно до 15:20`, `первая пара в 10:00`,
`пары закончились`, `сегодня без пар`). One tap picks and closes. A friend whose schedule is closed to the viewer is
dimmed and cannot be picked, with no reason given (someone else's privacy is not a gate). Without the connection a gate
offers `Подключить`.

## Pending sport rows

`Автозапись на спорт` (`SchedulePreferencesRepository`, default off) adds the
own active sport queues to the schedule (as lessons with `очередь 2 из 5`, Backend's place in the queue; plain
`очередь` while the place is unknown or the lesson is only predicted), Сегодня and both schedule widgets.

- `PendingSportBookingsRepository` (`core/sport`) excludes cancelled,
  terminal, started and already-signed entries; queues on the same lesson
  collapse into one row; unpublished predictions use prototype dates plus two
  weeks.
- Observed only when the preference is on and only for the own schedule; never for friends or the official cache.
  The Backend opt-in stays an independent gate in the data layer.
- A queue is never `идёт` or `далее` and does not count as a lesson (the day's count, the strip's and the month's
  dots). An error or empty snapshot from this source only removes the queues.

## Behaviour

- Shared with recordbook, home and widgets: `core/schedule/LessonTypeNames.kt`, `core/text/LocationTitles.kt`.
- A room booking comes from MyITMO as a lesson of the plain type in the booking flow (`flow_type_id` 5): the
  schedule gives it `ROOM_BOOKING_TYPE_ID`, named `Бронь помещения` and coloured `tertiary`.

## Lesson sheet

`Лист пары` is one sheet for every lesson list (the feed, Сегодня, the bell, the subject's Пары): `AppRoutes.LessonDetails`
for a timetable lesson, `AppRoutes.PendingSportDetails` for an own sport queue, content height. `LessonSheetViewModel`
reads the route's arguments (`LessonSheetArgs`, one JSON string in the `SavedStateHandle`); nothing is fetched for the
lesson itself.

On Android the feed and Сегодня open a sport lesson or an own queue through `SportDetailsRedirect`
(`app/shell/entries/SportEntries.kt`): the sport tab's lesson sheet when `SportBookingsHolder` finds the booking, this
sheet otherwise. On iOS there is no shell route to the sport tab's sheet yet, so they open this sheet.

- The lesson's type and day as the sheet's title, the status pill (`отменена`, `изменена`, `очередь`, `идёт ещё
  48 мин`, `через 2 ч 58 мин`), the subject, the room big (`Онлайн` for an online lesson) with the time and its length
  and the building's full address (`BuildingDirectory`, else MyITMO's text).
- Actions, only the relevant ones: `Видео` (the meeting link, opened, never shown), `Друзья 2` (scrolls to the friends
  on the lesson), `Ссылки 3` and `Предмет` from the first `LessonSubjectSource` that knows the subject (an open set in
  `core/lesson`). `Маршрут` waits for the map (phase 8).
- The recordbook's source (`RecordbookLessonSubjects`) takes the record book subject of the lesson's half-year that the
  subject page would bind to the lesson's schedule subject (`SubjectContextResolver`: a confirmed binding or the same
  id, else the one subject of that name not bound elsewhere). `Предмет` opens its [page](subject-page.md), `Ссылки 3`
  the page on `Материалы` (`AppRoutes.RecordbookSubject(materials = true)`), shown only with links. Both stay hidden
  when no record book subject matches. The count is what `Материалы` lists: the current links, 0 without the
  connection (the tab shows its gate then).
- The sheet opens often, so this source reads caches and may be stale: the record book list is the repository's
  memory cache (filled by Зачётка, the subject page or the first sheet that needs it, with the same My ITMO requests),
  and the links are the ones stored on the device as of the subject page's last refresh. The sheet never refreshes
  either and never calls Backend; the demo session gets the demo record book and links.
- The newest found [change](schedule-changes.md) of the occurrence: `было` struck through `→ стало` per field, or the
  summary of an added or cancelled lesson, with `Изменено вчера в 20:14`.
- The note and the meeting's password; the teacher with the tone of the reviews in words (`в основном хвалят`, from
  `TeacherLevelsRepository` behind the opt-in), which opens the person; the flow; an own queue (`Очередь`, `Запишем
  сами, когда освободится место`) or a booked sport lesson (`Записаны`) as a row that leads to Спорт.
- `Друзья на паре`: `GET /api/schedule/lessons/{pairId}/friends?date=`; Backend returns accepted friends who attend
  and share their schedule with the viewer. Absent without the opt-in or on an error.

## iOS

SwiftUI over the same view models ([iOS app](../ios.md)): `ScheduleScreen` and `UserScheduleScreen`
(`iosApp/Sources/Features/Schedule/ScheduleScreen.swift`) draw the feed under `itmoRootHeader` (with the month menu)
or the stack's navigation bar, the strip as a paged `TabView` of `ItmoWeekStrip`s and the days as a `LazyVStack` with
pinned section headers; `ScheduleSheets.swift` has the lesson sheet, the month sheet and the friend picker
(`Routes+Schedule.swift`, all `.swiftUISheet`). The picker and the month sheet answer through `AppRouter.open(_:onResult:)`
(`FriendPick`, `DatePick`). Snapshot references: `ScheduleSnapshotTests`. See also
[schedule changes](schedule-changes.md#ios), [calendar](calendar.md#ios), [widgets](widgets.md#timeline-widgetkit).
