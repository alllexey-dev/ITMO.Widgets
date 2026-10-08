# Teacher reviews

Reviews of a teacher appear at the bottom of the
[person profile](social.md#person-profile-overlay-user_profile-argument-userscreenargsisu);
there is no separate teacher screen and no «My reviews» screen. One list holds
own reviews of ITMO.Widgets users and anonymous copies from the Reviews
project, in Backend's order, under Backend's AI summary of them. With
`Подключение к ITMO.Widgets` a user writes one review per teacher, edits and
deletes it, votes on others' reviews and reports them. The tone of the summary
also appears as a coloured dot next to the teacher's name in the lesson sheet
and on the subject page. The
[Backend contract](../../../itmo-widgets-backend/docs/contracts/teacher-reviews.md)
defines the routes, limits, premoderation and the ISU check.

## Packages

- `core/reviews`: the `TeacherReviewsRepository` contract and the models
  `TeacherReviews`, `TeacherReview` with `ReviewOrigin` (`Community` or
  `Reviews`), `OwnTeacherReview` with `OwnReviewStatus`, `ReviewReportReason`,
  `TeacherReviewDraft`, `TeacherReviewLimits` and `ReviewDate`;
  `TeacherSummary.kt` with `TeacherSummary`, `SummaryScale`, `TeacherLevel`,
  `SummaryConfidence`, `SummaryScaleKind`, `SummaryScaleValue` and `SummaryTag`;
  the `TeacherLevelsRepository` contract.
- `core/schedule/TeacherLessons.kt`: `TeacherLessonsGateway`, the viewer's own
  lessons with a teacher ([schedule](schedule.md#lessons-with-a-teacher)).
- `core/navigation/TeacherReviewArgs.kt`: the teacher ISU and full name for the
  editor and the report dialog.
- `feature/reviews/data` (`:shared:feature-reviews` `commonMain`, package
  `dev.alllexey.itmowidgets.feature.reviews.data`): `TeacherReviewsRepositoryImpl`, the Core mapping,
  `TeacherLevelsRepositoryImpl` and `TeacherLevelsFileStore`; Koin's `reviewsModule` builds one instance of each
  repository, and sign-out reaches them as the `reviews` and `teacher-levels` cleaners.
- `core/ui/TeacherLevelTone.kt`: the dot colour and words of each tone and
  `ImageView.bindLevel`, shared by the profile, the lesson sheet and the subject
  page.
- `feature/reviews/presentation`: `ReviewEditorViewModel`, `ReportReviewViewModel`.
- `feature/reviews/ui` (`:shared:feature-reviews` `commonMain`): `ReviewEditorSheet.kt` with the editor's
  `ReviewEditorSheet` (the hosts' entry) and its stateless `ReviewEditorSheetContent`, and `ReportReviewDialog.kt`
  with `ReportReviewForm` (the entry) over `ReportReviewDialog` on the kit's report dialog; previews and goldens of
  both live there.
- `feature/reviews/ui` (`:app`): the Fragment hosts `ReviewEditorBottomSheet` (on the kit's
  `ItmoBottomSheetFragment`) and `ReportReviewDialogFragment`; they keep their class names, tags and `newInstance`
  and perform the effects: closing, the snackbar of a failed save.

The profile (`feature/social`) opens both through `AppNavigator.openReviewEditor`
and `openReviewReport`, so the two features never import each other.

## Connection and cache

`TeacherReviewsRepositoryImpl` and `TeacherLevelsRepositoryImpl` call Core
2.0's `TeacherReviewsApi` (`BackendClient.reviews`: `teacherReviews`,
`saveMyTeacherReview`, `deleteMyTeacherReview`, `voteTeacherReview`,
`reportTeacherReview`, `teacherSummaryLevels`). `DemoMode` is checked first and
every call passes `BackendGate.mayCallBackend()`.
`TeacherReviewsRepositoryImpl` checks the custom-services opt-in before every
read and mutation. Without it the repository immediately returns
`AppError.CustomServicesDisabled` and makes no request. Its per-ISU memory cache
is hidden while the opt-in is off or unknown and cleared when it is disabled or
the session is cleared. It is a single `SessionDataCleaner` so the cleaner,
the profile and the editor share one instance.

The opt-in observer runs in the application `CoroutineScope`. The cache is one immutable snapshot (the opt-in, a
local generation and the entries) that changes only under one `Mutex`, so the non-suspend `cachedReviews` reads it
without a lock. The generation and the publish check under that lock prevent requests or opt-in reads from an older connection
from refilling a cleared cache, including an off/on cycle. Cancellation is
propagated, not converted into a display error.

Every mutation (`save`, `delete`, `vote`, `report`) answers with the fresh
reviews of the teacher. The answer replaces the cache entry for its
`teacherIsu` and is emitted on `observeUpdates()`, both only while the
generation it was sent in is current, so every open profile of that teacher
updates without a reload. Input is checked before any request, as Backend
checks it: text after `\r\n` → `\n` and `trim()` of 30–3000 characters, a
subject up to 200, lengths in code points, at most 50 positive `flowIds`
(sorted), a UUID review id, a vote of -1, 0 or 1 and a comment up to 500.
Invalid input fails with `AppError.Unknown` without a request. Backend errors
map through `BackendException.asAppError()`: 401 -> `Unauthorized`, 403
`restricted` -> `Restricted`, any other 403 -> `Forbidden`, 404 -> `NotFound`,
no answer -> `Network`, anything else, including 409 and an answer that breaks
the contract, -> `Unknown`.

## Mapping

- Backend's order (score, then date, own reviews before copies) is kept; the
  viewer's own review comes separately in `mine`.
- Blank review text is dropped. Optional subject and source strings are
  trimmed, blank values become absent, and the subject remains free text.
- `writtenOn` becomes `ReviewDate.Month(YearMonth)` and is displayed as
  `Январь 2025` (`LLLL yyyy`, Russian, capitalized); for an own review it is the
  month the shown version was sent. `writtenBeforeYear` becomes
  `ReviewDate.BeforeYear`, displayed as `До 2024`. With neither there is no date.
- A `COMMUNITY` review becomes `ReviewOrigin.Community(verified, author,
  reportedByMe)`; `author` is a `UserSummary` only when the review is written
  under the name. A `REVIEWS` copy becomes `ReviewOrigin.Reviews(sourceTitle,
  sourceUrl)`: a valid HTTPS source link (host present, no user info) or else
  the provider's teacher page,
  `https://onetwozzzplus.github.io/reviews/#/teacher/{isu}`. `core/ui/LinkOpener`
  performs the final navigation-policy check.

- `summary` becomes `TeacherSummary` or `null`. Tags this app does not know
  are skipped and repeats dropped, blank pros, cons and reasons are dropped, and
  a summary with a blank description is no summary. `showsLevel` is true for
  confidence `MEDIUM` or `HIGH`.
- Core 2.0 decodes a value a newer Backend adds as `UNKNOWN`; the app never
  fails on one. A review of an unknown kind is not shown, an unknown own status
  shows as `PENDING`, a scale of an unknown kind or value is left out (its row
  reads `мало данных`), and an unknown tone or confidence keeps the summary with
  its tone hidden (`LOW`). In the levels an unknown tone is stored as no level.
- `TeacherReviewMappers.kt` is the one place that reads Core's reviews types;
  authors map through `UserData.toUserSummary()`
  (`core/model/ClientUserMapping.kt`), and `ReviewReportReason` maps by name.

## AI summary

Backend builds the summary from active Reviews copies and published own reviews
that passed the ISU check, once a teacher has at least three of them
([Backend](../../../itmo-widgets-backend/docs/ops/ai-summaries.md)). The app
shows what Backend returns and decides nothing about it: a hidden summary or one
of a teacher with fewer reviews is simply `null`, and until Backend builds a new
summary the previous one is shown with its own count, which may differ from
`Отзывы · N`.

The card (`TeacherSummaryCard` in `:shared:feature-social`, a tonal content card on `surfaceContainerLow`)
comes first in the section, right under the heading and before the own review,
with the usual 8 dp gap after it. Its texts are plain, with links switched off.
From top to bottom:

- `auto_awesome` and `Сводка по N отзывам` (`titleSmall`, plurals), with a
  muted `ИИ` at the end; TalkBack reads `Сводка по N отзывам, составлена ИИ`;
- the tone row, only when `showsLevel`: the dot and the words
  (`В основном отрицательные`, `Скорее отрицательные`, `Смешанные`,
  `Скорее положительные`, `В основном положительные`), read as
  `Тон отзывов: …`;
- the description;
- pros and cons as rows with `add` and `remove` icons, each block read as
  `Плюсы: …` or `Минусы: …` and hidden when empty;
- tag chips (28 dp, not clickable), hidden
  without tags: `Автомат`, `Много лаб`, `Много домашки`, `Частые контрольные`,
  `Строгий на защите`, `Мягкий на защите`, `Сложный экзамен`, `Лёгкий экзамен`,
  `Спрашивает теорию`, `Жёсткие дедлайны`, `Гибкие дедлайны`,
  `Важна посещаемость`, `Свободное посещение`, `Доп. баллы`,
  `Чёткие требования`, `Размытые требования`, `Интересные занятия`,
  `Читает по слайдам`, `Быстро отвечает`, `Сложно связаться`;
- the five scales, collapsed by default behind the text button `Подробнее`
  (`Свернуть` when open, with a chevron, a 48 dp target and the TalkBack state
  `свёрнуто` or `развёрнуто`). Each scale is a row `name … value` with its reason
  below: `Объясняет` плохо/средне/хорошо, `Отношение к студентам`
  плохое/нейтральное/хорошее, `Справедливость оценок`, `Строгость` and
  `Нагрузка` низкая/средняя/высокая, and a muted `мало данных` without a reason.

Opening the scales only grows the card: nothing above it moves. Whether they
are open belongs to the profile of that teacher (`UserProfileViewModel`,
`SavedStateHandle`), so it survives rebinding, new reviews, recreation and
process death, and another profile starts collapsed.

## Teacher levels

`TeacherLevelsRepository.levels(isus)` returns the tone of teachers whose shown
summary has confidence `MEDIUM` or `HIGH`; others are absent. The
implementation:

- is gated by the custom-services opt-in: without it the answer is empty, the
  cache is deleted and nothing is sent;
- keeps Backend's answers, including «no level», in
  `filesDir/teacher_levels/levels.json` (format 1, kotlinx JSON that 2.2 also
  reads, atomic writes, excluded from backup and device transfer) for a day, and asks Backend only for missing or
  older teachers, in sorted batches of 50
  (`GET /api/teachers/summary-levels?isu=a&isu=b`, one repeated `isu`
  parameter per teacher). Only ISU numbers in
  `100000..9999999` are sent; Backend would reject a whole batch with another
  one, and such a teacher has no summary anyway;
- serializes calls with a mutex, so screens asking at once send one request;
- on a failure of any batch writes nothing and returns the fresh cached part;
  a level is decoration, never a screen error. An answer that arrives after a
  sign-out or after the opt-in was switched off is dropped;
- is a `SessionDataCleaner`, and a corrupt file is deleted.

The dot (`bg_teacher_level_dot.xml`, 10 dp) is decorative; the row that carries
it adds `, тон отзывов: …` to its TalkBack description. Where it stands:

- the summary card's tone row;
- the teacher row of the lesson sheet ([schedule](schedule.md#lesson-details));
- the teacher rows of the subject page
  ([subject page](subject-page.md)).

## The profile section

The section exists while connected when there is at least one review, an own
review or `Написать`. `Написать` needs `canWrite` from Backend, no own review
yet and a person who teaches: Backend's `knownTeacher` (the person teaches a
loaded academic pair, a cached ISU flow, has a Reviews copy or a published
review; a room booking, which names the person who booked it, does not count)
or any position of the My ITMO person. The heading is `Отзывы` in
`colorPrimary` with the count after it (TalkBack `Отзывы, N`, counting the own
review too), or `Отзывы` without reviews; `Написать` is a text button with
`ic_edit` at its end. The own review is a group of its own with all four
corners rounded; the others' reviews follow 16 dp below as one connected group
([design](../design.md#connected-groups)); the AI summary card stands 8 dp
above the first of them. The rows sit on `colorSurfaceContainerLow`. Nothing
ends flush with a row's edge: a footer row keeps 12 dp under it, a row that
ends with its text or a rejection reason 16 dp.

The own review (`OwnTeacherReviewRow`):

- the `мой` badge and a status pill while it is not public: `На проверке` in
  `colorTertiary`, `Отклонён` or `Скрыт` in `colorError`, each on a 12 % wash of
  its tone; `⋮` at the end with `Изменить` (the editor) and `Удалить`
  (confirmed by `Удалить отзыв?`; afterwards `Написать` returns);
- `<subject>, анонимно` or `, с вашим именем`;
- `Причина: <note>` in `colorError` for a rejected review;
- the full text and, once published, the footer of the others' reviews: `Вёл
  у вас` or a muted `Не подтверждён` on the left, the read-only score in the
  vote pill on the right.

Every other review is a row in three zones (`TeacherReviewRow`):

- the caption `<subject>, <date>` in at most two lines with `⋮` at the end;
- the full text, never truncated;
- the footer: on the left who wrote it in `bodyMedium`, a named author as a
  link in `colorPrimary`, `Фамилия И. О.` (initials joined by a no-break space,
  the full name as content description), which opens that person's profile; a
  copy's source as a muted link with `ic_open_in_new` (`Reviews` or the source
  title such as `Google-форма`, TalkBack `Источник: Reviews, <source>`), which
  opens it; otherwise a muted `Анонимный отзыв`. Under it the verification in
  `bodySmall` without a chip: `Вёл у автора` with a 16 dp `ic_check` in
  `colorPrimary`, or `Не подтверждён` in `colorOnSurfaceVariant`. The links
  keep 48 dp targets; the line under them tucks into their padding. On the
  right the vote pill `▲ N ▼` (the kit's `VotePill`), centred on the left
  column.

Votes show arrows only with `canVote`; the score turns to the accent once the
viewer voted, and without arrows a zero score is left out. While the profile is open
the others' reviews keep the order they were first shown in
(`core/presentation/StableOrder` in `UserProfileViewModel`): Backend's answer to a vote
or an update from the editor changes the scores in place, a review not shown
yet follows the shown ones, and a new screen or `Повторить` takes Backend's
order again. Tapping the arrow of
the current vote takes the vote back. One vote or deletion runs at a time: the
row's controls are disabled until the answer replaces the section; a failure
(for example `AppError.Restricted`) shows a snackbar and keeps the section.
`⋮` holds `Пожаловаться` only on an own review of another user with `canReport`
that the viewer has not reported yet; otherwise it stays invisible in place, so
the rows keep one rhythm. Copies have no report. A negative score is written
with a typographic minus in the error colour.

## Editor

`ReviewEditorBottomSheet` hosts `ReviewEditorSheet` and writes a new review or
edits the own one:

- The title is `Новый отзыв` or `Изменить отзыв`, with the teacher's short name
  on a second line; a close button stands next to it. The sheet has no handle,
  cannot be dragged or closed by a tap outside, and opens at its content height.
- `Предмет` is optional and wraps up to three lines. Below it a row of filter
  chips suggests the subjects of the viewer's
  own academic lessons with this teacher, newest first. A chip fills the field;
  the chip matching the field shows as picked, and tapping it again clears it.
- `Отзыв` has a counter to 3000. While the text is shorter than 30 characters
  the helper says `Не короче 30 символов`; on send the same line becomes the
  error. Longer than 3000, or a subject longer than 200, is an error on send.
- `Анонимно` is on by default; turning it off shows `Имя будет видно всем`.
- The button is `Отправить` for a new review and `Сохранить` for an edit,
  pinned under the scrolling form, so the keyboard never hides it; it is
  disabled while the text is blank and shows a progress indicator while a save
  runs, which takes no second tap. A success closes the sheet, a failure shows
  a snackbar and keeps it.

Back or the close button with changes asks `Не сохранять отзыв?`
(`Не сохранять` / `Отмена`); without changes it simply closes. Fields, the
initial values and the mode live in `SavedStateHandle`, so the text survives
recreation and process death; the first opening starts from the cached own
review. The fields start from the view model's state, so restored text comes
back with the cursor at its end; the sheet keeps no second saved copy. The
flows of the viewer's lessons with the teacher go into the draft as
`flowIds`, candidates for Backend's check. Suggestions and flows grow as the
schedule weeks answer; a save does not wait for the history and sends the flows
collected by then, and an unavailable history leaves no suggestions without an
error.

## Report

`ReportReviewDialogFragment` hosts `ReportReviewForm` on the kit's report
dialog, which brings its own window; the Fragment's own dialog only anchors it.
It is `Жалоба на отзыв` with the reasons `Оскорбления`, `Не тот преподаватель`,
`Спам`, `Другое` and an optional `Комментарий` of up to 500 characters; the
chosen reason and the comment survive recreation. `Отправить` is enabled once a
reason is chosen and shows a progress indicator while the report is sent; back
and a tap outside close the dialog only while nothing is being sent. A success
closes the dialog and the report entry disappears from the review; a failure
shows its text under the comment and keeps the dialog.

## iOS

The iOS app (IO-09f, [iOS app](../ios.md)) loads `reviewsModule` with
`reviewsIosModule` (Core 2.0's reviews area from the one `BackendClient`; the
teacher's lessons from `scheduleDataModule`) and turns
`PlatformCapabilities.reviews` on, so the person profile shows a teacher's
reviews section and the schedule and subject page their teacher tones, as on
Android.

- Editor: `Написать` and an own review's edit open `AppRoutes.ReviewEditor` as a
  SwiftUI sheet at full height hosting `ReviewEditorSheet`
  (`reviewEditorViewController`, `shared/ios` `screens/ReviewsScreens.kt`;
  `iosApp/Sources/Features/Reviews/ReviewEditorSheet.swift`). It is a form sheet:
  a drag does not close it, the close button asks `Не сохранять отзыв?` over the
  form when it has unsaved changes. A failed save shows as a banner over the
  sheet.
- Report: `Пожаловаться` of a review shows the report dialog as the Compose
  dialog over the profile (`ReportReviewHosted`); `AppRoutes.ReportReview` has no
  iOS surface of its own.
- Pre-moderation, restrictions and reports are Backend's and work unchanged;
  there is no iOS-only moderation UI. The demo shows `DemoReviews` and refuses
  every change (`error_demo_unavailable`) with no request.
- Tests: `ReviewsIosModuleTest` (`scripts/ios/test.sh kn
  :shared:feature-reviews`: the graph resolves with the hosts' arguments, the
  demo answers and refuses with no request, the demo tone) and
  `ReviewsLinksUITests` on the demo session (a review's report, a new review
  refused by the demo, the discard question, the reviews at AX1).

## Verification

Backend decides «taught the author» through ISU flows; the app only sends
candidate flows and shows `verified`. `PENDING` and `UNVERIFIED` look the same
(`Не подтверждён`), a review is never rejected because of ISU, and the check
does not affect the order.

## Not implemented

Handing ITMO.Widgets reviews over to the Reviews project is separate work.

## Tests

`TeacherReviewsRepositoryRemoteTest` runs both repositories over the real Core
2.0 client and a MockEngine: both kinds with a summary, the routes and bodies of
save, delete, vote and report with the updated reviews, 51 teachers in two
batches with a repeated `isu`, an out-of-range ISU never sent, a failed batch
writing nothing, 401, 403, 403 `restricted`, 404, 409 and 5xx, and zero
requests without the opt-in or in the demo.
`TeacherReviewsRepositoryImplTest` covers mapping of both kinds and of the
summary (unknown tags, blank points, `LOW` confidence, unknown values), opt-in
and no-network behavior, input checks before the network, mutation routes, the cache and
`observeUpdates()` after opt-out and session clear, errors and cancellation.
`TeacherLevelsRepositoryImplTest` and `TeacherLevelsFileStoreTest` (the module's `commonTest`; the 2.2 files in
`TeacherLevels22GoldenTest`) cover the
levels: the opt-in, the day cache, batches, the ISU range, failures and answers
after the opt-in was switched off. `ReviewEditorViewModelTest` and `ReportReviewViewModelTest` cover
the forms, restoration, suggestions growing with the history, saving with
partial flows and validation; `ProfileReviewsTest`,
`UserProfileStateTest` and `UserProfileViewModelTest` the section, `Написать`,
votes, deletion, updates, the summary and its collapsed scales kept in the saved
state. In `:shared:feature-social`, `TeacherReviewRowTest`,
`TeacherSummaryCardTest`, `UserProfileScreenTest` and `UserProfileRouteTest`
cover the reviews group with the own review first, statuses, votes in the
pill, reports, author navigation, deletion after `Удалить отзыв?`, a late
answer and the summary card (tone, low confidence, empty blocks, the scales
toggle); `SocialScreenshotTest` records the rows, the summary card (collapsed,
expanded, sparse, long texts) and the profile in four appearances.
`ReviewEditorSheetTest` and `ReportReviewDialogTest` (the module's `androidHostTest`, on the real view models at
320 dp and font 1.3) cover the editor and the report: the new and the edited form, suggestions arriving with the
history and filling or clearing the subject, anonymity, the minimum as a hint and then the error, the discard
question, text and cursor after recreation and process death, one save at a time, `Отправить` above the keyboard,
the reasons, a failure under the comment, the comment's limit and cancel. `ReviewsHostsKoinTest` (`:app`) covers the
hosts: Koin view models, the form sheet (no tap outside, no drag, resized for the keyboard), back with and without
changes, the snackbar of a failed save, closing after a save or an accepted report. The
`ReviewEditorSheetContent_*` and `ReportReviewDialog_*` goldens of `ReviewsScreenshotTest` show every state in the
four appearances. The `LessonDetailsContent_teacher-level` goldens and
`RecordbookVisualTest` cover the tone dots. Use the [visual test commands](../design.md#running-the-visual-tests)
and inspect the saved PNGs.
