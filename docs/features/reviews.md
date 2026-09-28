# Teacher reviews

Older anonymous text reviews from the Reviews project appear at the bottom of
[person profiles](social.md#person-profile-overlay-user_profile-argument-userscreenargsisu).
There is no separate teacher screen. `core/reviews` contains
`TeacherReviewsRepository`, `TeacherReviews`, `ExternalTeacherReview` and
`ReviewDate`; `feature/reviews/data` owns the Core-backed repository and mapping.
The [Backend contract](../../../itmo-widgets-backend/docs/contracts/teacher-reviews.md)
defines the authenticated `GET /api/teachers/{isu}/reviews` response.

## Connection and cache

`TeacherReviewsRepositoryImpl` checks the custom-services opt-in before calling
Core. Without it the repository immediately returns
`AppError.CustomServicesDisabled` and makes no request. Its per-ISU memory cache
is hidden while the opt-in is off or unknown and cleared when it is disabled or
the session is cleared. It is a singleton `SessionDataCleaner` so the cleaner
and profile share one instance.

The opt-in observer runs in `ApplicationScope`. A local generation and a short
atomic publish check prevent requests or opt-in reads from an older connection
from refilling a cleared cache, including an off/on cycle. Cancellation is
propagated, not converted into a display error.

## Mapping and display

- Backend selects only active `REVIEWS_WORK_GD` copies, newest first. Android
  preserves that order; it does not fetch Reviews directly.
- Blank review text is dropped. Optional subject/source strings are trimmed,
  blank values become absent, and the subject remains free text.
- `writtenOn` becomes `ReviewDate.Month(YearMonth)` and is displayed as
  `Январь 2025` (`LLLL yyyy`, Russian, capitalized). `writtenBeforeYear` becomes
  `ReviewDate.BeforeYear`, displayed as `До 2024`. With neither there is no date.
- The source button says `Reviews · <sourceTitle>` or just `Reviews`. A valid
  HTTPS source link (host present, no user info) is used; otherwise the link is
  the provider's teacher page, `https://onetwozzzplus.github.io/reviews/#/teacher/{isu}`.
  `core/ui/LinkOpener` performs the final navigation-policy check.
- Text is shown in full, without ratings or author identity. Optional subject
  and date views disappear when absent and are reset on every recycled bind.
- The section and its separate heading exist only with at least one review and
  only while connected. Late reviews append without moving the header or facts;
  errors follow the profile's single partial-failure snackbar contract.

## Not implemented

Writing or editing reviews, eligibility/ISU verification, local moderation,
votes and aggregate summaries are separate work. Future fields extend the
same `TeacherReviewsResponse`; neither Backend's copied review DTO nor this
screen claims those capabilities today.

## Verification

`TeacherReviewsRepositoryImplTest` covers mapping, opt-in/no-network behavior,
cache generations, late responses, errors and cancellation.
`UserProfileVisualTest` covers all date/source combinations, long text and
15 recycled review cards, delayed append and the full light/dark/dynamic/font
matrix. Use the [visual test commands](../design.md#running-the-visual-tests)
and inspect the saved PNGs.
