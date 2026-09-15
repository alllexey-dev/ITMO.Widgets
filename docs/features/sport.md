# Sport

`feature/sport` has two tabs in a `ViewPager2`: `Мой спорт` (score, official
bookings, own queues) and `Запись` (catalog with filters). Official data comes
from MyItmoApi; queues, friends' bookings and quota come from Backend behind the
custom-services gate.

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

`SportBookingDelegate` performs an action, then requests a schedule-widget
refresh through `ScheduleWidgetRefreshRequester` and refreshes the screens.
Failed actions enqueue nothing. Queue mutations never invalidate the official
schedule cache.

## Cards and details

Lesson and booking cards share one restrained language: compact title and time
header, fixed-size metadata icons, a small outlined 48 dp action (36 dp visual),
a friends preview and, for lessons, a full-width occupancy bar whose label uses
the bar's tone. Start time is the scanning anchor and the only metadata on
`colorOnSurface`; the class kind is a filled chip on `colorSurfaceContainerHighest`.
Russian weekday and month names are capitalised by the helpers in
`SportCardPresentation`, never at the call site.

The details bottom sheet is its own layout. It shows the full title; an icon
rail with date and duration, teacher and location; registration state; queue
metrics and history; conditions; comment; friends on the lesson. Rail icons are a
fixed size and are re-centred at bind time against the scaled text line. The
sheet renders the selected domain snapshot without another request; booking-only
responses carry no capacity or comments, so none are invented. Tapping a friend
row dismisses the sheet and opens the public profile.

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
`SportScoreCollapseTest` drives the real layout from a debug host.

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

`UserSportFragment` (overlay `USER_SPORT`) lists confirmed lessons resolved
against the shared catalog plus pending queues returned by Backend, sorted by
start, read-only: no cancellation, no details, no friends preview. `Forbidden`
renders as a lock state.

## Push handling

`SportSignPushHandler` (free and auto instances) books each lesson from the push
through `SportActionRepository.signIn`, then marks the queue satisfied or
cancels it on a definite MyITMO rejection. "No free places" keeps the queue
waiting silently; network and auth failures only log. See
[`notifications.md`](notifications.md).

## Tests

`SportCardsVisualTest` runs the real adapters and sheet in an isolated debug host
across both themes, two dynamic palettes, 320 dp width, font scale 1.0 and 1.3,
queue states, busy-action protection, rebinding and recreation.
