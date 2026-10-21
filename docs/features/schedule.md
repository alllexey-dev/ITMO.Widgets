# Schedule

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

`feature/schedule` is the Расписание tab: an infinite feed of days with a week strip, the month sheet, the
friend picker and a friend's schedule in the same feed, and the lesson sheet (`Лист пары`) that every lesson list
opens. The background change check is in [schedule changes](schedule-changes.md), the phone calendar and `.ics`
export in [calendar](calendar.md).

## Modules

- `:shared:feature-schedule` holds domain, Compose-free view models ([ADR 0031](../decisions/0031-swiftui-ios-screens.md)),
  the Android screens in `androidMain` ([ADR 0035](../decisions/0035-ios-without-compose.md)) and data. `:app` keeps
  only the Android widgets, workers, the change notification and the phone calendar; `ScheduleRulesTest` (`:konsist`)
  keeps them there. iOS draws the same view models in SwiftUI (`iosApp/Sources/Features/Schedule/`).
- My ITMO comes through MyItmoApi, Backend through Core 2.0's `ScheduleApi`: `syncLessons` (best-effort upload of
  own lessons, skipped while Backend already accepted the same snapshot within 24 h, `BackendUploadPreferences`),
  `userLessons` (another user's schedule) and `friendsOnLesson`, all behind `BackendGate`. Every
  network call checks `DemoMode` first (`DemoSchedule`).
- Screens take state and callbacks only; the entry performs effects (the meeting link, profiles, Спорт, navigation).

### Files

All schedule files are kotlinx JSON (`ScheduleStoreJson`) with the keys 2.2 wrote, so 2.2 files read unchanged in
both directions (`app/src/androidTest/.../upgrade/stores/`). Stores write through `AtomicTextFile` (`<name>.new`,
sync, atomic move).

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
- Cache: a file per user and date, 24-hour TTL. A refresh keeps the loaded range (including pages fetched by
  scrolling) and replaces it with one snapshot after success; partial lists are never published. Dates missing from
  the response are removed; other ranges and users are kept. Refreshing the own schedule skips sport requests while
  queue rows are off.
- `Forbidden` for a foreign schedule removes that user's cache and content only. A generation check stops a late
  response from restoring a revoked cache. Ordinary network failures keep cached content.
- One instance of each repository, gateway and source per process (`scheduleDataModule`); workers, widgets and the
  list adapter reach Koin through `KoinStarter`. Sign-out runs four `SessionDataCleaner`s: cache, change history,
  teacher weeks, calendar sync.
- A room booking comes from MyITMO as a lesson of the plain type in the booking flow (`flow_type_id` 5): the
  schedule gives it `ROOM_BOOKING_TYPE_ID`, named `Бронь помещения`.

## Lessons with a teacher

`core/schedule/TeacherLessonsGateway.taughtBy(teacherIsu)` feeds the [review editor](reviews.md#editor): subjects of
the viewer's own academic lessons with that teacher (newest first) and their `flowId`s. `TeacherLessonsGatewayImpl`
samples the personal My ITMO schedule by Monday-Sunday weeks (`StudyWeeks.sampled`): in the current and 3 previous
academic years the weeks containing 25 September, 3 December, 24 February and 5 March (not starting after today),
plus the current week once. At most 17 requests, all in parallel. Keeps `flowTypeId == 2` lessons with that
`teacherId`. Read only: never uploaded, never touches the schedule cache.

- The result is a flow: after each week it emits everything so far, newer weeks first. A failed week is skipped and
  retried next time; only when all fail is the single emission an error.
- Past weeks never change: `TeacherWeeksFileStore` keeps them in `filesDir/teacher_lessons/weeks.json` (format 1,
  atomic, excluded from backup); weeks no longer sampled are dropped on write. The current week is always requested.
  A corrupt or foreign-format file is ignored and replaced.
- `clearSessionData()` deletes the file; an answer requested before the clear is not stored. The recordbook is not
  used (its teachers carry no ISU).

## What a teacher gives now

`core/schedule/TeacherNowGateway.lessonOf(teacherIsu)` is the data of the person card's «Сейчас ведёт». It asks My
ITMO's teacher timetable (`ScheduleApi.getTeacherSchedule`, `/api/schedule/teacher/{isu}`) for today on every call;
the caller decides how often. The lesson running at academic now, else the next one today, else null; room bookings
(`flowTypeId` 5) are left out and lessons with the same time, subject and place for several groups merge into one.
A student's or unknown ISU answers with days without lessons, so null. Demo answers from the demo week. Read only:
never uploaded, nothing cached.

## The feed

`ScheduleFeedViewModel` reads a window of whole weeks around the visible day from `ScheduleDays` (the same source as
Сегодня's `DayLessonsGateway`), re-evaluated every minute.

- Every day of the window that a refresh answered for is a row: a day with lessons is a sticky header over the
  shared lesson timeline (`core/lesson/LessonTimelines`, the rules of Сегодня); a weekday without lessons is one
  short row; a free Saturday and Sunday are one `Выходные` row; a week starts with MyITMO's week number (its parity
  is never shown). Days no refresh answered for yet are left out, so a missing day never reads as free.
- The week strip pages by weeks with a swipe: settling on a week shows its Monday (today on the current week), a tap
  shows that day. Pages and settled callbacks identify a week by its Monday, never by a mutable index. Only a settled
  user gesture selects a week; programmatic synchronization and prepending pages do not. A new date jump wins over a
  stale settled callback; a later user gesture takes over from the old jump.
- The view reports the day at its top and its ends: the window grows by two weeks there. A jump (a strip day, the
  month sheet, `Сегодня`, the `today` link) brings the day's weeks in and scrolls there once (`FeedJump`). A free
  Sunday lands on its `Выходные` row. Each direction has at most one expansion in flight; identical range requests
  share their load within one owner generation, and switching away and back starts a new generation. On iOS a lazy
  list positions a jump twice after layout; cancellation stops the old jump before its second pass, and visible-day
  callbacks cannot overwrite a jump still pending in the ViewModel.
- The first show loads the window once; each return of the app to the foreground (`AppForeground`) reloads it
  silently, a tab switch, a pop or a sheet closing does not. A pull reloads it and, on the own schedule, brings the
  phone's calendar up to date when it is synced. Nothing cached and a failed load: the error state; a failure behind
  content: a snackbar (Android) or a banner (iOS).
- A friend's schedule is the same feed with `Вы` on the lessons the viewer's own cache has on the same occurrence and
  `Вернуться к своему`. The friend survives process death (`SavedStateHandle`). A schedule closed to the viewer
  (`Forbidden`, `NotFound`, ...) is the error state. `AppRoutes.UserSchedule` (from a profile) is the same feed on a
  pushed screen, without the month sheet and picker.
- `TabRequest.ScheduleToday` (the `today` shortcut, the widget) waits in the navigator until `ScheduleTabRoot`
  consumes it once: the own schedule on today. Another user's schedule never reads it.

## The month sheet

`AppRoutes.ScheduleMonth(date, userIsu)`: the month of the visible day with a dot per lesson (at most four; cancelled
lessons and queues do not count). Each month shown is refreshed once in the background. `Открыть` answers the feed
with `DatePick` (Android's result bus, iOS `AppRouter.open(_:onResult:)`) and closes the sheet.

## Friend picker

`AppRoutes.FriendSelector(selectedIsu)` (`FriendSelectionContract.NO_USER_ISU` for the own schedule), content height,
opened by the feed's friends button. Friendships and privacy are in [Social](social.md); whether a schedule is open is
Backend's answer (`sharing.schedule`), the picker never decides it.

- `FriendPickerViewModel` reads `FriendRepository` (social's, behind the opt-in): `Моё` first, then the friends whose
  schedule is open by name, then the others by name. Each open row says where the person is now from today's
  lessons of their schedule (refreshed once per opening); nothing until today is known.
- A friend whose schedule is closed to the viewer is dimmed and cannot be picked; no lock and no reason (someone
  else's privacy is not a gate). No people search.
- Without the connection a gate offers `Подключить`; an error offers `Повторить`.
- One tap sends one `FriendPick` and closes the sheet (Android: the Navigation 3 result bus, taken by
  `ScheduleTabRoot`; iOS: `AppRouter.deliver(_:from:)`). Closing delivers nothing.

## Pending sport rows

`Автозапись на спорт` (`SchedulePreferencesRepository`, default on; a stored off stays off) adds the own active sport
queues to the schedule, Сегодня and both schedule widgets, as lessons with `очередь 2 из 5` (Backend's place in the
queue; plain `очередь` while the place is unknown or the lesson is only predicted).

- `PendingSportBookingsRepository` (`core/sport`) excludes cancelled, terminal, started and already-signed entries;
  queues on the same lesson collapse into one row; unpublished predictions use prototype dates plus two weeks.
- Observed only when the preference is on and only for the own schedule; never for friends or the official cache.
  The Backend opt-in stays an independent gate in the data layer.
- A queue is never `идёт` or `далее` and does not count as a lesson (the day's count, the strip's and the month's
  dots). An error or empty snapshot from this source only removes the queues.

## Lesson sheet

`Лист пары` is one sheet for every lesson list (the feed, Сегодня, the bell, the subject's Пары):
`AppRoutes.LessonDetails` for a timetable lesson, `AppRoutes.PendingSportDetails` for an own sport queue. The
lesson travels in the route's arguments (`LessonSheetArgs`, one JSON string in the `SavedStateHandle`); nothing is
fetched for the lesson itself. On Android the feed and Сегодня open a sport lesson or an own queue through
`SportDetailsRedirect`: the sport tab's lesson sheet when `SportBookingsHolder` finds the booking, this sheet
otherwise; iOS always opens this sheet.

- The room big (`Онлайн` for an online lesson) with the building's full address (`BuildingDirectory`, else MyITMO's
  text). Actions, only the relevant ones: `Маршрут` for an in-person lesson (the [campus map](map.md) with the route
  from the day's previous lesson), `Видео` (the meeting link, opened, never shown), `Друзья`, `Ссылки` and `Предмет`
  from the first `LessonSubjectSource` that knows the subject (an open set in `core/lesson`).
- The tiles never reflow once shown: their row keeps its height until the opt-in and the subject are known (the
  subject at most 1 s), and `Друзья` stays in place, disabled, while the friends load and when nobody is on the
  lesson.
- The recordbook's source (`RecordbookLessonSubjects`) takes the record book subject of the lesson's half-year that
  the subject page would bind (`SubjectContextResolver`: a confirmed binding or the same id, else the one subject of
  that name not bound elsewhere). `Предмет` opens its [page](subject-page.md), `Ссылки` the page on `Материалы`;
  both stay hidden when no record book subject matches.
- The sheet opens often, so this source reads caches and may be stale: the record book list is the repository's
  memory cache, the links are the ones stored on the device. Only a subject whose links the device has never loaded
  gets one links request (`SubjectLinksRepository.refresh`, behind the opt-in) when its sheet opens.
- The newest found [change](schedule-changes.md) of the occurrence, field by field.
- The teacher with a directory photo (`core/social/PersonPhotosRepository`, no Backend opt-in) and the tone of
  the reviews in words (`TeacherLevelsRepository`, behind the opt-in), which opens the person; an own queue or a
  booked sport lesson as a row that leads to Спорт. `LessonSheetViewModel` seeds the cached photo URL and loads it
  independently of the lesson's other content, publishing `LessonTeacher.pictureUrl`; both platforms use their
  standard avatar components, falling back to initials without a photo or a known ISU.
- `Друзья на паре`: `GET /api/schedule/lessons/{pairId}/friends?date=`; Backend returns accepted friends who attend
  and share their schedule with the viewer. Absent without the opt-in or on an error.

## iOS

The vertical feed observes visible target IDs with `onScrollTargetVisibilityChange`; it has no `scrollPosition`
binding that could retarget a lazy section during sheet layout. Headers and timelines have separate IDs, and an
explicit date jump targets the concrete header, never the union of a section's views. That jump stays pending
until its rows finish loading, and a vertical drag or lesson tap cancels it. Visible-date updates and edge
pagination follow real vertical drags/deceleration, not layout changes during a sheet transition.
`ScheduleUITests.testRepeatedLessonSheetsKeepTheFeedPosition` checks three open/close cycles on today and after a
partial scroll, measuring the same lesson's frame each time.

The shell identifies a sheet by its complete route, including arguments, and keys its content by that identity.
Quickly closing one lesson and opening another therefore recreates the sheet's `@State` ViewModel even when the
dismissal has not finished. The UI regression also replaces lesson entry routes without an intervening empty
presentation and checks both room and time, including different pairs of the same subject.

SwiftUI over the same view models ([iOS app](../ios.md)): `ScheduleScreen.swift` has the feed, `ScheduleSheets.swift`
the lesson sheet, the month sheet and the friend picker (`Routes+Schedule.swift`). The week strip stays a few points
off the bar above it: a scroll view touching the bar's edge takes the bar's height as a vertical content inset and
draws its days outside its clip (`ScheduleUITests.testWeekStripIsDrawnOnLaunchAndAfterATabSwitch` checks the
pixels). iOS animates programmatic week changes unless Reduce Motion is on. See also
[schedule changes](schedule-changes.md#ios), [calendar](calendar.md#ios), [widgets](widgets.md#timeline-widgetkit).
