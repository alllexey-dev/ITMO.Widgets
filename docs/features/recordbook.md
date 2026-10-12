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

- Tab root `RecordbookTabRoot` (`RecordbookEntries.kt`): `RecordbookRoute` over `RecordbookViewModel`
  (Koin, `RecordbookKoinModule.kt`). The period sheet is `AppRoutes.RecordbookPeriod` (result via
  `ResultEffect`); a subject opens `AppRoutes.RecordbookSubject` only with valid arguments
  (`program_id`, `semester`, `study_year`, `entry_id`).
- `Требуют внимания` rule: `attentionReason` in `presentation/RecordbookAttention.kt`. It uses only
  controls an earlier answer brought (`RecordbookRepository.cachedControls` or
  `BarsRecordbookRepository.cachedControls`); the list never requests controls itself. A passed
  subject never needs attention.
- Row: `SubjectRow` in `ui/RecordbookScreen.kt`. New-mark dot `RecordbookTestTags.NEW_MARK`
  ([mark tracking](marks-tracking.md#in-the-recordbook)); sheet fallback
  ([sheet scores](sheet-scores.md#in-the-recordbook-list)).

## BARS overlay

The `БАРС` chip (off by default, DataStore, cleared on sign-out) overlays BARS score, grade, attempt
and control tree; periods, identities, teachers, PE and sport stay MyITMO.

- Pure merge `RecordbookBarsMerge.apply(myItmo, bars)` by `subjectNameKey` inside one period;
  duplicate titles on either side are not matched; unmatched keeps MyITMO with `нет в БАРС` (not for
  PE). An empty journal (`total = 0`, no marks) is "no marks", not 0.
- Journals are requested in parallel; the note appears only after a successful BARS response.
- A BARS failure keeps MyITMO data and shows a snackbar (`RecordbookErrorSnackbars`); an ended ITMO.ID
  session offers `Войти в БАРС` (`BarsLoginActivity`). Nothing is silently substituted.
- Changing the period with the chip on changes the saved period in web BARS too (server-side setting,
  no stateless read).
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
  among them `RecordbookRepositoryImpl` (memory cache), `BarsPreferenceRepositoryImpl` (token and
  chip) and `BarsRecordbookRepositoryImpl` (in-memory `cachedControls`).
- Screens renew via `BarsWebSilentLogin`: the official OIDC URL in a hidden WebView, intercepting
  only the exact callback with a checked `state`; the library retries once on 401 through
  `BarsRenewal`. No JavaScript bridge, no localStorage reads.
- The app's `BarsClient` binds the session to the current ISU, verifies `login`, serialises period
  selection and maps `MyItmoException`: `Auth` 401 -> `Unauthorized`, `Auth` 403 and `Http` 423 ->
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
ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui MarksWorkTest,MarksNotificationTest
```

Host tests and Roborazzi goldens in `:shared:feature-recordbook`
([ADR 0022](../decisions/0022-jvm-screenshot-tests.md)); `RecordbookEntriesTest` in `:app`.

## iOS

Same `RecordbookRoute` as the first tab's root (`PlatformCapabilities.recordbook`):
`RecordbookTabScreen` (`iosApp/Sources/Features/Recordbook/`) via `recordbookRootPage`
(`shared/ios`, `screens/RecordbookScreens.kt`), modules `recordbookModule` + `recordbookIosModule`.

- Period picker: Compose sheet `RecordbookPeriodSheetView`; the pick returns through the router
  (`open(_:onResult:)`, `deliver(_:from:)`) instead of `ResultEffect`.
- `Войти в БАРС` opens `BarsLoginSheet`; the list reloads after sign-in
  (`RecordbookPage.barsSignedIn`).
- A subject opens [its page](subject-page.md#ios) only with valid arguments.
- Demo: `DemoRecordbook` answers with no request; mark tracking: [iOS](marks-tracking.md#ios).
- Tests: `RecordbookIosModuleTest`, `RecordbookUITests`.
