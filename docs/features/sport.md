# Sport

Product behaviour: [Как это работает: спорт](../how-it-works/sport.md).

The sport tab has two pages, `Мой спорт` and `Запись`. Screens are Compose in
`shared/feature-sport` (`commonMain`); `:app` keeps only the shell entries, the
platform effects (`feature/sport/ui/common/SportHostEffects.kt`), the FCM
adapter and the debug template provider. Official data comes from MyItmoApi;
queues, friends' bookings and quota come from Backend behind the
custom-services gate.

## Tab host

- `SportScreen` is a stateless `HorizontalPager` of `SportMyScreen` and
  `SportSignScreen` with `tabSwipeHandover` ([tab swipe](../design.md#tab-swipe)).
- `SportRoute` takes `SportMyViewModel` and `SportSignViewModel`
  (`viewModelOf` in `SportModule.kt`). On Android the tab root is
  `SportTabRoot` (`shell/entries/SportEntries.kt`), which keeps both
  ViewModels in the tab root's store, so `Запись` keeps its week and filters
  across tab switches and re-entering with content loaded starts no request.
- No sport ViewModel is shared with other screens; the feed, the schedule and
  the shell's `SportDetailsRedirect` share the singleton `SportBookingsHolder`.
- Each page has its own lifecycle (`STARTED` while behind) and shows
  snackbars and dialogs only while current.
- A pending `TabRequest.SportLesson` becomes `SportSharedLesson` once; it
  selects `Запись` and calls `openSharedLesson`.
- The details sheet `SportRoutes.SportCommonDetails` gets the opener
  (`SportDetailsOpener.MY`, `SIGN`, `SHELL`) as `replyTo` and answers on the
  result bus under its `resultKey`.

## Loading states

- `Loading` exists only before the first snapshot; afterwards both pages keep
  content while refreshing (`Content.refreshing`), and a failed refresh keeps
  the last snapshot with `hasPartialError` and a snackbar.
- First load and background reloads are silent (`RefreshMode.Silent`); only a
  pull (`Pull`) or `Повторить` (`Force`) sets `refreshing`.
- Before the catalogue answers, `Запись` is `Content(initialLoading = true)`.

## Repositories

| Contract | Data |
|---|---|
| `SportDataRepository` | score, attempts, auto-sign limits, own queue entries, queues, friends' bookings |
| `SportBookingRepository` | official chosen-section bookings merged with queues and friends |
| `SportScheduleRepository` | catalog lessons, filters, time slots merged with queues and friends |
| `SportActionRepository` | sign in / sign out on MyITMO, create and cancel free and auto queues on Backend |
| `UserSportRepository` | another user's confirmed lessons and pending queues |
| `core/sport/SportScoreRepository` | period-specific score summaries shared with the recordbook |
| `core/sport/PendingSportBookingsRepository` | read-only projection of own active queues for the schedule and widgets |

- Sign-out runs the session cleaners of `SportDataRepositoryImpl` and
  `SportBookingRepositoryImpl`: score and attempts return to `Loading`, queue
  data to `Disabled`, bookings to an empty list, and an in-flight response of
  the previous session is dropped. Another account or the demo never sees the
  previous session's data.
- `SportBookingDelegate` performs an action, then requests a schedule-widget
  refresh (`ScheduleWidgetRefreshRequester`) and refreshes the screens. MyITMO
  often answers the first fetch with the old state, so a MyITMO booking or
  cancellation re-fetches bookings, own schedule and catalogue once more after
  `FOLLOW_UP_DELAY` (1 s) in the application scope. Failed actions enqueue
  nothing. Queue mutations never invalidate the official schedule cache.

## Offer policy and details

- `SportBookingConditions` is the deterministic local offer policy shared by
  cards and the details sheet. Academic intersections only warn; official
  booking conflicts, quotas, selection, credit and health-group restrictions
  block; a full or unpublished lesson can be waited for.
- A prediction is never a booking: it does not inherit the prototype's
  registration, `signed` counts only for a real lesson, and inferred
  restrictions are marked as coming from the previous lesson. An explicit
  MyITMO denial is a definite restriction even without an enum mapping.
- For real lessons the free queue closes 1 hour before start
  (`SportDetailsPresenter.FREE_QUEUE_CLOSES_BEFORE`); force-sign relaxes only
  that deadline. Unknown capacity and predictions never render as
  zero-capacity lessons.
- The sheet renders the selected snapshot without another request. Its action
  sends one `SportSheetAction`; the page resolves the current item, keeps
  confirmations, custom-services gates and busy protection, and rechecks
  time-dependent offers on tap. Recreation cannot duplicate a result. A sheet
  opened by the shell asks before a cancellation itself
  (`AppRoutes.CancelBookingConfirm`).
- `Поделиться` exists while the lesson has not ended, for ids > 0 and for
  predictions (`/sport/p/{prototypeLessonId}`); see
  [app-links.md](app-links.md).
- Condition tones (`SportConditionTone`) are fixed colours, independent of
  the dynamic palette.
- The score total is attendance plus at most 40 bonus points
  (`SportScoreRules`), a UI formula, not an official rule.

## Backend forecast matching

Auto-sign predicts the occurrence exactly two weeks after the prototype; the
matching rules (frozen prototype fields, building `0`, online rooms) are in the
Backend sport-automation contract.

## Another user's sport

`UserSportRoute` / `UserSportViewModel` for `AppRoutes.UserSport` (arguments
`UserScreenArgs.ISU` and `NAME`): confirmed lessons plus pending queues,
read-only. `Forbidden` shows the lock with nothing to retry. It reads
`observeSportCatalog()`, the raw catalogue, not the merged schedule, which
also waits for the viewer's queues and friends that only the sport tab
refreshes.

## Push handling

- `SportSignPushBooker` (`commonMain`, `feature/sport/data/push/`); iOS has
  a Swift port (`SportPushBooker`, `iosApp/Extensions/NotificationService/`).
  Per lesson it skips malformed, unnamed, repeated or ended ones, books through
  MyItmoApi's `SportApi.signInLesson`, which classifies the answer (`SportSignResult`):
  booked -> report, mark satisfied on Backend, refresh widgets; definite
  rejection -> report and cancel the queue entry; no free places -> stay
  silent, the queue keeps waiting; network, auth and other failures -> log
  only, Backend retries later. The demo and a missing opt-in book nothing.
  Reports (`SportSignNotice`) go to the `sport` channel.
- Android adapter: `SportSignPushHandler` (`app/`, free and auto instances as
  named `FcmPayloadHandler`s in `di/ComponentBindingsModule.kt`, run by
  `FcmMessageWorker`). See [notifications](notifications.md).

Tests: `shared/feature-sport/src/*Test`; screenshots in `screenshots/`.

## iOS

- Entry: `SportTabScreen` (`iosApp/Sources/Features/Sport/`) hosts the same
  `SportRoute` through `sportViewController` (`shared/ios`,
  `screens/SportScreens.kt`); Koin modules `sportModule`, `sportIosModule`,
  `friendSelectorModule`. Tabs move by the native tab bar only.
- The details sheet is a SwiftUI `.sheet` (large detent) hosting
  `SportDetailsSheet` via `sportDetailsViewController`; `SportTabState` returns
  the sheet's action to the opening page, which asks for the cancellation
  confirmation itself.
- `UserSportRoute` via `userSportViewController`, arguments from
  `UserSportIosRoute`; sharing through `SportShares`, maps in Apple Maps.
- `sportIosModule` binds `SportApi` from the one `BackendClient` and empty
  debug ports; loading `sportModule` also wires pending sport rows into the
  schedule, the sport home card and the sign-out cleaners.
- Tests: `SportIosModuleTest` (`scripts/ios/test.sh kn :shared:feature-sport`),
  `SportUITests`.
