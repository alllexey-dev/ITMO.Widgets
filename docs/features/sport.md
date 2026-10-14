# Sport

Product behaviour: [Как это работает: спорт](../how-it-works/sport.md).

The Спорт tab is `Моё` and `Запись` under the static root header, switched by a
`SegmentedChoice` (DECISIONS.md, Спорт); `Мои секции` and `Откуда баллы` are
pushed screens. View models are shared and Compose-free (`presentation/`, texts
are `UiText` keys); Android draws them in Compose (`ui/`, `commonMain` until
the screens leave it), iOS in SwiftUI (`iosApp/Sources/Features/Sport/`).
Official data comes from MyItmoApi; queues, friends' bookings and the queue
limit come from Backend behind the custom-services gate.

## Screens

| Screen | View model | Android | iOS |
|---|---|---|---|
| `Моё`: the score ring (opens `Откуда баллы`), bookings and queues as lesson cards by day, the entry to `Мои секции` | `SportMyViewModel` | `SportScreen` (`SportTab.MY`) | `SportTabScreen`, `SportMyPart` |
| `Запись`: search, quick chips, the six-week strip paging by weeks, the day's dense rows with one action, `Фильтры скрыли N` | `SportSignViewModel` | `SportScreen` (`SportTab.SIGN`), `SportFiltersSheetContent` | `SportSignPart`, `SportFiltersSheet` |
| The lesson sheet: kind, section and day, the places large and as a bar, the status, the clash, one action, the conditions, teacher, place, comment, friends, share | `SportLessonSheetPresenter` | `SportLessonSheetContent` in the `SportRoutes.SportCommonDetails` sheet | `SportLessonSheet` |
| `Встать в очередь?`: three steps, the late-sign switch of a real lesson, the queues left | `SportPrompt.Join` | `SportJoinSheetContent` | `SportJoinSheet` |
| `Мои секции` (`AppRoutes.SportSections`): pinned sections, their next three lessons with their actions, the queue limit in the subtitle, `Очередь через неделю` | `SportSectionsViewModel` | `SportSectionsScreen` | `SportSectionsScreen` |
| `Откуда баллы` (`AppRoutes.SportPoints`): visits + bonus = total, the bonus counted up to 40, the awards by month, `Зачёт набран 22 сентября` | `SportPointsViewModel` | `SportPointsScreen` | `SportPointsScreen` |

- A booking card is the kit's lesson card built by the shared lesson rules
  (`SportCommon.toLessonUi`): `Спорт, преподаватель`, `зал, корпус`, the pill
  `записаны`, `очередь 2 из 5` or, for a lesson not published yet,
  `ждём расписание`. Ended bookings and finished queues are not listed; queue
  results live in the bell.
- `Запись` lists every upcoming lesson of the day; `Есть места` keeps those
  bookable now and the own ones. A row's action is `Записаться`, `В очередь`,
  its status pill, or the lock with MyITMO's reason. A clash with an own
  lesson (MyITMO's `intersection`) names the lesson from the timetable cache
  (`DayLessonsGateway`): `Пара: Алгоритмы` in the row, `Пересекается с парой
  Алгоритмы, 08:20` in the sheet as an orange line; it never blocks.
- The filters sheet shows the teacher row and the part of the day only while
  the sport settings show those filters (`SportSignDisplayOptions`).
- Pinned sections are kept on the device (`sport_pinned_sections` in the app
  preferences, `SportSignPreferencesRepository`).
- `Откуда баллы` reads My ITMO's award history, `attendances` of
  `/api/sport/personal/score` (date, section, points, competition and place).
  The credit day is when visits plus the counted bonus first reach 100. There is
  no forecast.
- Sheets on Android are in-screen `ModalBottomSheet`s (filters, the queue's
  sheet) or the shell's sheet route (the lesson sheet); on iOS SwiftUI sheets.
  Leaving a queue, a reason the queue cannot be joined and the cancellation
  question are alerts.

## Actions

- `SportLessonActions` runs a lesson's action for every screen: sign in, sign
  out, join or leave a queue. A busy lesson ignores another tap; a queue asks
  first (`SportPrompt`): the join sheet, `Выйти из очереди?`, or why it cannot
  (no connection, the day taken, the limit spent until a date). Outcomes are
  notices (`Записали`, `Вы в очереди`, the error). A debug template lesson
  (negative id) reaches no server and says so.
- The lesson sheet sends its one action once (`SportDetailsSubmission`) to the
  part that opened it (`SportDetailsOpener.MY`, `SIGN`, `SECTIONS`, `SHELL`),
  which runs it only while the lesson still offers it now
  (`SportLessonActions.runOffered`); otherwise `Занятие уже недоступно`. `Моё`
  asks before a cancellation.
- A pending `TabRequest.SportLesson` selects `Запись` and opens the lesson's
  sheet once the catalog answers, also when the filters hide it
  (`SportSharedLessonResolver`). A prediction keeps its prototype's id, so a
  lookup prefers the shown day, then the linked lesson.

## Очередь через неделю

The design's repeat queue (DECISIONS.md, "Data the design needs") needs a
Backend that does not exist yet. The screens and `SportSectionsViewModel` are
built against `SportRecurringQueueRepository`; the app binds
`UnavailableSportRecurringQueueRepository` (`available = false`), so the switch,
its dates and the note stay hidden. Backend has to provide:

- a rule per user: the prototype lesson, section id, teacher ISU, weekday,
  start time and the week parity of the timetable;
- create, delete and list routes (`GET`, `POST`, `DELETE`
  `/api/sport/recurring-queue`), behind the custom-services gate;
- matching of every lesson My ITMO publishes against the rules (the same match
  key as forecasts, without the fixed two-week offset), a queue entry per
  matching date that spends one queue of the 30-day limit, and stopping before a
  date the user has a lesson at (or the app reports it);
- the rules in the queue lists the app reads, so a repeat's entries show as
  queues in `Моё`.

## Loading states

- `Loading` exists only before the first answer; afterwards the parts keep
  content while refreshing (`refreshing`), and a failed refresh keeps the last
  content with `partialError` and a notice.
- First load and background reloads are silent (`RefreshMode.Silent`); only a
  pull (`Pull`) or `Повторить` (`Force`) shows the indicator.
- On Android the tab root keeps both view models in the root's store
  (`SportTabRoot`), so `Запись` keeps its week and filters across tab switches.

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
- The sheet renders the snapshot it was opened with; its action is rechecked
  by the screen that opened it. A sheet opened by the shell asks before a
  cancellation itself (`AppRoutes.CancelBookingConfirm`).
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

Tests: `shared/feature-sport/src/*Test` (view models, presenters, data);
Android screenshots in `shared/feature-sport/screenshots/` from the previews
(`SportSamples`, the design lab's Monday); iOS references in
`iosApp/Tests/SnapshotTests/Features/__Snapshots__/SportSnapshotTests/`.

## iOS

- `SportTabScreen`, `SportSectionsScreen` and `SportPointsScreen`
  (`iosApp/Sources/Features/Sport/`) are SwiftUI over the same view models
  (`ObservableViewModel`); the lesson sheet is drawn from
  `SportMyViewModel.sheet`, `SportSignViewModel.sheet` or
  `SportSectionsViewModel.sheet`. Sharing goes through `SportShares`, maps
  through `PlatformActions`. Koin modules `sportModule`, `sportIosModule` and
  social's `FriendRepository`. Tabs move by the native tab bar only.
- `UserSportRoute` via `userSportViewController` (still a Compose host),
  arguments from
  `UserSportIosRoute`; sharing through `SportShares`, maps in Apple Maps.
- `sportIosModule` binds `SportApi` from the one `BackendClient` and empty
  debug ports; loading `sportModule` also wires pending sport rows into the
  schedule and Сегодня, the queue results of the bell (`SportResultLog`, the JSON file sport_results.json in the no-backup directory, written by
  `SportSignPushBooker` on Android) and the sign-out cleaners.
- Tests: `SportIosModuleTest` (`scripts/ios/test.sh kn :shared:feature-sport`),
  `SportSnapshotTests`, `SportUITests`.
