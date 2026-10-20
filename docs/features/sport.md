# Sport

Product behaviour: [Как это работает: спорт](../how-it-works/sport.md).

The Спорт tab is `Моё` and `Запись` under the static root header: a fixed `SegmentedChoice` over a pager of the two
parts, so a tap or a horizontal swipe switches them. The week strip keeps its swipes to the weeks and never switches
the part. `Откуда баллы` (`AppRoutes.SportPoints`) is a pushed screen. View models are shared and Compose-free
(`presentation/`, texts are `UiText` keys); Android draws them in Compose (`androidMain`), iOS in SwiftUI
(`iosApp/Sources/Features/Sport/`). Official data comes from MyItmoApi; queues, sections, friends' bookings and the
queue limit come from Backend behind the custom-services gate.

## Screens

- `Моё` (`SportMyViewModel`): the score ring, bookings and queues as lesson cards by day, then `Секции`. Ended
  bookings and finished queues are not listed; queue results live in the bell.
- `Запись` (`SportSignViewModel`): search, quick chips, the week strip, the day's rows with one action. `Доступные`
  hides the lessons the viewer can neither sign up for nor queue for and keeps the own ones; `Есть места` keeps those
  bookable now and the own ones. Every filter starts off. A clash with an own lesson (MyITMO's `intersection`) names
  the lesson from the timetable cache (`DayLessonsGateway`); it only warns, never blocks.
- The week strip runs from the current week to the week of the catalog's last lesson, published or predicted, at
  least to the next week and at most five weeks ahead (`MAX_WEEKS_FORWARD`). My ITMO publishes about two weeks ahead
  and answers `/api/sport/sign/schedule` with everything published from `date_start`, ignoring `date_end`; the
  queue's predictions add the two weeks after that.
- The building filter lists the buildings of My ITMO's dictionary that have lessons, then `Другие корпуса` for
  lessons outside it (My ITMO's `Другие объекты`, id 0, and buildings it does not list), then `Онлайн` for online
  rooms (id -1), each only when it has lessons.
- The lesson sheet (`SportLessonSheetPresenter`): places, status, the clash, one action, conditions, teacher (with
  the tone of the reviews from `TeacherLevelsRepository`, nothing without the opt-in, demo levels in the demo),
  friends, share.
- `Откуда баллы` (`SportPointsViewModel`) reads My ITMO's award history, `attendances` of `/api/sport/personal/score`.
  The total is visits plus at most 40 bonus points (`SportPointsUiState.BONUS_CAP`), a UI formula, not an official
  rule. The credit day is when that total first reaches 100. There is no forecast.

## Actions

- `SportLessonActions` runs a lesson's action for every screen: sign in, sign out, join or leave a queue. A busy
  lesson ignores another tap; a queue asks first (`SportPrompt`). A debug template lesson (negative id) reaches no
  server.
- The lesson sheet sends its one action once (`SportDetailsSubmission`) to the part that opened it, which runs it only
  while the lesson still offers it now (`SportLessonActions.runOffered`); otherwise `Занятие уже недоступно`. A sheet
  opened by the shell asks before a cancellation itself (`AppRoutes.CancelBookingConfirm`).
- A pending `TabRequest.SportLesson` selects `Запись` and opens the lesson's sheet once the catalog answers, also when
  the filters hide it (`SportSharedLessonResolver`). A prediction keeps its prototype's id, so a lookup prefers the
  shown day, then the linked lesson.
- `Поделиться` exists while the lesson has not ended, for ids > 0 and for predictions
  (`/sport/p/{prototypeLessonId}`, [app links](app-links.md)).

## Sections

A section is auto-sign for the auto-sign: the user makes a booked lesson a section, and Backend queues its slot, the
same lesson every two weeks, through an ordinary auto entry for each occurrence as soon as auto-sign for it opens (its
prototype, the lesson two weeks earlier, is published), in subscription order and only while the monthly limit has a
free slot. Semantics are in Backend's sport automation contract (Sections).

- `SportSectionRepository` calls `/api/sport/sections` behind the opt-in; the demo shows `DemoSport.sections` and
  refuses changes.
- `SportSectionsHolder` is the one app-wide holder of the sections, slots, in-flight changes and notices; `Моё` and
  every lesson sheet go through it.
- Only a booked lesson's sheet offers `Сделать секцией`; the slot's cap is a share of the lesson's places (Backend's
  rule).
- Each date of a section is `очередь 2 из 5` (the place in the lesson's auto-sign queue, where section and manual
  entries alternate, a section first), `запись не открыта`, `пропустим` (off, its queue cancelled, the place kept) or
  `лимит исчерпан` (not retried). Each date spends one queue of the monthly limit. Turning a section off keeps the
  queues it already made. Backend queues and cancels the dates inside the section request, so every section change
  reloads the queue entries at once.

## Loading states

- `Loading` exists only before the first answer; afterwards the parts keep content while refreshing, and a failed
  refresh keeps the last content with `partialError` and a notice.
- First load and background reloads are silent (`RefreshMode.Silent`); only a pull or `Повторить` shows the
  indicator.
- On Android the tab root keeps both view models in the root's store (`SportTabRoot`), so `Запись` keeps its week and
  filters across tab switches.

## Data

- MyITMO sends some lessons with a `date_end` on the wrong date (a weekly series ending two days before it starts).
  The MyItmoApi mappers take the end from MyItmoApi's `normalizedDateEnd`: an end that is not after the start or falls
  on another Moscow date is rebuilt on the start's Moscow date from the time slot end (else the raw time of day) and
  kept when 15 minutes to 6 hours long; when it gives no sane end the raw value stays. Backend applies the same rule
  to its catalog.
- Sign-out runs the session cleaners of `SportDataRepositoryImpl` and `SportBookingRepositoryImpl`; an in-flight
  response of the previous session is dropped, so another account or the demo never sees its data.
- `SportBookingDelegate` performs an action, then requests a schedule-widget refresh and refreshes the screens.
  MyITMO often answers the first fetch with the old state, so a MyITMO booking or cancellation re-fetches bookings,
  own schedule and catalogue once more after `FOLLOW_UP_DELAY` (1 s) in the application scope. Failed actions enqueue
  nothing. Queue mutations never invalidate the official schedule cache.

## Offer policy

- `SportBookingConditions` is the deterministic local offer policy shared by cards and the sheet. Academic
  intersections only warn; official booking conflicts, quotas, selection, credit and health-group restrictions block;
  a full or unpublished lesson can be waited for.
- A prediction is never a booking: it does not inherit the prototype's registration, `signed` counts only for a real
  lesson, and inferred restrictions are marked as coming from the previous lesson. An explicit MyITMO denial is a
  definite restriction even without an enum mapping.
- For real lessons the free queue closes 1 hour before start (`SportLessonActions.FREE_QUEUE_CLOSES_BEFORE`);
  force-sign relaxes only that deadline. Unknown capacity and predictions never render as zero-capacity lessons.
- The sheet renders the snapshot it was opened with; its action is rechecked by the screen that opened it.
- Auto-sign predicts the occurrence exactly two weeks after the prototype; the matching rules (frozen prototype
  fields, building `0`, online rooms) are in Backend's sport automation contract.

## Another user's sport

`AppRoutes.UserSport` (`UserSportViewModel`): confirmed lessons plus pending queues, read-only. `Forbidden` shows the
lock with nothing to retry. It reads `observeSportCatalog()`, the raw catalogue, not the merged schedule, which also
waits for the viewer's queues and friends that only the sport tab refreshes.

## Push handling

- `SportSignPushBooker` (`commonMain`, `feature/sport/data/push/`); iOS has a Swift port (`SportPushBooker`,
  `iosApp/Extensions/NotificationService/`). Per lesson it skips malformed, unnamed, repeated or ended ones, books
  through MyItmoApi's `SportApi.signInLesson`, which classifies the answer (`SportSignResult`): booked -> report, mark
  satisfied on Backend, refresh widgets; definite rejection -> report and cancel the queue entry; no free places ->
  stay silent, the queue keeps waiting; network, auth and other failures -> log only, Backend retries later. The demo
  and a missing opt-in book nothing. Reports go to the `sport` channel and to `SportResultLog`
  (the JSON file sport_results.json in the no-backup directory), which the bell reads.
- Android adapter: `SportSignPushHandler` (free and auto instances as named `FcmPayloadHandler`s, run by
  `FcmMessageWorker`). See [notifications](notifications.md).

## iOS

`SportTabScreen`, `SportPointsScreen` and `UserSportScreen` are SwiftUI over the same view models; `Моё` and `Запись`
are pages of a paging `ScrollView` synced with the segmented choice both ways. `sportIosModule` binds `SportApi` from
the one `BackendClient` and empty debug ports; loading `sportModule` also wires pending sport rows into the schedule
and Сегодня, the bell's queue results and the sign-out cleaners.
