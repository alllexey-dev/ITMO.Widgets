# Subject page

Part of the [recordbook](recordbook.md): the page a subject row opens.
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

- Key `AppRoutes.RecordbookSubject(args)`, registered in `RecordbookEntries.kt`, which closes a key
  whose arguments `validOrNull` refuses. `RecordbookSubjectViewModel` (Koin, reads the arguments from
  `SavedStateHandle`) drives `RecordbookSubjectRoute` (`ui/subject/RecordbookSubjectScreen.kt`).
- One `LazyColumn` over `subjectHubItems` with one stable key per row (`subjectHubKeys`). Sections in
  order: result card (`SubjectHero`; PE shows the sport card instead), `Ссылки`
  ([resources](resources.md#subject-page)), `Чаты`, `Контрольные точки`, `Преподаватели`,
  `Ближайшие пары` (current period only, never PE).
- The host opens links, link sheets, `Мои баллы`, a teacher profile and the BARS sign-in through
  `RecordbookSubjectExits`, and refreshes the page after a completed sign-in.

## Grade scale

`RecordbookGradeScale`: above 90 is 5A, above 83 4B, above 74 4C, above 67 3D, 60 and above 3E,
below 60 unsatisfactory; a plain credit has the single threshold 60. Hints
(`RecordbookGradeScale.nextStep`) count whole points, so a strict threshold aims at the next whole
score; a passed plain credit has no hint.

## Control groups

`RecordbookControlGroups.groupControls` keeps server order and returns `ControlEntry` values, lone
controls or a `ControlGroup` in the place of its first control:

- A tree root with children is a group titled by the root; its rows are the leaves, because a parent
  carries its own total.
- At least two childless roots whose names differ only by a trailing number (`Лабораторная работа 3`,
  `№ 3`) form one group titled by the name without the number. `ControlGroupKind` maps known kinds to
  string resources; any other title keeps the source text.
- A group's heading sums known scores over known maxima; `ниже минимума` when any graded control is
  under its positive minimum. Ungraded controls are never below anything.

## Teachers

- Distinct people from the subject's lessons; without lessons the recordbook teacher stands in.
- Only a usable teacher ISU opens the person profile; no identifier is guessed by name.
- With `Подключение к ITMO.Widgets`, rows with an ISU show the review tone (`ToneDot`):
  `RecordbookSubjectViewModel` asks `TeacherLevelsRepository` whenever the set of ISUs changes
  ([teacher levels](reviews.md#teacher-levels)).

## Lessons

- The schedule side is `core/schedule/SubjectLessonsGateway`, implemented in
  `feature/schedule/data` (features never import each other). Window: today to +28 days, refreshed
  through `ScheduleRefreshGateway`; a failed refresh with an empty cache is an error, with a cache the
  cache is shown.
- `SubjectContextResolver` binds a discipline to a schedule subject: `discipline_id == subject_id`
  binds silently (true on live data for every discipline except PE); a confirmed binding from
  `SubjectBindingStore` (DataStore, cleared on sign-out) wins; otherwise a single normalised-name
  match is proposed, nothing is stored on `Нет`. `subjectNameKey` is the one normalisation, shared
  with the BARS merge.
- Links are observed separately from lessons, so a lesson update never drops a loaded link snapshot.

## Cache

`RecordbookRepositoryImpl` is a singleton with a memory cache (`cachedPrograms`, `cachedSubjects` per
`(programId, semester)`, `cachedControls` per entry), a `SessionDataCleaner`. The root and the subject
page seed `Content(refreshing = true)` from it, so a second visit never shows the skeleton.

## Tests

Host tests and Roborazzi goldens (`RecordbookSubjectScreen_*`) in `:shared:feature-recordbook`.

## iOS

Same `RecordbookSubjectRoute`, pushed on the recordbook stack: `RecordbookSubjectView`
(`iosApp/Sources/Features/Recordbook/`) via `recordbookSubjectPage` (`shared/ios`), arguments from
a `SavedStateHandle` built by `RecordbookSubjectIosRoute`. The page draws its own header with
`К зачётке`.

- Sheets and LMS pages open in the system browser; `Изменить итог` opens
  [`Мои баллы`](sheet-scores.md#ios); the BARS snackbar opens the BARS sign-in sheet.
- `PlatformCapabilities.reviews` gives `linksEnabled = true`; link sheets are SwiftUI
  ([subject links](resources.md#ios)). With `linksEnabled = false` `subjectHubItems` keeps only the
  course's LMS page under `Ссылки` and drops `Чаты`.
