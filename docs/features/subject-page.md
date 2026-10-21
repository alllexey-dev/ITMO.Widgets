# Subject page

Part of the [recordbook](recordbook.md): the page a subject row opens.
Product behaviour: [Как это работает: оценки](../how-it-works/grades.md).

- Key `AppRoutes.RecordbookSubject(args)`; `RecordbookEntries.kt` closes a key whose arguments
  `validOrNull` refuses. `RecordbookSubjectViewModel` drives `RecordbookSubjectRoute` on Android and
  `RecordbookSubjectScreen` on iOS; `SubjectPresenter` (pure) puts each tab into words.
- A pushed screen with the tabs `Баллы`, `Пары`, `Материалы` (`SegmentedChoice`) pinned under the title;
  a tap or a horizontal swipe changes the page (Android `HorizontalPager`, iOS a page `TabView`), each page
  keeps its scroll. It opens on `Баллы`; a key with `materials = true` (the lesson sheet's `Ссылки`) opens
  on `Материалы`. No `Люди` tab: teachers are rows of `Пары`.
- `Баллы`: a BARS notice row (`Войти` or `Повторить`), the hero (points, grade or final result, the
  next grade hint), the connected sheet with its menu ([sheet scores](sheet-scores.md#on-the-subject-page)),
  the sport points of Физкультура, then the checkpoints by group with their sums and minimums. The source
  of the hero's number follows the list: BARS, a sheet's total while in progress, My ITMO.
- `Пары`: the schedule question (below), the teachers as rows that open the person (review gate or
  `Написать отзыв` when no teacher has a summary), then the three nearest lessons as the schedule's lesson
  cards (`LessonTimelines.lesson` of `core/lesson`); a tap opens the lesson sheet.
- `Материалы`: without the connection to ITMO.Widgets only a gate with `Подключить`. With it, chats and
  links as in [subject links](resources.md#subject-page); earlier semesters behind `Все ссылки, N`. The My
  ITMO LMS link is not shown.
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
- `RecordbookSubjectViewModel` resolves directory photos through `core/social/PersonPhotosRepository` when the
  set of known ISUs changes, seeds cached URLs and publishes each answer in `SubjectTeacherRow.pictureUrl`.
  Loading a photo never holds the subject or its lessons, needs no Backend connection and is cancelled when the
  teachers change. Android and iOS render the same state with their standard avatar components; without a known
  ISU or photo, initials remain. See [social data](social.md).
- With the connection to ITMO.Widgets, rows with an ISU say the review tone in words (`TeacherLevelTone.words`):
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

## iOS

`RecordbookSubjectScreen` (`iosApp/Sources/Features/Recordbook/`), a SwiftUI screen pushed on the recordbook stack
under the native bar (title, subtitle), over `RecordbookSubjectViewModel` resolved with the arguments
(`ScreenViewModelStore.resolve(type:parameters:)`).

- Links open in the system; the link editor, a link's actions and `Все ссылки` are the SwiftUI sheets of
  [subject links](resources.md#ios); `Мои баллы` is the SwiftUI sheet of [sheet scores](sheet-scores.md#ios).
- `Подключить` and `Войти` open `BarsLoginSheet`; a vote that fails says so in a banner.
