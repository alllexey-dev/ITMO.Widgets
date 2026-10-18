# Mark tracking

Part of the [recordbook](recordbook.md).
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

Detects new and changed own marks of the current half-year in My ITMO and BARS and changed totals of
the connected sheets ([sheet scores](sheet-scores.md#background-check-of-sheets)), and names the
subjects in one notification.

- The switches (`Баллы` and its sources) are on «Уведомления»
  ([settings](../settings.md#notifications)).
- Everything stays on the device: marks, the BARS token and the ITMO.ID cookies never reach Backend;
  the feature works without `Подключение к ITMO.Widgets`.
- BARS is read in the background through the cookie renewal (decision
  [0012](../decisions/0012-bars-background-renewal.md)); storage and delivery follow the schedule
  changes (decision [0013](../decisions/0013-schedule-changes-on-device.md)).
- Code: `:shared:feature-recordbook` `commonMain` (`MarksCheck`, `MarkDiff`, `MarkNewsRules`,
  `MarkDigests`, `MarksFileStore`, `MarkTrackingRepositoryImpl`, `DefaultMarkTracking`,
  `BarsMarksActivation`, `MarksBellSource`, `BarsTodoSource`), Koin `recordbookModule`; Android work in
  `feature/recordbook/work`, bound in `di/RecordbookModule.kt`. Wall clock is the injected
  `kotlin.time.Clock`, the date comes from `AcademicTimeProvider`.

## Background check

- `MarksWorker` runs the unique periodic work `marks-check` (`MARKS_SPEC`): every 3 hours,
  `NetworkType.CONNECTED`, exponential backoff from 15 minutes, tag `marks`,
  `ExistingPeriodicWorkPolicy.UPDATE` (enqueuing on every start does not push the next run away).
  `MarksScheduler` is `PeriodicCheckScheduler` over that spec; `AndroidMarksNotifier` posts.
- `MarksCheck.run()` is `SKIPPED` without a request when there is no refresh token or all three
  switches are off. Otherwise it runs `checkMyItmo()`, `checkBars()`, `checkSheets()` per switch, then
  delivers even when a check failed. The outcome is `outcomeOf(errors)`: any error except
  `Unauthorized` asks for a retry, `workResultOf` grants two retries per period. BARS `NoSession` /
  `SessionEnded` ends the run normally.
- `MarkTracking` (`DefaultMarkTracking`): the work exists with a refresh token and at least one source
  on. `syncWork()` on app start, after sign-in and on a switch change; `stopWork()` before a session
  change cancels the periodic and the one-off work. `BarsMarksActivation` enqueues when BARS turns on
  by itself.
- Half-year: `StudyHalf.of(today)`, September-January autumn (1), February-August spring (2). My ITMO
  is read for every programme's periods of that half-year (`RecordbookPeriod.studyHalf()`), BARS for
  that term. The previous half-year (retakes) is not tracked.
- Timing is Android's: Doze, App Standby and vendor limits can delay or stop runs.

## Comparison

`MarkDiff.myItmo` / `MarkDiff.bars` are pure: they return the snapshot to store, the events and
whether it was a first look.

- My ITMO: recordbook rows only, `current_score` and `rate`. Key `(programId, semester, est_id)`; a
  new unpaired row is linked to a vanished one with the same programme, semester, `discipline_id` and
  `subjectNameKey(name)` when there is exactly one candidate on each side.
- BARS: per `planId`, checkpoints with a mark or absence (final, nested, additional points with id
  `-planId`) and the last unambiguous statement. Built by `BarsPlanMarks.of` from
  `BarsRecordbookMapper` output, so the overlay's rules apply; a plan the mapper rejects keeps its
  previous state. Plan totals are not compared.
- Scores are equal when both are empty/zero or closer than 0.005; grades compare by `rateKey` (trim,
  lower case, `ё -> е`, no spaces or `/`).
- Events: `MARK_ADDED`, `MARK_CHANGED`, `FINAL_CHANGED`. A new subject, new plan or missing mark is not
  an event; answer order does not matter.
- Anything missing from an answer is carried into the new snapshot until the half-year changes.
- The first successful answer per source after install, sign-in, switching on or a new half-year is
  only a `Baseline`.

## Unread subjects

`MarkNewsRules` turns events into `MarkNews`:

- One record per half-year and `subjectNameKey(name)` for both sources (id `<half>|<nameKey>`).
- `withoutEchoes`: a My ITMO event is dropped when the previous BARS snapshot has exactly one plan of
  that name with the same points and grade (marks reach BARS first).
- A background check re-arms the record for notification; the open list records it as delivered.
- Each record keeps the value of its latest check (`MarkValue`, the most telling event first: a final grade,
  then a BARS checkpoint): the checkpoint's name, its mark out of the maximum or an absence, the change of the
  points, the total, the grade. BARS snapshots keep each checkpoint's name and maximum for it.
- `markRead` (subject page opens) / `markAllRead` (opening the bell) mark records read; `observeNews` is the
  unread ones (the dots, the digest), `observeHistory` every record (the bell). A new event makes a read record
  unread again. Retention: 30 days by detection, at most 100 newest (`MAX_NEWS`).
- A BARS subject with no My ITMO row is notified and in the bell but gets no dot.

## Storage

- `MarksFileStore`: `filesDir/marks/state.json`, format 1 (owner ISU, last snapshot per source with
  half-year and `fetchedAt`, unread records), written atomically via `AtomicTextFile`. The directory
  is excluded from backup and device transfer. The JSON shape must stay readable by 2.2 (nulls
  absent, defaults written, instants in epoch ms). A corrupt file, another format, an invalid field
  or another account's file is deleted and the next check is a baseline.
- `MarkTrackingRepositoryImpl` (one Koin single, state in memory): mutex `checks` runs one check at a
  time, `lock` guards the state, so UI reads never wait for the network.
- It is a `SessionDataCleaner`: sign-out and account change delete the file; a check started before
  writes nothing and returns `AppError.Unauthorized`.
- `resetSource(source)` forgets one snapshot and keeps the records; an in-flight check of it is
  `Stale`. An answer started before the stored `fetchedAt` is `Stale` and not written.
- A failed write outside a check keeps the state in memory until the next write.

Switches and the BARS prompt live in DataStore (`MarkSourcePreferences`), not in the file, to avoid
the cycle `BarsClient -> repository -> BARS read -> BarsClient`:

| Key | Absent means | Cleared on sign-out |
|---|---|---|
| `myitmo_marks_enabled` | on | no (device setting) |
| `bars_marks_enabled` | no `Оценки БАРС` switch yet | yes, with the BARS session |
| `bars_marks_prompt` | `NONE` (`NONE`, `PENDING`, `SHOWN`) | yes, with the BARS session |
| `sheet_marks_enabled` | on | no (device setting) |

- Any successful BARS answer runs `BarsMarksActivation.onBarsAnswered()`: the first turns
  `Оценки БАРС` on (`null -> true`) and enqueues the work; each one resets the prompt to `NONE`. A
  failed preference write never fails a good BARS answer.
- Switching a source off forgets its snapshot (records stay) and syncs the work; for BARS it also
  resets the prompt. Switching on makes the next check a baseline.

## BARS in the background

`BarsMarkReader.read(half)` runs inside `BarsClient.backgroundAccount`
([session](recordbook.md#bars)):

- No saved BARS session for the current ISU: `NoSession`, no requests.
- It selects the half-year on the server, reads via `journalReferences` (shared with the overlay),
  then selects the previous period back when it differs and matches `\d{4}/\d{4}`. A failed
  select-back does not change the result; a cancelled read does not select back.
- Any failed request fails the whole read: a partial answer is never a snapshot.
- `SessionEnded` moves the prompt `NONE -> PENDING`. With `PENDING`/`SHOWN` every run still tries the
  cookies once.
- A network failure (`isCausedByNetworkFailure` in `core/network`, including a
  `TokenRefreshException` with a network cause) is `AppError.Network` on every path: retried,
  snapshots kept, never a prompt. Only ITMO.ID's `LOGIN_REQUIRED` or missing cookies end a session.
- Xiaomi `Умный режим` cuts background network even with `CONNECTED`; the settings row
  `Работа в фоне` leads to the fix ([settings](../settings.md#background-work)).

## Notification

`MarkDigests.decide(news, prompt, now)`, `now` in Moscow time:

- 00:00-06:00 (`QuietHours`): nothing shown or marked; the first run at or after 06:00 delivers.
- With any pending record the digest names every unread subject, newest first; one record is
  `single`.
- A `PENDING` prompt is shown once and becomes `SHOWN`.
- Pending records count as delivered after the attempt, even without the notification permission.
- `AndroidMarksNotifier`: channel `marks`. One subject with numbers says them in the title
  (`MarkNewsTexts.notificationTitle`: `Матанализ: 9 из 10`) and the details under it; otherwise `Новые оценки`
  with up to three names (`MarkSubjects.split`). The lock screen shows `Новые оценки` only. The tap target is `MarkNewsRules.target` (a single subject's
  page, else the recordbook); the tap never connects BARS. Ids and routes:
  [notifications](notifications.md#marks).

## In the recordbook

- `RecordbookViewModel` fills `Content.newSubjects` (name keys of the listed period's half-year)
  without a request; `RecordbookSubjectViewModel` calls `markRead` once with the first content.
- The open list advances snapshots: `readStarted()` before requests, then `recordMyItmoSeen` /
  `recordBarsSeen` for the current half-year only, never after an error. What it finds gets a dot,
  never a notification. A source without a snapshot of that half-year is left alone.
- The bell: `MarksBellSource` shows every record with its value (`Матанализ: контрольная работа 2`,
  `9 из 10, итого 74`, `+9`) and leads to the subject page ([the bell](home.md#the-bell)).
- Сегодня: `BarsTodoSource` asks to sign in to BARS again while the prompt is not `NONE`
  ([Сегодня](home.md#сегодня)).

## Debug tools

- `Проверить оценки` calls `MarkTracking.checkNow()`: one-off `marks-check-now`
  (`ExistingWorkPolicy.REPLACE`, network, tag `marks`) on the real account.
- `Проверить продление БАРС` enqueues `BarsCookieProbeWorker` (`bars-cookie-probe`, 120 s delay):
  read-only, writes no cookies or BARS header; logcat `BarsCookieProbe` holds only outcome, step,
  counts and process age. Debug builds only.

## Tests

`shared/feature-recordbook/src/androidHostTest` (2.2 store files in `resources/stores/`), plus the
instrumented `MarksWorkTest` and `MarksNotificationTest` in `:app`.

## iOS

Same shared check; `PlatformCapabilities.marks` is on. Platform side:
`shared/feature-recordbook/src/iosMain/.../data/marks/`, bound in `recordbookIosModule`.

- Work: the `marks` step of the app's refresh task (`RefreshStepKeys.MARKS`, after schedule changes,
  before calendar sync, see [`docs/ios.md`](../ios.md#background-refresh)), period
  `RefreshStepKeys.MARKS_PERIOD` (3 hours), Android's retries. `RefreshTaskMarksScheduler.runOnce`
  makes the step due now; `cancel` does nothing (the check skips itself).
- No exact rhythm: it never runs with Background App Refresh off or in Low Power Mode, and also runs
  on every return to the app.
- BARS renews only through ITMO.ID cookies (`BarsCookieSilentLogin` over `KeychainItmoIdCookies`),
  never the hidden WebKit view.
- `IosMarksNotifier` posts via `IosAppNotifier` on thread `marks` (`marks-1` digest, `marks-2`
  prompt).
- Quiet hours differ: `MarksRefresh` hands night results to the system for 06:00 at once (calendar
  trigger) and counts them delivered.
- Debug: `-itmoRunRefresh` also posts a fixture digest (`MarksFixture`). Tests in
  `shared/feature-recordbook` iosTest (`MarksRefreshTest`) and `ITMOWidgetsTests`.
