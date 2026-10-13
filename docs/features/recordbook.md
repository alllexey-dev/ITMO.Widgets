# Recordbook

Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

`feature/recordbook` (`:shared:feature-recordbook`, Compose Multiplatform) shows official grades from
MyITMO, optionally overlaid with BARS, and links PE to the sport score. Grades never reach Backend and
the feature works without `Подключение к ITMO.Widgets`; only the subject page's links go through
`core/resources` ([resources](resources.md)). Related docs: [subject page](subject-page.md),
[mark tracking](marks-tracking.md), [sheet scores](sheet-scores.md).

## MyITMO sources

| Data | Endpoint | Identity |
|---|---|---|
| Programmes and periods | `/api/record_book/specializations` | `main_plan` selects the programme; `semester` is through-numbered (1/2 first year, 3/4 second) |
| Recordbook entries | `/api/record_book/{main_plan}/{semester}` | `est_id` is one entry, `discipline_id` the curriculum discipline |
| Control events | `/api/record_book/{est_id}` | only when `have_tree = true`; server order and `parent_id` kept |
| Sport semesters | `/api/sport/semesters/list`, `/current` | own `id` and labels like `Осень YYYY/YYYY`, not the recordbook semester |
| Sport score | `/api/sport/personal/score?semester_id={id}` | `sum.attendances`, `sum.other` |

Rules the mapper relies on:

- The catalog contains future semesters; an empty result or unset grade is not an error. The initial
  period follows `AcademicTimeProvider`; an explicit choice is restored separately.
- `current_score` and `rate` are independent (PE has a credit with `current_score = null`). Never
  render null as zero or derive a grade from sport.
- `rate` may be a fractional code (`4/C`), a credit word or `null`; the UI renders `4C`, `2FX`.
  `Незачёт` is not a pass; unknown values are not counted as passed.
- For a control event `rate` is the obtained score, `min_value`/`max_value` the bounds; `lower_value`
  is not a result.
- A tree parent carries its own total and is never added to its children
  ([control groups](subject-page.md#control-groups)).
- An empty `teacher` object maps to `null`; `attendances` history may be `null` while `sum` exists.
- HTTP 200 is not success: `error_code` is checked, and an API error never becomes an empty
  recordbook or a zero sport score.
- Library limits: `attempt` is a primitive (not shown), `sem_id` is absent, `lms_link` always empty.

## Physical education and sport

- Only `Физическая культура и спорт (базовая)` and `(элективная)` are recognised after
  normalisation; a new title is not guessed.
- Odd recordbook semesters map to autumn, even to spring; year and season label must match and only a
  unique match is accepted. Ambiguity or an unknown label is its own state.
- `core/sport/SportScoreRepository` returns the summary (same implementation as `Мой спорт`).
- The grade badge and the passed count come only from the official `rate`, never from sport.
- `isSportBehind`: points below 100 and the sport period is over or ends within 28 days. Only
  `/api/sport/semesters/current` carries `date_end`; when it fails no alarm is raised.
- Debug score overrides apply to the current sport period only and are disabled in release.
- A sport error never hides the official recordbook.

## Recordbook list

Зачётка is one shared view model, `RecordbookViewModel` (Koin, `RecordbookKoinModule.kt`), drawn by Compose on
Android (`RecordbookRoute`, `ui/RecordbookScreen.kt`) and SwiftUI on iOS. It loads; `RecordbookPresenter`
(`presentation/RecordbookPresenter.kt`, pure) puts the subjects into `RecordbookUiState`: the header's subtitle
(`2 курс, 2025/2026`) and period menu (`3 семестр`, every period of every programme, newest first), and the body.

- One list: `Требуют внимания`, then `Дисциплины`. No `Семестр` / `Сессия` switch.
- Session: while `RecordbookSessionWindow` (`domain/RecordbookSessionWindow.kt`) is open, the hero `Сдано N из M`
  with the average of the passed graded subjects stands over the list. My ITMO has no session dates, so it is open
  for a period of an earlier half; for the current one from its last weeks by the calendar (1 December in autumn,
  15 May in spring) or once an exam is at most four weeks ahead; never for a later one. Upcoming exams carry their
  date in the row, final results their pill, in any period.
- `Требуют внимания` rule: `attentionReason` in `presentation/RecordbookAttention.kt`. It uses only controls an
  earlier answer brought (`RecordbookRepository.cachedControls` or `BarsRecordbookRepository.cachedControls`); the
  list never requests controls itself. A passed subject never needs attention. The reason replaces the kind, in red
  (or orange for sport points).
- A row's number and its quiet source: [which number is shown](#which-number-is-shown). Points sit over a 0-100 bar
  in the tone of the row's reason; a final result is a pill (`4C`, `Зачёт`, `2FX`, `Неявка`); a missing number is a
  dash.
- Физкультура shows the sport total in a small ring (visits and bonus) and opens the Спорт tab; its official result
  still comes only from `rate`.
- A tap on a source opens `Откуда баллы` (`RecordbookSources`): BARS, My ITMO and the teacher's sheet side by side,
  the shown one first and marked, why that one, and `Открыть предмет`.
- New-mark dot: `RecordbookTestTags.NEW_MARK` ([mark tracking](marks-tracking.md#in-the-recordbook)).
- A subject opens `AppRoutes.RecordbookSubject` only with valid arguments (`program_id`, `semester`, `study_year`,
  `entry_id`).

## Which number is shown

One rule, `RecordbookPointsRule` (`presentation/RecordbookPoints.kt`), for the list, `Откуда баллы` and the
subject page's hero (decision [0032](../decisions/0032-score-source-priority.md)). The most current source with a
value wins:

1. a connected sheet ([sheet scores](sheet-scores.md)) whose last reading succeeded and whose cell is not empty;
2. BARS, when the subject's journal has a score (an untouched journal has none);
3. the last value of a sheet whose last reading failed (`Нет связи`, closed, row or column not found);
4. My ITMO's score.

A source without a value never hides one with a value. With no value anywhere the dash is labelled by the first
source present in the same order: a connected sheet, a BARS journal, else My ITMO. The sheet counts only while the
subject is in progress; a final result or a no-show takes BARS's or My ITMO's number. `Откуда баллы` says why the
shown one won, or that no source has points yet. Tests: `RecordbookPointsRuleTest`.

## BARS

BARS is optional. `BarsPreferenceRepository` says whether it is connected in the recordbook (key `recordbook_bars`,
off by default, cleared on sign-out) and whether its offer was hidden (`recordbook_bars_offer_hidden`). BARS
overlays score, grade, attempt and control tree; periods, identities, teachers, PE and sport stay My ITMO.

- Not connected: the list is complete from My ITMO and one quiet line under it, `Контрольные и минимумы из БАРС`,
  offers `Подключить` (the BARS sign-in) and a cross that hides it. A completed sign-in connects BARS and loads again.
- Connected: journals are requested in parallel with My ITMO; My ITMO is on screen at once.
- An ended session (`Unauthorized`): one row over the list, `БАРС: сессия истекла`, with `Войти`; the earlier BARS
  answer of the period stays, its sources dimmed with its time or date. Another failure: `БАРС: не удалось
  загрузить` with `Повторить`, My ITMO values. Nothing is silently substituted.
- Pure merge `RecordbookBarsMerge.apply(myItmo, bars)` by `subjectNameKey` inside one period; duplicate titles on
  either side are not matched. An empty journal (`total = 0`, no marks) is "no marks", not 0.
- Changing the period with BARS connected changes the saved period in web BARS too (server-side setting, no
  stateless read).
- Mapper (`BarsRecordbookMapper`): server `marks.total`; the last unambiguous active approval
  (`Approval.gradeCode`, `Удвл., E` -> `3/E`); works by `checkpoint_id`; extra points as a separate
  row; absence is not a pass; plans with `has_course_project` are rejected.

Session:

- The BARS token lives 30 minutes, has no refresh and cannot be exchanged from a MyITMO token.
- MyItmoApi 2.x: `dev.alllexey.itmoapi.bars.BarsClient` (wrapped by the app's `BarsClient` over
  `OwnerBoundBarsStorage` and `BarsRenewal`) and `BarsLogin` for ITMO.ID. Own Ktor OkHttp engine
  (`barsEngineQualifier` in `di/RecordbookModule.kt`: connect 20 s, read 30 s, no cookie jar, cache or
  redirects), never MyITMO's.
- Data classes live in `:shared:feature-recordbook` `commonMain`, one instance each (Koin
  `recordbookModule`); the platform supplies the engine, `BarsLogin`, `ItmoIdCookies` and
  `BarsSilentLogin`. Six named `SessionDataCleaner`s in `RecordbookKoinModule.kt` join sign-out,
  among them `RecordbookRepositoryImpl` (memory cache), `BarsPreferenceRepositoryImpl` (token,
  connection and offer) and `BarsRecordbookRepositoryImpl` (in-memory `cachedControls`).
- The interactive sign-in (`BarsLoginActivity`, iOS `BarsLoginSheet`) opens the same OIDC URL in the web store
  the ITMO.ID sign-in used (`CookieManager`, `WKWebsiteDataStore.default()`), so the SSO session that sign-in left
  there redirects straight to the callback, with no second password (decision
  [0012](../decisions/0012-bars-background-renewal.md#the-sign-in-keeps-the-itmoid-session)).
- Screens renew via `BarsWebSilentLogin`: the official OIDC URL in a hidden WebView, intercepting
  only the exact callback with a checked `state`; the library retries once on 401 through
  `BarsRenewal`. No JavaScript bridge, no localStorage reads.
- The app's `BarsClient` binds the session to the current ISU, verifies `login`, serialises period
  selection and maps `MyItmoException`: `Auth` 401 -> `Unauthorized`, `Http` 403 and 423 ->
  `Forbidden`, `Http` 404 -> `NotFound`, `Network` -> `Network`, else retriable `Unknown`.
- `OwnerBoundBarsStorage` is the only writer of `bars_tokens.enc` (`<isu>\n<header>`, validated with
  `BarsClient.isValidAuthorization`) via `SecureStore` (Android: `noBackupFilesDir`, Keystore
  `TokenCipher`, byte-compatible with 2.2). The library's locks are not reentrant: never nest period
  changes in a block.

Background renewal (`BarsClient.backgroundAccount`, decision
[0012](../decisions/0012-bars-background-renewal.md)):

- `BarsCookieSilentLogin` takes the `Cookie` header the WebView would send (`ItmoIdCookies`,
  `CookieManager` on the main thread) and calls `BarsLogin.requestCodeWithCookies` (`CODE`,
  `LOGIN_REQUIRED`, `REJECTED`, `HTTP_ERROR`): one HTTPS request, no redirects, cookie jar or cache.
  `Set-Cookie` goes back to `CookieManager` for that URL only.
- No cookies or `LOGIN_REQUIRED` ends the session; `REJECTED`/HTTP error is `Unknown`, a network
  failure `Network`; neither ends the session and the saved header stays.
- For one `backgroundAccount` block `BarsRenewal` uses the cookie renewal, then the WebView again; the
  client's mutex ties the mode to the block. Without a saved session for the current ISU nothing is
  requested (`NoSession`).
- Codes, cookies and tokens never reach the log, exceptions or `toString()`.
- Every successful `account`, `login` or `backgroundAccount` is reported to `BarsSessionListener`
  (`BarsMarksActivation`) after the lock is released.
- The background read restores the user's server-side period
  ([mark tracking](marks-tracking.md#bars-in-the-background)).

## Verification

```bash
./gradlew verifyQuick
scripts/verify.sh shots feature-recordbook -Pshots.appearance=full
scripts/ios/test.sh --only SnapshotTests/RecordbookSnapshotTests
ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui MarksWorkTest,MarksNotificationTest
```

View model tests (`RecordbookViewModelTest`, `RecordbookBarsTest`, `RecordbookSubjectViewModelTest`), the pure rules
(`RecordbookPointsRuleTest`, `RecordbookSessionWindowTest`) and Roborazzi
references (`RecordbookScreen_*`, `RecordbookSourcesSheetContent_*`, `RecordbookSubjectScreen_*`) in
`:shared:feature-recordbook` ([ADR 0022](../decisions/0022-jvm-screenshot-tests.md)), on the states `RecordbookSamples`
builds with the real presenters; `RecordbookEntriesTest` in `:app`; the iOS references in
`iosApp/Tests/SnapshotTests/Features/RecordbookSnapshotTests.swift` on the same samples.

## iOS

`RecordbookScreen` (`iosApp/Sources/Features/Recordbook/`) is the tab's root over `RecordbookViewModel`
(`ObservableViewModel`), with the native root header (`.itmoRootHeader`) and the period `Menu` in the toolbar.

- `Подключить`, `Войти` and the BARS reminder's tap open `BarsLoginSheet`; a completed sign-in calls
  `barsSignedIn()`.
- `Откуда баллы` is `RecordbookSourcesSheet`, a content-height `ItmoSheet`.
- A subject opens [its page](subject-page.md#ios) only with valid arguments; Физкультура selects the sport tab.
- Demo: `DemoRecordbook` answers with no request; mark tracking: [iOS](marks-tracking.md#ios).
- Tests: `RecordbookSnapshotTests`, `RecordbookIosModuleTest`, `RecordbookUITests`.
