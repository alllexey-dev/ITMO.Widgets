# Sheet scores

Part of the [recordbook](recordbook.md).
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

The own total of a subject from a teacher's public Google Sheet. The connection, the downloaded sheet
and other people's names stay on the device: no Backend, no `Подключение к ITMO.Widgets` (decision
[0014](../decisions/0014-sheet-scores-on-device.md)). Code: `feature/recordbook` under
`domain/sheets`, `data/sheets`, `presentation/sheets`, `ui/sheets`; `feature/resources` only offers
the action.

## Connecting

- A link is a sheet when `core/resources/GoogleSheetUrl` accepts it
  (`https://docs.google.com/spreadsheets/d/<id>` or `.../u/<n>/d/<id>`, `gid` from the fragment, then
  the parameter; published `/d/e/...` addresses are not sheets).
- `Мои баллы` opens `AppRoutes.SheetScores(SheetScoresArgs)` with the link's `ResourceScope`
  (`discipline_id` and period), registered in `RecordbookEntries.kt` with `SheetScoresViewModel` and
  `SheetScoresSheet`. One connection per scope; a new one replaces the old after a successful choice.
- The workbook lives only in the view model, never in saved state; after process death it is
  downloaded again. A failed write keeps the choice.
- Name search appears for more than 8 student rows. Student rows start at the topmost name or ISU
  above the row in its column; empty cells and up to two other texts are skipped, a people title
  such as `ФИО` ends them. A name is 2-8 words.

## Downloading

`PublicSheetClient` (`commonMain`, one Koin single) owns a Ktor client over the platform engine
(OkHttp / `darwinHttpEngine()`), without cookie store or cache:

- No `HttpCookies`; `HttpRedirect` never goes from HTTPS to HTTP; `HttpTimeout` 15 s connect, 30 s
  between bytes, 90 s per request.
- Requests only to `https://docs.google.com/`; nothing in the demo session. Bodies are streamed up to
  the limit. Addresses and bodies never reach the log or an exception.
- A tab: CSV `GET /spreadsheets/d/<id>/export?format=csv&gid=<gid>`. Not `text/csv`, 401 or 403 falls
  back to `GET /spreadsheets/d/<id>/htmlview/sheet?headers=false&gid=<gid>` (`SheetHtmlGrid`, Ksoup,
  `table.waffle`, `colspan`/`rowspan` spread).
- Tabs: `GET /spreadsheets/d/<id>/htmlview`, the `items.push({name, pageUrl, gid})` lines
  (`SheetTabsParser`). The link's tab first; at most 50 tabs, 4 at a time. `gviz/tq` and `pubhtml`
  are not used.
- Closed: a sign-in page (`accounts.google.com`, `/ServiceLogin...`, `/v3/signin...`) at any step,
  401/403 of the HTML tab, 404 of the tab list. 400/404 of a tab: the column is not found. No
  connection, 5xx and 429: `Нет связи`, the stored value stays.
- Limit 5 MiB per answer (`Content-Length` or counted). Such a tab is skipped while connecting; the
  sheet is too large only when every tab is.
- `CsvGrid`: RFC 4180, BOM, CRLF/LF, line breaks in quotes, ragged rows padded.

## Own row, header and total

- Own row: a cell equal to the ISU (`CurrentUser.isu`, spaces ignored) in any tab, else a cell equal to
  a form of the ITMO.ID name after `SheetText` normalisation (case, `ё`, spaces, the space after a
  dot): full, surname + name, `Фамилия И.О.`, `Фамилия И.`, surname first and last (ITMO.ID word
  order is unknown).
- The stored key is the ISU or the normalised name, never a row number; every reading finds the row
  again (`SheetRows.locate`: key column first, then any column, exactly one row).
- Header (`SheetHeaders`): up to 6 rows above the first student row. A row whose only text is at or
  left of the key column is a title and skipped. Group titles span to the next title; the last
  header row does not span. Path segments join with ` · `; a column without one is
  `Столбец <буква>`.
- Total (`SheetTotals.detect`): keyword groups by priority, matched by word start: `итог`, `σ`/`∑`,
  `сумма`/`сум`/`sum`, `total`, `score`, `bars credits`/`барс`, `оценка`, `зачет`. Ties: a one-word
  segment starting with the keyword, a filled value, an earlier tab, a column further right.
- The column is stored as `gid` + header path; a reading finds the same path (nearest to the old index
  when repeated), falls back to the old index while the tab is that wide, else the column is not
  found. Values are shown as the sheet shows them, never recomputed.

## On the subject page

`SubjectSheetTotal` in `SubjectHero`'s sheet slot shows the stored value at once and downloads the
tab on entry and on every pull; PE has neither the total nor the offer.

## In the recordbook list

`RecordbookViewModel` only collects `SheetScoresRepository.observe()`, never downloads, and passes
`Content.sheetTotals` by `discipline_id`. `sheetFallback` keeps a total only for a non-PE subject
without a no-show, a final grade and My ITMO or BARS points (`RecordbookTestTags.SHEET_MARK`).

## Background check of sheets

- `MarksCheck` runs `MarkTrackingRepository.checkSheets()` after My ITMO and BARS while
  `Оценки из таблиц` (`sheet_marks_enabled`, on by default, kept on sign-out) is on. It reads every
  connection of the current half-year (`StudyHalf.of(today).periodKey`), one at a time.
- A connection keeps `value` (last read, shown) and `baseline` (last non-empty). Only a background
  read of a `tracked` connection with a new non-empty value different from the baseline is news:
  `MARK_ADDED` from an empty baseline, else `MARK_CHANGED` (`MarkSource.SHEETS`). An emptied cell is
  no news and keeps the baseline.
- Any successful read (connecting, opening the page, a pull) moves the baseline and tracks the
  connection, so a total seen in the app is not notified later. A failed read changes only the
  status; `Нет связи` is a run error (retry), other statuses are not.
- Switching `Оценки из таблиц` off keeps the totals and untracks every connection
  (`resetSource(SHEETS)` -> `untrack()`), so the first read after switching on is a baseline. A check
  started before the reset or a session clear writes nothing (`Stale`).

## Storage

- `SheetScoresFileStore`: `filesDir/sheet_scores/state.json`, format 1, `owner` is the ISU, written
  through `AtomicTextFile`, JSON shape readable by 2.2; excluded from backup and device transfer. A
  corrupt file, another format, an invalid field or another account's file is deleted.
- `SheetScoresRepositoryImpl` is one Koin single and a `SessionDataCleaner`: sign-out deletes the
  file; a reading is written only when the session generation and the whole connection it was taken
  for are unchanged.

## Tests

`shared/feature-recordbook/src/androidHostTest` with synthetic sheets in `resources/sheets/`; no real
sheet is opened, names and ISUs are made up.

## iOS

`Мои баллы` is the shared `SheetScoresSheet` in a SwiftUI sheet (`SheetScoresSheetView`,
`sheetScoresViewController` in `shared/ios`, `SheetScoresIosRoute`); the route builds the
`SavedStateHandle`. A failed save shows a banner over the sheet. The client runs on
`darwinHttpEngine()` with the same policy; storage is the same `filesDir/sheet_scores`. Links and
the offer come from the subject's links ([subject page](subject-page.md#ios),
[subject links](resources.md#ios)).
