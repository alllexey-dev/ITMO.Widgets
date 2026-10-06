# Schedule

`feature/schedule` shows the own academic schedule from MyITMO, a friend's
schedule chosen through the picker, and optional pending sport rows. It also
checks the own schedule for changes in the background
([schedule changes](#schedule-changes)) and puts it into the phone's calendar
or a `.ics` file ([calendar](#calendar)).

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
  hydrated, so `loadInitialSchedule` publishes `Content(loadingMore = true)` at
  once and only a screen with nothing cached shows the skeleton
  (`schedule_skeleton`) until the cache flow answers.
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
  and preview scenario to the Hilt-built workers, widget and debug tools. Only
  the phone calendar sync, the `.ics` export and the widget snapshot file
  (`ScheduleWidgetSnapshotStoreImpl`) stay in `:app`. The three Koin cleaners
  (the cache, the change history, the teacher weeks) join sign-out's set
  through `SessionCleanersBridge`.

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

`feature/friendselector` is a bottom sheet opened from the schedule FAB and
answered through `FriendSelectionContract` fragment results.

- Recent chips (own schedule first, then up to five recent friends), a
  `Друзья / Все` toggle, a search field, the list and a filled apply button.
- `Друзья` filters the loaded friend list locally by name, ISU or group. `Все`
  searches ITMO.Widgets users by name through `PeopleSearchRepository` after a
  300 ms debounce and shows only registered people.
- A row is selectable only when its schedule is open (`sharing.schedule`).
  Closed rows show a lock and open the public profile on tap; long-press opens
  the profile for any row. Applying records the choice in the recent history.
- Recent-chip identities and order are fixed when the loaded selector first opens
  and survive view recreation. Pending taps change only selection markers; a new
  choice enters the recent history only after Apply and appears on the next opening.
  Profile refreshes update metadata in place; removed or private schedules stop
  being selectable without reordering the remaining chips.
- The own chip reads the current user from the picker state, so an empty or
  failed friend list still shows the avatar.
- Filtering, the pending choice and the recent-chip order live in
  `FriendSelectorViewModel`; the pending ISU and the chip order survive process
  death through its `SavedStateHandle`.

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
- The adapter diffs the whole display day, so live additions and cancellations
  render without clearing the cache or resetting scroll.
- A pending row opens `PendingSportDetailsBottomSheet`
  (`feature/schedule/ui/details`) with `core/navigation/PendingSportDetailsArgs`;
  the home feed opens the same sheet through `AppNavigator`.

## Behaviour

- Timeline markers and day alpha follow [`design.md`](../design.md).
- Lesson type colours and names (`core/ui/LessonTypes.kt`) and the short
  building and room titles (`core/ui/LocationTitles.kt`) are shared with the
  recordbook's lesson rows.
- `core/navigation/ScheduleTodayRequest` is the Fragment result `MainActivity`
  sends to show today in the own schedule (the `Сегодня` shortcut, see
  [home](home.md#quick-settings-tile-and-app-shortcuts)).
- The list snapshots its scroll position before the view is destroyed; restoration
  waits for data, and adapter callbacks never touch an old view.
- Switching between own and a friend's schedule keeps the reader on the day and
  offset they were reading: the visible day is anchored by date, the closest later
  day is used when that date has no lessons, and a day past the initial range is
  paged in (up to four pages) before the list is shown. An unreachable day opens
  the schedule on today instead.
- After the first successful load, later refresh failures show a snackbar while
  content stays; the initial failure is an explicit error state.

## Lesson details

- Every ordinary lesson card opens `LessonDetailsBottomSheet` from the schedule
  fragment's child fragment manager, in the own and in a friend's schedule alike.
  Pending sport rows never open it. A sport lesson (type 11) in the own schedule goes through
  `AppNavigator.openLessonDetails` to `MainActivity`, which finds the booking
  with the same date and start in the sport tab's data (a confirmed booking
  before a queue, then the matching section name) and opens the sport sheet
  with `Отменить`; without a match the lesson sheet appears. The sheet gets a `Serializable`
  `LessonDetailsArgs` built from the `Lesson` plus the day's date; nothing is
  fetched for the lesson itself.
- The sheet starts with the header every details sheet shares
  (`view_details_header.xml`, bound through `core/ui/DetailsHeader.kt`): subject,
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
  chevron (`fact_mark` in `item_sport_detail_fact.xml`,
  `ViewDetailsHeaderBinding.bindTeacherLevel`). `LessonDetailsViewModel` gets it
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
  so. A friend row opens the person profile.
- `Поток` (`flow_fact`, `ic_group`) is the My ITMO flow of the lesson, for
  example `ФИЗ ПИИКТ 3.2`: `LessonDetailsArgs.flowName` is the lesson's
  `groupName`, trimmed, and a blank one hides the row. It is informational,
  without a chevron or a click action; TalkBack reads `Поток: …`. The lesson
  type is already in the kind line, so the row does not repeat it. It exists
  only in the lesson sheet: schedule cards, `PendingSportDetailsBottomSheet`
  and the sport sheets have no flow.
- `Изменения` (`changes_card`, after the header and before the meeting info)
  shows the newest [schedule change](#schedule-changes) of the last 30 days
  whose occurrences contain this lesson's `pairId` and date
  (`LessonDetailsViewModel.change`, from the local store only). A divider, the
  title and the change's `detailLines()`: one `было → стало` line per changed
  field of an updated lesson, or the single line of an added or cancelled one
  (`Добавлена: пн, 7 сентября, 08:20`). Without a change the block is gone;
  the header above it never moves.

## Schedule changes

The application notices changes of the own personal schedule by itself and
tells the user about them. The check runs only on the device and never talks to
Backend (decision [0013](../decisions/0013-schedule-changes-on-device.md)).

### Background check

- `ScheduleChangesWorker` (`feature/schedule/work`) runs the unique periodic
  work `schedule-changes-check`: every 2 hours with `NetworkType.CONNECTED`,
  exponential backoff from 15 minutes, tag `schedule-changes`.
  `ExistingPeriodicWorkPolicy.UPDATE` keeps the enrolment time, so enqueuing it
  again on every start does not push the next run away. The worker gets
  `ScheduleChangesCheck` through the `ScheduleChangesEntryPoint` entry point,
  not `@HiltWorker` (see `QrWidgetEntryPoint`).
  `WorkManagerScheduleChangesScheduler` enqueues and cancels it, and
  `AndroidScheduleChangeNotifier` posts the notification; all three live in
  `feature/schedule/work`.
- `ScheduleChangesCheck.run()` ends `SKIPPED` without a request when there is no
  refresh token or the switch is off. Otherwise it runs
  `ScheduleChangesRepository.check()` and then delivers the notification, even
  when the check failed, so changes found in the quiet hours are delivered by
  the first run after them whatever the network does. The outcome is
  `outcomeOf(errors)` from `core/work`, shared with the mark check:
  `Unauthorized` and success end the run; another error asks for a retry,
  which `workResultOf` grants twice (`runAttemptCount < 2`) before it gives up
  until the next period. The quiet hours are `QuietHours` from the same
  package.
- A network failure before any answer (`UnknownHostException` and other
  `IOException`s anywhere in the cause chain, `isCausedByNetworkFailure` in
  `core/network`), including a My ITMO token refresh that could not reach
  ITMO.ID, is `AppError.Network`, not `Unauthorized`: the run is retried
  through `outcomeOf` and the snapshot and the changes stay as they were.
- `ScheduleChangeTracking` (`core/schedule`, implemented by
  `DefaultScheduleChangeTracking`) keeps the work in line with the session and
  the switch: `syncWork()` enqueues it with a refresh token and the switch on
  and cancels it otherwise. It runs on application start (`@ApplicationScope`),
  after sign-in and when the switch changes; `stopWork()` runs before a session
  change. Cancelling removes both the periodic and the one-off work.
- The check asks `MyItmoApi.getPersonalSchedule(today, today + 7)` itself,
  8 days with Moscow dates from `AcademicTimeProvider`, like
  `TeacherLessonsGatewayImpl`. It neither fills the schedule cache nor uploads
  lessons to Backend, with the ITMO.Widgets connection too; the screen and the
  widgets keep refreshing the cache with their own requests.
- Android picks the moment of a run. Doze, App Standby and vendor limits can
  delay a check by hours or stop it for an application that is not exempt from
  battery optimisation. Prompt delivery is not promised.
- On Xiaomi (MIUI, HyperOS) the default battery mode `Умный режим` cuts the
  network of a backgrounded application although the `CONNECTED` constraint
  holds, so every background run fails with `UnknownHostException` and is only
  retried; `Без ограничений` lifts it. While the check is on and Android
  restricts the application, the schedule settings show the row
  `Работа в фоне` that opens the right system page
  ([settings](../settings.md#schedule)).

### Snapshot and comparison

Only academic lessons (`flowTypeId == 2`, `ACADEMIC_FLOW`) are compared; sport
(3) and room bookings (5) are ignored. `academicSnapshot()` builds a
`ScheduleSnapshot` of the window from the mapped lessons: per lesson the
`pairId`, date, start, end, subject, type, flow id and name, teacher ISU and
name, room, building and format; blank strings become `null`, and lessons with
the same positive `pairId` collapse into the earliest one.

`ScheduleDiff.compare(previous, current, now)` is pure, with `now` in Moscow
time:

1. The overlap `O` of both windows; when it is empty (a snapshot older than
   8 days) nothing changed.
2. A lesson with the same positive `pairId` in both snapshots is the same
   lesson wherever its dates are, so a move from yesterday or to the new last
   day of the window is a move.
3. The rest is linked only among lessons dated in `O`, grouped by subject, flow
   and type: the same date and start first, then pairwise in time order.
4. What stays unlinked in `O` is `CANCELLED` (old) or `ADDED` (new). Days that
   left the window and the new last day are never changes; a reordered answer
   is not a change either.
5. A linked lesson compares `TIME` (date, start or end), `FORMAT` (`formatId`),
   `PLACE` (room or building, trimmed, spaces collapsed, case ignored, blank as
   missing) and `TEACHER` (the ISUs when both are known, otherwise the
   normalized names). No difference is no change; otherwise it is one `UPDATED`
   with every changed field, so a move to another room is one change
   `{TIME, PLACE}`.
6. A change is dropped when every side of it has ended by `now`; a move from a
   past slot to a future one stays.
7. Changes are ordered by the soonest start of their sides, the subject and the
   `pairId`.

`ScheduleChangesRepositoryImpl.check()` around the comparison:

- Without a snapshot the answer becomes the snapshot (`Baseline`) and nothing
  changed: the first check after installation, sign-in or switching on.
- An answer without academic lessons while the snapshot had lessons in `O` is
  held once (`emptyHeld`, `EmptyHeld`), the snapshot stays; a second empty
  answer in a row is accepted and cancels them. A single empty My ITMO answer
  therefore never reads as «all lessons cancelled».
- A snapshot without lessons in `O` followed by more than 5 lessons there is a
  new schedule published after empty weeks (holidays, a new term): the snapshot
  is replaced without changes (`Baseline`). Up to 5 are ordinary `ADDED`.
- Otherwise the new changes are appended unread and not notified, with
  `detectedAt` from the wall clock and the id `<millis>-<index>`; the snapshot
  is replaced and `Compared(found)` returned.
- A failed request returns its `AppError` and keeps the snapshot; an answer
  without `data` is `AppError.Unknown`.

### Storage

The change model, shared with the home card, is `ScheduleChange` in
`core/schedule` with `LessonSlot` (one side of a change) and `LessonOccurrence`
(a `pairId` on a date). `ScheduleChangesFileStore` keeps everything in
`filesDir/schedule_changes/state.json` (format 1): the snapshot, `emptyHeld`
and the changes with both sides, subject, type, flow, `read` and `notified`.
The snapshot and the changes are written together, atomically (`.tmp`,
`fd.sync()`, `ATOMIC_MOVE`), and the directory is excluded from backup and
device transfer. Changes older than 30 days by detection are dropped on every
write and never emitted; at most the 500 newest are kept. A corrupt file or one
of another format is deleted and the state starts empty, so the next check is a
baseline.

`ScheduleChangesRepositoryImpl` is a `@Singleton` that reads the file once and
keeps the state in memory:

- Two mutexes: `checks` runs one check at a time (the periodic and the one-off
  work in one process), `lock` guards the state, so marking changes read never
  waits for the network. The comparison and the write run under `lock` against
  the state at the moment of writing.
- It is a `SessionDataCleaner`: sign-out and account change delete the file,
  and a check that started before that writes nothing and returns
  `AppError.Unauthorized`.
- `resetSnapshot()` forgets the snapshot and keeps the history. It moves a reset
  counter, so a check that was already asking writes nothing and reports
  `Baseline`.
- When the write of `markNotified`, `markAllRead` or `resetSnapshot` fails, the
  new state stays in memory and reaches the file with the next write.

The switch `Изменения расписания` ([settings](../settings.md#schedule)) turns
the whole check off: the work is cancelled and the snapshot reset, the history
stays. After switching on, the first check is a baseline again.

### Notification

`ScheduleChangeDigests.decide(changes, now)` chooses one summary notification
after every run; `now` is Moscow time:

- Pending changes are unread and not yet notified.
- From 00:00 to 06:00 nothing is shown or marked. The first run at or after
  06:00 delivers what was found at night.
- Pending changes that are over by then are marked delivered without a
  notification.
- Otherwise the digest counts the unread changes that are not over, names the
  fresh pending change with the soonest start (then subject, then id) and makes
  a sound only when a fresh pending change touches today or tomorrow of the
  delivery date.
- Every pending change counts as delivered after the attempt, even without the
  notification permission.

`AndroidScheduleChangeNotifier` shows `Расписание изменилось: 3 пары` with the
named change's headline (`Физика — отменена: вт, 8 сентября, 10:00`) in the
`schedule_changes` channel. The rest is in
[notifications](notifications.md#schedule-changes).

### In the schedule

- A lesson card whose occurrence (`pairId` and date) belongs to a change of the
  30-day history shows `change_indicator` (`ic_edit_calendar`, 16 dp,
  `colorPrimary`, `Изменена` for TalkBack) after the video-call icon. The
  occurrences of a change are the new slot plus the old one for a cancel or a
  move, so a stale cache that still has the old slot is marked too. Read or
  not does not matter.
- `ScheduleViewModel` observes the changes and passes their occurrences to
  `buildScheduleDisplayDays` (`ScheduleDisplayDay.changedPairIds`), in the own
  and in a friend's schedule alike; a change that disappears removes its mark
  without a new request. Pending sport rows never carry it.
- The lesson sheet shows the change in `Изменения`
  ([lesson details](#lesson-details)).

### History

`ScheduleChangesFragment` is the `SCHEDULE_CHANGES` overlay with the contextual
header (back, `Изменения в расписании`), opened from the notification and the
home card.

- Rows are grouped by the Moscow day of detection, newest first; within a day
  newest first, then by the soonest lesson. Day titles are `Сегодня`, `Вчера`
  or a date (`30 сентября`); a date of another year spells the year out
  (`RelativeDay.OTHER_YEAR`, decided by `ScheduleChangesViewModel` from
  `AcademicTimeProvider`).
- A row (`item_schedule_change.xml`) has the subject, a `colorPrimary` dot for
  a new change, the main line, one line per changed field and `вид · поток`.
  The main line of an added or cancelled lesson is its summary
  (`Отменена: вт, 8 сентября, 10:00`); an updated lesson with several fields
  has the summary of the first field (`Перенесена на ср, 9 сентября, 10:00`)
  with the `было → стало` lines below; with one field the main line is that
  field's `было → стало` line itself (`Формат: Очный → Дистанционный`) and
  nothing repeats it. Texts are in `core/ui/ScheduleChangeTexts.kt`.
- Rows react to nothing. Each is one TalkBack node:
  `Новое. Предмет. Итог. Строки. Мета`.
- `Loading` keeps the area blank until the local file answers, without a
  skeleton; `Empty` says `Изменений нет` and `За последние 30 дней`. There is
  no error state: the store resets a broken file.
- While the screen is visible (`onStart` to `onStop`) every unread change is
  marked read (`markAllRead()`, which also removes the notification). The rows
  that were unread when shown keep the dot for the life of the screen; their
  ids are kept in `SavedStateHandle`, so recreation keeps them. A screen hidden
  under another overlay marks nothing.

### Debug tools

`Проверить изменения расписания` in the debug tools calls
`ScheduleChangeTracking.checkNow()`: the one-off work `schedule-changes-now`
(`ExistingWorkPolicy.REPLACE`, network constraint, the same tag), which runs
the same `ScheduleChangesCheck` on the real account. Debug builds only.

### Tests

`ScheduleDiffTest`, `ScheduleSnapshotTest`, `ScheduleChangeDigestTest` and
`ScheduleChangeTest` cover the comparison, the snapshot, the quiet hours and the
digest. `ScheduleChangesFileStoreTest` and `ScheduleChangesRepositoryImplTest`
cover the file, the request, baselines, held empty answers, the publication
rule, retention, the session clear and the snapshot reset against My ITMO stubs.
`ScheduleChangesCheckTest`, `BackgroundChecksTest` (quiet hours,
`outcomeOf`, `workResultOf`) and `DefaultScheduleChangeTrackingTest` cover the
run, retries and the work, `ScheduleChangesRepositoryImplTest` also a network
failure that keeps the file;
`ScheduleChangesViewModelTest`, `ScheduleViewModelTest`,
`LessonDetailsViewModelTest` and `LessonDetailsMappingTest` the history, the
marks, the block and the flow. Instrumented: `ScheduleChangesWorkTest` (the
app's `WorkManager` through the debug `ScheduleChangesTestEntryPoint`),
`ScheduleChangesNotificationTest`, `ScheduleChangesVisualTest` (with the debug
`ScheduleChangesPreviewActivity`), `ScheduleCardsVisualTest` and
`LessonDetailsVisualTest` (marks and the block through
`ScheduleLifecycleTestActivity.changes`). They use synthetic `pairId`s and
dates and restore the WorkManager state they found.

## Calendar

The own personal schedule goes to the phone's calendar in two ways, both on
the `Расписание` settings page ([settings](../settings.md#schedule)): a kept
synchronization and a one-off `.ics` file. The schedule screen has no top-bar
menu, so it offers neither; lessons and subjects have no calendar actions.
Backend is not involved.

### Events

- `OwnScheduleSource` (`MyItmoOwnScheduleSource`) asks
  `MyItmoApi.getPersonalSchedule` itself in pieces of at most 31 days, one
  after another, like the change check: no schedule cache, no upload to
  Backend.
- `CalendarEvents.from` (`domain/calendar`) turns every lesson of the answer
  into a `CalendarEvent`, as My ITMO sends it: academic lessons, sport and
  room bookings alike. The key is `lesson-<pairId>`; a lesson without a
  positive id is `lesson-<date>-<minute of start>-<flowId>-<subjectId>`. A
  repeated key is kept once, at its first slot.
- Title: the subject. Start and end: the date and times in `Europe/Moscow`
  (`AcademicTimeProvider.zoneId`). Location: the room and the street address of
  a known building (`BuildingDirectory`), otherwise the building as My ITMO
  spells it. Description: the lesson type, the teacher and the flow, one per
  line. The UID is `<key>@widgets.alllexey.dev`.

### Synchronization

- `CalendarSyncRepositoryImpl` keeps the window today..today+28 in the app's
  own local calendar `ITMO.Widgets` (`ACCOUNT_TYPE_LOCAL`, created and deleted
  through the sync-adapter URI, colour `calendar_app`, owner access). There is
  no calendar choice. The calendar exists only on this phone: calendar apps
  that read the phone's calendars (Xiaomi, Samsung) show it; Google and Yandex
  Calendar do not, since they show only their own accounts' calendars; for
  Google Calendar there is the `.ics` export.
- Google-account calendars are never written (owner's decision of
  2026-10-02): a delete there is final only once Google's sync adapter uploads
  it, and Android's guard against too many deletions undoes bulk deletes, after
  which the adapter writes the server's copies back as new rows. On the
  owner's phone 37 of 38 events came back after turning off.
- `AndroidPhoneCalendars` is the only `CalendarContract` code. Events are busy,
  have no reminders and carry the event time zone. The last line of the
  description is the app's tag `ITMO.Widgets · <key>`
  (`CalendarEvent.taggedDescription`); `CUSTOM_APP_PACKAGE`, `CUSTOM_APP_URI`
  and `UID_2445` are set too. Sweeps find the app's events by the tag, which
  also survives rows Google's sync adapter writes back. The `.ics` file has no
  tag; its UIDs name the occurrences.
- The ids of the app's events, the calendar of each and the content they were
  given live in `filesDir/calendar_sync/state.json` (format 1, with the
  switch, the calendar and the reason it turned itself off), written
  atomically, excluded from backup and device transfer. A restored device
  therefore starts with synchronization off. A corrupt file is deleted and
  synchronization is off.
- `CalendarSyncPlanner.plan` (pure) compares the window with the stored
  events. Only occurrences that have not ended are touched: a new one is
  inserted, a changed one (title, time, location or description) is updated in
  place, one that vanished from the window is deleted. A past event stays as
  it was; nothing outside the window is deleted; an event that ended more than
  180 days ago is forgotten (it stays in the calendar). A key is inserted once,
  so repeated syncs add no duplicates. An event the user deleted is inserted
  again by its next change. Every insert is written to the file at once, so an
  event in the calendar never lacks its id.
- Before planning, a sync deletes tagged events of the calendar in the window
  that have not ended and whose ids the file lacks (a process death between an
  insert and its write), then inserts them anew: lost ids never double the
  lessons.
- Turning off first stores the switch as off (the stop flag), so a sync queued
  behind it writes nothing, then deletes the app's calendar with all its
  events in one operation, under the same lock. Turning on reuses the app's
  calendar only when the stored ids belong to it, otherwise recreates it.
- Before each sync: without the calendar permission synchronization turns
  off with `NO_PERMISSION` and keeps the ids, so turning on again adopts the
  calendar with its events; a deleted calendar turns it off with
  `CALENDAR_MISSING` and forgets them. Settings show the reason in the
  switch's line.
- Every operation (turning on, off, syncing, sign-out) runs behind one mutex,
  so a switch-off waits for a running sync and then deletes all it wrote. Only
  the request to My ITMO can be cancelled; calendar writes and the ids they
  produce run to their end (`NonCancellable`) even when WorkManager cancels
  the work. A sync whose calendar or session changed meanwhile writes nothing.
  Sign-out and account change delete the app's calendar (when the permission
  is there) and the file (`SessionDataCleaner`).

### Earlier builds that wrote to Google

Builds of 2026-10-02 before the decision could write into a Google calendar.
A state with that target (`target = phone`) reads as off. The next run, or
turning on, deletes the app's tracked events there and never inserts there
again; turning on uses the app's own calendar. That Google calendar then stays
in the file's `cleanups`: every run (the work stays on while one is pending,
also with the switch off, and then asks My ITMO nothing) sweeps it by the tag
from 180 days back to 400 days ahead, because Google may write the deleted
events back. A sweep that finds events starts its 3 days anew; a calendar
clean for 3 days or deleted is done. A delete drops its id only when it went
through; failed ones stay in the file with their calendar and are retried.

### Work

- `DefaultCalendarSync` (`core/schedule/CalendarSync`) owns the switch and the
  work. `CalendarSyncWorker` runs the unique periodic work `calendar-sync`
  every 2 hours with `NetworkType.CONNECTED`, backoff from 15 minutes,
  `ExistingPeriodicWorkPolicy.UPDATE`, tag `calendar-sync`, through
  `CalendarSyncEntryPoint`; `WorkManagerCalendarSyncScheduler` enqueues and
  cancels it. Both live in `feature/schedule/work`, the rest of the
  synchronization in `domain/calendar` and `data/calendar`. The work does not
  depend on `Изменения расписания` and has no quiet hours, since it notifies nothing. Failures are retried through
  `outcomeOf` and `workResultOf` like the background checks.
- The one-off work `calendar-sync-now` (`ExistingWorkPolicy.REPLACE`, network)
  runs right after turning on and after a successful
  pull on the own schedule (`ScheduleViewModel` calls `requestSync()`, which
  does nothing while synchronization is off).
- `syncWork()` runs on application start and after sign-in, `stopWork()`
  before a session change. The work stays while synchronization is on or a
  Google calendar of an earlier build is still swept; otherwise a run cancels
  it.

### `.ics` export

`IcsFileExport` (`core/schedule/ScheduleIcsExport`) reads the range of a
`ScheduleExportRange`: `Неделя` (today and 6 days), `2 недели` (today and 13
days), `До конца семестра` (to 31 January from August to January, to 31 July
from February to July) or `Свои даты` (a `MaterialDatePicker` range).
`IcsWriter` writes RFC 5545: CRLF, lines folded at 75 octets without splitting
a UTF-8 character, escaped text, times in UTC (no `VTIMEZONE`), the same UIDs
as synchronization, `TRANSP:OPAQUE`. The file is
`cacheDir/ics/itmo-schedule-<start>-<end>.ics` (only the latest is kept),
shared through the `FileProvider` `${applicationId}.files`. A range without
lessons writes nothing. This is the way into Google Calendar.

`IcsExportBottomSheet` (`feature/settings/ui`, `res/layout/sheet_ics_export.xml`)
is the whole flow: the title `Выгрузить в .ics`, the subtitle `Своё расписание
из My ITMO` and one area of at least 288 dp that every state shares, so the
sheet does not jump. `IcsExportViewModel` holds the state:

- Choose: the four ranges as one connected group (`item_ics_range.xml`,
  `colorSurfaceContainerHigh`, 56 dp rows, the whole row is the target), each
  with its days on the second line from today (`2–8 октября`, `до 31 января`,
  `Выбрать в календаре`; `IcsDateLabels`: one day, a month, two months, two
  years). `Свои даты` opens the `MaterialDatePicker` and comes back with its
  range.
- Preparing: a progress indicator and `Готовим файл…`.
- Ready: `Файл готов`, `23 пары, 2–8 октября`, the filled `Отправить`
  (`ACTION_SEND`, `text/calendar`), the tonal `Открыть в календаре` only when
  an app opens `.ics` files (`ACTION_VIEW`), and the hint `Импортируйте в
  отдельный календарь — так его легко удалить или заменить свежим файлом`.
- Empty: `В этом периоде пар нет` with `Выбрать другой период`.
- Failed: the error's message with `Повторить`.

The chosen range and the written file live in the `SavedStateHandle`:
recreation shows the same state, and after a process death the file is
written again; the date picker is listened to again by its tag.

### Tests

`CalendarEventsTest`, `CalendarSyncPlannerTest`, `IcsWriterTest` and
`ScheduleExportRangeTest` cover the domain; `CalendarSyncFileStoreTest`,
`CalendarSyncRepositoryImplTest` (fake calendars), `DefaultCalendarSyncTest`
and `IcsFileExportTest` the data and the work; `SettingsViewModelTest` and
`ScheduleViewModelTest` the rows and the sync after a pull, `IcsExportViewModelTest`
the sheet's states, saved state and date labels. Instrumented:
`CalendarSyncProviderTest` against the emulator's CalendarProvider (the local
calendar, two syncs without duplicates, update, delete, lost ids, turning off
during the first sync, leaving an earlier build's calendar that behaves like a
Google one, with `_SYNC_ID` rows and copies the sync adapter writes back) and
`SettingsNavigationTest` for the rows, every state of the `.ics` sheet (in a
light and a dark appearance, with the area height kept) and the permission
dialog.
