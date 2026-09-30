# Notifications

`core/notification` owns the FCM receiver, token sync, the payload dispatcher and
the notification contract. Feature handlers are Hilt multibindings; the app
layer renders notifications and PendingIntents. Next to the pushes, the
schedule's background check posts a local notification through the same
contract ([schedule changes](#schedule-changes)).

## Wire contract

A data message with two string keys: `data` holds the `{type, payload}` JSON
envelope from Core (`FcmJsonWrapper`); `recipient_isu` identifies the account the
message is for. Payload types:

| Type | Handler | Effect |
|---|---|---|
| `SPORT_FREE_SIGN_LESSONS_PAYLOAD` | `SportSignPushHandler(auto = false)` | book the listed lessons, settle the free queue |
| `SPORT_AUTO_SIGN_LESSONS_PAYLOAD` | `SportSignPushHandler(auto = true)` | book the listed lessons, settle the auto queue |
| `FRIENDSHIP_EVENT_PAYLOAD` | `FriendshipPushHandler` | notify about a received or accepted request |

Unknown types and malformed payloads are logged by type only and ignored;
payloads above 4 KB are dropped.

## Flow

1. `MyFirebaseMessagingService.onMessageReceived` enqueues `FcmMessageWorker`
   with the JSON and recipient ISU. Work is a serialized unique chain with a
   network constraint, so Firebase's short callback lifetime cannot interrupt a
   booking and two pushes never book the same lesson concurrently.
2. The worker checks `FcmDeliveryGuard`: services enabled, a signed-in session,
   and the recipient ISU equal to the current user's ISU. Token rotation inside
   the same account never drops a queued message (decision
   [0007](../decisions/0007-push-guard.md)). Sign-out cancels queued work and
   clears shown notifications.
3. `FcmPayloadDispatcher` parses the envelope and calls the handler for its type.
4. Handlers produce `AppNotification` values (channel, stable id, `UiText`
   title and text, destination, `silent`); `AndroidAppNotifier` renders them
   with the channel as the tag, checks the permission, and builds an immutable
   PendingIntent into `MainActivity`. `AppNotifier.cancel(channel, id)` removes
   one notification, `clear()` all of them.

## Token sync

`DefaultFcmTokenSync` fetches the current SDK token, stores it in `UtilityStorage`
and registers the device with Backend when services are on, the user is signed
in and the registered token or owner differs. It runs from `FcmTokenWorker` on
application start and on `onNewToken`, and directly on sign-in and on enabling
services. Disabling services unregisters the device first; sign-out unregisters
before clearing credentials.

## Channels

`sport` (`Спорт: автозапись`) and `friends` (`Друзья`) for pushes and
`schedule_changes` (`Изменения расписания`) for the local check, all at default
importance, created on application start (`AppNotificationChannels`). Android owns permission, sound and
per-channel visibility; the app has no duplicate switches. Friend notifications
use the actor's ISU as id, so a repeated request replaces the old notification,
and are grouped under a summary.

## Sport handler

For every lesson in the payload, skipping duplicates and lessons that already
ended: `signIn` on MyITMO, then by outcome:

| Outcome | Action |
|---|---|
| signed in | notification "Вы записаны на спорт", mark the queue satisfied by lesson, refresh widgets |
| no free places (legacy MyITMO message signature) | nothing; the queue keeps waiting for Backend's next attempt |
| other MyITMO rejection | notification "Не удалось записать", cancel the queue by lesson, refresh widgets |
| network or auth failure | log only |

Afterwards bookings and the pending projection refresh. A notification failure
never turns a successful booking into a cancellation.

## Friendship handler

Decodes `FriendshipEventPayload`, shows "`Имя` хочет добавить вас в друзья" or
"`Имя` принял вашу заявку" and refreshes `SocialRepository` if it already holds
a loaded list. Tapping opens the actor's profile: the intent carries
`ACTION_OPEN_USER_PROFILE` and the ISU, `MainActivityIntentRouting` validates
it, the activity selects the profile root and opens `USER_PROFILE` once.

## Schedule changes

A local notification, not a push: `ScheduleChangesCheck` in
`feature/schedule` decides it after every background run and
`AndroidScheduleChangeNotifier` shows it (the rules are in
[schedule](schedule.md#notification)).

- One summary notification in `schedule_changes` with the tag
  `schedule_changes` and id 1, so a new one replaces the previous. The title is
  the plural `Расписание изменилось: N пара/пары/пар` of unread changes, the
  text the headline of the nearest new change.
- A change of today or tomorrow makes a sound; later ones arrive silently
  (`setSilent(true)`). `VISIBILITY_PRIVATE` like every notification of the app.
  Nothing is shown from 00:00 to 06:00 Moscow time.
- Tapping sends `ACTION_OPEN_SCHEDULE_CHANGES`; `MainActivityIntentRouting`
  turns it into the schedule root with `screen = SCHEDULE_CHANGES`, and
  `MainActivity` selects the root and opens the history overlay on top. The
  pending screen is saved across recreation and consumed once.
- `ScheduleChangesRepository.markAllRead()` (the history screen, the home
  card's close button) cancels it through `AppNotifier.cancel`; sign-out
  clears every notification as before.
- Without the notification permission nothing is shown, but the changes still
  count as delivered and wait in the history and on the home card.

## Tests

`FcmPayloadDispatcherTest`, `FcmDeliveryGuardTest`, `DefaultFcmTokenSyncTest`,
`BackendDeviceRegistrationTest`, `SportSignPushHandlerTest`,
`FriendshipPushHandlerTest`, `MainActivityIntentRoutingTest` (including
`ACTION_OPEN_SCHEDULE_CHANGES`); the instrumented `FcmNotificationFlowTest`
drives the flow through a debug entry point. `ScheduleChangesNotificationTest`
shows the digest through the app's notifier: tag and id, channel, title and
text, the tap intent, replacement, `cancel` and the channel's importance and
name.
