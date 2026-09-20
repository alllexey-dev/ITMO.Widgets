# Recordbook

`feature/recordbook` shows official grades from MyITMO, optionally overlaid with
BARS, and links physical education to the sport score. Backend and Core are not
involved; the feature works without the custom-services opt-in.

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
- Trees are stored and rendered but parents and children are never summed and no
  groups are invented from words. `have_tree = false` is normal.
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
4. PE rings show the matched period's sport progress, blue, even while `rate` is
   `null`; a total above 100 stays visible and fills the arc to 100 %. The grade
   marker and passed-subject count come only from the official `rate`.
5. Debug scores apply to the current sport period only, in both screens, and are
   disabled in release.
6. A sport error never hides the official recordbook; retry is available by pull
   or from the PE details.

## Screens

The root is a period selector, a compact summary card and the subject list;
fewer than fifteen subjects, so no search or filters. Subject cards show a ring
on a 100 scale with the official code or credit icon inside; the ring accent is
green for passed, error colour for failed, primary for pending; without a score
the ring is empty. Subject details reload the official subject, keep control
hierarchy with quiet cards and progress relative to each control's maximum, and
show a differing teacher or date per control. Rings are Material
`CircularProgressIndicator`s that jump to the final value on rebind.

Subjects open with `program_id`, `semester`, `study_year` and `entry_id`; the
subject's own `discipline_id` comes with the reloaded official subject.

## Subject hub

The subject screen has two tabs: `Баллы` (overview and controls, the screen as
before) and `Расписание` with three sections (teachers, lessons, resources), all
built in the
recordbook feature (Konsist forbids cross-feature imports, so the schedule side
is reached through `core/schedule/SubjectLessonsGateway`, implemented in
`feature/schedule/data`):

- `Преподаватели` comes first: distinct people from the subject's lessons in
  schedule order, each with the lesson types they run; without lessons the
  recordbook teacher stands in.
- `Ближайшие пары`: the viewer's academic lessons of this subject within
  today … +28 days. The view model refreshes that window through
  `ScheduleRefreshGateway` (a failed refresh with an empty cache is an error
  with `Повторить`; with cached lessons the cache is shown) and observes the
  gateway. The section exists only for the current period and never for
  physical education.
- The link between a recordbook discipline and a schedule subject is
  `SubjectContextResolver`: an exact `discipline_id == subject_id` binds
  silently (on live data this holds for every discipline except PE); a
  confirmed binding from `SubjectBindingStore` (DataStore, cleared on sign-out)
  wins over it; otherwise a single normalised-name match is *proposed* in an
  outlined card (`Связать` / `Нет`, nothing is stored on `Нет`), several
  matches ask which one, none reads as not found. `subjectNameKey` is the one
  normalisation, shared with the BARS merge.
- `Ресурсы`: only the MyITMO `lms_link`, so usually absent.

The schedule tab appears only when the hub has something to show, the chosen
tab survives recreation, and pull-to-refresh reloads both tabs at once.
Lesson rows are informational: the details sheet belongs to the schedule
feature, and teacher profiles wait for Stage 29. Review or resource tabs do not
exist yet.

## BARS overlay

The header has a `БАРС` filter chip (off by default, DataStore, cleared on
sign-out). When on, subjects found in BARS for the same academic year and term
show the BARS score, grade, attempt and control tree; periods, identities,
teachers, PE and sport stay MyITMO.

- Matching is by normalised title (case, spaces, ё/е) inside one period.
  Unmatched subjects keep MyITMO values with a `нет в БАРС` note; PE gets no note;
  duplicate titles on either side are not matched. An empty BARS journal
  (`total = 0`, no marks) shows as "no marks", not 0.
- The MyITMO list appears immediately; the refresh indicator stays until BARS
  answers, journals are requested in parallel, and the note appears only after a
  successful BARS response for that period.
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
is the session cleaner for both the token and the chip.

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
the BARS merge, chip persistence, silent login and the 401 retry, the subject
context resolver, the binding store and every hub state of the view model. Visual tests
run the real Fragments in `RecordbookPreviewActivity` with synthetic data; add
`-Pandroid.testInstrumentationRunnerArguments.appearanceMatrix=full` and
`-Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true` for all
four appearances and the PNGs (see
[Running the visual tests](../design.md#running-the-visual-tests)).
