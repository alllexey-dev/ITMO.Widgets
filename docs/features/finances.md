# Finances

`Оплата обучения` shows a student on paid study each Договор об обучении with its balance and schedule of Платежи.
Read-only: paying happens in my.itmo in the system browser. The code is the `:shared:feature-finances` module, which
will later hold the scholarship too; the spec is issue #566 (this screen #570). Vocabulary:
[GLOSSARY.md](../../GLOSSARY.md).

## Профиль row

`TuitionProfileEntrySource` contributes the `Оплата обучения` row to the `Университет` group of Профиль through
`core/profile/ProfileEntrySource` ([Profile](me.md)): title `Оплата обучения`, subtitle `Договор об обучении`,
`AppRoutes.Tuition` on tap. The row is shown while the stored answer has at least one Договор об обучении, and hidden
on a budget place (my.itmo says tuition is not available, or lists no agreement) or before any answer. A Профиль that
finds no stored answer, or one older than 3 hours, asks my.itmo again once, so new agreements show up on their own; a
failure keeps the stored answer.

## Нужно сделать

`TuitionTodoSource` contributes to `Нужно сделать` on Сегодня ([Home](home.md)) through `core/today/TodayTodoSource`
(qualifier `tuition`). It reads only the stored answer, so the items show offline and Сегодня sends no tuition
request; the answer is refreshed by the screen and the Профиль row. The items are core's `PaymentTodos`, the same as
the [dormitory's](dormitory.md#нужно-сделать) for the Договор найма:

| Item | While | Text |
|---|---|---|
| `Погасить долг, 60 000,50 ₽` | a Договор об обучении with a Долг, of any academic year, until it is paid off | `Договор об обучении № …` |
| `Оплатить до 13 ноября, 100 000 ₽` | an unpaid Платёж from 7 days before its due date to that day; the amount is what is left | `Договор об обучении · через 5 дней` or `· сегодня` |

An overdue Платёж is covered by its Договор's Долг item and never shown on its own. Each item's action is `Открыть`:
`EntryRoute(AppTab.HOME, overlay = AppRoutes.Tuition)`, `Оплата обучения` over Сегодня on both platforms. Ids are
`tuition-debt-<contract>` and `tuition-payment-<contract>-<due date>`. A budget place, the demo and a signed-out
device show none. Amounts are in the item text only: nothing is logged.

## Screen

`AppRoutes.Tuition`, a screen pushed from Профиль, over `TuitionViewModel` (Android `ui/TuitionScreen.kt` in
`androidMain`, iOS `iosApp/Sources/Features/Finances/TuitionScreen.swift`). `TuitionPresenter` builds the state;
the agreements are core's `ContractList` (`core/text/ContractCards.kt`), drawn by the kit's `ContractSections`
(`kit/Contracts.kt`) on Android and `ItmoContractList` (`iosApp/Sources/DesignSystem/ItmoContracts.swift`) on iOS,
the same sections the [dormitory](dormitory.md#договор-найма) draws for the Договор найма.

- Each Договор об обучении: its number and dates, one balance line (`Долг`, `Переплата` or `Баланс 0 ₽`),
  `Оплатить` while something is left to pay, then its Платежи with ITMO's period label, the due date, what is paid
  in part and the state pill (`Оплачен`, `Сегодня`, `Через N дней`, `Просрочен`); the rules are core's
  `Payment.stateOn` and `Contract.debtOn`, shared with the dormitory.
- Earlier academic years fold into `Прошлые годы` (`TuitionViewModel.togglePastYears`); a Долг from an earlier year
  stays in front (`ContractsByYear`). An agreement's academic year is the one its start date falls in.
- `Оплатить` opens `https://my.itmo.ru/finances/education` (`TuitionWeb.PAY_URL`, the tuition page of the site's
  router, where my.itmo lists the agreements and starts a payment) in the system browser through
  `PlatformActions.openLink`: the in-app view does not follow the redirect to the bank, and its policy
  (`MyItmoWebPolicy`) stays as it is. A snackbar says so when no browser can open it.
- States: loading (no stored answer yet); `Договоров об обучении нет` for a budget place; `Не удалось загрузить` with
  the error and `Повторить` when nothing is stored and the request failed. Pull to refresh on both platforms.

## Data

`TuitionRepositoryImpl` (one instance, `financesModule`) reads my.itmo through MyItmoApi's finances area:

| Call | When | Read |
|---|---|---|
| `getTuitionAvailability()` (`GET /api/finances/edupayments/availability`) | every refresh | whether tuition applies; `false` on a budget place, and nothing more is asked |
| `getTuitionPayments()` (`GET /api/finances/edupayments/payments`) | when available | `contractId`, `number`, `dateStart`, `dateEnd`, `active`, `balance`, `payments {sum, paid, payUntil, period}`; a null result is no agreement |

`getTuitionContracts()` is not read: the payments call carries every field the screen shows. The shapes of both
answers come from the my.itmo site bundle and are not yet verified on a live paid-study account; an answer MyItmoApi
cannot decode is its redacted `MyItmoException.Decode`, a retriable error that keeps the stored answer and touches no
other Профиль row. ITMO's rubles become kopecks and its dates Moscow calendar dates when stored.

- **Stored answer.** The last successful answer is kept in finances/tuition.json in the no-backup directory
  (`TuitionFileStore`) with its wall-clock time and the account's ISU. The screen and the Профиль row read it first,
  so they open offline. A file of another account or a corrupt one is removed. Sign-out removes it
  (`SessionDataCleaner` `tuition`); an answer that lands after sign-out is dropped.
- **Offline and errors.** A failed refresh keeps the stored answer and the screen shows it dimmed under
  `Обновлено N мин / ч назад` or `Обновлено N дней назад` (core's `updatedAgo`); a later successful refresh drops the
  line. Errors map in `toFinancesAppError` (network first, then `MyItmoException`).
- **Privacy.** Nothing goes to Backend or a widget, nothing is logged; exception messages carry no student data.
  Agreement numbers and amounts are personal financial data: they stay in the no-backup file and on the screen.
- **Demo.** The demo session is on a budget place (`DemoTuition`): no Договор об обучении, no row, no request
  (`FinancesDemoGateTest`).

Not here yet: the background check, its notifications and bell events (#573), the scholarship.

## iOS

`TuitionScreen` is a SwiftUI screen over the same view model (`Routes+Finances.swift`, `RouteFeature.FINANCES`); the
Профиль row comes from the same `ProfileEntrySource` through `MeViewModel`. Snapshot tests:
`iosApp/Tests/SnapshotTests/Features/TuitionSnapshotTests.swift` over `TuitionSamples`.

## Tests

- `TuitionViewModelTest`, `TuitionProfileEntryTest`, `TuitionTodoSourceTest`, `FinancesDemoGateTest`
  (`androidHostTest`): the real MyItmoApi
  client over a Ktor `MockEngine` for my.itmo.ru with synthetic fixtures in
  `src/androidHostTest/resources/myitmo/finances/`, a fixed clock and a fake file system. They cover a budget
  student (no row, one request), a paid student with Платежи and a Долг, an overpayment and `Прошлые годы`, offline
  from the stored answer with its age, an ITMO error and an undecodable answer, sign-out (also while a request is on
  its way) and another account's file; the `Нужно сделать` items: a Долг until paid off, a Платёж due within a week,
  due today and then overdue as a Долг offline without a request, a budget place and sign-out.
- `TuitionScreenTest` (Compose actions and touch targets), `FinancesScreenshotTest` (baselines in
  `shared/feature-finances/screenshots/`), `FinancesModuleTest`.
