# Sheet scores

Part of the [recordbook](recordbook.md).

A student connects the own total of a subject from a teacher's public Google
Sheet and sees it on the subject page and, while the official points are empty,
in the list. The connection, the downloaded sheet and other people's names stay
on the device: no Backend, no `Подключение к ITMO.Widgets` (decision
[0014](../decisions/0014-sheet-scores-on-device.md)). The code lives in
`feature/recordbook` under `domain/sheets`, `data/sheets`, `presentation/sheets`
and `ui/sheets`; `feature/resources` only offers the action.

## Connecting

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

## Downloading

`PublicSheetClient` (`commonMain`, one Koin single) owns its Ktor client over
the platform engine (OkHttp on Android, URLSession on iOS, neither with a cookie
store, cache or redirects of its own): no `HttpCookies`, `HttpRedirect` follows
redirects but never from HTTPS to HTTP, `HttpTimeout` 15 s to connect, 30 s
between bytes and 90 s per request, requests only to
`https://docs.google.com/`, nothing in the demo session. Bodies are streamed and
read up to the limit. Addresses and bodies never reach the log or an exception.

- A tab: CSV `GET /spreadsheets/d/<id>/export?format=csv&gid=<gid>` (redirects
  to the download host are followed). An answer that is not `text/csv`, or 401
  or 403, falls back to the HTML tab
  `GET /spreadsheets/d/<id>/htmlview/sheet?headers=false&gid=<gid>`, parsed by
  `SheetHtmlGrid` (Ksoup, `table.waffle`, `colspan`/`rowspan` spread as in CSV).
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

## Own row, header and total

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

## On the subject page

At the bottom of the result card (`SubjectSheetTotal` in `SubjectHero`'s sheet
slot), under a hairline:

- A connection: a row of the card, with
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
  several ask `Какая таблица?` first in the kit's `ChoiceDialog`: own links
  (`, моя`), the pinned one, `SCORES`, the rest by rank, one entry per address.
- Without controls the `My ITMO не присылает детализацию…` card is not shown
  when the card has either row (the sheet is the detail); a failure to load
  the controls stays.
- The stored value shows at once; the page downloads the tab on entry and on
  every pull, without an indicator. PE has neither row.

## In the recordbook list

`RecordbookViewModel` collects `SheetScoresRepository.observe()` only, never
downloads, and passes the totals of the selected period
(`Content.sheetTotals` by `discipline_id`). `sheetFallback` keeps a total only
for a non-PE subject without a no-show, a final grade and My ITMO or BARS
points. The row then shows the table mark (`RecordbookTestTags.SHEET_MARK`,
`ic_table`, 16 dp) and the value
(`titleMedium`, one line, at most 96 dp) without the bar; TalkBack reads
`Из таблицы: 66,3`.

## Background check of sheets

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

## Storage

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
deleted. The store, the client and `SheetScoresRepositoryImpl` live in
`:shared:feature-recordbook` `commonMain`; the repository is one Koin single and
a `SessionDataCleaner`: sign-out deletes the file; a reading is written only when
the session generation and the whole connection it was taken for are unchanged.

## Tests

Unit, in `shared/feature-recordbook/src/androidHostTest`: `CsvGridTest`,
`SheetIdentityTest`, `SheetHeadersTest`, `SheetRowsTest`, `SheetTotalsTest`,
`SheetScoreRulesTest` on synthetic sheets in its `resources/sheets/`;
`SheetTabsParserTest`, `SheetHtmlGridTest`, `PublicSheetClientTest` (the
production Ktor configuration on a `MockEngine`: cookies, redirects, timeouts,
the limit), `SheetScoresFileStoreTest`, `SheetScoresRepositoryImplTest`
(MockWebServer over the OkHttp engine),
`SheetScoresViewModelTest`, `SheetScoresSheetTest` (the Compose sheet:
every failure in one area, the row, tab and total picks, the name search) and
the `SheetScoresSheet_*` goldens of `:shared:feature-recordbook`; the sheet cases of `MarkTrackingRepositoryImplTest`,
`MarksCheckTest`, `DefaultMarkTrackingTest`, `MarkNewsRulesTest`,
`RecordbookSubjectViewModelTest`, `RecordbookViewModelTest` and
`RecordbookDisplayedScoreTest`; the subject page's row, menu and offer in
`SubjectHubSectionsTest`, the link picker in `RecordbookSubjectScreenTest` and
the `SubjectSheetTotal*` and `RecordbookSubjectScreen_{sheet,offer}` goldens;
`GoogleSheetUrlTest` in `:shared:core`. Instrumented:
`SubjectLinksVisualTest.actionsSheetOffersMyScoresOnlyForAGoogleSheet`. No real
sheet is opened; names and ISUs are made up.
