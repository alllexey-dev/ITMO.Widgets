# Recordbook

`feature/recordbook` shows official grades from MyITMO, optionally overlaid with
BARS, links physical education to the sport score and notices new marks in the
background ([mark tracking](marks-tracking.md)). Grades never reach Backend and the
feature works without `Подключение к ITMO.Widgets`; only the subject page's
links go through `core/resources` (see [resources](resources.md)).
The [subject page](subject-page.md), [mark tracking](marks-tracking.md) and
[sheet scores](sheet-scores.md) have docs of their own.

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
  normal. Grouping for display is described under [Control groups](subject-page.md#control-groups).
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
- A subject row (`SubjectRow` in `ui/RecordbookScreen.kt`) is the name (two lines), a
  metadata line (assessment kind, an uncoded result such as a no-show reason,
  `нет в БАРС`, a PE sport state) and one result on the right. A final result
  (a grade, a credit, a no-show, or no points at all) is a badge: the compact
  code (`4C`, `2FX`), `Зачёт` or `—`, green when passed, error colour when
  failed, neutral otherwise. Until then the row shows the points and a 64 dp
  bar of their share of 100: error colour with an attention reason, the sport
  colour for PE, otherwise the status colour. Bars draw their value without
  animation.
- A subject with unread new marks has an 8 dp `primary` dot
  (`RecordbookTestTags.NEW_MARK`) after its name until its page is opened
  ([mark tracking](marks-tracking.md#in-the-recordbook)).
- A subject without My ITMO or BARS points, final grade or no-show shows the
  total of its connected sheet instead of `—`: `ic_table` and the value, no bar
  ([sheet scores](sheet-scores.md#in-the-recordbook-list)).

Subjects open with `program_id`, `semester`, `study_year` and `entry_id`; the
subject's own `discipline_id` comes with the reloaded official subject.

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
- A BARS failure keeps the MyITMO list or subject page and shows a snackbar
  (`RecordbookErrorSnackbars`, shared by both screens); when the ITMO.ID session
  has ended the snackbar offers `Войти в БАРС` (`BarsLoginActivity`, opened by
  the host). Nothing is silently substituted in either direction.
- Changing the period while the chip is on changes the saved period in web BARS
  as well: it is a server-side setting with no stateless read.

Session: the BARS token lives 30 minutes, has no refresh and cannot be exchanged
from a MyITMO token. BARS goes through MyItmoApi 2.x: the library's
`dev.alllexey.itmoapi.bars.BarsClient` (built by the app's `BarsClient` over
`OwnerBoundBarsStorage` and `BarsRenewal`) and `BarsLogin` for the ITMO.ID side
(`loginUrl`, `isCallback`, `isAllowedPage`, `extractCode`,
`requestCodeWithCookies`). Both run on a Ktor OkHttp engine of their own
(`@BarsHttp` in `di/RecordbookModule.kt`: connect 20 s, read 30 s, no cookie jar,
cache or redirects), never MyITMO's.

The MyITMO and BARS data (`RecordbookRepositoryImpl`, `BarsClient`,
`BarsRenewal`, `BarsTokenStore`, `OwnerBoundBarsStorage`,
`BarsCookieSilentLogin`, `BarsMarkReader`, the BARS, session and switch
repositories, `DemoRecordbook`) lives in `commonMain` of
`:shared:feature-recordbook` and is constructed by Koin (`recordbookModule`),
one instance of each per process. The platform supplies the engine,
`BarsLogin`, the WebView ports `ItmoIdCookies` (`WebViewItmoIdCookies`) and
`BarsSilentLogin` (`BarsWebSilentLogin`) and `BarsSessionListener`; on Android
they are Hilt's and reach Koin through `di/bridge/RecordbookBridge.kt`, which
also hands Koin's `BarsClient`, `RecordbookRepository`,
`BarsPreferenceRepository` and `BarsMarkSource` to the Hilt-built mark
tracking. The three cleaners of that data join sign-out's set through
`SessionCleanersBridge`. The ITMO.ID session in the app's WebView lives
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
(`<isu>\n<header>`, checked with `BarsClient.isValidAuthorization`) goes
through `SecureStore` under that name: on Android the `noBackupFilesDir` file
sealed by the Keystore `TokenCipher`, byte for byte the one 2.2 wrote. The library's locks are not reentrant, so a block never nests
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
  shows ([mark tracking](marks-tracking.md#bars-in-the-background)).

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

## Verification

```bash
scripts/verify.sh quick
scripts/verify.sh shots feature-recordbook -Pshots.appearance=full
ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui MarksWorkTest,MarksNotificationTest
```

The screens are Compose Multiplatform in `:shared:feature-recordbook`, checked
on the JVM with host tests and Roborazzi goldens in all four appearances
([ADR 0022](../decisions/0022-jvm-screenshot-tests.md)). The tests of the
[subject page](subject-page.md#tests), [mark tracking](marks-tracking.md#tests)
and [sheet scores](sheet-scores.md#tests) are listed there; the list and the
period sheet are `RecordbookScreenTest`, `RecordbookPeriodSheetTest`,
`RecordbookViewModelTest` (attention reasons, the session-only summary) and the
`RecordbookScreen_*` goldens, with the samples in `ui/preview/RecordbookPreviewSamples`.
The debug `RecordbookPreviewActivity` with `RecordbookPreviewFixtures` remains only
for the site screenshots (`SiteScreenshotCapture`) and the Koin host checks
(`RecordbookKoinHostsTest`); it uses in-memory repositories, its own link fixture
`RecordbookPreviewLinks` included, and never reads a session.

## iOS

The iOS app ([iOS app](../ios.md)) hosts the same `RecordbookRoute` as the
first tab's root, in Android's tab order, since `PlatformCapabilities.recordbook`
is on there: `RecordbookTabScreen` (`iosApp/Sources/Features/Recordbook/`)
through `recordbookRootPage` (`shared/ios`, `screens/RecordbookScreens.kt`), with
`recordbookModule` and `recordbookIosModule` in `IosKoinModules`. The tab's
ViewModel lives in the Compose controller's store, so the period and the list
stay while the tab's root stays alive.

- Period picker: `AppRoutes.RecordbookPeriod` is a Compose sheet above the shell
  (`RecordbookPeriodSheetView`, `recordbookPeriodViewController`), half height
  first with the system drag indicator; a pick answers the tab root through the
  router (`open(_:onResult:)`, `deliver(_:from:)`) and the root's
  `RecordbookPage` hands it to the ViewModel, as Android's `ResultEffect` does.
- BARS: the snackbar's `Войти в БАРС` opens IO-09d1's `BarsLoginSheet`;
  after a completed sign-in the list loads again (`RecordbookPage.barsSignedIn`).
  In the demo the chip's overlay says `Недоступно в демо`, as on Android.
- A subject opens [its page](subject-page.md#ios) only with valid arguments.
- Demo: `DemoRecordbook` answers the list, the subject pages and the demo sheet
  total with no request.
- Mark tracking is on since IO-09d3 (`PlatformCapabilities.marks`): the
  new-mark dots, the marks card and the settings page
  ([mark tracking](marks-tracking.md#ios)). A tap on a marks notification opens
  the app without a route for now ([degradations](../ios.md#degradations)).
- Tests: `RecordbookIosModuleTest` (`scripts/ios/test.sh kn
  :shared:feature-recordbook`: every definition of the screens resolves on the
  demo session, the demo recordbook answers, the subject links stand-in asks
  nothing, all with no request) and `RecordbookUITests` on the demo session (the
  list and an earlier period through the picker, the picker's close and drag,
  the BARS demo refusal, the subject page and `Мои баллы`, the list at AX1 to
  its last subject).
