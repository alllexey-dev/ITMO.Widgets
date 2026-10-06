# Recordbook

`feature/recordbook` shows official grades from MyITMO, optionally overlaid with
BARS, links physical education to the sport score and notices new marks in the
background ([mark tracking](#mark-tracking)). Grades never reach Backend and the
feature works without `Подключение к ITMO.Widgets`; only the subject page's
links go through `core/resources` (see [resources](resources.md)).

## MyITMO sources

| Data | Endpoint | Identity |
|---|---|---|
| Programmes and periods | `/api/record_book/specializations` | `main_plan` selects the programme; `semester` is the through-numbered semester (1/2 for the first year, 3/4 for the second) |
| Recordbook entries | `/api/record_book/{main_plan}/{semester}` | `est_id` is one entry, `discipline_id` the curriculum discipline |
| Control events | `/api/record_book/{est_id}` | requested only when `have_tree = true`; server order and `parent_id` are kept |
| Sport semesters | `/api/sport/semesters/list`, `/current` | own `id` and labels like `Осень YYYY/YYYY`; not the recordbook semester number |
| Sport score | `/api/sport/personal/score?semester_id={id}` | `sum.attendances`, `sum.other` |

Observed behaviour the mapper relies on:

- The catalog contains future semesters; an empty result or an unset grade is
  not an error. The initial period follows `AcademicTimeProvider`; an explicit
  choice is restored separately.
- `current_score` and `rate` are independent; PE has been seen with a credit and
  `current_score = null`. Never render that as zero or derive a grade from sport.
- `rate` may be a fractional code (`4/C`), a credit word or `null`. The UI renders
  contiguous codes (`4C`, `2FX`); `Незачёт` is not a pass; unknown values are not
  counted as passed.
- For a control event `rate` is the obtained score, `min_value`/`max_value` the
  bounds; `lower_value` is not a result. A missing result shows as `—`.
- Trees are stored with their server order and `parent_id`; a parent carries
  its own total, so it is never added to its children. `have_tree = false` is
  normal. Grouping for display is described under [Control groups](#control-groups).
- A `teacher` object may be present with empty fields; the mapper returns `null`.
- `attendances` history may be `null` while `sum` exists.
- HTTP 200 is not success: `error_code` is checked, and an API error never
  becomes an empty recordbook or a zero sport score.

Library limits: MyItmoApi stores `attempt` as a primitive, so the attempt number
is not shown; `sem_id` is absent from the model and its relation to the sport
`id` is unconfirmed; `lms_link` was empty in every observed response.

## Physical education and sport

1. Only the observed titles `Физическая культура и спорт (базовая)` and
   `(элективная)` are recognised after normalisation. A new title is not guessed.
2. Odd recordbook semesters map to autumn, even to spring; the academic year and
   the full season label of the sport catalog must match, and only a unique match
   is accepted. Ambiguity or an unknown label is its own state.
3. `core/sport/SportScoreRepository` (implemented in sport data) returns the
   summary; `Мой спорт` uses the same implementation for full history.
4. The PE row shows the matched period's sport total and bar in the sport
   attendance colour, even while `rate` is `null`; a total above 100 stays
   visible and fills the bar. The PE subject page shows the sport card (ring
   with attendance and bonus sectors) instead of the grade scale. The grade
   badge and the passed-subject count come only from the official `rate`.
5. PE falls behind (`isSportBehind`) when points are short of 100 and the sport
   period is over, or the current one ends within 28 days. Only
   `/api/sport/semesters/current` carries `date_end`; when it fails, every
   period counts as current without an end date and no alarm is raised.
6. Debug scores apply to the current sport period only, in both screens, and are
   disabled in release.
7. A sport error never hides the official recordbook; retry is available by pull
   or from the PE details.

## Recordbook list

The root is a period selector with the `БАРС` chip and one list; fewer than
fifteen subjects, so no search or filters.

- A summary card `Сдано N из M` with a thin bar appears only once at least one
  subject has a final grade or credit, that is, in the session.
- `Требуют внимания` lists the subjects that need action, then `Дисциплины`
  lists the rest; without such subjects there are no headings. A passed subject
  never needs attention. The reason replaces the assessment kind in the row, in
  the error colour, in this order: `Неявка`; `<control> ниже минимума` for a
  known, non-additional control graded under its positive minimum; `Пересдача`
  for a failed result; `Спорт: ещё N баллов` for PE that
  [falls behind](#physical-education-and-sport). Controls are only those an
  earlier answer brought (`RecordbookRepository.cachedControls`, or
  `BarsRecordbookRepository.cachedControls` for a BARS journal, which the
  overlay fills for every journal); the list never requests controls itself.
  The rule is `attentionReason` in `presentation/RecordbookAttention.kt`.
- A subject row (`item_recordbook_subject.xml`) is the name (two lines), a
  metadata line (assessment kind, an uncoded result such as a no-show reason,
  `нет в БАРС`, a PE sport state) and one result on the right. A final result
  (a grade, a credit, a no-show, or no points at all) is a badge: the compact
  code (`4C`, `2FX`), `Зачёт` or `—`, green when passed, error colour when
  failed, neutral otherwise. Until then the row shows the points and a 64 dp
  bar of their share of 100: error colour with an attention reason, the sport
  colour for PE, otherwise the status colour. Rows bind without animation, so
  a recycled row never animates another subject's value.
- A subject with unread new marks has an 8 dp `colorPrimary` dot `new_mark`
  after its name until its page is opened
  ([mark tracking](#in-the-recordbook)).
- A subject without My ITMO or BARS points, final grade or no-show shows the
  total of its connected sheet instead of `—`: `ic_table` and the value, no bar
  ([sheet scores](#in-the-recordbook-list)).

Subjects open with `program_id`, `semester`, `study_year` and `entry_id`; the
subject's own `discipline_id` comes with the reloaded official subject.

## Subject page

`RecordbookSubjectFragment` is one page without tabs: the toolbar holds the
subject name and `<assessment kind>, N семестр`; below it one pull-to-refresh
list drawn by `SubjectHubAdapter`. Every list section is a heading in
`colorPrimary` over one connected group of rows
([design](../design.md#connected-groups)); nothing on the page is separated
by « · ». In this order:

1. The result card (`item_subject_hero.xml`, `Card.Hero`): points, the grade
   badge once a final result exists, and `GradeScaleView`, a bar on the 100
   scale with a tick at the lowest whole score of each grade, labelled by its
   letter (E 60, D 68, C 75, B 84, A 91; a plain credit has one `зачёт` tick at
   60). While the result is open the hint names the next step, `до 4C ещё 3` or
   `до зачёта ещё 8` (`RecordbookGradeScale.nextStep`); a passed plain credit
   has none. Under a hairline the card ends with the own total from a
   connected sheet, or with the offer to connect one
   ([sheet scores](#on-the-subject-page)). PE shows the sport card instead.
2. `Ссылки` for everything except PE, including past periods: at most three
   link rows, then `Все ссылки, N` that opens the links sheet, or
   `Добавить ссылку` while the subject has no links. See
   [resources](resources.md#subject-page).
3. `Чаты`: own and shared chat links in a group of their own.
4. `Контрольные точки`: controls and [control groups](#control-groups),
   groups expanded.
5. `Преподаватели`: distinct people from the subject's lessons, each with the
   lesson types they run, most frequent first; without lessons the recordbook
   teacher stands in. A row with a usable teacher ISU opens the shared person
   profile. A recordbook-only teacher without ISU remains informational, including
   past periods, PE and unmatched subjects; no identifier is guessed by name.
   With `Подключение к ITMO.Widgets` a row with an ISU shows the tone of the
   teacher's AI summary as a 10 dp dot before the chevron (`level_dot` in
   `item_subject_teacher.xml`); `RecordbookSubjectViewModel` asks
   `TeacherLevelsRepository` whenever the set of teacher ISUs changes and keeps
   the answer in `SubjectHubState.teacherLevels`
   ([teacher levels](reviews.md#teacher-levels)). A row with an ISU but no level
   keeps the place of the dot, so the chevrons stay in one column; a row without
   an ISU has none. TalkBack adds `, тон отзывов: …` to the row.
6. `Ближайшие пары`: only for the current period and never for PE.

The scale is `RecordbookGradeScale`: above 90 is 5A, above 83 4B, above 74 4C,
above 67 3D, 60 and above 3E, below 60 unsatisfactory; a plain credit
(`Зачёт`, not graded) has the single threshold 60. Hints count whole points,
so a strict threshold is aimed at the next whole score.

A control row (`item_recordbook_control.xml`, a row of a connected group)
shows the name and `score / maximum` with a thin bar relative to its maximum:
error colour below the minimum, green once the minimum is met or the maximum
reached, primary otherwise. Requirements appear only when broken: `минимум N`
below the minimum and `Неявка`, both in the error colour. A date and a teacher
who differs from the subject's teacher follow on the next line, separated by a
comma; additional points are named `Дополнительные баллы`. Lone controls in a
row share one group; each control group has its own heading and group.

### Control groups

`RecordbookControlGroups.groupControls` keeps server order and returns
`ControlEntry` values, lone controls or a `ControlGroup` in the place of its
first control:

- A tree root with children is a group titled by the root; its rows are the
  leaves, because a parent carries its own total.
- At least two childless roots whose names differ only by a trailing number
  (`Лабораторная работа 3`, `№ 3`) form one group titled by the name without
  the number. `ControlGroupKind` names the known kinds through string resources
  (`Лабораторные`, `Контрольные`, `Практические`, `Домашние задания`); any other
  title keeps the source text.
- A group is a heading (`item_recordbook_control_group.xml`: the title in
  `titleSmall`, the sum of its known scores out of the sum of known maxima at
  the end, `—` while nothing is graded) and a `ниже минимума` label when any
  graded control in it is under its positive minimum. Its controls follow as
  one connected group. Ungraded controls are never below anything.

### Lessons

The schedule side is reached through `core/schedule/SubjectLessonsGateway`,
implemented in `feature/schedule/data`, because features never import each
other.

- The window is today … +28 days. The view model refreshes it through
  `ScheduleRefreshGateway` (a failed refresh with an empty cache is an error
  with `Повторить`; with cached lessons the cache is shown) and observes the
  gateway. The two nearest lessons are shown (`вид, аудитория, корпус` on one
  line); `Все пары, N`, the last row of the group, opens the rest in
  place. Lesson rows are informational; the details sheet belongs to the
  schedule feature.
- The link between a recordbook discipline and a schedule subject is
  `SubjectContextResolver`: an exact `discipline_id == subject_id` binds
  silently (on live data this holds for every discipline except PE); a
  confirmed binding from `SubjectBindingStore` (DataStore, cleared on sign-out)
  wins over it; otherwise a single normalised-name match is *proposed* in an
  outlined card (`Связать` / `Нет`, nothing is stored on `Нет`), several
  matches ask which one, none reads as not found. `subjectNameKey` is the one
  normalisation, shared with the BARS merge.

Links are observed separately from lessons, so a lesson update never drops an
asynchronously loaded link snapshot.

`RecordbookRepositoryImpl` is a singleton with a memory cache of the last
programs, subjects per `(programId, semester)` and controls per entry
(`cachedPrograms`, `cachedSubjects`, `cachedControls`, cleared on sign-out as a
`SessionDataCleaner`). Both the root and the subject page seed
`Content(refreshing = true)` from it before the network answers, so a screen
opened a second time never shows the skeleton; the sport card waits for the
refresh.

## BARS overlay

The header has a `БАРС` filter chip (off by default, DataStore, cleared on
sign-out). When on, subjects found in BARS for the same academic year and term
show the BARS score, grade, attempt and control tree; periods, identities,
teachers, PE and sport stay MyITMO.

- Matching is by normalised title (case, spaces, ё/е) inside one period.
  Unmatched subjects keep MyITMO values with a `нет в БАРС` note; PE gets no note;
  duplicate titles on either side are not matched. An empty BARS journal
  (`total = 0`, no marks) shows as "no marks", not 0.
- The MyITMO list appears immediately; after a pull the refresh indicator stays
  until BARS answers (the load on entry, a period change and the switch itself
  wait silently), journals are requested in parallel, and the note appears only
  after a successful BARS response for that period.
- A BARS failure keeps the MyITMO list and shows a snackbar; when the ITMO.ID
  session has ended the snackbar offers `Войти в БАРС` (`BarsLoginActivity`).
  Nothing is silently substituted in either direction.
- Changing the period while the chip is on changes the saved period in web BARS
  as well: it is a server-side setting with no stateless read.

Session: the BARS token lives 30 minutes, has no refresh and cannot be exchanged
from a MyITMO token. BARS goes through MyItmoApi 2.x: the library's
`dev.alllexey.itmoapi.bars.BarsClient` (one per process, built in
`di/RecordbookModule.kt` over `OwnerBoundBarsStorage` and `BarsRenewal`) and
`BarsLogin` for the ITMO.ID side (`loginUrl`, `isCallback`, `isAllowedPage`,
`extractCode`, `requestCodeWithCookies`). Both run on a Ktor OkHttp engine of
their own (`@BarsHttp`: connect 20 s, read 30 s, no cookie jar, cache or
redirects), never MyITMO's. The ITMO.ID session in the app's WebView lives
about 90 days, so `BarsWebSilentLogin` renews the token by loading the official
OIDC URL in a hidden WebView and intercepting only the exact callback with a
checked `state`; the library retries once on 401 through the suspend
`BarsCodeSupplier`, which is `BarsRenewal`. No JavaScript bridge, no
localStorage reads. The app's `BarsClient` binds the encrypted session file to
the current ISU, verifies `login` against it, serialises period selection and
maps `MyItmoException` to `AppError` (`Auth` 401 `Unauthorized`, `Auth` 403 and
`Http` 423 `Forbidden`, `Http` 404 `NotFound`, `Network` `Network`, anything
else a retriable `Unknown`). `OwnerBoundBarsStorage` is the library's suspend
`BarsStorage` and the only writer of `bars_tokens.enc`, whose content
(`<isu>\n<header>` through `TokenCipher`, checked with
`BarsClient.isValidAuthorization`) is the one 2.2 wrote. The library's locks are not reentrant, so a block never nests
period changes. `BarsSessionRepositoryImpl` serves the interactive sign-in
from `BarsLogin` and exchanges the code through `BarsClient.login`.
`BarsPreferenceRepositoryImpl` is the session cleaner for both the token and
the chip. `BarsRecordbookRepositoryImpl` keeps the controls of every journal it
has read in memory (`cachedControls`, the source of `Требуют внимания` while the
chip is on) and is a session cleaner too.

Screens keep renewing through the WebView (`BarsClient.account`). The
background mark check has no WebView and renews through the ITMO.ID cookies
instead (`BarsClient.backgroundAccount`, decision
[0012](../decisions/0012-bars-background-renewal.md)):

- `BarsCookieSilentLogin` reads the `Cookie` header the WebView would send to
  the `bars` authorization URL (`ItmoIdCookies`, `CookieManager` on the main
  thread) and asks MyItmoApi's `BarsLogin.requestCodeWithCookies` for a
  code (`BarsSessionCode`: `CODE`, `LOGIN_REQUIRED`, `REJECTED`,
  `HTTP_ERROR`): one HTTPS request with redirects off and no cookie jar or
  cache, only the exact callback with the checked `state` gives a code. `Set-Cookie` of the answer goes back to
  `CookieManager` for that URL only, as the WebView would store it.
- No cookies or ITMO.ID's sign-in page (`LOGIN_REQUIRED`) is an ended session;
  a rejected callback or an HTTP error is `Unknown`, a network failure
  `Network`. Neither of the last two ends the session, and the saved BARS
  header stays.
- For the duration of one `backgroundAccount` block `BarsRenewal` (the
  library's `BarsCodeSupplier`) asks the cookie renewal, afterwards the WebView
  again; the client's mutex makes the mode belong to the block that holds it.
  An ended session leaves the supplier as `BarsSessionEnded`, a failed renewal
  as `BarsFailure` with its `AppError`, past the library's renewal, which
  keeps the saved header. Without a
  saved session for the current ISU nothing is requested (`NoSession`).
- Codes, cookies and tokens never reach the log, exceptions or `toString()`.
- Every successful answer of `account`, `login` or `backgroundAccount` is
  reported to `BarsSessionListener` (`BarsMarksActivation`) after the lock is
  released.
- The background read selects its half-year on the server and then selects
  back the period the user had, so the check does not move the period web BARS
  shows ([mark tracking](#bars-in-the-background)).

```text
RecordbookFragment → RecordbookViewModel ─┬─ RecordbookRepository (MyItmoApi)
                                          ├─ BarsRecordbookRepository → BarsClient → itmoapi.bars.BarsClient
                                          └─ BarsPreferenceRepository (DataStore)
RecordbookBarsMerge.apply(myItmo, bars)   pure merge by title
```

Mapper rules: server `marks.total`; the last unambiguous active approval
(`Approval.gradeCode` turns `Удвл., E` into `3/E`, credits stay words);
works by `checkpoint_id`; extra points as a separate row; absence is not a pass;
plans with `has_course_project` are rejected for now.

## Mark tracking

The application notices new and changed own marks of the current half-year in
My ITMO and BARS and changed totals of the connected sheets
([sheet scores](#background-check-of-sheets)) by itself and names the subjects
in one notification.
Everything stays on the device: marks, the BARS token and the ITMO.ID cookies
never reach Backend, and the feature works without
`Подключение к ITMO.Widgets`. BARS is read in the background through the
cookie renewal (decision [0012](../decisions/0012-bars-background-renewal.md));
storage and delivery follow the schedule changes (decision
[0013](../decisions/0013-schedule-changes-on-device.md)).

### Background check

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

### Comparison

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

### Unread subjects

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

### Storage

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

`MarkTrackingRepositoryImpl` is a `@Singleton` that reads the file once and
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

### BARS in the background

`BarsMarkReader.read(half)` runs inside `BarsClient.backgroundAccount`
([session](#bars-overlay)) on the MyItmoApi 2.x models (`User`, `Term`,
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

### Notification

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

### In the recordbook

- `RecordbookViewModel` observes the records and fills `Content.newSubjects`
  with the name keys of the listed period's half-year, also for an open list,
  without a request. `RecordbookAdapter` shows the dot `new_mark` for a row
  whose `subjectNameKey(name)` is among them, sets it on every bind and starts
  the row's TalkBack description with `Новое`. Another half-year's records mark
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

### Network failures in the background

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

### Debug tools

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

### Tests

Unit: `StudyHalfTest`, `MarkSnapshotsTest`, `MarkDiffTest`,
`MarkNewsRulesTest`, `MarkDigestsTest` and `MarkSubjectsTest` for the model,
the comparison, the unread rules and the digest; `BarsMarkReaderTest`,
`MarksFileStoreTest` and `MarkTrackingRepositoryImplTest` for the BARS read,
the file and the repository (baselines, carrying over, echoes, stale answers,
the session clear, a new half-year, network failures); `MarksCheckTest`,
`DefaultMarkTrackingTest`, `BarsMarksActivationTest` and
`BackgroundChecksTest` for the run, the work and the switches;
`BarsCookieSilentLoginTest` and `BarsClientTest` for the cookie renewal and
`backgroundAccount`; `RecordbookSubjectArgsTest`,
`MainActivityIntentRoutingTest`, `MarksHomeCardSourceTest`; the dots, reading
and advancing in `RecordbookViewModelTest`, `RecordbookBarsOverlayTest` and
`RecordbookSubjectViewModelTest`. Instrumented: `MarksWorkTest` (the app's
`WorkManager` through the debug `MarksTestEntryPoint`), `MarksNotificationTest`,
`RecordbookVisualTest.newMarksShowADotUntilTheSubjectOpens` and the marks card
in `HomeFeedVisualTest`. They use synthetic subjects and restore the
WorkManager state they found.

## Sheet scores

A student connects the own total of a subject from a teacher's public Google
Sheet and sees it on the subject page and, while the official points are empty,
in the list. The connection, the downloaded sheet and other people's names stay
on the device: no Backend, no `Подключение к ITMO.Widgets` (decision
[0014](../decisions/0014-sheet-scores-on-device.md)). The code lives in
`feature/recordbook` under `domain/sheets`, `data/sheets`, `presentation/sheets`
and `ui/sheets`; `feature/resources` only offers the action.

### Connecting

- Any link of a subject period whose address is a Google Sheet
  (`core/resources/GoogleSheetUrl`: `https://docs.google.com/spreadsheets/d/<id>`
  or `…/u/<n>/d/<id>`, `gid` from the fragment, then the parameter; published
  `/d/e/…` addresses are not sheets) has `Мои баллы` in its action sheet, own,
  shared or from past years. It opens `SheetScoresBottomSheet` through
  `AppNavigator.openSheetScores(SheetScoresArgs)` with the link's
  `ResourceScope` (`discipline_id` and period). One connection per scope; a new
  one replaces the old after a successful choice.
- `SheetScoresViewModel` downloads every tab (`inspect`) and looks for the own
  row. Found in one row per tab with one key: the cells of that row are
  collected from every tab; a total found by its header is connected at once
  and the sheet closes, otherwise `Выберите итог` lists the filled cells by tab.
  Several rows: `Выберите свою строку`. No row: `Выберите лист`, then the
  student rows of that tab; more than 8 rows get `Поиск по фамилии` (case and
  `ё` ignored, `Никого не нашлось` when empty). Step prompts are `titleSmall`
  in `colorPrimary`, apart from the choices. The students start at the topmost
  name or ISU above the row in its column; empty cells and up to two other
  texts in a row are skipped, a people title such as `ФИО` ends them. A name
  is 2–8 words. The workbook lives only in the view model, never in
  the saved state; after process death the sheet is downloaded again.
- States share one bounded area of the sheet (288 dp): loading, the choices,
  and failures with `ic_error` and their text (`Нет связи` with a tonal
  `Повторить`, `Таблица закрыта`, `Таблица слишком большая`,
  `Строка не найдена`). A failed write keeps the choice and shows a snackbar.

### Downloading

`PublicSheetClient` uses its own `@PublicWebClient OkHttpClient`: no cookies,
no HTTPS-to-HTTP redirects, timeouts 15/30/90 s, requests only to
`https://docs.google.com/`. Addresses and bodies never reach the log or an
exception.

- A tab: CSV `GET /spreadsheets/d/<id>/export?format=csv&gid=<gid>` (redirects
  to the download host are followed). An answer that is not `text/csv`, or 401
  or 403, falls back to the HTML tab
  `GET /spreadsheets/d/<id>/htmlview/sheet?headers=false&gid=<gid>`, parsed by
  `SheetHtmlGrid` (Jsoup, `table.waffle`, `colspan`/`rowspan` spread as in CSV).
- The tabs: `GET /spreadsheets/d/<id>/htmlview`, the JavaScript
  `items.push({name, pageUrl, gid})` lines (`SheetTabsParser`). The link's tab
  comes first; at most 50 tabs, 4 at a time. `gviz/tq` and `pubhtml` are not used.
- Closed: a sign-in page (`accounts.google.com`, `/ServiceLogin…`,
  `/v3/signin…`) at any step, 401/403 of the HTML tab, 404 of the tab list.
  400/404 of a tab: the tab is gone (`Столбец не найден`). No connection, 5xx
  and 429: `Нет связи`, the stored value stays. Over 5 MiB per answer
  (`Content-Length` or counted): `Таблица слишком большая`; such a tab is
  skipped while connecting, and the sheet is too large only when every tab is.
- CSV is parsed by `CsvGrid` (RFC 4180, BOM, CRLF/LF, line breaks in quotes,
  ragged rows padded).

### Own row, header and total

- The own row: a cell equal to the ISU (`CurrentUser.isu`, spaces ignored) in
  any tab, else a cell equal to a form of the ITMO.ID name after `SheetText`
  normalisation (case, `ё`, spaces, the space after a dot): `Фамилия Имя
  Отчество`, `Фамилия Имя`, `Фамилия И.О.`, `Фамилия И.`, built with the surname
  first and with it last, because the order of the words in ITMO.ID is not
  known. The stored key is the ISU or the normalised name, never a row number;
  every reading finds the row again (`SheetRows.locate`: the key column first,
  then any column, exactly one row).
- The header (`SheetHeaders`): the students start at the topmost row of the
  same kind of key above the own row; up to 6 rows above it are the header. A
  row whose only text is at or left of the key column is the tab's title (a
  teacher) and is skipped. A group title spans to the next title of its row or
  of a row above; the last header row does not span. A column's path joins its
  titles top-down with ` · `; a column without one is `Столбец <буква>`.
- The total (`SheetTotals.detect`): keyword groups by priority, matched as
  words of the path by their start (`итог` finds `ИТОГО` and `Итоговый балл`):
  `итог`, `σ`/`∑`, `сумма`/`сум`/`sum`, `total`, `score`, `bars credits`/`барс`,
  `оценка`, `зачет`. Ties: a one-word path segment starting with the keyword, a
  filled value, an earlier tab, a column further right.
- The column is stored as the tab's `gid` and the header path; a reading finds
  the same path again (the nearest to the old index when repeated) and falls
  back to the old index while the tab is that wide, else `Столбец не найден`.
  Values are shown as the sheet shows them, never recomputed.

### On the subject page

At the bottom of the result card (`item_subject_hero.xml`, bound in
`SubjectHubAdapter`), under a hairline:

- A connection: `item_subject_sheet_score.xml` included in the card, with
  `ic_table`, the value (`titleMedium`, `—` when empty), `путь, лист «Лист»`
  (`sheetCaption`: the levels of a header path joined by ` › `, the tab name
  only when it has one) and a status line: `Обновлено в HH:mm` today or
  `Обновлено d MMMM`; `Нет связи` keeps the stored value; `Таблица закрыта`,
  `Строка не найдена`, `Столбец не найден`, `Таблица слишком большая` in the
  error colour. The status line runs under the 48 dp `⋮`, whose glyph lines up
  with the card's content edge, so a status never wraps on a narrow screen. A
  tap opens the tab (`tabUrl`); `⋮` offers `Открыть таблицу`, `Изменить итог`
  (the sheet at the choice of the total, the current one checked) and
  `Отключить`. The row has no surface of its own.
- No connection but sheet links: the text button `Мои баллы из таблицы` with
  `ic_table` in the same place. One sheet link opens the connection for it;
  several ask `Какая таблица?` first: own links (`, моя`), the pinned one,
  `SCORES`, the rest by rank, one entry per address.
- Without controls the `My ITMO не присылает детализацию…` card is not shown
  when the card has either row (the sheet is the detail); a failure to load
  the controls stays.
- The stored value shows at once; the page downloads the tab on entry and on
  every pull, without an indicator. PE has neither row.

### In the recordbook list

`RecordbookViewModel` collects `SheetScoresRepository.observe()` only, never
downloads, and passes the totals of the selected period
(`Content.sheetTotals` by `discipline_id`). `sheetFallback` keeps a total only
for a non-PE subject without a no-show, a final grade and My ITMO or BARS
points. The row then shows `sheet_mark` (`ic_table`, 16 dp) and the value
(`titleMedium`, one line, at most 96 dp) without the bar; TalkBack reads
`Из таблицы: 66,3`.

### Background check of sheets

- `MarksCheck` runs `MarkTrackingRepository.checkSheets()` after My ITMO and
  BARS while `Оценки из таблиц` (`sheet_marks_enabled`, on by default, kept on
  sign-out) is on. It reads every connection of the current half-year
  (`StudyHalf.of(today).periodKey`), one at a time.
- A connection keeps `value` (the last read, shown) and `baseline` (the last
  non-empty). Only a background read of a `tracked` connection with a new
  non-empty value different from the baseline is news: `MARK_ADDED` from an
  empty baseline, else `MARK_CHANGED`, a `MarkEvent` of `MarkSource.SHEETS`
  named by the scope's subject. An emptied cell is no news and keeps the
  baseline. Any successful read, also connecting, opening the page or a pull,
  moves the baseline and tracks the connection, so a total seen in the app is
  not notified later. A failed read changes only the status; `Нет связи` is an
  error of the run (retry), other statuses are not.
- Switching `Оценки из таблиц` off keeps the totals and untracks every
  connection (`resetSource(SHEETS)` → `untrack()`), so the first background read
  after switching on is a baseline. A check started before the reset or a
  session clear writes nothing (`Stale`).

### Storage

`SheetScoresFileStore` keeps every connection in
`filesDir/sheet_scores/state.json` (format 1, `owner` is the ISU): the scope,
the address, the tab, the row key and its column and kind, the header path and
index, `value`, `baseline`, `tracked`, the status, `updatedAt` and
`connectedAt` (wall clock, epoch milliseconds). Written through
`AtomicTextFile` as kotlinx JSON (`RecordbookStoreJson`) in the shape 2.2's
Gson wrote: format 1, absent nulls, `format` and an empty `connections` always
written. Excluded from backup and device transfer. A corrupt file, another
format, a missing or invalid required field (a blank key, an address that is
not a sheet, an unknown key kind or status) or another account's file is
deleted. `SheetScoresRepositoryImpl` is a `@Singleton`
`SessionDataCleaner`: sign-out deletes the file; a reading is written only when
the session generation and the whole connection it was taken for are unchanged.

### Tests

Unit: `GoogleSheetUrlTest`, `CsvGridTest`, `SheetIdentityTest`,
`SheetHeadersTest`, `SheetRowsTest`, `SheetTotalsTest`, `SheetScoreRulesTest`
on synthetic sheets in `app/src/test/resources/sheets/`;
`SheetTabsParserTest`, `SheetHtmlGridTest`, `PublicSheetClientTest`
(MockWebServer), `SheetScoresFileStoreTest`, `SheetScoresRepositoryImplTest`,
`SheetScoresViewModelTest`; the sheet cases of `MarkTrackingRepositoryImplTest`,
`MarksCheckTest`, `DefaultMarkTrackingTest`, `MarkNewsRulesTest`,
`RecordbookSubjectViewModelTest`, `RecordbookViewModelTest` and
`RecordbookDisplayedScoreTest`. Instrumented: `SheetScoresVisualTest`, the sheet
cases of `RecordbookVisualTest` and
`SubjectLinksVisualTest.actionsSheetOffersMyScoresOnlyForAGoogleSheet`. No real
sheet is opened; names and ISUs are made up.

## Verification

```bash
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.recordbook.RecordbookVisualTest,dev.alllexey.itmowidgets.feature.recordbook.RecordbookBarsVisualTest
./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.recordbook.SheetScoresVisualTest
./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.recordbook.work.MarksWorkTest,dev.alllexey.itmowidgets.feature.recordbook.MarksNotificationTest
```

Unit tests cover the Retrofit paths through an in-memory interceptor, nullable
fields, HTTP-200 errors, the shared sport formula, period matching and ambiguity,
the BARS merge, chip persistence, silent login and the 401 retry, the grade
scale and next-step hints (`RecordbookGradeScaleTest`), control groups
(`RecordbookControlGroupsTest`), the sport shortfall (`RecordbookSportBehindTest`),
attention reasons and the session-only summary (`RecordbookViewModelTest`), the
subject context resolver, the binding store and every subject-page state of
`RecordbookSubjectViewModel`, including links, chats and `Все пары`, and mark
tracking ([Tests](#tests) above). Visual tests
run the real Fragments in `RecordbookPreviewActivity` with synthetic data
(`RecordbookPreviewFixtures`): compact rows, `Требуют внимания` with PE, the
summary only in the session, the one-page subject with its hint, `Ссылки` as
a connected group of three rows ranked by score with votes and `Все ссылки, N`,
a vote from the page, chats with the own one marked `моя`,
`Добавить ссылку` without links, expanded groups, two lessons and `Все пары`,
a past period with its own link and PE without links, the dot of unread marks until the subject
opens, and the sheet total in every state, the connect hint and the list
fallback; `SheetScoresVisualTest` covers the connection sheet. Add
`-Pandroid.testInstrumentationRunnerArguments.appearanceMatrix=full` and
`-Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true` for all
four appearances and the PNGs (see
[Running the visual tests](../design.md#running-the-visual-tests)).
