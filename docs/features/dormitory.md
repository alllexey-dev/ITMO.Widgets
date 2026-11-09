# Dormitory

`Общежитие` shows where a student stands with an ITMO dormitory: the Этап общежития, the queue place, the assigned
dormitory with its address and the settlement instructions, the Документы для заселения while they matter, and the
Договор найма with its balance and schedule of Платежи. Read-only: picking a settlement date and uploading documents
happen in my.itmo, opened inside the app; paying happens in my.itmo in the system browser. The code is the
`:shared:feature-dormitory` module; the spec is issue #566 (this screen #568, the Договор найма #569, the background
check #573). Vocabulary:
[GLOSSARY.md](../../GLOSSARY.md).

## Профиль row

`DormitoryProfileEntrySource` contributes the `Общежитие` row to the `Университет` group of Профиль through
`core/profile/ProfileEntrySource` ([Profile](me.md)): title `Общежитие`, the stage as the subtitle, `AppRoutes.Dormitory`
on tap. The row is shown while the stored answer has an application or a Договор найма (then the subtitle is
`Договор найма`), and hidden for an empty status (my.itmo answers `result: null`) without agreements or before any
answer. A Профиль that finds no stored answer, or one older than 3 hours, asks my.itmo
again once, so a new application shows up without the student doing anything; a failure keeps the stored answer.

## Screen

`AppRoutes.Dormitory`, a screen pushed from Профиль, over `DormitoryViewModel` (Android `ui/DormitoryScreen.kt` in
`androidMain`, iOS `iosApp/Sources/Features/Dormitory/DormitoryScreen.swift`). `DormitoryPresenter` builds every
text, so both platforms only lay it out.

- The stage card (the screen's hero), by `statusId`:

  | `statusId` | Stage | Shows |
  |---|---|---|
  | 1 | `В очереди` | `Место в очереди: N` |
  | 2 | `Место предоставлено` (warning tone) | that the window to pick a date is limited, never a computed deadline; `Приложение видит этот этап с 09:40` (the time the app first saw it); `Выбрать дату заселения` |
  | 3 | `Дата заселения выбрана` | that ITMO must confirm the documents |
  | 4 | `Заселён` (success tone) | that the student lives in the dormitory |
  | other | `Заявка на общежитие` | a pointer to my.itmo |

- `Документы для заселения`, only for stages 2 and 3: each document with ITMO's status label in a pill (success for
  a confirmed one, status 2; warning otherwise), then `Загрузить` while one is not confirmed. No documents yet:
  `ИТМО пока не запросил документы`.
- `Ваше общежитие` once a dormitory is assigned: its name and address, `На карте` and `Как заселиться`, the settlement
  instructions. The instructions are ITMO's HTML turned into safe text by core's `SafeHtml` (the same whitelist as
  [ISU messages](isu-messages.md)); links in them open outside the app.
- The Договоры найма, after the documents and before the dormitory, see [Договор найма](#договор-найма). A student
  with agreements but no application sees them without the stage card.
- `Выбрать дату заселения` and `Загрузить` open my.itmo's dormitory section in the app's browser:
  `AppRoutes.MyItmoWeb("/services/dormitories")` (`DormitoryWeb.route`; the path is the site's own route for the
  dormitory service). Its navigation policy is unchanged ([My ITMO in the app](my-itmo-web.md)).
- `На карте` opens the system map app on the address (`PlatformActions.openMap(MapDestination)`: Android `geo:`,
  iOS Apple Maps), as the sport screens do; a snackbar says so when no app can. The campus map has no dormitories.
- States: loading (no stored answer yet); `Заявки на общежитие нет` for an empty status without agreements;
  `Не удалось загрузить` with the error and `Повторить` when nothing is stored and the request failed. Pull to refresh on both platforms.

## Договор найма

Each Договор найма is a section titled `Договор найма`; `DormitoryPresenter` builds it from core's payments domain
(`core/payments`, texts in `core/text/PaymentTexts.kt`, the sections in `core/text/ContractCards.kt`, drawn by the
kit's `ContractSections` and iOS `ItmoContractList`), which the [tuition screen](finances.md) shares.

- A card with `№ <number> · <start> - <end>` and one balance line: `Долг` with the amount (error tone) when the
  Договор is a Долг, `Переплата` for a positive balance, else `Баланс 0 ₽`. The Долг amount is the negative balance,
  or, with a balance of zero or more, what is left of the overdue Платежи.
- `Оплатить` while something is left to pay (a negative balance or an unpaid Платёж). It opens
  `https://my.itmo.ru/services/dormitories` (`DormitoryWeb.PAY_URL`) in the system browser through
  `PlatformActions.openLink`: the in-app view does not follow the redirect to the bank, and its policy
  (`MyItmoWebPolicy`) stays as it is. A snackbar says so when no browser can open it.
- The Платежи in ITMO's order: ITMO's period label, `до 10 октября` (with the year when it is not this year's),
  `оплачено 500 ₽` for a part payment, the amount, and a pill by `Payment.stateOn(today)`, today being the Moscow day
  of `AcademicTimeProvider`:

  | State | Rule | Pill |
  |---|---|---|
  | `PAID` | paid at least the amount | `Оплачен` (success) |
  | `OVERDUE` | unpaid, past the due date | `Просрочен` (error); makes the Договор a Долг |
  | `DUE_TODAY` | unpaid, due today | `Сегодня` (warning) |
  | `DUE_SOON` | unpaid, due in 1-7 days | `Через N дней` (warning) |
  | `UPCOMING` | unpaid, due later | none |

- `ContractsByYear`: the agreements of this academic year (1 September - 31 August) or later, and any Долг whatever its
  year, are shown in full, newest year first; the other earlier years fold into `Прошлые годы`, a row that unfolds
  them (`DormitoryViewModel.togglePastYears`), each under `2025/2026 учебный год`. A screen reader hears the row as
  `развёрнуто` or `свёрнуто` (Android `stateDescription`, iOS `accessibilityValue`).
- Amounts are kopecks (`Money`), shown as `1 500 ₽` or `1 500,50 ₽` with no-break spaces.

## Нужно сделать

`DormitoryTodoSource` contributes to `Нужно сделать` on Сегодня ([Home](home.md)) through `core/today/TodayTodoSource`
(qualifier `dormitory`). It reads only the stored answer, so the items show offline and Сегодня sends no dormitory
request; the answer is refreshed by the screen, the Профиль row and the [background check](#background-check).

| Item | While | Text |
|---|---|---|
| `Выберите дату заселения` | `Место предоставлено` | `Место предоставлено (видим с 09:40), время на выбор ограничено`; `7 нояб., 09:40` before today |
| `Документ «…»` | `Место предоставлено` or `Дата заселения выбрана`, one per document not confirmed | ITMO's status label, `— нужен для заселения` |
| `Погасить долг, 1 500 ₽` | a Договор найма with a Долг, of any academic year, until it is paid off | `Договор найма № …` |
| `Оплатить до 13 ноября, 1 000 ₽` | an unpaid Платёж from 7 days before its due date to that day; the amount is what is left | `Договор найма · через 5 дней` or `· сегодня` |

Each item's action is `Открыть`: `EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory)`, the `Общежитие` screen over
Сегодня on both platforms. Ids are stable (`dormitory-date`, `dormitory-document-<name>`, `dormitory-debt-<contract>`,
`dormitory-payment-<contract>-<due date>`). An item leaves with the answer that changes the stage or confirms the
document; a Платёж item leaves once it is paid or overdue, a Долг item once the Договор owes nothing.

The Платёж and Долг items are core's `PaymentTodos` (`core/today/PaymentTodos.kt`), shared with
[tuition](finances.md#нужно-сделать): the Долг is `Contract.debtOn`, the window is `Payment.stateOn` (`DUE_SOON` within
`PaymentState.DUE_SOON_DAYS` = 7 days, `DUE_TODAY`). An overdue Платёж is covered by its Договор's Долг item and never
shown on its own. Долг items come first, then Платежи by due date. Amounts are in the item text only: nothing is
logged. The items are evaluated when Сегодня starts reading them (the app opens or returns to the foreground). The
demo is `Заселён` with its one Платёж 12 days ahead, so it shows none.

The time is when the app first saw `Место предоставлено`, not when ITMO offered the place (my.itmo gives no time):
a refresh that brings the stage stores its wall-clock time with the answer, later answers in the same stage keep it,
an answer in another stage drops it, and sign-out removes it with the file. No deadline is computed.

## Data

`DormitoryRepositoryImpl` (one instance, `dormitoryModule`) reads my.itmo through MyItmoApi's dormitory area:

| Call | When | Read |
|---|---|---|
| `getDormitoryStatus()` (`GET /api/dormitory/status/full`) | every refresh | `statusId`, `queuePlace`, `assignedDormitory {name, address, settlementProcedureText}`; a null result is no application |
| `getDormitoryDocuments()` (`GET /api/dormitory/documents`) | only for stages 2 and 3 | `documentTypeName`, `status`, `status_name` |
| `getDormitoryContractPeriods()` (`GET /api/dormitory/payments/contracts/periods`) | every refresh | `year`, `dateFrom`, `dateTo` of each period with agreements: calendar years, 1 January - 31 December |
| `getDormitoryContracts(dateFrom, dateTo)` (`GET /api/dormitory/payments/contracts?from&to`) | once per listed period | `contractId`, `number`, `dateStart`, `dateEnd`, `active`, `balance`, `payments {sum, paid, payUntil, period}` |

The status (with its documents) and the agreements are asked apart. A failed status call fails the refresh and keeps
the stored answer as it is. A failed agreements call keeps the stored agreements beside the new status, with the time
they came (`contractsFetchedAt`), and the refresh still fails, so the screen shows `Обновлено N назад` with the age of
the older part and the background check retries. A null list (`result: null`) of periods or agreements counts as none.
Each period's answer has every agreement that overlaps it, so one running from September to June comes in two
periods: it is kept once by `contractId`, as the latest period has it. Its academic year comes from its `dateStart`
(1 September - 31 August), never from the period.
ITMO's rubles become kopecks and its dates Moscow calendar dates when stored.

- **Stored answer.** The last successful answer is kept in dormitory/state.json in the no-backup directory (`DormitoryFileStore`)
  with its wall-clock time, the agreements' own time and the account's ISU, and while `Место предоставлено` the time the app first saw it; the
  instructions are kept as ITMO's HTML and parsed again on read.
  The screen, the Профиль row and `Нужно сделать` read it first, so they open offline. A file of another account or a corrupt one is
  removed. Sign-out removes it (`SessionDataCleaner` `dormitory`); an answer that lands after sign-out is dropped.
- **Offline and errors.** A failed refresh keeps the stored answer and the screen shows it dimmed under
  `Обновлено N мин / ч назад` or `Обновлено N дней назад`; a later successful refresh drops the line. Errors map in
  core's `toMyItmoAppError` (network first, then `MyItmoException`); a file that cannot be written is
  `AppError.Unknown` and changes nothing.
- **Privacy.** Nothing goes to Backend or a widget, nothing is logged; exception messages carry no student data.
  Agreement numbers and amounts are personal financial data: they stay in the no-backup file and on the screen.
- **Demo.** The demo session answers from `DemoDormitory` (`Заселён` in the fictional `Общежитие №1`, one Договор
  найма with one upcoming Платёж) and sends no request (`DormitoryDemoGateTest`).

## Background check

One check `Общежитие и оплата` keeps the stored answers of the dormitory and the [tuition](finances.md) fresh and tells
the student what happened. It is core's `PaymentsCheck` (`core/payments/check`) over every feature's
`PaymentsCheckPart` (qualifiers `dormitory`, `tuition`), so the two features never import each other;
`DormitoryCheckPart` is this module's.

- **Cadence.** Android runs it every 3 hours with a network (`PaymentsWorker`, `PAYMENTS_SPEC`, unique work
  `dormitory-payments-check` and `dormitory-payments-check-now`); iOS as the step `dormitory-payments` of the one app
  refresh task, at most every 3 hours. A run decides part by part (`PaymentsCadence`): it asks my.itmo for a part
  whose stored answer has a Этап общежития (any status, `Заселён` included) or something left to pay (an unpaid
  Платёж or a negative balance), and otherwise only 24 hours after the later of its answer (the older part of it)
  and its last attempt, so a status that appears is found within a day and a part that keeps failing, or has no
  answer yet, is asked once a day rather than every run. The time of each part's last attempt is kept with the
  posted notices (payments_notices.json). A run that asks nothing is `SKIPPED`. It runs with any session; the demo and a signed-out device send nothing and post nothing.
- **Notifications** ([notifications](notifications.md#dormitory-and-payments), switch `Общежитие и оплата`,
  [settings](../settings.md#notifications)): `Место предоставлено` once per time the app first saw the stage, and
  each Платёж three days before and on its due day (`PaymentReminders`, shared with tuition); nothing for a Долг.
  They come from the stored answers after the request, so they work offline. Nothing is posted 00:00-06:00 Moscow:
  Android posts it at the first run after 06:00, iOS hands it to the system for 06:00.
- **Bell** ([the bell](home.md#the-bell), qualifier `dormitory`, kinds `DORMITORY` and `PAYMENT`): every successful
  refresh, whoever asks, compares the previous stored answer with the new one (`DormitoryBellEvents`,
  dormitory_bell.json in the no-backup directory, 60 days, at most 50 events, cleared on sign-out) and records `Место в очереди на
  общежитие` with `было` and `стало` when the queue place moved, `Назначено общежитие` with its name when a dormitory
  is assigned (or another one), and `Платёж зачислен` (`Договор найма · Ноябрь 2026 · 1 500 ₽`, core's
  `PaymentBellEvents`) when an unpaid Платёж became paid. The first answer is a baseline. The bell is written with the
  switch off. Each event opens `Общежитие` over Сегодня.
- The debug tools' `Проверить общежитие и оплату` asks for one run.

## iOS

`DormitoryScreen` is a SwiftUI screen over the same view model (`Routes+Dormitory.swift`, `RouteFeature.DORMITORY`);
the Профиль row comes from the same `ProfileEntrySource` through `MeViewModel`. Snapshot tests:
`iosApp/Tests/SnapshotTests/Features/DormitorySnapshotTests.swift` over `DormitorySamples`.

## Tests

- `DormitoryViewModelTest`, `DormitoryContractTest`, `DormitoryProfileEntryTest`, `DormitoryTodoSourceTest`,
  `DormitoryDemoGateTest` (`androidHostTest`): the real MyItmoApi client over a Ktor `MockEngine` for my.itmo.ru with
  synthetic fixtures in `src/androidHostTest/resources/myitmo/dormitory/`, a fixed clock and a fake file system;
  `DormitoryContractTest` covers the balance, paid and unpaid Платежи, a Долг, several academic years and an
  agreement listed in two calendar-year periods; `DormitoryTodoSourceTest` the Платёж window (8 days no item, 7 days
  an item), due today, an overdue Платёж inside the Долг item, a Долг that stays until paid and one of an earlier
  year, offline from the stored answer without a request;
  `DormitoryTodoSourceTest` covers the items appearing and leaving with the stage and the documents, and the
  first-seen time across restarts, stage changes and sign-out.
- `DormitoryPaymentsCheckTest` (`androidHostTest`): the check over the same harness: `Место предоставлено` once and
  again after the stage came back, a Платёж reminded three days before and on the due day once each (and at once two
  days ahead), no reminder for a Долг, the quiet hours on Android (held until 06:00) and iOS (scheduled for 06:00
  once), the bell's queue move, assigned dormitory and paid Платёж, the switch off (nothing posted, the bell
  written, no backlog once it is back on), the 3-hour versus daily cadence (a part that keeps failing, or whose daily
  refresh failed, waits a day after the attempt), offline reminders from the stored answer, sign-out, no session and
  the demo.
- `DormitoryScreenTest` (Compose actions and touch targets), `DormitoryScreenshotTest` (baselines in
  `shared/feature-dormitory/screenshots/`), `DormitoryModuleTest`.
