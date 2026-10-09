# Subject page

Part of the [recordbook](recordbook.md): the page a subject row opens.

`RecordbookSubjectRoute` (`ui/subject/RecordbookSubjectScreen.kt` in
`:shared:feature-recordbook`, hosted by `RecordbookSubjectFragment`, the
overlay destination `recordbook_subject`) is one page without tabs: the header
holds the back button, the subject name in up to two lines and
`<assessment kind>, N семестр` (only `N семестр` while the subject loads or
failed); below it one pull-to-refresh `LazyColumn` over `subjectHubItems`, one
stable key per row (`subjectHubKeys`). The first load without a cache shows
list placeholders under the header, a failed one `Не удалось загрузить` with
`Повторить` in the same area. Every list section is a `SectionHeading` in
`colorPrimary` over one connected group of rows
([design](../design.md#connected-groups)); nothing on the page is separated
by « · ». In this order:

1. The result card (`SubjectHero`, the hero card): points, the grade
   badge once a final result exists, and the kit's `GradeScale`, a bar on the 100
   scale with a tick at the lowest whole score of each grade, labelled by its
   letter (E 60, D 68, C 75, B 84, A 91; a plain credit has one `зачёт` tick at
   60). While the result is open the hint names the next step, `до 4C ещё 3` or
   `до зачёта ещё 8` (`RecordbookGradeScale.nextStep`); a passed plain credit
   has none. Under a hairline the card ends with the own total from a
   connected sheet, or with the offer to connect one
   ([sheet scores](sheet-scores.md#on-the-subject-page)). PE shows the sport card instead.
2. `Ссылки` for everything except PE, including past periods: at most three
   link rows, then `Все ссылки, N` that opens the links sheet, or
   `Добавить ссылку` while the subject has no links. See
   [resources](resources.md#subject-page).
3. `Чаты`: own and shared chat links in a group of their own.
4. `Контрольные точки`: controls and [control groups](#control-groups),
   groups expanded.
5. `Преподаватели`: distinct people from the subject's lessons, each with the
   lesson types they run, most frequent first; without lessons the recordbook
   teacher stands in. A row with a usable teacher ISU opens the shared person
   profile. A recordbook-only teacher without ISU remains informational, including
   past periods, PE and unmatched subjects; no identifier is guessed by name.
   With `Подключение к ITMO.Widgets` a row with an ISU shows the tone of the
   teacher's AI summary as a 10 dp dot before the chevron (`ToneDot` in
   `SubjectTeacherRow`); `RecordbookSubjectViewModel` asks
   `TeacherLevelsRepository` whenever the set of teacher ISUs changes and keeps
   the answer in `SubjectHubState.teacherLevels`
   ([teacher levels](reviews.md#teacher-levels)). A row with an ISU but no level
   keeps the place of the dot, so the chevrons stay in one column; a row without
   an ISU has none. TalkBack adds `, тон отзывов: …` to the row.
6. `Ближайшие пары`: only for the current period and never for PE.

The scale is `RecordbookGradeScale`: above 90 is 5A, above 83 4B, above 74 4C,
above 67 3D, 60 and above 3E, below 60 unsatisfactory; a plain credit
(`Зачёт`, not graded) has the single threshold 60. Hints count whole points,
so a strict threshold is aimed at the next whole score.

A control row (`SubjectControlRow`, a row of a connected group)
shows the name and `score / maximum` with a thin bar relative to its maximum:
error colour below the minimum, green once the minimum is met or the maximum
reached, primary otherwise. Requirements appear only when broken: `минимум N`
below the minimum and `Неявка`, both in the error colour. A date and a teacher
who differs from the subject's teacher follow on the next line, separated by a
comma; additional points are named `Дополнительные баллы`. Lone controls in a
row share one group; each control group has its own heading and group.

## Control groups

`RecordbookControlGroups.groupControls` keeps server order and returns
`ControlEntry` values, lone controls or a `ControlGroup` in the place of its
first control:

- A tree root with children is a group titled by the root; its rows are the
  leaves, because a parent carries its own total.
- At least two childless roots whose names differ only by a trailing number
  (`Лабораторная работа 3`, `№ 3`) form one group titled by the name without
  the number. `ControlGroupKind` names the known kinds through string resources
  (`Лабораторные`, `Контрольные`, `Практические`, `Домашние задания`); any other
  title keeps the source text.
- A group is a heading (`SubjectControlGroupHeading`: the title in
  `titleSmall`, the sum of its known scores out of the sum of known maxima at
  the end, `—` while nothing is graded) and a `ниже минимума` label when any
  graded control in it is under its positive minimum. Its controls follow as
  one connected group. Ungraded controls are never below anything.

## Lessons

The schedule side is reached through `core/schedule/SubjectLessonsGateway`,
implemented in `feature/schedule/data`, because features never import each
other.

- The window is today … +28 days. The view model refreshes it through
  `ScheduleRefreshGateway` (a failed refresh with an empty cache is an error
  with `Повторить`; with cached lessons the cache is shown) and observes the
  gateway. The two nearest lessons are shown (`вид, аудитория, корпус` on one
  line); `Все пары, N`, the last row of the group, opens the rest in
  place. Lesson rows are informational; the details sheet belongs to the
  schedule feature.
- The link between a recordbook discipline and a schedule subject is
  `SubjectContextResolver`: an exact `discipline_id == subject_id` binds
  silently (on live data this holds for every discipline except PE); a
  confirmed binding from `SubjectBindingStore` (DataStore, cleared on sign-out)
  wins over it; otherwise a single normalised-name match is *proposed* in an
  outlined card (`Связать` / `Нет`, nothing is stored on `Нет`), several
  matches ask which one, none reads as not found. `subjectNameKey` is the one
  normalisation, shared with the BARS merge.

Links are observed separately from lessons, so a lesson update never drops an
asynchronously loaded link snapshot.

`RecordbookRepositoryImpl` is a singleton with a memory cache of the last
programs, subjects per `(programId, semester)` and controls per entry
(`cachedPrograms`, `cachedSubjects`, `cachedControls`, cleared on sign-out as a
`SessionDataCleaner`). Both the root and the subject page seed
`Content(refreshing = true)` from it before the network answers, so a screen
opened a second time never shows the skeleton; the sport card waits for the
refresh.

A failed refresh keeps the page and shows a snackbar with `Повторить`; a failed
vote on a link says why in a short snackbar (`RecordbookSubjectEvent.VoteFailed`).
The host opens links outside the app, the link sheets (`Все ссылки`, `Добавить
ссылку`, a long press on a link), `Мои баллы`, a teacher's profile and the BARS
sign-in through `RecordbookSubjectExits`, and refreshes the page after a
completed sign-in.

## Tests

Unit tests cover the grade scale and next-step hints (`RecordbookGradeScaleTest`),
control groups (`RecordbookControlGroupsTest`), the subject context resolver, the
binding store and every subject-page state of `RecordbookSubjectViewModel`,
including links, chats and `Все пары`. Host tests run the stateless page with
synthetic data (`RecordbookSubjectScreenTest`, `SubjectHubSectionsTest`,
`SubjectHubItemsTest`: rows, states, retries, snackbars, `Все пары` opening in
place, the link picker, touch targets); the Roborazzi goldens are
`RecordbookSubjectScreen_{session,credit,sport,bars,sheet,offer,binding,loading,error}`
and the sections' `Subject*Preview*`, with the samples in
`RecordbookSubjectPreviewSamples`.

## iOS

The iOS app hosts the same `RecordbookSubjectRoute` for
`AppRoutes.RecordbookSubject`, pushed on the recordbook stack:
`RecordbookSubjectView` (`iosApp/Sources/Features/Recordbook/`) through
`recordbookSubjectPage` (`shared/ios`, `screens/RecordbookScreens.kt`); the
ViewModel reads the page's arguments from a `SavedStateHandle` the iOS route
builds (`RecordbookSubjectIosRoute`). The page draws its own header with
`К зачётке`, and the edge swipe goes back too.

- Exits: a teacher with a usable ISU opens the person profile on the same
  stack; the connected sheet and an LMS page open in the system browser
  (`link_open_failed` when nothing takes them); `Изменить итог` opens
  [`Мои баллы`](sheet-scores.md#ios); `Войти в БАРС` of a BARS snackbar opens the BARS
  sign-in sheet, after which the page loads again.
- Links: `PlatformCapabilities.reviews` is on since IO-09f, so the route gets
  `linksEnabled = true` and shows `Ссылки` and `Чаты` as on Android; a link's
  actions (a long press), `Все ссылки` and the editor open as SwiftUI sheets
  ([subject links](resources.md#ios)), and the teacher rows carry their tones.
  A platform without links gets `linksEnabled = false`: `subjectHubItems` keeps
  only the course's LMS page under `Ссылки` and drops `Чаты`.
- Tests: `SubjectHubItemsTest` covers `linksEnabled = false`;
  `RecordbookUITests` opens the demo's algorithms page with its control points,
  sheet total, links and chats; `ReviewsLinksUITests` the links sheets.
