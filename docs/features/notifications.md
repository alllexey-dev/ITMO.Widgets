# Notifications

Product behaviour: [Как это работает: уведомления](../how-it-works/notifications.md).

`core/notification` owns the FCM receiver, token sync, the payload dispatcher
and the notification contract. Feature handlers are `FcmPayloadHandler`
bindings (`di/ComponentBindingsModule.kt`). Local checks post through the same
contract ([schedule changes](#schedule-changes), [marks](#marks)).

## Wire contract

A data message: `data` holds the `{type, payload}` envelope from Core
(`FcmJsonWrapper`), `recipient_isu` names the target account. Types: `SPORT_FREE_SIGN_LESSONS_PAYLOAD` and `SPORT_AUTO_SIGN_LESSONS_PAYLOAD`
(`SportSignPushHandler`, `QueueKind.FREE`/`AUTO`), `FRIENDSHIP_EVENT_PAYLOAD`
(`FriendshipPushHandler`), `DEVICE_CONNECTED_PAYLOAD` (`DeviceConnectedPushHandler`: `Подключено новое
устройство`, the agent's name or the site, opens `Подключённые устройства`) and `SPORT_AGENT_BOOKED_PAYLOAD`
(`SportAgentBookedPushHandler`: `Вы записаны на спорт` with `записал «<agent>»`, nothing booked). iOS shows
Backend's alert for a new connection (`notification_device_connected_title`, `_agent`, `_web`) and rewrites the
agent's booking with `notification_sport_agent_booked`. Unknown types and malformed payloads are logged by
type only and ignored; payloads above 4 KB (`FcmPayloadDispatcher.MAX_PAYLOAD_BYTES`) are dropped.

## Flow

1. `MyFirebaseMessagingService.onMessageReceived` enqueues `FcmMessageWorker`:
   a serialized unique chain with a network constraint, so Firebase's short
   callback cannot interrupt a booking and two pushes never book the same
   lesson concurrently.
2. `FcmDeliveryGuard`: services enabled, a signed-in session, and
   `recipient_isu` equal to the current user's ISU. Token rotation inside the
   same account never drops a queued message (Backend decision
   [0007](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0007-push-guard.md)).
   Sign-out cancels queued work and clears shown notifications.
3. `FcmPayloadDispatcher` parses the envelope and calls the handler with the
   notifier. Each handler names its channel; while the user's switch of that
   channel is off (`PushCategoryPreferences`) the handler still runs (a sport
   push still books, a friendship push still refreshes) but gets a notifier
   that shows nothing. Backend has no per-type preference: the gate is on the
   device.
4. Handlers post `AppNotification` (channel, stable id, `UiText` title and
   text, destination, `silent`, `publicTitle`); `AndroidAppNotifier` renders
   it with the channel as tag and an immutable PendingIntent into
   `MainActivity`. Every notification is `VISIBILITY_PRIVATE`; `publicTitle`
   adds a public version with that title only. Taps go through
   `EntryRouteParser` and the `RouteQueue` ([navigation](../architecture.md#navigation)).

## Token sync

`DefaultFcmTokenSync` stores the SDK token in `UtilityStorage` and registers
the device with Backend when services are on, the user is signed in and the
registered token, owner or build (`X-App-Version`, `ClientVersion`) differs, so
an update registers once and an unchanged start sends nothing. It runs from
`FcmTokenWorker` on start and on `onNewToken`, and directly on sign-in and on
enabling services. Disabling services unregisters first; sign-out unregisters
before clearing credentials.

## Channels

`sport`, `friends`, `schedule_changes`, `marks`, `connections`, all default importance,
created on start (`AppNotificationChannels`). The app switches each type on or
off on «Уведомления»; Android owns the permission, sound and per-channel
visibility. Friend
notifications use the actor's ISU as id (a repeated request replaces the old
one) and are grouped under a summary.

## Settings page

«Уведомления» (`AppRoutes.Settings` `NOTIFICATIONS`, DECISIONS.md: one settings
page) is the only home of notification settings; the bell's last row, the
settings root and the schedule settings link to it. `NotificationSettingsViewModel`
(`:shared:feature-settings`, `presentation/notifications`) serves the Compose and
the SwiftUI screens; details are in [settings](../settings.md#notifications).

- Every type has its switch here. The local checks: `Баллы` (the mark check,
  with its sources My ITMO, БАРС once BARS has answered, and the teachers'
  sheets) and `Изменения расписания` switch the check itself.
- The pushes (`Спорт`, `Друзья`, `Новые устройства`) switch their channel in
  `PushCategoryPreferences` (`sport_notifications_enabled`,
  `friends_notifications_enabled`, `connections_notifications_enabled`, absent
  means on, kept on sign-out). Without the connection one row
  `Нужно подключение к ITMO.Widgets` opens «Подключение и данные» and the push
  switches are disabled.
- iOS lists no push switch and no connection row
  (`PlatformCapabilities.pushSwitches`): the notification service can hide a
  delivered push only with `com.apple.developer.usernotifications.filtering`,
  which the app does not hold yet, so all three would do nothing (Спорт
  included: iOS has no local sport notification, only the remote push). The
  stored switches stay, and the notification service ignores them
  (`PushHandler.honorsDisabledPushes`): a type left off before is shown.
- One card stands first while the system keeps something back: the permission
  (`Разрешить`; the settings below stay, dimmed), else on Android a system
  channel of a type that is on here (`Открыть настройки`, the app's
  notification page).

## Sport handler

Booking logic: [sport](sport.md#push-handling). A notification failure never
turns a successful booking into a cancellation. The friendship handler
refreshes `SocialRepository` if loaded; its tap carries
`ACTION_OPEN_USER_PROFILE` and the ISU.

## Schedule changes

Local, not a push: `ScheduleChangesCheck` (`feature/schedule`) decides after
every background run, `AndroidScheduleChangeNotifier` shows it (rules in
[schedule changes](schedule-changes.md#notification)).

- One summary, tag `schedule_changes`, id 1; today or tomorrow makes a sound,
  later changes are silent. Nothing is shown 00:00-06:00 Moscow time.
- Tap: `ACTION_OPEN_SCHEDULE_CHANGES` -> Сегодня with the bell's sheet
  (`AppRoutes.Bell`, [the bell](home.md#the-bell)); the pending route survives
  recreation.
- `ScheduleChangesRepository.markAllRead()` cancels it; opening the bell calls it. Without the
  permission nothing is shown, but changes still count as delivered.

## Marks

Local: `MarksCheck` (`feature/recordbook`) decides, `AndroidMarksNotifier`
shows (rules in [mark tracking](marks-tracking.md#notification)). Channel and
tag `marks`.

- Digest, id 1: one subject's value in the title (`Матанализ: 9 из 10`), else the subject names; a
  `publicTitle` so the lock screen shows neither;
  includes sheet total changes ([sheet
  scores](sheet-scores.md#background-check-of-sheets)).
- BARS sign-in prompt, id 2, shown once until the next successful BARS answer;
  a successful answer or switching BARS off cancels it.
- Nothing is shown 00:00-06:00 Moscow time.
- Tap on a one-subject digest: `ACTION_OPEN_RECORDBOOK_SUBJECT` with
  `RecordbookSubjectArgs.toBundle()`; `EntryRouteParser` validates them
  (`validOrNull`: positive ids, `YYYY/YYYY` study year, a journal given entirely
  or not at all), invalid ones open the tab alone. Other digests send
  `ACTION_OPEN_RECORDBOOK`, the prompt `ACTION_OPEN_BARS_LOGIN`.
- `markRead` leaving no unread subject and `markAllRead()` cancel the digest.
  Without the permission subjects count as delivered, the prompt as shown.

## iOS

See [iOS app](../ios.md#notifications) and
[background refresh](../ios.md#background-refresh).

- `IosAppNotifier` (`shared/core`, iosMain): identifier `<channel>-<id>`,
  thread = channel id, no sound when `silent`; `publicTitle` is unused. Sign-out
  removes shown and scheduled notifications.
- `IosScheduleChangeNotifier` and `IosMarksNotifier` match Android. Checks run
  only on background refresh or app open, so they may be hours late; a find in
  00:00-06:00 is scheduled for 06:00 with a calendar trigger.
- Taps: `NotificationTapRoutes.userInfoOf` stores Android's action and extras;
  `NotificationTaps` reads a push's `data` envelope first, else the action via
  `EntryRouteParser`.
- Backend pushes arrive as `mutable-content` alerts; the notification service
  runs a Swift port of the [sport handler](#sport-handler) before showing. Another
  account, the demo and a missing opt-in drop the push. Dropped pushes are not
  hidden until the filtering entitlement exists; until then the types switched
  off in the app (`disabledPushes` of session-v1.json, written by
  `SessionSnapshotSync`) are ignored and their switches hidden
  ([settings page](#settings-page)).
