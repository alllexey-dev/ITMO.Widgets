# Sheet scores

Part of the [recordbook](recordbook.md).
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

The own total of a subject from a teacher's public Google Sheet. The connection, the downloaded sheet
and other people's names stay on the device: no connection to ITMO.Widgets needed
(decision [0014](../decisions/0014-sheet-scores-on-device.md)). Only `Посчитать с ИИ` for a sheet a link shares
with a flow or everybody asks Backend for a rule, never with sheet data
(decision [0038](../decisions/0038-ai-sheet-formula.md)). `feature/resources` only offers the action.

## Connecting

- A link is a sheet when `core/resources/GoogleSheetUrl` accepts it
  (`https://docs.google.com/spreadsheets/d/<id>` or `.../u/<n>/d/<id>`, `gid` from the fragment, then
  the parameter; published `/d/e/...` addresses are not sheets).
- `Мои баллы` opens `AppRoutes.SheetScores(SheetScoresArgs)` with the link's `ResourceScope`
  (`discipline_id` and period) and `linkId`, `SubjectLink.sharedLinkId`: the link's id when it is a Backend link
  shared with a flow or everybody, else null. One connection per scope; a new one replaces the old after a successful
  choice. The connection keeps the link id; `Изменить итог` passes it again (or the id of the subject's link with the
  same address).
- The workbook lives only in the view model, never in saved state; after process death it is
  downloaded again. A failed write keeps the choice.
- Name search appears for more than 8 student rows. Student rows start at the topmost name or ISU
  above the row in its column; empty cells and up to two other texts are skipped, a people title
  such as `ФИО` ends them. A name is 2-8 words.
- The sheet asks one question per step (`SheetScoresUiState`): several own rows `Какая строка ваша?` (`Ваше имя есть
  в нескольких строках`, the tab under each name); no own row `На каком листе ваша группа?` (`Не нашли вас по имени и
  ИСУ`), then that tab's rows with a back arrow to the tabs. A found total is saved at once and the sheet closes.
- A failure shows its title and one line why: `Таблица закрыта` (`Баллы читаются только из таблиц, открытых по
  ссылке`), `Таблица слишком большая` (`Каждый её лист больше 5 МБ`), `Строка не найдена` (`Возможно, её удалили или
  переименовали`), `Столбец не найден` (`Возможно, столбец или лист удалили`); only `Нет связи` has `Повторить`.

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
  subject page asks to check it), `MANUAL` or `AI`. Saving the automatic total again confirms it (`AUTO`), also in
  place of a hand-picked one.
- Issue 419 (АК): some students were also in `Copy of Общий список` of an earlier run under `Итого`,
  which beat `∑` of `Общий список`; the copy now loses to the current tab. The 13 sheets of the issue,
  anonymised, are fixtures in `shared/feature-recordbook/src/androidHostTest/resources/sheets/real`
  with the real total of each tab (`truth.tsv`); no test opens a sheet over the network.
- The total is a `SheetFormula` (`domain/sheets/SheetFormula.kt`): `Direct` (one cell) or `Computed` (a node tree
  over cells of the own row: `sum`, `multiply`, `min`, `max`, `subtract`, `divide`, `round`, numbers, with the
  rule's `blank_policy`). Every cell is stored as `gid` + header path; a reading finds the same path (nearest to the
  old index when repeated), falls back to the old index while the tab is that wide, else the column is not found.
  The own row is found again in every tab the formula reads (`SheetScoreRules.OwnCells`, `SheetRows.locate` by the
  stored key); each tab is downloaded once, one at a time.
- A direct value is shown as the sheet shows it, never recomputed. A computed one parses numbers (`7,5`, `-3`,
  spaces) and is written as the app writes numbers (`66,3`); as in Backend's analyzer (`evidence.py`) an empty input
  is skipped by a sum under `ignore_blank_inputs` (a sum of blanks only is 0, a rounded blank 0) and gives no value
  under `preserve`; text, a percentage, an error cell, a blank operand of another operation or a division by zero give
  no value (`—`), never a guessed 0. A missing tab, row or column fails the reading with its status.

## On the subject page

The connected sheet sits in the hero of `Баллы` with its menu (open, `Изменить итог`, disconnect); its
total is the hero's number (`таблица, в 16:30`) by the
[recordbook's rule](recordbook.md#which-number-is-shown). The caption is the same source line as the sheet's:
`Столбец «ЛР › Итог», лист «…»`, or for a computed total `Сумма: ДЗ 1 + ДЗ 2 + … + Экзамен, лист «…»`; an AI rule
with the teacher's total adds `Итог преподавателя: 0`. The stored value shows
at once and the tab is downloaded on entry and on every pull. An `AUTO_CHECK` total read without a
failure adds `Проверьте итог`, which opens the review. Without a connection `Мои баллы из
таблицы` offers the subject's sheet links.

## The total

`Изменить итог`, `Проверьте итог` and a row without a total header open the total (`SheetScoresUiState.PickTotal`),
one screen that saves and two pages (`TotalPage`):

- On top (`hero`): `Ваш итог`, the chosen value as the sheet shows it (a sum or a rule as the app writes numbers),
  one source line (`Столбец «ИТОГО баллов», лист «P3110»`, `Сумма: ЛР3 + ЛР4 + ЛР5`, the rule in words) and at most
  one note: the automatic total waiting for a check in the warning colour (`Проверьте: на другом листе у вас другой
  итог`, `... этот столбец пока пуст у всех`, `... в таблице нет столбца «Итог»`, `... в этом столбце не баллы`), or for
  an AI rule `Посчитано по формуле ИИ — сверьте с таблицей` with `Итог преподавателя: 0` and `Как считает ИИ` (the
  rule's summary and warnings). The choice starts at the connected total, else the automatic one; nothing chosen
  shows `Итог не найден` with `В таблице нет столбца «Итог»` or `В вашей строке пока нет баллов`.
- `Другой итог` (`Как найти итог` while nothing is chosen): the other ready totals (`variants`), never the one on
  top: the automatic total (`Найден автоматически`), up to 4 total columns whose own value is a number, the
  connected and the last picked column, the saved sum or AI rule, the sum made here and the AI result, each with
  its value; the tab joins the caption when they span tabs. A tap puts it on top.
- Then `Выбрать столбец`, `Сложить столбцы` and, for a shared sheet, `Посчитать с ИИ`.
- One button (`action`): `Сохранить` when the choice differs from the connected total or confirms one waiting for a
  check (choice `AUTO` for the automatic column, else `MANUAL`, `AI` for the rule); `Готово` closes without a
  write when nothing changes; none while nothing is chosen. On iOS it is the bar's confirmation button.
- `Выбрать столбец` (back arrow, Back on Android): tab chips when the row is in several tabs, every column of the own
  row (its last header segment over the group above it, the own value, a check on the chosen one); columns nobody
  has a value in fold under `Пустые у всех`. A tap chooses it and returns.
- `Сложить столбцы` (back arrow): the live sum on top with the columns in words (`Отметьте два столбца или больше`
  below two), then only the columns that can be added: the own value is a number, or empty in a column of numbers
  for others (no grades, percentages or text). `Готово` puts the sum on top and returns. A hand-made sum opens with
  its columns checked. The sum is `SheetFormula.sum` with `ignore_blank_inputs`.

## AI rule

- `Посчитать с ИИ` shows only with a link id and `SheetRulesRepository.available()` (the services opt-in, or the demo
  session). A tap posts `POST /api/links/{id}/sheet-rule`, then polls `GET` every 4 s while Backend answers
  `PENDING`/`RUNNING` (a stale rule that is still sent is used at once), up to 3 minutes; leaving the sheet stops it.
  The row says `Разбираем таблицу, обычно до минуты` meanwhile, and the rest of the screen stays usable.
- `SheetRulesRepositoryImpl` holds the client: the demo session answers `DemoRecordbook.sheetRule()` and calls nothing;
  otherwise every call passes `BackendGate.mayCallBackend()`. A route with an unknown purpose, kind, policy or node is
  dropped; no route left is a failure.
- READY: the route of the tab holding the own row (a workbook with a tab per group has a route per tab;
  `current_accumulated` first) is resolved against the downloaded tabs (gid, else tab name; each cell takes its header
  path there) and read over the own row. The result goes on top at once: the own value, the formula in words (a sum
  of more than 4 columns only as `Сумма 12 столбцов`), `Итог преподавателя` from the `official_total` route; the
  rule's summary and the routes' warnings stay under `Как считает ИИ`. `Сохранить` saves it (choice `AI`); another
  variant on top drops nothing and saves nothing of it.
- INSUFFICIENT, FAILED, 404 or 409 (not shared, not a sheet, the feature off), 429, no connection, a rule naming what
  the device does not see, or still pending after 3 minutes: a short Russian line on the AI row, which can be tapped
  again.

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
- A connection keeps `value` (last read, shown; the formula's value) and `baseline` (last non-empty); the official
  total of an AI rule is read along and never compared. Only a background
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

- `SheetScoresFileStore`: `filesDir/sheet_scores/state.json`, format 2, `owner` is the ISU, written
  through `AtomicTextFile`; excluded from backup and device transfer. A connection holds `formula` (`kind` `direct`
  or `computed` with `blanks`, a `node` tree of `op` `cell` (`gid`, `headerPath`, `column`), `number`, `sum`,
  `multiply`, `min`, `max`, `subtract`, `divide`, `round`), and for an AI rule `official`, `officialValue` and
  `summary`; `linkId` when known. Format 1 (2.2: `headerPath`, `columnIndex`) is read as a direct formula of
  `tabGid` and written back as format 2. 2.2 deletes a format 2 file, so a downgrade loses the connections
  ([0038](../decisions/0038-ai-sheet-formula.md)). A corrupt file, another format, an invalid field or another
  account's file is deleted.
- `SheetScoresRepositoryImpl` is one Koin single and a `SessionDataCleaner`: sign-out deletes the
  file; a reading is written only when the session generation and the whole connection it was taken
  for are unchanged.

## iOS

`Мои баллы` is the SwiftUI `SheetScoresSheetView` (`iosApp/Sources/Features/Recordbook/SheetScoresSheet.swift`)
over the shared `SheetScoresViewModel`, resolved with `SheetScoresIosParameters` (the `SavedStateHandle` Android's
entry seeds): the same steps, the button in the bar, a chevron back on the column pages. A failed save shows a
banner over the sheet. The client runs on
`darwinHttpEngine()` with the same policy; storage is the same `filesDir/sheet_scores`. Links and
the offer come from the subject's links ([subject page](subject-page.md#ios),
[subject links](resources.md#ios)).
