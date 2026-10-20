# Teacher reviews

Product behaviour: [Как это работает: отзывы](../how-it-works/reviews.md).

Reviews of a teacher are a section at the bottom of the
[person profile](social.md#person-profile); there is no separate teacher screen.
One list holds own reviews of ITMO.Widgets users and anonymous copies from the
Reviews project, in Backend's order, under Backend's AI summary. The summary's
tone also appears as a dot next to the teacher in the lesson sheet and on the
subject page. The
[Backend contract](../../../itmo-widgets-backend/docs/contracts/teacher-reviews.md)
defines the routes, limits, premoderation and the ISU check.

The editor and the report are `AppRoutes.ReviewEditor` (a form sheet) and
`AppRoutes.ReportReview` (a dialog) with `TeacherReviewArgs`; the profile opens
them through its route's exits, so the social and reviews features never import
each other. Own lessons with a teacher come from `core/schedule/TeacherLessonsGateway`
([schedule](schedule.md#lessons-with-a-teacher)).

## Connection and cache

- Both repositories check `DemoMode` first and pass `BackendGate.mayCallBackend()`
  on every call; without the opt-in `TeacherReviewsRepositoryImpl` returns
  `AppError.CustomServicesDisabled` with no request.
- Its per-ISU memory cache (one instance, a `SessionDataCleaner`) is hidden
  while the opt-in is off or unknown and cleared when it is disabled or the
  session is cleared. A local generation, changed under one `Mutex`, keeps
  answers from an older connection (an off/on cycle included) from refilling a
  cleared cache. Cancellation is propagated, not turned into a display error.
- Every mutation (`save`, `delete`, `vote`, `report`) answers with the teacher's
  fresh reviews; the answer replaces the cache entry and is emitted on
  `observeUpdates()`, both only while its generation is current.
- Input is checked before any request, as Backend checks it: text after
  `\r\n` -> `\n` and `trim()` of 30-3000 characters, subject up to 200, lengths
  in code points, at most 50 positive `flowIds` (sorted), a UUID review id, a
  vote of -1, 0 or 1, a report comment up to 500. Invalid input fails with
  `AppError.Unknown` without a request.
- Errors map through `BackendException.asAppError()`: 401 -> `Unauthorized`, 403
  `restricted` -> `Restricted`, other 403 -> `Forbidden`, 404 -> `NotFound`, no
  answer -> `Network`, anything else (409, contract violations) -> `Unknown`.

## Mapping

`TeacherReviewMappers.kt` is the one place that reads Core's review types.

- Backend's order (score, then date, own reviews before copies) is kept; the
  viewer's own review comes separately in `mine`.
- Blank review text is dropped; optional subject and source are trimmed and
  blank becomes absent.
- `writtenOn` -> `ReviewDate.Month(YearMonth)` (shown `LLLL yyyy`, Russian,
  capitalised; for an own review the month the shown version was sent);
  `writtenBeforeYear` -> `ReviewDate.BeforeYear`.
- `COMMUNITY` -> `ReviewOrigin.Community(verified, author, reportedByMe)`;
  `author` is set only for a review under the name. `REVIEWS` ->
  `ReviewOrigin.Reviews(sourceTitle, sourceUrl)`: a valid HTTPS source (host
  present, no user info) or else
  `https://onetwozzzplus.github.io/reviews/#/teacher/{isu}`. `PlatformActions.openLink`
  makes the final navigation-policy check.
- `summary` -> `TeacherSummary` or `null`: unknown tags skipped, repeats and
  blank pros, cons and reasons dropped, a blank description means no summary.
  `showsLevel` is true for confidence `MEDIUM` or `HIGH`.
- Unknown values from a newer Backend never fail: an unknown review kind is not
  shown, an unknown own status shows as `PENDING`, an unknown scale kind or value
  is left out, an unknown tone or confidence hides the tone (`LOW`); in levels an
  unknown tone is stored as no level.

## AI summary

Backend builds the summary from active Reviews copies and published own reviews
that passed the ISU check, once a teacher has at least three
([Backend](../../../itmo-widgets-backend/docs/ops/ai-summaries.md)). The app
decides nothing: a hidden summary is `null`, and until Backend rebuilds it the
previous one is shown with its own count, which may differ from the section's.
`TeacherSummaryCard` comes right under the section heading; its texts have
links switched off. Whether the five scales are expanded belongs to that
teacher's profile (`UserProfileViewModel`, `SavedStateHandle`): it survives
recreation and process death; another profile starts collapsed.

## Teacher levels

`TeacherLevelsRepository.levels(isus)` returns the tone of teachers whose shown
summary has confidence `MEDIUM` or `HIGH`; others are absent. The
implementation:

- is gated by the opt-in: without it the answer is empty, the cache is deleted
  and nothing is sent;
- keeps Backend's answers, including "no level", in
  `filesDir/teacher_levels/levels.json` (format 1, atomic writes, excluded from
  backup and device transfer) for a day, and asks only for missing or older
  teachers, in sorted batches of 50 (`GET /api/teachers/summary-levels?isu=a&isu=b`);
- sends only ISUs in `100000..9999999` (Backend would reject the whole batch;
  such a teacher has no summary anyway);
- serializes calls with a mutex, so screens asking at once send one request;
- on a failed batch writes nothing and returns the fresh cached part: a level
  is decoration, never a screen error. Answers arriving after sign-out or
  opt-out are dropped;
- is a `SessionDataCleaner`; a corrupt file is deleted.

The dot is decorative; its row adds the tone to its TalkBack description. It
stands in the summary card; the teacher rows of the lesson sheet ([schedule](schedule.md#lesson-sheet)) and the
subject page ([subject page](subject-page.md)) say the tone in words instead (`TeacherLevelTone.words`).

## The profile section

- The section exists while connected when there is at least one review, an own
  review or `Написать`.
- `Написать` needs Backend's `canWrite`, no own review yet and a person who
  teaches: Backend's `knownTeacher` (teaches a loaded academic pair, a cached ISU
  flow, has a Reviews copy or a published review; a room booking does not count)
  or any My ITMO position.
- The heading count includes the own review. The own review is
  `OwnTeacherReviewRow`; others are `TeacherReviewRow` in one connected group
  ([design](../design.md#connected-groups)).
- While the profile is open, others' reviews keep their first-shown order
  (`core/presentation/StableOrder`): a vote answer or an editor update changes
  scores in place, a new review follows the shown ones; a new screen or retry
  takes Backend's order again.
- Vote thumbs only with `canVote`; tapping the current vote takes it back. One
  vote or deletion at a time; a failure (for example `AppError.Restricted`)
  shows a snackbar and keeps the section.
- `Пожаловаться` only on another user's own review with `canReport` that the
  viewer has not reported; copies have no report. Otherwise the menu stays
  invisible in place, so rows keep one rhythm.

## Editor

`ReviewEditorSheet` writes a new review or edits the own one.

- A form sheet: no drag or outside-tap dismissal; Back or close with changes
  asks before discarding.
- Subject suggestions are the subjects of the viewer's own academic lessons with
  this teacher, newest first; their flows go into the draft as `flowIds`,
  candidates for Backend's check. Suggestions and flows grow as schedule weeks
  answer; a save does not wait and sends the flows collected so far; an
  unavailable history gives no suggestions and no error.
- Fields, initial values and mode live in `SavedStateHandle` (text survives
  process death); the first opening starts from the cached own review.
- One save at a time; success closes, failure keeps the sheet with a snackbar.

## Report

`ReportReviewForm`: a reason is required, the comment is optional (up to 500);
both survive recreation. Not dismissable while sending.

## Verification of authors

Backend decides "taught the author" through ISU flows; the app only sends
candidate flows and shows `verified`. `PENDING` and `UNVERIFIED` look the same,
a review is never rejected because of the ISU check, and the check does not
affect the order.

## iOS

The iOS app ([iOS app](../ios.md)) loads `reviewsModule` with
`reviewsIosModule` and turns `PlatformCapabilities.reviews` on. What differs:

- The editor is a full-height SwiftUI form sheet over `ReviewEditorViewModel`
  (`iosApp/Sources/Features/Reviews/ReviewEditorSheet.swift`): a drag does not
  close it, the close button asks before unsaved changes are dropped (an alert);
  a failed save shows as a banner.
- The report (`AppRoutes.ReportReview`) is a SwiftUI form sheet over `ReportReviewViewModel`
  (`iosApp/Sources/Features/Reviews/ReportReviewSheet.swift`); a failure shows under the comment.
- The demo shows `DemoReviews` and refuses every change
  (`error_demo_unavailable`) with no request.
