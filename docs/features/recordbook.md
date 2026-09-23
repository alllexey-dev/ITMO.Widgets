# Recordbook

`feature/recordbook` shows official grades from MyITMO, optionally overlaid with
BARS, and links physical education to the sport score. Grades never reach
Backend and the feature works without `Подключение к ITMO.Widgets`; only the
subject page's links go through `core/resources` (see [resources](resources.md)).

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

Subjects open with `program_id`, `semester`, `study_year` and `entry_id`; the
subject's own `discipline_id` comes with the reloaded official subject.

## Subject page

`RecordbookSubjectFragment` is one page without tabs: the toolbar holds the
subject name and `<assessment kind> · N семестр`; below it one pull-to-refresh
list drawn by `SubjectHubAdapter`, in this order:

1. The result card (`item_subject_hero.xml`): points, the grade badge once a
   final result exists, and `GradeScaleView`, a bar on the 100 scale with a tick
   at the lowest whole score of each grade, labelled by its letter (E 60, D 68,
   C 75, B 84, A 91; a plain credit has one `зачёт` tick at 60). While the
   result is open the hint names the next step, `до 4C ещё 3` or `до зачёта ещё 8`
   (`RecordbookGradeScale.nextStep`); a passed plain credit has none. PE shows
   the sport card instead.
2. Link chips and then `Чаты` for everything except PE, including past periods;
   see [resources](resources.md).
3. `Баллы`: controls and [control groups](#control-groups), groups expanded.
4. `Преподаватели`: distinct people from the subject's lessons, each with the
   lesson types they run, most frequent first; without lessons the recordbook
   teacher stands in. Rows are informational; teacher profiles wait for Stage 29.
5. `Ближайшие пары`: only for the current period and never for PE.

The scale is `RecordbookGradeScale`: above 90 is 5A, above 83 4B, above 74 4C,
above 67 3D, 60 and above 3E, below 60 unsatisfactory; a plain credit
(`Зачёт`, not graded) has the single threshold 60. Hints count whole points,
so a strict threshold is aimed at the next whole score.

A control row shows `score / maximum` with a bar relative to its maximum:
error colour below the minimum, green once the minimum is met or the maximum
reached, primary otherwise. Requirements appear only when broken: `минимум N`
below the minimum and `Неявка`, both in the error colour. A date and a teacher
who differs from the subject's teacher follow on the next line; additional
points are named `Дополнительные баллы`.

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
- A group shows the sum of its known scores out of the sum of known maxima
  (`—` while nothing is graded) and a `ниже минимума` label when any graded
  control in it is under its positive minimum. Ungraded controls are never
  below anything.

### Lessons

The schedule side is reached through `core/schedule/SubjectLessonsGateway`,
implemented in `feature/schedule/data`, because features never import each
other.

- The window is today … +28 days. The view model refreshes it through
  `ScheduleRefreshGateway` (a failed refresh with an empty cache is an error
  with `Повторить`; with cached lessons the cache is shown) and observes the
  gateway. The two nearest lessons are shown; `Все пары · N` opens the rest in
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
from a MyITMO token. The ITMO.ID session in the app's WebView lives about 90
days, so `BarsWebSilentLogin` renews the token by loading the official OIDC URL
in a hidden WebView and intercepting only the exact callback with a checked
`state`; the library's `Bars` retries once on 401 through `BarsCodeSupplier`. No
JavaScript bridge, no localStorage reads. `BarsClient` binds the encrypted
session file to the current ISU, verifies `login` against it, serialises period
selection and maps `BarsApiException` to `AppError`. `BarsPreferenceRepositoryImpl`
is the session cleaner for both the token and the chip. `BarsRecordbookRepositoryImpl`
keeps the controls of every journal it has read in memory (`cachedControls`, the
source of `Требуют внимания` while the chip is on) and is a session cleaner too.

```text
RecordbookFragment → RecordbookViewModel ─┬─ RecordbookRepository (MyItmoApi)
                                          ├─ BarsRecordbookRepository → BarsClient → api.bars.Bars
                                          └─ BarsPreferenceRepository (DataStore)
RecordbookBarsMerge.apply(myItmo, bars)   pure merge by title
```

Mapper rules: server `marks.total`; the last unambiguous active approval
(`Approval#getGradeCode()` turns `Удвл., E` into `3/E`, credits stay words);
works by `checkpoint_id`; extra points as a separate row; absence is not a pass;
plans with `has_course_project` are rejected for now.

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.recordbook.RecordbookVisualTest,dev.alllexey.itmowidgets.feature.recordbook.RecordbookBarsVisualTest
```

Unit tests cover the Retrofit paths through an in-memory interceptor, nullable
fields, HTTP-200 errors, the shared sport formula, period matching and ambiguity,
the BARS merge, chip persistence, silent login and the 401 retry, the grade
scale and next-step hints (`RecordbookGradeScaleTest`), control groups
(`RecordbookControlGroupsTest`), the sport shortfall (`RecordbookSportBehindTest`),
attention reasons and the session-only summary (`RecordbookViewModelTest`), the
subject context resolver, the binding store and every subject-page state of
`RecordbookSubjectViewModel`, including links, chats and `Все пары`. Visual tests
run the real Fragments in `RecordbookPreviewActivity` with synthetic data
(`RecordbookPreviewFixtures`): compact rows, `Требуют внимания` with PE, the
summary only in the session, the one-page subject with its hint, chips and
`Ещё N`, chats, expanded groups, two lessons and `Все пары`, a past period with
links and PE without chips. Add
`-Pandroid.testInstrumentationRunnerArguments.appearanceMatrix=full` and
`-Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true` for all
four appearances and the PNGs (see
[Running the visual tests](../design.md#running-the-visual-tests)).
