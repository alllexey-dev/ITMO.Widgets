# Mark tracking

Part of the [recordbook](recordbook.md).

The application notices new and changed own marks of the current half-year in
My ITMO and BARS and changed totals of the connected sheets
([sheet scores](sheet-scores.md#background-check-of-sheets)) by itself and names the subjects
in one notification.
Everything stays on the device: marks, the BARS token and the ITMO.ID cookies
never reach Backend, and the feature works without
`Подключение к ITMO.Widgets`. BARS is read in the background through the
cookie renewal (decision [0012](../decisions/0012-bars-background-renewal.md));
storage and delivery follow the schedule changes (decision
[0013](../decisions/0013-schedule-changes-on-device.md)).

## Background check

- `MarksWorker` (`feature/recordbook/work`) runs the unique periodic work
  `marks-check` for both sources: every 3 hours with
  `NetworkType.CONNECTED`, exponential backoff from 15 minutes, tag `marks`,
  `ExistingPeriodicWorkPolicy.UPDATE`, so enqueuing it on every start does not
  push the next run away. It gets `MarksCheck` through `MarksEntryPoint`, not
  `@HiltWorker`. `WorkManagerMarksScheduler` enqueues and cancels it, and
  `AndroidMarksNotifier` posts the notification; all three live in
  `feature/recordbook/work`.
- `MarksCheck.run()` ends `SKIPPED` without a request when there is no refresh
  token or all three switches are off. Otherwise it runs `checkMyItmo()` when
  `Оценки My ITMO` is on, `checkBars()` when `Оценки БАРС` is on and
  `checkSheets()` when `Оценки из таблиц` is on, then
  delivers, even when a check failed: what was found in the quiet hours is
  delivered by the first run after them whatever the network does. The outcome
  is `outcomeOf(errors)` from `core/work`: any error except `Unauthorized` asks
  for a retry, which `workResultOf` grants twice before the next period. BARS
  without a session (`NoSession`) or with an ended one (`SessionEnded`) ends
  the run normally.
- `MarkTracking` (`core/recordbook`, implemented by `DefaultMarkTracking`)
  keeps the work in line with the session and the switches: it exists with a
  refresh token and at least one source on. `syncWork()` runs on application
  start, after sign-in and when a switch changes, `BarsMarksActivation`
  enqueues the work when BARS turns on by itself, and `stopWork()` runs before
  a session change. Cancelling removes the periodic and the one-off work.
- The half-year is `StudyHalf.of(today)` with `today` from
  `AcademicTimeProvider`: September to January is autumn (1), February to
  August spring (2), the rule of the recordbook's default period. My ITMO is
  read for every programme's periods of that half-year
  (`RecordbookPeriod.studyHalf()`): one request for the programmes and one per
  period, usually two. BARS is read for that term. The previous half-year
  (retakes in February and September) is not tracked.
- Android picks the moment of a run. Doze, App Standby and vendor limits can
  delay a check by hours or stop it for an application that is not exempt from
  battery optimisation. Prompt delivery is not promised.

## Comparison

`MarkDiff.myItmo` and `MarkDiff.bars` are pure and return the snapshot to
store, the events and whether this was only a first look:

- My ITMO compares the rows of the recordbook list only, points
  (`current_score`) and the grade (`rate`), without control events: a new mark
  of an event changes the subject's points. A row is keyed by
  `(programId, semester, est_id)`; a new row without a pair is linked to a
  vanished one with the same programme, semester, `discipline_id` and
  `subjectNameKey(name)` when there is exactly one candidate on each side (an
  entry recreated under another `est_id`).
- BARS compares, per journal (`planId`), the checkpoints with a mark or an
  absence, including the final and nested ones and the additional points
  (id `-planId`), and the last unambiguous statement (grade, attempt,
  absence). `BarsPlanMarks.of` builds them from what `BarsRecordbookMapper`
  makes of the journal, so the rules are the overlay's. A plan the mapper
  rejects (`has_course_project`, an unknown shape) is skipped and keeps its
  previous state; the plan's total is not compared, it follows from the marks.
- Scores are equal when both are empty or zero or closer than 0.005; grades
  compare by `rateKey` (trimmed, lower case, `ё → е`, without spaces and `/`,
  so `4/C` and `4C` are one grade; an empty grade is none).
- Events: My ITMO points that appear or change to a non-zero value
  (`MARK_ADDED`, `MARK_CHANGED`) and a changed non-empty grade
  (`FINAL_CHANGED`); a new BARS checkpoint mark (`MARK_ADDED`), a changed mark
  or absence (`MARK_CHANGED`) and a changed statement with a grade or an
  absence (`FINAL_CHANGED`). A new subject, a new plan and a missing mark are
  not events, and the order of an answer does not matter.
- What is missing from an answer (a subject, a plan, a mark) is carried into
  the new snapshot until the half-year changes, so one short answer never turns
  everything into news the next time.
- The first successful answer of each source after installation, sign-in,
  switching on or a new half-year only takes the snapshot (`Baseline`).

## Unread subjects

`MarkNewsRules` turns events into unread records (`MarkNews`):

- One record per half-year and `subjectNameKey(name)` for both sources (id
  `<half>|<nameKey>`), so a subject that changed in both sources is named once,
  within a check and between checks. A new record takes the name of the first
  event, My ITMO before BARS.
- A My ITMO event is dropped when the BARS snapshot before this check has
  exactly one plan of that name with the same points and grade
  (`withoutEchoes`): marks usually reach BARS first and My ITMO later, and
  without the rule every mark would arrive twice.
- A background check makes the record wait for a notification again, an
  existing one too, so a new mark of the same subject is notified again. The
  open list records it as delivered: the user is looking at it.
- A record goes when its subject page opens (`markRead`) or with the home
  card's close button (`markAllRead`). Records older than 30 days by detection
  are neither emitted nor kept, and at most the 100 newest are kept.
- A BARS subject whose name matches no My ITMO row (the overlay's
  `нет в БАРС` case) is named in the notification and on the card but has no
  row for a dot; the card's close button reads it.

## Storage

`MarksFileStore` keeps everything in `filesDir/marks/state.json` (format 1):
the owner's ISU, the last snapshot of each source with its half-year and
`fetchedAt`, and the unread records. They are written together through
`AtomicTextFile` (`state.json.new`, sync, move; a `state.json.tmp` left by 2.2
is ignored), and the directory is excluded from backup and device transfer;
there is no other copy. The file is kotlinx JSON (`RecordbookStoreJson`) in the
shape 2.2's Gson wrote, so format 1 stays and each version reads the other's
file: nulls are absent, every non-null field and default (`format`, an empty
`news`) is written, instants are epoch milliseconds. A corrupt file, another
format, a missing or invalid required field (an unknown half-year, an absent
name) or another account's file is deleted and the state starts empty, so the
next check of each source is a baseline.

The mark tracking data (`MarksFileStore`, `MarkTrackingRepositoryImpl`,
`DefaultMarkTracking`, `MarksCheck`, `BarsMarksActivation`,
`MarksHomeCardSource`) lives in `:shared:feature-recordbook` `commonMain` and is
built by Koin (`recordbookModule`); the files go through okio on the platform
file system, the wall clock is the injected `kotlin.time.Clock`. The app's
`RecordbookBridge` hands `MarksWorker` its `MarksCheck` (`MarksEntryPoint`),
Hilt readers `DefaultMarkTracking` as `MarkTracking` and as one of the
Application's background checks, and Koin the Android `MarksScheduler` and
`MarksNotifier`.

`MarkTrackingRepositoryImpl` is one Koin single that reads the file once and
keeps the state in memory:

- Two mutexes: `checks` runs one check at a time, `lock` guards the state, so
  reading records, marking them read and advancing from the list never wait for
  the network.
- It is a `SessionDataCleaner`: sign-out and account change delete the file,
  and a check that started before that writes nothing and returns
  `AppError.Unauthorized`.
- `resetSource(source)` forgets one source's snapshot and keeps the records; a
  check of that source already on its way writes nothing (`Stale`).
- `fetchedAt` is the wall-clock start of the request. An answer that started
  before the stored `fetchedAt` of its source is `Stale` and is not written.
- When a write fails outside a check, the new state stays in memory and reaches
  the file with the next write.

The switches and the sign-in prompt live in DataStore (`MarkSourcePreferences`),
not in the file: `BarsClient`'s listener reads them, and a dependency on the
repository would be a cycle `BarsClient → repository → BARS read → BarsClient`.

| Key | Absent means | Cleared on sign-out |
|---|---|---|
| `myitmo_marks_enabled` | on | no, a device setting like `schedule_changes_enabled` |
| `bars_marks_enabled` | no `Оценки БАРС` switch yet | yes, with the BARS session (`BarsPreferenceRepositoryImpl`) |
| `bars_marks_prompt` | `NONE` (`BarsLoginPrompt`: `NONE`, `PENDING`, `SHOWN`) | yes, with the BARS session |
| `sheet_marks_enabled` | on | no, a device setting like `myitmo_marks_enabled` |

Any successful BARS answer of the account, an interactive sign-in, the overlay
or the background read, runs `BarsMarksActivation.onBarsAnswered()`: the first
one turns `Оценки БАРС` on (`enableBarsMarksIfUnset`, `null → true`) and
enqueues the work; every one sets the prompt back to `NONE` and withdraws it.
Users signed in to BARS before this version get the switch with the first use
of the chip, without a new sign-in. A failed preference write never turns a
good BARS answer into an error. Switching a source off saves the switch,
forgets its snapshot (the records stay) and syncs the work; for BARS it also
sets the prompt to `NONE` and withdraws it. Switching on syncs the work, and
the first check of that source is a baseline again.

## BARS in the background

`BarsMarkReader.read(half)` runs inside `BarsClient.backgroundAccount`
([session](recordbook.md#bars-overlay)) on the MyItmoApi 2.x models (`User`, `Term`,
`StudentJournal`):

- Without a saved BARS session for the current ISU it is `NoSession` and asks
  nothing.
- It remembers the period selected on the server, selects the half-year, reads
  the disciplines, flows and journal references through `journalReferences`
  (shared with the overlay) and the journals in parallel, then selects the
  previous period back when it was different and has the form `\d{4}/\d{4}`.
  A failure to select it back does not change the result; a cancelled read
  does not select it back.
- Any failed request fails the whole read: a partial answer is never a
  snapshot.
- An ended ITMO.ID session (`SessionEnded`) moves the prompt from `NONE` to
  `PENDING` and changes nothing else. With `PENDING` or `SHOWN` every run still
  tries the cookies once: the ITMO.ID session may have come back, for example
  after signing in to My ITMO in the embedded browser.

## Notification

`MarkDigests.decide(news, prompt, now)` chooses after every run; `now` is
Moscow time:

- From 00:00 to 06:00 (`QuietHours`) nothing is shown or marked; the first run
  at or after 06:00 delivers what was found at night.
- Pending records are the ones not yet notified. With any, the digest names
  every unread subject, newest first, so a subject named by the replaced
  notification stays in it; with exactly one unread record it is `single`.
- A `PENDING` prompt is shown once and becomes `SHOWN`.
- Pending records count as delivered after the attempt, even without the
  notification permission: they stay on the home card and as dots. The prompt
  becomes `SHOWN` without the permission as well; the recordbook's snackbar
  still offers `Войти в БАРС`.

`AndroidMarksNotifier` shows `Новые оценки` with up to three subject names and
`… и ещё N` for the rest (`MarkSubjects.split`, `core/ui/markSubjectList`),
never the marks, in the `marks` channel `Оценки`; the lock screen shows the
title only. A tap on a single subject with exactly one My ITMO row of that name
opens its page, with the BARS journal when the `БАРС` chip is on and exactly
one plan has that name (`MarkNewsRules.target`); the tap never changes the
chip. Anything else opens the recordbook. The prompt `Войдите в БАРС`
(`Без входа оценки БАРС не проверяются.`) opens the recordbook with
`BarsLoginActivity` above it. Ids, routes and tests are in
[notifications](notifications.md#marks).

## In the recordbook

- `RecordbookViewModel` observes the records and fills `Content.newSubjects`
  with the name keys of the listed period's half-year, also for an open list,
  without a request. `RecordbookScreen` shows the new-mark dot for a row
  whose `subjectNameKey(name)` is among them and starts the row's TalkBack
  description with `Новое`. Another half-year's records mark
  nothing.
- `RecordbookSubjectViewModel` calls `markRead(half, subjectNameKey(name))`
  once, with the page's first content; a refresh does not repeat it, and a
  period without a half-year reads nothing. The dot of that row goes when the
  list is shown again; `markRead` that leaves no record removes the
  notification.
- The open list advances the snapshots. Before its requests the view model
  takes `readStarted()` (a wall-clock stamp); for the current half-year only, a
  successful My ITMO answer goes to `recordMyItmoSeen` and a successful overlay
  to `recordBarsSeen` (plans from `BarsPlanMarks.of` over the cached controls;
  journals without them are skipped). Both run as children of the load and
  never after an error. The answer is compared like a background one, but
  what it finds only gets a dot, never a notification, so a mark already seen
  in the app does not arrive an hour later. A source without a snapshot of
  that half-year is left alone: the list never creates the first snapshot.
  Other programmes and plans are carried unchanged.
- The home card `Новые оценки` is described in
  [home](home.md#feed).

## Network failures in the background

A network failure before any answer (`UnknownHostException` and other
`IOException`s anywhere in the cause chain, `isCausedByNetworkFailure` in
`core/network`) is `AppError.Network` on every path: My ITMO requests, the
My ITMO token refresh (a `TokenRefreshException` with a network cause is not
`Unauthorized`), BARS requests and the cookie renewal. The run is retried
through `outcomeOf`, the snapshots and records stay, and it never produces the
`Войдите в БАРС` prompt: only ITMO.ID's answer (`LOGIN_REQUIRED`) or missing
cookies end a session.

On Xiaomi (MIUI, HyperOS) the default battery mode `Умный режим` cuts the
network of a backgrounded application although WorkManager's `CONNECTED`
constraint holds: every background check fails with `UnknownHostException`,
while with `Без ограничений` a cold background run renews BARS and reads marks.
The settings row `Работа в фоне` leads to the page that lifts it
([settings](../settings.md#recordbook)).

## Debug tools

In the schedule-changes card of the debug tools, `Проверить оценки` calls
`MarkTracking.checkNow()`: the one-off work `marks-check-now`
(`ExistingWorkPolicy.REPLACE`, network constraint, tag `marks`) with the same
`MarksCheck` on the real account. `Проверить продление БАРС` starts the
read-only probe `BarsCookieProbeWorker` (`core/debug/BarsSessionProbe`,
implemented by `WorkManagerBarsSessionProbe` in `feature/recordbook/work`,
unique work `bars-cookie-probe`, 120 seconds after the tap, so the app can be
sent to the background and its process killed): one ITMO.ID request with the
WebView's cookies and, on a code, one exchange. It writes no `Set-Cookie`, no
BARS header and nothing else; its logcat line (`BarsCookieProbe`) holds only
the outcome, the step, counts and the process age. Debug builds only.

## Tests

Unit, in `shared/feature-recordbook/src/androidHostTest` with the 2.2 files in
its `resources/stores/`: `StudyHalfTest`, `MarkSnapshotsTest`, `MarkDiffTest`,
`MarkNewsRulesTest` and `MarkDigestsTest` for the model, the comparison, the
unread rules and the digest; `BarsMarkReaderTest`,
`MarksFileStoreTest` and `MarkTrackingRepositoryImplTest` for the BARS read,
the file and the repository (baselines, carrying over, echoes, stale answers,
the session clear, a new half-year, network failures); `MarksCheckTest`,
`DefaultMarkTrackingTest` and `BarsMarksActivationTest` for the run, the work
and the switches; `BarsCookieSilentLoginTest` and `BarsClientTest` for the
cookie renewal and `backgroundAccount`; `MarksHomeCardSourceTest`,
`RecordbookModuleTest` (the graph, one instance per type, the six cleaners, the
qualified home card) and `RecordbookSignOutTest`; the dots, reading and
advancing in `RecordbookViewModelTest`, `RecordbookBarsOverlayTest` and
`RecordbookSubjectViewModelTest`. In `:shared:core`: `MarkSubjectsTest`,
`BackgroundChecksTest` and `RecordbookSubjectArgsTest`. In `:app`:
`MainActivityIntentRoutingTest`, and for the two graphs `RecordbookBridgeTest`,
`RecordbookBindingsTest`, `BackgroundCheckGraphTest` and
`HomeSourcesGraphTest`; the dot on the Compose list in `RecordbookScreenTest`.
Instrumented: `MarksWorkTest` (the app's `WorkManager` through the debug
`MarksTestEntryPoint`) and `MarksNotificationTest`; the marks card
is in `HomeScreenTest` and the `HomeScreenshotTest` goldens. They use synthetic subjects and restore the
WorkManager state they found.
The iOS tests are listed in [iOS](#ios).

## iOS

The iOS app runs the same check: `MarksCheck`, the comparison, the unread
subjects, `MarkDigests` and the BARS read are the shared ones above, and
`PlatformCapabilities.marks` is on, so the settings page, the home card and
the dots show. iOS adds only the platform side
(`shared/feature-recordbook/src/iosMain/.../data/marks/`, bound in
`recordbookIosModule`).

- Work. The check is the `marks` step of the app's one refresh task
  (`RefreshStepKeys.MARKS`, after the schedule changes and before the calendar
  sync, see [`docs/ios.md`](../ios.md#background-refresh)) with Android's three
  hours (`RefreshStepKeys.MARKS_PERIOD`) and Android's retries. It runs at
  launch, on every return to the app and on the system's wakes once its period
  has passed. `RefreshTaskMarksScheduler` is the `MarksScheduler`: `runOnce` (a
  switch turned on, BARS turning on by itself) makes the step due at once and
  asks the running app for a run, as `marks-check-now`; `cancel` does nothing,
  since the check skips itself signed out or with every switch off.
- Degradation. iOS picks the moments of the background refresh from how the
  app is used, often hours apart, and never runs it with Background App
  Refresh off or in Low Power Mode, so there is no exact three-hour rhythm; the
  check also runs on every return to the app. The settings row `Обновление
  контента` (Background App Refresh) shows while it is off for the app.
- BARS. In the background BARS renews only through the ITMO.ID cookies
  (`BarsCookieSilentLogin` over `KeychainItmoIdCookies`), never the hidden
  WebKit view. No cookies or ITMO.ID's `LOGIN_REQUIRED` end the session with
  one `Войдите в БАРС`; a network failure is retried and never prompts, as on
  Android.
- Notification. `IosMarksNotifier` posts Android's two notifications through
  `IosAppNotifier` on the thread `marks` (`marks-1` for `Новые оценки` with the
  subject names, `marks-2` for `Войдите в БАРС`), texts by catalog key. A tap
  carries Android's entry action in `userInfo["action"]`, which the iOS tap
  handler does not route yet, so it opens the app where it was
  ([degradations](../ios.md#degradations)); the recordbook's snackbar offers
  `Войти в БАРС`.
- Quiet hours. `MarksRefresh` runs the check, then hands marks and the
  reminder found between 00:00 and 06:00 Moscow time to the system for 06:00
  at once (a calendar trigger); they count as delivered, as the schedule
  change step does. Android keeps them for its first run after 06:00.
- Demo: the home card and the dots show the demo's unread subjects; the check
  asks nothing.
- Debug: a Debug build launched with `-itmoRunRefresh` also posts a fixture
  digest (`MarksFixture`, four demo subjects).
- Tests: `MarksRefreshTest` (an ended BARS session prompts once, a failure
  retries without a prompt, night marks and the reminder at 06:00, a skipped
  check), `RefreshTaskMarksSchedulerTest` and `RecordbookIosModuleTest` in
  `shared/feature-recordbook` iosTest; `BackgroundRunnerTest` (`shared/ios`:
  the mark check after the schedule changes within the deadline, its
  three-hour period); `ITMOWidgetsTests/BackgroundRunnerTests` (the step in
  the app graph, the fixture digest by catalog key),
  `SnapshotTests/SettingsSnapshotTests` (the recordbook page),
  `UITests/HomeUITests` (the marks card in the demo) and
  `UITests/SettingsUITests`.
