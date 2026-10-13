# Subject page

Part of the [recordbook](recordbook.md): the page a subject row opens.
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

- Key `AppRoutes.RecordbookSubject(args)`, registered in `RecordbookEntries.kt`, which closes a key whose arguments
  `validOrNull` refuses. `RecordbookSubjectViewModel` (Koin, the arguments as a parameter) drives
  `RecordbookSubjectRoute` (`ui/subject/`) on Android and `RecordbookSubjectScreen` on iOS; `SubjectPresenter`
  (pure) puts each tab into words.
- A pushed screen: the subject's name with `Дифзачёт, 3 семестр`, the tabs `Баллы`, `Пары`, `Материалы`
  (`SegmentedChoice`) under it. No `Люди` tab: teachers are rows of `Пары`.
- `Баллы`: a BARS notice row (`Войти` or `Повторить`), the hero (the points `из 100`, the grade by points or the
  final result as a pill over its quiet source, the grade scale, `до 3D ещё 4,5` or `до зачёта ещё 8`), the
  connected sheet in the hero with its menu (open, `Изменить итог`, disconnect), the sport points of Физкультура
  (a row to Спорт), then the checkpoints by group with their sums (`28 из 30`), each `9 / 10` over a bar, `минимум 6`
  on the left (red when below) and the date on the right. Without checkpoints: the quiet BARS offer while BARS is not
  connected and `Мои баллы из таблицы` while a sheet link exists; with BARS connected, a note that there are none.
  The source of the hero's number follows the list: BARS, a sheet's total while in progress, My ITMO.
- `Пары`: the schedule question (below), the teachers as rows (`Ершов Н. А.`, `Лабораторные, в основном хвалят`)
  that open the person, `Отзывы недоступны` with `Подключить` without the connection, `Отзывов пока мало` with
  `Написать отзыв` when no teacher has a summary, then the three nearest lessons with `Все пары, N`.
- `Материалы`: without `Подключение к ITMO.Widgets` only a gate with `Подключить` (the connection settings). With it:
  `Чаты`, then the links by category (the pinned one first, then by rank in the order the page first showed them),
  votes on the right, own links with `моя` and their review state (`на проверке`, `отклонена`, `скрыта`, `только у
  вас`); a tap opens a link, a long press its actions; `Добавить ссылку` (the FAB on Android, `+` on iOS); links of
  earlier semesters behind `Все ссылки, N`. The My ITMO LMS link is not shown.
- If BARS is connected and the arguments carry no journal (a notification, a new sign-in), the page finds the
  subject in the period's BARS answer; a subject BARS does not have is no error.

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
- With `Подключение к ITMO.Widgets`, rows with an ISU say the review tone in words (`TeacherLevelTone.words`):
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

`RecordbookSubjectViewModelTest` and the Roborazzi references `RecordbookSubjectScreen_*` in
`:shared:feature-recordbook`; `RecordbookSubjectSnapshotTests` on iOS.

## iOS

`RecordbookSubjectScreen` (`iosApp/Sources/Features/Recordbook/`), a SwiftUI screen pushed on the recordbook stack
under the native bar (title, subtitle), over `RecordbookSubjectViewModel` resolved with the arguments
(`ScreenViewModelStore.resolve(type:parameters:)`).

- Links open in the system; the link editor, a link's actions and `Все ссылки` are the Compose sheets of
  [subject links](resources.md#ios); `Мои баллы` is the Compose sheet of [sheet scores](sheet-scores.md#ios).
- `Подключить` and `Войти` open `BarsLoginSheet`; a vote that fails says so in a banner.
