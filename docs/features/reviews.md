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
- `feature/reviews/data`: `TeacherReviewsRepositoryImpl`, the Core mapping,
  `TeacherLevelsRepositoryImpl` and `TeacherLevelsFileStore`.
- `core/ui/TeacherLevelTone.kt`: the dot colour and words of each tone and
  `ImageView.bindLevel`, shared by the profile, the lesson sheet and the subject
  page.
- `feature/reviews/presentation`: `ReviewEditorViewModel`, `ReportReviewViewModel`.
- `feature/reviews/ui`: `ReviewEditorBottomSheet`, `ReportReviewDialogFragment`.

The profile (`feature/social`) opens both through `AppNavigator.openReviewEditor`
and `openReviewReport`, so the two features never import each other.

## Connection and cache

`TeacherReviewsRepositoryImpl` checks the custom-services opt-in before every
read and mutation. Without it the repository immediately returns
`AppError.CustomServicesDisabled` and makes no request. Its per-ISU memory cache
is hidden while the opt-in is off or unknown and cleared when it is disabled or
the session is cleared. It is a singleton `SessionDataCleaner` so the cleaner,
the profile and the editor share one instance.

The opt-in observer runs in `ApplicationScope`. A local generation and a short
atomic publish check prevent requests or opt-in reads from an older connection
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
Invalid input fails with `AppError.Unknown` without a request; a Backend
`restricted` answer is `AppError.Restricted`.

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

## AI summary

Backend builds the summary from active Reviews copies and published own reviews
that passed the ISU check, once a teacher has at least three of them
([Backend](../../../itmo-widgets-backend/docs/ops/ai-summaries.md)). The app
shows what Backend returns and decides nothing about it: a hidden summary or one
of a teacher with fewer reviews is simply `null`, and until Backend builds a new
summary the previous one is shown with its own count, which may differ from
`Отзывы · N`.

The card (`item_teacher_summary.xml`, `Widget.ItmoWidgets.Card.Content.Tonal`)
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
- tag chips (`item_summary_tag_chip.xml`, 28 dp, not clickable), hidden
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
  `filesDir/teacher_levels/levels.json` (format 1, atomic writes, excluded from
  backup and device transfer) for a day, and asks Backend only for missing or
  older teachers, in sorted batches of 50
  (`GET /api/teachers/summary-levels`). Only ISU numbers in
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
  ([recordbook](recordbook.md#subject-page)).

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
above the first of them. Every row ends with the same bottom edge: a footer
brings its 48 dp row, a row that ends with its text or a rejection reason gets
16 dp of padding.

The own review (`item_own_teacher_review.xml`):

- the `мой` badge and a status pill while it is not public: `На проверке` in
  `colorTertiary`, `Отклонён` or `Скрыт` in `colorError`, each on a 12 % wash of
  its tone; `⋮` at the end with `Изменить` (the editor) and `Удалить`
  (confirmed by `Удалить отзыв?`; afterwards `Написать` returns);
- `<subject>, анонимно` or `, с вашим именем`;
- `Причина: <note>` in `colorError` for a rejected review;
- the full text and, once published, a footer with `Вёл у вас` or a muted
  `Не подтверждён` and the read-only score in the vote pill.

Every other review is a row in three zones (`item_teacher_review.xml`):

- the caption `<subject>, <date>` in at most two lines with `⋮` at the end;
- the full text, never truncated;
- the footer (`ReviewFooterLayout`): who wrote it at the start, a named
  author as a link, `Фамилия И. О.` (`labelLarge`, initials joined by a
  no-break space, the full name as content description), which opens that
  person's profile; a copy's source as a muted link with `ic_open_in_new`
  (`Reviews` or the source title such as `Google-форма`, TalkBack
  `Источник: Reviews, <source>`), which opens it; otherwise `Анонимный отзыв`.
  Then the `Вёл у автора` pill (`view_review_verified.xml` with
  `ic_check_small`) or a muted `Не подтверждён`, and the vote pill `▲ N ▼`
  (`view_link_vote_pill.xml`, `core/ui` `bindVotes`) flush at the end. The
  source wraps inside the room left of the votes; the verification follows it
  on the same line when it fits, otherwise on a line of its own below; the
  votes stay centred on the first line.

Votes show arrows only with `canVote`; the score turns to the accent once the
viewer voted, and without arrows a zero score is left out. While the profile is open
the others' reviews keep the order they were first shown in
(`core/util/StableOrder` in `UserProfileViewModel`): Backend's answer to a vote
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

`ReviewEditorBottomSheet` (`sheet_review_editor.xml`) writes a new review or
edits the own one:

- The title is `Новый отзыв` or `Изменить отзыв`, with the teacher's short name
  on a second line; a close button stands next to it. The sheet has no handle,
  cannot be dragged or closed by a tap outside, and opens at its content height.
- `Предмет` is optional and wraps up to three lines. Below it a row of filter
  chips (`item_review_subject_chip.xml`) suggests the subjects of the viewer's
  own academic lessons with this teacher, newest first. A chip fills the field;
  the chip matching the field shows as picked, and tapping it again clears it.
- `Отзыв` has a counter to 3000. While the text is shorter than 30 characters
  the helper says `Не короче 30 символов`; on send the same line becomes the
  error. Longer than 3000, or a subject longer than 200, is an error on send.
- `Анонимно` is on by default; turning it off shows `Имя будет видно всем`.
- The button is `Отправить` for a new review and `Сохранить` for an edit,
  disabled while the text is blank or a save runs. A success closes the sheet,
  a failure shows a snackbar and keeps it.

Back or the close button with changes asks `Не сохранять отзыв?`
(`Не сохранять` / `Отмена`); without changes it simply closes. Fields, the
initial values and the mode live in `SavedStateHandle`, so the text survives
recreation and process death; the first opening starts from the cached own
review. The flows of the viewer's lessons with the teacher go into the draft as
`flowIds`, candidates for Backend's check. Suggestions and flows grow as the
schedule weeks answer; a save does not wait for the history and sends the flows
collected by then, and an unavailable history leaves no suggestions without an
error.

## Report

`ReportReviewDialogFragment` (`dialog_report_review.xml`) is `Жалоба на отзыв`
with the reasons `Оскорбления`, `Не тот преподаватель`, `Спам`, `Другое` and an
optional `Комментарий` of up to 500 characters. `Отправить` is enabled once a
reason is chosen. A success closes the dialog and the report entry disappears
from the review; a failure shows its text under the comment and keeps the
dialog.

## Verification

Backend decides «taught the author» through ISU flows; the app only sends
candidate flows and shows `verified`. `PENDING` and `UNVERIFIED` look the same
(`Не подтверждён`), a review is never rejected because of ISU, and the check
does not affect the order.

## Not implemented

Handing ITMO.Widgets reviews over to the Reviews project is separate work.

## Tests

`TeacherReviewsRepositoryImplTest` covers mapping of both kinds and of the
summary (unknown tags, blank points, `LOW` confidence), opt-in and no-network
behavior, input checks before the network, mutation routes, the cache and
`observeUpdates()` after opt-out and session clear, errors and cancellation.
`TeacherLevelsRepositoryImplTest` and `TeacherLevelsFileStoreTest` cover the
levels: the opt-in, the day cache, batches, the ISU range, failures and answers
after the opt-in was switched off. `ReviewEditorViewModelTest` and `ReportReviewViewModelTest` cover
the forms, restoration, suggestions growing with the history, saving with
partial flows and validation; `ProfileReviewsTest`,
`UserProfileStateTest` and `UserProfileViewModelTest` the section, `Написать`,
votes, deletion, updates, the summary and its collapsed scales kept in the saved
state. `UserProfileVisualTest` (with
`UserProfilePreviewActivity`) and `ReviewEditorVisualTest` (with
`ReviewEditorPreviewActivity`, debug only) cover the reviews group with the
own review first, statuses, votes in the pill, reports, author navigation, long names, 20
recycled reviews, the summary card in every state (tone, low confidence, empty
blocks, long texts at 320 dp and font 1.3, every tone, collapsed and expanded
scales across recreation, a late answer), the editor and the report dialog in
the full appearance matrix. `LessonDetailsVisualTest` and
`RecordbookVisualTest` cover the tone dots. Use the [visual test commands](../design.md#running-the-visual-tests)
and inspect the saved PNGs.
