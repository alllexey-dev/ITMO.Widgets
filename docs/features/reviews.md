# Teacher reviews

Reviews of a teacher appear at the bottom of the
[person profile](social.md#person-profile-overlay-user_profile-argument-userscreenargsisu);
there is no separate teacher screen and no «My reviews» screen. One list holds
own reviews of ITMO.Widgets users and anonymous copies from the Reviews
project, in Backend's order. With `Подключение к ITMO.Widgets` a user writes
one review per teacher, edits and deletes it, votes on others' reviews and
reports them. The
[Backend contract](../../../itmo-widgets-backend/docs/contracts/teacher-reviews.md)
defines the routes, limits, premoderation and the ISU check.

## Packages

- `core/reviews`: the `TeacherReviewsRepository` contract and the models
  `TeacherReviews`, `TeacherReview` with `ReviewOrigin` (`Community` or
  `Reviews`), `OwnTeacherReview` with `OwnReviewStatus`, `ReviewReportReason`,
  `TeacherReviewDraft`, `TeacherReviewLimits` and `ReviewDate`.
- `core/schedule/TeacherLessons.kt`: `TeacherLessonsGateway`, the viewer's own
  lessons with a teacher ([schedule](schedule.md#lessons-with-a-teacher)).
- `core/navigation/TeacherReviewArgs.kt`: the teacher ISU and full name for the
  editor and the report dialog.
- `feature/reviews/data`: `TeacherReviewsRepositoryImpl` and the Core mapping.
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

## The profile section

The section exists while connected when there is at least one review, an own
review or `Написать`. `Написать` needs `canWrite` from Backend, no own review
yet and a person who teaches: Backend's `knownTeacher` (the person teaches a
loaded lesson, a cached ISU flow, has a Reviews copy or a published review) or
any position of the My ITMO person. The heading is `Отзывы · N`, counting the
own review too, or `Отзывы` without reviews; `Написать` is a text button with
`ic_edit` in the heading.

The own review comes first on an outlined card
(`Widget.ItmoWidgets.Card.Content.Own`, 1 dp `colorPrimary`):

- a status pill while it is not public: `На проверке` in `colorTertiary`,
  `Отклонён` or `Скрыт` in `colorError`, each on a 12 % wash of its tone; once
  published, `Вёл у вас` or a muted `Не подтверждён`;
- `Ваш отзыв · <subject> · анонимно` or `· с вашим именем`;
- `Причина: <note>` in `colorError` for a rejected review;
- the full text, and the read-only score only when published;
- `⋮` with `Изменить` (the editor) and `Удалить` (confirmed by
  `Удалить отзыв?`; afterwards `Написать` returns).

Every other review is one card in three zones:

- a named author on top as a link, `Фамилия И. О.` (`labelMedium`, initials
  joined by a no-break space, the full name as content description), which opens
  that person's profile; anonymous reviews and copies have no author row;
- `<subject> · <date>` in at most two lines, then the full text, never truncated;
- a bottom row (`ReviewFooterLayout`): the `Вёл у автора` pill
  (`view_review_verified.xml` with `ic_check_small`) or a muted `Не подтверждён`
  for every unverified own review, named or anonymous, or the muted
  `Reviews · <source>` link of a copy; on the right the votes
  (`view_review_votes.xml`) and `⋮`. The source and the votes share one line
  when the source fits, otherwise the source takes the full width above them.

Votes show arrows only with `canVote`; the score turns to the accent once the
viewer voted, and without arrows a zero score is left out. Tapping the arrow of
the current vote takes the vote back. One vote or deletion runs at a time: the
card's controls are disabled until the answer replaces the section; a failure
(for example `AppError.Restricted`) shows a snackbar and keeps the section.
`⋮` holds `Пожаловаться` only on an own review of another user with `canReport`
that the viewer has not reported yet; otherwise it stays invisible in place, so
the votes line up from card to card. Copies have no report.

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

An AI summary of a teacher's reviews and handing ITMO.Widgets reviews over to
the Reviews project are separate work.

## Tests

`TeacherReviewsRepositoryImplTest` covers mapping of both kinds, opt-in and
no-network behavior, input checks before the network, mutation routes, the
cache and `observeUpdates()` after opt-out and session clear, errors and
cancellation. `ReviewEditorViewModelTest` and `ReportReviewViewModelTest` cover
the forms, restoration, suggestions growing with the history, saving with
partial flows and validation; `ProfileReviewsTest`,
`UserProfileStateTest` and `UserProfileViewModelTest` the section, `Написать`,
votes, deletion and updates. `UserProfileVisualTest` (with
`UserProfilePreviewActivity`) and `ReviewEditorVisualTest` (with
`ReviewEditorPreviewActivity`, debug only) cover the mixed list with the own
review first, statuses, votes, reports, author navigation, long names, 20
recycled reviews, the editor and the report dialog in the full appearance
matrix. Use the [visual test commands](../design.md#running-the-visual-tests)
and inspect the saved PNGs.
