# Sheet scores

Part of the [recordbook](recordbook.md).
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

The own total of a subject from a teacher's public Google Sheet. The connection, the downloaded sheet
and other people's names stay on the device: no Backend, no connection to ITMO.Widgets needed
(decision [0014](../decisions/0014-sheet-scores-on-device.md)). `feature/resources` only offers the
action.

## Connecting

- A link is a sheet when `core/resources/GoogleSheetUrl` accepts it
  (`https://docs.google.com/spreadsheets/d/<id>` or `.../u/<n>/d/<id>`, `gid` from the fragment, then
  the parameter; published `/d/e/...` addresses are not sheets).
- `Мои баллы` opens `AppRoutes.SheetScores(SheetScoresArgs)` with the link's `ResourceScope`
  (`discipline_id` and period). One connection per scope; a new one replaces the old after a successful choice.
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
  dot): full, surname + name, `Фамилия И.О.`, `Фамилия И.`. The forms of the token's `family_name`,
  `given_name` and `middle_name` come first (a patronymic of several words included, and `Имя Фамилия`),
  then those of `name` with the surname first and last (its two words come in an unknown order).
- A person cell is an ISU or 2-8 words; titles such as `ФИО`, summary rows (`Итого по группе`,
  `Средний балл`) and cells of initials only (`Ф.И.О.`) are not people.
- The stored key is the ISU or the normalised name, never a row number; every reading finds the row
  again (`SheetRows.locate`: key column first, then any column, exactly one row).
- Header (`SheetHeaders`): up to 6 rows above the first student row. A row whose only text is at or
  left of the key column is a title and skipped. Group titles span to the next title; the last
  header row does not span. Path segments join with ` · `; a column without one is
  `Столбец <буква>`.
- Total (`SheetTotals.decide`, over every tab holding the own row): the candidates are the cells under
  a keyword header, matched by word start: `итог`/`всего`, `σ`/`∑`, `сумма`/`сум`/`sum`, `total` name
  a total; `score`, `bars credits`/`барс` are weaker; `оценка`, `зачет` weakest. Candidates are ordered
  by: a column of points (not text, percentages or `#REF!`-style errors); a tab not named as a copy,
  template or archive (`Copy of`, `Копия`, `Шаблон`, `Архив`); not a column empty or zero for every
  student while another total of the tab has points (a semester not begun); the header's strength; a
  filled cell; the tab where the own row has more values; a segment that is the keyword itself
  (`ИТОГО`); the keyword's order; an earlier tab; a column further right.
- The decision carries a reason (`TotalReason`) and its confidence. Sure: the total is the sum of a run
  of at least two columns on one side of it in four of five student rows with points (at least 3),
  else just the strongest header. To check (`Проверьте итог`): the row is in several tabs whose
  totals differ, the column is empty for everyone while the row has points, only a weak header, or
  the column holds no points. Without any keyword header the viewer picks the cell.
- The choice is stored with the connection (`TotalChoice`): `AUTO`, `AUTO_CHECK` (found, but the
  subject page asks to check it) or `MANUAL`. Picking the automatic total again confirms it (`AUTO`);
  `Вернуть авто` replaces a hand-picked one with the automatic one.
- Issue 419 (АК): some students were also in `Copy of Общий список` of an earlier run under `Итого`,
  which beat `∑` of `Общий список`; the copy now loses to the current tab. The 13 sheets of the issue,
  anonymised, are fixtures in `shared/feature-recordbook/src/androidHostTest/resources/sheets/real`
  with the real total of each tab (`truth.tsv`); no test opens a sheet over the network.
- The column is stored as `gid` + header path; a reading finds the same path (nearest to the old index
  when repeated), falls back to the old index while the tab is that wide, else the column is not
  found. Values are shown as the sheet shows them, never recomputed.

## On the subject page

The connected sheet sits in the hero of `Баллы` with its menu (open, `Изменить итог`, disconnect); its
total is the hero's number (`таблица, в 16:30`) by the
[recordbook's rule](recordbook.md#which-number-is-shown). The stored value shows
at once and the tab is downloaded on entry and on every pull. An `AUTO_CHECK` total read without a
failure adds `Проверьте итог`, which opens the review. Without a connection `Мои баллы из
таблицы` offers the subject's sheet links.

## The total's review

`Изменить итог`, `Проверьте итог` and a row without a total header open the review: the total with its
column, tab and reason, the own row with the headers, up to 4 candidate columns as chips and
`Другая ячейка` (every cell of the row by tab). Any tap saves.

## In the recordbook list

`RecordbookViewModel` only collects `SheetScoresRepository.observe()` and never downloads. Every
connection of the period goes to the list with its value and whether its last reading succeeded; a total
is the row's number with the source `таблица` by the
[recordbook's rule](recordbook.md#which-number-is-shown): over BARS and My ITMO while the subject is in
progress, behind BARS once a reading failed, never for PE or a no-show. `Откуда баллы` shows it beside My
ITMO and BARS.

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

## iOS

`Мои баллы` is the SwiftUI `SheetScoresSheetView` (`iosApp/Sources/Features/Recordbook/SheetScoresSheet.swift`)
over the shared `SheetScoresViewModel`, resolved with `SheetScoresIosParameters` (the `SavedStateHandle` Android's
entry seeds). A failed save shows a banner over the sheet. The client runs on
`darwinHttpEngine()` with the same policy; storage is the same `filesDir/sheet_scores`. Links and
the offer come from the subject's links ([subject page](subject-page.md#ios),
[subject links](resources.md#ios)).
