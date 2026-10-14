# Schedule changes

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md).

The app detects changes of the own personal schedule on the device only and
never talks to Backend about them (decision
[0013](../decisions/0013-schedule-changes-on-device.md)). The code is part of
the schedule feature ([modules](schedule.md#modules)).

## Background check

- `ScheduleChangesWorker` (`:app` `feature/schedule/work`): unique periodic
  work `schedule-changes-check`, every 2 hours, `NetworkType.CONNECTED`,
  exponential backoff from 15 minutes, tag `schedule-changes`,
  `ExistingPeriodicWorkPolicy.UPDATE` (re-enqueuing on start keeps the next
  run). `PeriodicCheckScheduler` with `SCHEDULE_CHANGES_SPEC` enqueues and
  cancels; `AndroidScheduleChangeNotifier` posts. The worker gets
  `ScheduleChangesCheck` through `KoinStarter`, so it also runs before
  `Application.onCreate()`.
- `ScheduleChangesCheck.run()` is `SKIPPED` without a request when there is
  no refresh token or the switch is off. Otherwise it runs
  `ScheduleChangesRepository.check()` and then always delivers the
  notification, even after a failed check, so night findings go out on the
  first run after the quiet hours.
- Outcome via `outcomeOf(errors)` (`core/work`, shared with the mark check):
  `Unauthorized` and success end the run; other errors retry, which
  `workResultOf` grants twice (`runAttemptCount < 2`).
- A network failure before any answer (`IOException` in the cause chain,
  `isCausedByNetworkFailure` in `core/network`), including an unreachable
  ITMO.ID token refresh, is `AppError.Network`, not `Unauthorized`: retried,
  snapshot and changes untouched.
- `ScheduleChangeTracking` (`DefaultScheduleChangeTracking`): `syncWork()`
  enqueues with a refresh token and the switch on, cancels otherwise. Runs on
  start (every `BackgroundCheck`), after sign-in and when the switch changes;
  `stopWork()` before a session change. Cancelling removes periodic and
  one-off work.
- The check calls `MyItmoApi.getPersonalSchedule(today, today + 7)` itself
  (Moscow dates from `AcademicTimeProvider`). It neither fills the schedule
  cache nor uploads to Backend.
- Timing is up to Android; Xiaomi's default battery mode cuts a background
  app's network ([settings](../settings.md#schedule), `Работа в фоне`).

## Snapshot and comparison

Only academic lessons (`flowTypeId == 2`) are compared. `academicSnapshot()`
keeps per lesson `pairId`, date, start, end, subject, type, flow id and name,
teacher ISU and name, room, building and format; blanks become `null`, equal
positive `pairId`s collapse into the earliest.

`ScheduleDiff.compare(previous, current, now)` is pure (`now` in Moscow):

1. Overlap `O` of both windows; empty (snapshot older than 8 days) means no
   change.
2. The same positive `pairId` in both is the same lesson wherever its date.
3. The rest links only within `O`, grouped by subject, flow and type: same
   date and start first, then pairwise in time order.
4. Unlinked in `O` is `CANCELLED` (old) or `ADDED` (new). Days that left the
   window, the new last day and a reordered answer are never changes.
5. Linked lessons compare `TIME`, `FORMAT`, `PLACE` (trimmed, collapsed
   spaces, case-insensitive, blank = missing) and `TEACHER` (ISUs when both
   known, else normalized names); differences make one `UPDATED` with every
   changed field.
6. A change whose every side ended by `now` is dropped.
7. Order: soonest start of the sides, subject, `pairId`.

`ScheduleChangesRepositoryImpl.check()`:

- No snapshot: the answer becomes the snapshot (`Baseline`), no changes.
- An answer without academic lessons while the snapshot had some in `O` is
  held once (`emptyHeld`); a second empty answer in a row is accepted.
- Snapshot empty in `O` followed by more than 5 lessons
  (`PUBLICATION_THRESHOLD`) is a newly published schedule: `Baseline`. Up to
  5 are ordinary `ADDED`.
- Otherwise new changes are appended unread and unnotified (`detectedAt` from
  the clock, id `<millis>-<index>`), the snapshot replaced, `Compared(found)`.
- A failed request keeps the snapshot; an answer without `data` is
  `AppError.Unknown`.

## Storage

Model: `ScheduleChange`, `LessonSlot`, `LessonOccurrence` in `core/schedule`
(shared with Сегодня and the bell). `ScheduleChangesFileStore` keeps the snapshot,
`emptyHeld` and the changes in `filesDir/schedule_changes/state.json`
(format 1), written together through `AtomicTextFile`, excluded from backup
and device transfer. Changes older than 30 days are dropped on every write
and never emitted; at most 500 newest are kept. A corrupt or foreign-format
file is deleted, so the next check is a baseline.

`ScheduleChangesRepositoryImpl` is a Koin `single` holding state in memory:

- Two mutexes: `checks` (one check at a time) and `lock` (state), so marking
  read never waits for the network.
- `SessionDataCleaner`: sign-out and account change delete the file; a check
  started before that writes nothing and returns `AppError.Unauthorized`.
- `resetSnapshot()` forgets the snapshot, keeps history and bumps a reset
  counter so an in-flight check writes nothing and reports `Baseline`.
- A failed write of `markNotified`, `markAllRead` or `resetSnapshot` keeps
  the state in memory until the next write.

Turning `Изменения расписания` off ([settings](../settings.md#schedule))
cancels the work and resets the snapshot; history stays.

## Notification

`ScheduleChangeDigests.decide(changes, now)` (Moscow time) after every run:

- Pending = unread and not yet notified.
- 00:00-06:00 (`QuietHours`): nothing shown or marked.
- Pending changes already over are marked delivered silently.
- Otherwise one digest counts unread not-over changes, names the pending one
  with the soonest start, and makes a sound only when a pending change touches
  today or tomorrow.
- Every pending change counts as delivered after the attempt, even without
  the notification permission.

Channel `schedule_changes`; the rest is in
[notifications](notifications.md#schedule-changes).

## In the schedule, on Сегодня and in the bell

- A card whose occurrence (`pairId` and date) belongs to any change of the
  30-day history gets a mark, read or not; occurrences include the old slot of
  a cancel or move, so a stale cache is marked too. Own and friend's schedule
  alike (`ScheduleDaysImpl`); never sport queues.
- Сегодня's timeline marks the lesson itself ([Сегодня](home.md#сегодня)):
  `изменена` with the old room struck through and `Изменено вчера в 20:14`, a
  cancelled lesson struck through on its old day with `отменена`.
- There is no history screen: each change is an event of the bell
  ([the bell](home.md#the-bell), `ScheduleChangesBellSource`): what happened,
  `было` over `стало`, leading to the lesson's sheet. The notification opens
  the bell (`ACTION_OPEN_SCHEDULE_CHANGES` via `EntryRouteParser`). Opening the
  bell marks every change read (`markAllRead()`, also removes the
  notification). Notification texts: `core/text/ScheduleChangeTexts.kt`.
- Debug builds: `Проверить изменения расписания` runs
  `ScheduleChangeTracking.checkNow()` (one-off `schedule-changes-now`,
  `ExistingWorkPolicy.REPLACE`) on the real account.

## iOS

Same `ScheduleChangesCheck`, comparison, store and digest
([iOS app](../ios.md#background-refresh)); only work and notification differ
(`shared/feature-schedule/src/iosMain/.../data/changes/`,
`scheduleChangesIosModule`).

- The check is the `schedule-changes` step of the app's single refresh task,
  2-hour period, same retries; runs at launch, on return and on system wakes.
  `ScheduleChangesRefresh` resolves the check per run; switching on makes the
  step due at once.
- Never runs with Background App Refresh off or in Low Power Mode.
- `IosScheduleChangeNotifier` posts the same digest; a night change is
  scheduled for 06:00 at once (calendar trigger) and counts as delivered. A
  tap opens the app without a route ([degradations](../ios.md#degradations)).
- Debug: `-itmoRunRefresh` posts a fixture notification
  (`ScheduleChangeFixture`).
