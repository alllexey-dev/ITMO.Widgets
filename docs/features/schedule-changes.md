# Schedule changes

The application notices changes of the own personal schedule by itself and
tells the user about them. The check runs only on the device and never talks to
Backend (decision [0013](../decisions/0013-schedule-changes-on-device.md)).
The code is part of the schedule feature ([modules](schedule.md#modules)).

## Background check

- `ScheduleChangesWorker` (`feature/schedule/work`) runs the unique periodic
  work `schedule-changes-check`: every 2 hours with `NetworkType.CONNECTED`,
  exponential backoff from 15 minutes, tag `schedule-changes`.
  `ExistingPeriodicWorkPolicy.UPDATE` keeps the enrolment time, so enqueuing it
  again on every start does not push the next run away. The worker gets
  `ScheduleChangesCheck` from Koin through `KoinStarter`, so it also runs
  before `Application.onCreate()`. `PeriodicCheckScheduler` with
  `SCHEDULE_CHANGES_SPEC` enqueues and cancels it, and
  `AndroidScheduleChangeNotifier` posts the notification; the worker, the spec
  and the notifier live in `feature/schedule/work`.
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

## Snapshot and comparison

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

## Storage

The change model, shared with the home card, is `ScheduleChange` in
`core/schedule` with `LessonSlot` (one side of a change) and `LessonOccurrence`
(a `pairId` on a date). `ScheduleChangesFileStore` keeps everything in
`filesDir/schedule_changes/state.json` (format 1): the snapshot, `emptyHeld`
and the changes with both sides, subject, type, flow, `read` and `notified`.
The snapshot and the changes are written together, atomically
(`AtomicTextFile`), and the directory is excluded from backup and
device transfer. Changes older than 30 days by detection are dropped on every
write and never emitted; at most the 500 newest are kept. A corrupt file or one
of another format is deleted and the state starts empty, so the next check is a
baseline.

`ScheduleChangesRepositoryImpl` is a Koin `single` that reads the file once and
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

## Notification

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

## In the schedule

- A lesson card whose occurrence (`pairId` and date) belongs to a change of the
  30-day history shows `ic_edit_calendar` (16 dp, `primary`, `Изменена` for
  TalkBack) after the video-call icon. The
  occurrences of a change are the new slot plus the old one for a cancel or a
  move, so a stale cache that still has the old slot is marked too. Read or
  not does not matter.
- `ScheduleViewModel` observes the changes and passes their occurrences to
  `buildScheduleDisplayDays` (`ScheduleDisplayDay.changedPairIds`), in the own
  and in a friend's schedule alike; a change that disappears removes its mark
  without a new request. Pending sport rows never carry it.
- The lesson sheet shows the change in `Изменения`
  ([lesson details](schedule.md#lesson-details)).

## History

`ScheduleChangesFragment` is the `SCHEDULE_CHANGES` overlay with the contextual
header (back, `Изменения в расписании`), opened from the notification and the
home card.

- Rows are grouped by the Moscow day of detection, newest first; within a day
  newest first, then by the soonest lesson. Day titles are `Сегодня`, `Вчера`
  or a date (`30 сентября`); a date of another year spells the year out
  (`RelativeDay.OTHER_YEAR`, decided by `ScheduleChangesViewModel` from
  `AcademicTimeProvider`).
- A row (`ScheduleChangesScreen`) has the subject, a `primary` dot for
  a new change, the main line, one line per changed field and `вид · поток`.
  The main line of an added or cancelled lesson is its summary
  (`Отменена: вт, 8 сентября, 10:00`); an updated lesson with several fields
  has the summary of the first field (`Перенесена на ср, 9 сентября, 10:00`)
  with the `было → стало` lines below; with one field the main line is that
  field's `было → стало` line itself (`Формат: Очный → Дистанционный`) and
  nothing repeats it. Texts are in `core/text/ScheduleChangeTexts.kt` (`:shared:core`).
- Rows react to nothing. Each is one TalkBack node:
  `Новое. Предмет. Итог. Строки. Мета`.
- `Loading` keeps the area blank until the local file answers, without a
  skeleton; `Empty` says `Изменений нет` and `За последние 30 дней`. There is
  no error state: the store resets a broken file.
- While the screen is visible (started; `ScheduleChangesRoute` reports it from
  `LifecycleStartEffect`) every unread change is
  marked read (`markAllRead()`, which also removes the notification). The rows
  that were unread when shown keep the dot for the life of the screen; their
  ids are kept in `SavedStateHandle`, so recreation keeps them. A screen hidden
  under another overlay marks nothing.

## Debug tools

`Проверить изменения расписания` in the debug tools calls
`ScheduleChangeTracking.checkNow()`: the one-off work `schedule-changes-now`
(`ExistingWorkPolicy.REPLACE`, network constraint, the same tag), which runs
the same `ScheduleChangesCheck` on the real account. Debug builds only.

## Tests

`ScheduleDiffTest`, `ScheduleSnapshotTest`, `ScheduleChangeDigestTest` and
`ScheduleChangeTest` cover the comparison, the snapshot, the quiet hours and the
digest. `ScheduleChangesFileStoreTest` and `ScheduleChangesRepositoryImplTest`
cover the file, the request, baselines, held empty answers, the publication
rule, retention, the session clear and the snapshot reset against My ITMO stubs.
`ScheduleChangesCheckTest`, `BackgroundChecksTest` (`:shared:core`, quiet
hours, `outcomeOf`), `WorkResultsTest` (`workResultOf`),
`PeriodicCheckSpecTest` and `DefaultScheduleChangeTrackingTest` cover the
run, retries and the work, `ScheduleChangesRepositoryImplTest` also a network
failure that keeps the file;
`ScheduleChangesViewModelTest`, `ScheduleViewModelTest`,
`LessonDetailsViewModelTest` and `LessonDetailsMappingTest` the history, the
marks, the block and the flow; `ScheduleChangesScreenTest` and
`ScheduleChangesRouteTest` (`:shared:feature-schedule`) the history rows and
the read mark on start, the `ScheduleChangesScreen_*` goldens its look.
Instrumented: `ScheduleChangesWorkTest` (the
app's `WorkManager` through the debug `ScheduleChangesTestEntryPoint`),
`ScheduleChangesNotificationTest`; the marks and the block are covered by the
`ScheduleList*` and `LessonDetailsContent_change` goldens. They use synthetic `pairId`s and
dates and restore the WorkManager state they found.
