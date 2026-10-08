# Sport

The sport tab has two pages: `Мой спорт` (score, official bookings, own
queues) and `Запись` (catalog with filters). The screens are Compose in
`shared/feature-sport` (`commonMain`); `:app` keeps only the Fragment hosts, the
FCM adapter and the debug hosts. Both pages keep their
content while they refresh: `SportMyUiState.Content.refreshing` and
`SportSignUiState.Content.refreshing` drive the pull-to-refresh indicator,
`Loading` exists only before the first snapshot and shows placeholder cards
(in `Мой спорт`, and under the filter header in `Запись`), and a refresh whose sources fail keeps the last snapshot with
`hasPartialError` and its snackbar instead of an error screen. The first load
and background reloads are silent (`refresh(RefreshMode.Silent)`); only a
pull (`Pull`) or `Повторить` (`Force`) sets `refreshing`. Re-entering
`Мой спорт` with content loaded starts no request (`ensureDataLoaded()`). In `Запись` the header and the week
calendar are deterministic, so while the catalogue has not answered the state
is `Content(initialLoading = true)` with an empty list: the filters and days
render at once and only the list area is a placeholder. Official data comes
from MyItmoApi; queues, friends' bookings and quota come from Backend behind the
custom-services gate.

## Tab host

`SportScreen` is stateless: the secondary tabs `Мой спорт` and `Запись` over a
`HorizontalPager` whose pages are `SportMyScreen` and `SportSignScreen`. A tap
on a tab slides to its page; a swipe moves one page, and the pager carries the
kit's `tabSwipeHandover`, so its fling stops at `Запись` and only a new swipe
at either end moves the bottom tabs (design.md, Tab swipe). Both pages stay
composed, as the View pager kept its neighbour.

`SportRoute` obtains `SportMyViewModel` and `SportSignViewModel` with
`koinViewModel()` from the host's `ViewModelStoreOwner`: `SportFragment`'s on
Android, a Nav3 entry's or the SwiftUI host's later. Navigation keeps the
Fragment's store on the tab's saved back stack, so `Запись` keeps its week and
filters across a round trip through the other tabs, and re-entering the tab
with content loaded starts no request. No sport ViewModel is scoped to the
activity; the feed, the schedule and `MainActivity` share the singleton
`SportBookingsHolder` instead (`SportRulesTest`).

Each page runs on a lifecycle of its own that stays `STARTED` while the other
page is in front: a page shows its snackbars and dialogs only while it is the
current one, and the page behind keeps them for later. A shared link reaches
the route as a `SportSharedLesson`: it selects `Запись` at once and calls
`openSharedLesson`. `SportFragment` relays `SportLessonRequest` from the
activity's `FragmentManager`, opens the details sheet on its child
`FragmentManager` and sends the sheet's result back to the page that opened it
(remembered across recreation); it also keeps `changeView(index)` and exposes
`currentPage` for the instrumented tests, starts the `geo:` map and shows the
debug template-lesson Toast.

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

Sign-out runs the session cleaners of `SportDataRepositoryImpl` and
`SportBookingRepositoryImpl`: score and attempts return to `Loading`, a failed
load included; own queue entries, auto-sign limits, queues and friends'
bookings to `Disabled`; the confirmed bookings to an empty list; and a response
of the previous session is dropped. `Loading` makes
the next entry of «Мой спорт» load again, so another account or the demo never
sees the previous session's data or its expired session.

`SportBookingDelegate` performs an action, then requests a schedule-widget
refresh through `ScheduleWidgetRefreshRequester` and refreshes the screens.
MyITMO often answers the first fetch with the state from before the change, so
a MyITMO booking or cancellation fetches the bookings, the own schedule and the
sport catalogue a second time 1 s later in the application scope, outside the
screen that asked. Failed actions enqueue nothing. Queue mutations never
invalidate the official schedule cache.

## Cards and details

Lesson and booking cards share one restrained language: compact title and time
header, fixed-size metadata icons, a small outlined 48 dp action (36 dp visual),
a friends preview and, for lessons, a full-width occupancy bar whose label uses
the bar's tone. Start time is the scanning anchor and the only metadata on
`colorOnSurface`; the class kind is a filled chip on `colorSurfaceContainerHighest`.
Russian weekday and month names are capitalised by the helpers in
`ui/common/SportCardTexts.kt`, never at the call site. Condition and occupancy
tones are `SportConditionTone` (allowed, waiting, warning, blocked), fixed
colours of the design system independent of the dynamic palette; the
schedule's pending sport sheet uses the same tones.

The details bottom sheet adds a fixed bottom action: sign in, sign out, auto-sign
or cancel the active queue, following the same offer policy as the card. Existing
card buttons and menus remain unchanged. Read-only details and unavailable offers
show no action. Selection dismisses the sheet and routes a one-shot Fragment
Result to its owning sport tab, which resolves the current item and preserves
existing confirmations, custom-services gates and busy protection. Time-dependent
offers are rechecked on tap; recreation preserves the target and cannot duplicate
a submitted result.

The details bottom sheet is its own layout. It shows the full title; an icon
rail with date and duration, teacher and location; registration state; queue
metrics and history; conditions; comment; friends on the lesson. Rail icons are a
fixed size and are re-centred at bind time against the scaled text line. The
sheet renders the selected domain snapshot without another request; booking-only
responses carry no capacity or comments, so none are invented. Tapping a friend
row dismisses the sheet and opens the person profile. The teacher row in the
header also opens that profile when a usable ISU is available, with a chevron,
48 dp target and localized click action. It dismisses the sheet first; without
an ISU the teacher remains a non-clickable fact.

The sheet's toolbar menu has `Поделиться` while the lesson has not ended, for
a real lesson or booking with a positive id and for a prediction, which is
shared by its prototype (`/sport/p/{prototypeLessonId}`). A shared link opens
`Запись` with the lesson's day selected and its card, ignoring filters; a
predicted link opens the real repeat once the catalog has it, and an ended or
missing lesson shows `Занятие недоступно`. See [app-links.md](app-links.md).

`SportBookingConditions` is the deterministic local offer policy shared by cards
and details. Academic intersections only warn; official booking conflicts,
quotas, selection, credit and health-group restrictions block a new offer; a full
or unpublished lesson can be waited for. Explicit MyITMO denial is a definite
restriction even without an enum mapping; only a missing explanation is
"unknown". Predictions carry inferred restrictions marked as coming from the
previous lesson. Condition categories use stable accents (green permission, blue
waiting, amber warning, red denial) plus a label and icon.

Registration status and capacity come from pure helpers in
`presentation/common`. Capacity bars show occupied places; unknown capacity and
predicted lessons never appear as zero-capacity real sessions. For real lessons
the ordinary free queue closes one hour before start; the force-sign confirmation
relaxes only that deadline.

## Score card

The `Мой спорт` score card collapses into a compact bar as the list scrolls.
`SportScoreCollapseController` derives the collapse fraction from the list's
vertical scroll offset; the list reserves the expanded card height as top
padding, and only drawing bounds, translations and alpha change during scrolling,
so nothing is remeasured per frame. A half-collapsed header snaps to the nearer
edge by scrolling the list, and a list too short to reach an edge stays put.
`SportScoreCollapseTest` (`:shared:feature-sport`) drives the real layout.

The score ring keeps a constant gap between attendance and bonus sectors and a
visible minimum for non-zero sectors; the app's total is attendance plus at most
40 bonus points, a UI formula rather than an official rule.

## Backend forecast matching

Auto-sign entries freeze thirteen prototype fields and a match key at creation;
the predicted occurrence is exactly two weeks later. A real lesson matches on
section, teacher, building, room, both levels, type, time slot and both shifted
times. Building `0` is a filter category and never confirms a match; online rooms
(`room_id = -1`) match each other when building IDs are null or -1. Details are
in the Backend sport-automation contract.

## Another user's sport

`UserSportScreen`, hosted by `UserSportRoute` in `UserSportFragment` (overlay
`USER_SPORT`, arguments `UserScreenArgs.ISU` and `NAME`), lists confirmed
lessons resolved against the ITMO catalog plus pending queues returned by
Backend, sorted by start, read-only: a card opens no details and offers no
cancellation, shows no friends preview and no teacher profile action; its more
button appears only when the place has an address and offers «Открыть на карте».
The title is «Спорт: <first name>», or «Спорт» without a name. The first load
shows placeholder cards and a list follows a pull; no bookings show «Записей
нет», `Forbidden` the lock («Спорт скрыт») with nothing to retry, other errors
a retry. The screen reads `observeSportCatalog()`, the raw
free-attendance lessons, not the merged schedule: that stream also waits for
the viewer's queues and friends, which only the sport tab refreshes.

## Push handling

The booking decision is shared: `SportSignPushBooker` (`commonMain`,
`feature/sport/data/push/`) takes a free or auto queue push
(`PendingSportBooking.QueueKind`) and, for each lesson, skips a malformed,
unnamed, repeated or already ended one, books it through
`SportActionRepository.signIn` and classifies MyITMO's answer
(`SportSignOutcome`). A booking is reported, marked satisfied on Backend and
refreshes the schedule widgets; a definite MyITMO rejection is reported and
cancels the queue entry; "no free places" keeps the queue waiting silently;
network, auth and other failures only log, and Backend retries later. Then the
bookings and the pending queues refresh. The demo session and a missing opt-in
book nothing. Each report is a `SportSignNotice`; `toAppNotification()` turns
it into the `sport` channel notification with the `notification_sport_*` texts
and the Moscow start, opening the sport tab.

On Android, `SportSignPushHandler` (`app/`, free and auto instances, Hilt
`@IntoSet` of `FcmPayloadHandler`, run by `FcmMessageWorker`) is the adapter:
it calls the booker, which Koin builds and `SportBridge` hands to Hilt, and posts
each notice through `AppNotifier`. The iOS notification service calls the same
booker (L18 IO-12a). See [`notifications.md`](notifications.md).

## Tests

`SportScreenshotTest` (`:shared:feature-sport`) records every sport preview,
including `SportScreen` (both pages) and `UserSportScreen` (content, empty,
lock), into `shared/feature-sport/screenshots/`; the 7-day week strip is 45 dp
per day at 320 dp, so the screens that show it keep light and dark only.
`SportScreenTest` covers the tabs and the swipe handover inside a pager built
like the shell's; `SportRouteTest` the shared link, the page lifecycles, the
sheet's results and the bottom-tab round trip; `SportMyScreenTest`,
`SportSignScreenTest` and `SportDetailsSheetTest` the pages and the sheet.
`SportSignViewModelTest` covers opening a shared lesson. On a device,
`MainActivityDeepLinkTest` opens `/sport/1` and `/sport/p/1` on `Запись`,
`DemoModeFlowTest` walks both pages, and `SportSessionBindingsTest` checks that
screens, cleaners, home cards and widgets share one graph.

## iOS

The iOS app ([iOS app](../ios.md)) hosts the same `SportRoute` as the root of
the sport tab: `SportTabScreen` (`iosApp/Sources/Features/Sport/`) through
`sportViewController` (`shared/ios`, `screens/SportScreens.kt`), with
`sportModule`, `sportIosModule` and `friendSelectorModule` in
`IosKoinModules`. The ViewModels live in the Compose controller's store, so
`Запись` keeps its week and filters while the tab's root stays alive. Both
pages, the filter dialogs and the week strip are the shared ones; weeks move by
the strip's arrows and tabs by the native tab bar only.

- Details sheet: a booking or lesson asks the Swift host for its sheet, a
  SwiftUI `.sheet` at the large detent (Android's 90 % sheet) hosting
  `SportDetailsSheet` through `sportDetailsViewController`; the system drag
  indicator sits over the shared header, and the grouped sheet background
  shows under the home indicator. `SportTabState` carries the sheet's booking
  action back to the page that opened it, as `SportFragment`'s result does,
  and the page asks for the cancellation's confirmation itself.
- Effects: sharing sends `share_sport_text` with `ShareLinkFactory`'s
  `/sport/<id>` link, or `/sport/p/<id>` for a predicted lesson, through the
  system share sheet (`SportShares`, `PlatformActions`); a building opens in
  Apple Maps; a teacher or a friend closes the sheet and opens the profile on
  the sport stack.
- Shared lessons: a `TabRequest.SportLesson` (a `/sport/<id>` link through the
  router, a sport notification) is consumed by `SportTabScreen` and opens the
  lesson on `Запись`.
- Another user's sport: `AppRoutes.UserSport` from a profile opens
  `UserSportRoute` through `userSportViewController`; its ViewModel reads the
  ISU and name from a `SavedStateHandle` the iOS route builds
  (`UserSportIosRoute`).
- Graph: `sportIosModule` binds `SportApi` from the one `BackendClient`, empty
  debug ports (no developer tools on iOS) and `SportShares`;
  `scheduleDataModule` gives the schedule refresh after a booking. Loading
  `sportModule` also gives the schedule its pending sport rows, puts the sport
  card on the iOS home feed and the sport repositories in the sign-out
  cleaners.
- Tests: `SportIosModuleTest` (`scripts/ios/test.sh kn :shared:feature-sport`:
  the graph resolves, the demo answers and refuses a booking with no request,
  the share texts name the build's site and parse back) and `SportUITests` on
  the demo session (a booking's details and its close, the share sheet, the
  demo refusal of a booking, the section dialog and the week arrows, another
  user's sport, `Мой спорт` at AX1 to its last card).
