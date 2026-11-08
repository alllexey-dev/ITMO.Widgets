# Dormitory

`Общежитие` shows where a student stands with an ITMO dormitory: the Этап общежития, the queue place, the assigned
dormitory with its address and the settlement instructions, and the Документы для заселения while they matter.
Read-only: picking a settlement date and uploading documents happen in my.itmo, opened inside the app. The code is
the `:shared:feature-dormitory` module; the spec is issue #566 (this screen #568). Vocabulary: [GLOSSARY.md](../../GLOSSARY.md).

## Профиль row

`DormitoryProfileEntrySource` contributes the `Общежитие` row to the `Университет` group of Профиль through
`core/profile/ProfileEntrySource` ([Profile](me.md)): title `Общежитие`, the stage as the subtitle, `AppRoutes.Dormitory`
on tap. The row is shown while the stored answer has an application and hidden for an empty status (my.itmo answers
`result: null`) or before any answer. A Профиль that finds no stored answer, or one older than 3 hours, asks my.itmo
again once, so a new application shows up without the student doing anything; a failure keeps the stored answer.

## Screen

`AppRoutes.Dormitory`, a screen pushed from Профиль, over `DormitoryViewModel` (Android `ui/DormitoryScreen.kt` in
`androidMain`, iOS `iosApp/Sources/Features/Dormitory/DormitoryScreen.swift`). `DormitoryPresenter` builds every
text, so both platforms only lay it out.

- The stage card (the screen's hero), by `statusId`:

  | `statusId` | Stage | Shows |
  |---|---|---|
  | 1 | `В очереди` | `Место в очереди: N` |
  | 2 | `Место предоставлено` (warning tone) | that the window to pick a date is limited, never a computed deadline; `Выбрать дату заселения` |
  | 3 | `Дата заселения выбрана` | that ITMO must confirm the documents |
  | 4 | `Заселён` (success tone) | that the student lives in the dormitory |
  | other | `Заявка на общежитие` | a pointer to my.itmo |

- `Документы для заселения`, only for stages 2 and 3: each document with ITMO's status label in a pill (success for
  a confirmed one, status 2; warning otherwise), then `Загрузить` while one is not confirmed. No documents yet:
  `ИТМО пока не запросил документы`.
- `Ваше общежитие` once a dormitory is assigned: its name and address, `На карте` and `Как заселиться`, the settlement
  instructions. The instructions are ITMO's HTML turned into safe text by core's `SafeHtml` (the same whitelist as
  [ISU messages](isu-messages.md)); links in them open outside the app.
- `Выбрать дату заселения` and `Загрузить` open my.itmo's dormitory section in the app's browser:
  `AppRoutes.MyItmoWeb("/services/dormitories")` (`DormitoryWeb.route`; the path is the site's own route for the
  dormitory service). Its navigation policy is unchanged ([My ITMO in the app](my-itmo-web.md)).
- `На карте` opens the system map app on the address (`PlatformActions.openMap(MapDestination)`: Android `geo:`,
  iOS Apple Maps), as the sport screens do; a snackbar says so when no app can. The campus map has no dormitories.
- States: loading (no stored answer yet); `Заявки на общежитие нет` for an empty status; `Не удалось загрузить` with
  the error and `Повторить` when nothing is stored and the request failed. Pull to refresh on both platforms.

## Data

`DormitoryRepositoryImpl` (one instance, `dormitoryModule`) reads my.itmo through MyItmoApi's dormitory area:

| Call | When | Read |
|---|---|---|
| `getDormitoryStatus()` (`GET /api/dormitory/status/full`) | every refresh | `statusId`, `queuePlace`, `assignedDormitory {name, address, settlementProcedureText}`; a null result is no application |
| `getDormitoryDocuments()` (`GET /api/dormitory/documents`) | only for stages 2 and 3 | `documentTypeName`, `status`, `status_name` |

- **Stored answer.** The last successful answer is kept in dormitory/state.json in the no-backup directory (`DormitoryFileStore`)
  with its wall-clock time and the account's ISU; the instructions are kept as ITMO's HTML and parsed again on read.
  The screen and the Профиль row read it first, so they open offline. A file of another account or a corrupt one is
  removed. Sign-out removes it (`SessionDataCleaner` `dormitory`); an answer that lands after sign-out is dropped.
- **Offline and errors.** A failed refresh keeps the stored answer and the screen shows it dimmed under
  `Обновлено N мин / ч назад` or `Обновлено N дней назад`; a later successful refresh drops the line. Errors map in
  `toDormitoryAppError` (network first, then `MyItmoException`).
- **Privacy.** Nothing goes to Backend or a widget, nothing is logged; exception messages carry no student data.
- **Demo.** The demo session answers from `DemoDormitory` (`Заселён` in the fictional `Общежитие №1`) and sends no
  request (`DormitoryDemoGateTest`).

Not here yet: the Договор найма and its Платежи (#569), the `Нужно сделать` items with the time `Место предоставлено`
was first seen (#571), the background check, its notifications and bell events (#573).

## iOS

`DormitoryScreen` is a SwiftUI screen over the same view model (`Routes+Dormitory.swift`, `RouteFeature.DORMITORY`);
the Профиль row comes from the same `ProfileEntrySource` through `MeViewModel`. Snapshot tests:
`iosApp/Tests/SnapshotTests/Features/DormitorySnapshotTests.swift` over `DormitorySamples`.

## Tests

- `DormitoryViewModelTest`, `DormitoryProfileEntryTest`, `DormitoryDemoGateTest` (`androidHostTest`): the real
  MyItmoApi client over a Ktor `MockEngine` for my.itmo.ru with synthetic fixtures in
  `src/androidHostTest/resources/myitmo/dormitory/`, a fixed clock and a fake file system.
- `DormitoryScreenTest` (Compose actions and touch targets), `DormitoryScreenshotTest` (baselines in
  `shared/feature-dormitory/screenshots/`), `DormitoryModuleTest`.
