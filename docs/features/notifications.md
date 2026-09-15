# Notifications

`core/notification` owns the FCM receiver, token sync, the payload dispatcher and
the notification contract. Feature handlers are Hilt multibindings; the app
layer renders notifications and PendingIntents.

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
   title and text, destination); `AndroidAppNotifier` renders them, checks the
   permission, and builds an immutable PendingIntent into `MainActivity`.

## Token sync

`DefaultFcmTokenSync` fetches the current SDK token, stores it in `UtilityStorage`
and registers the device with Backend when services are on, the user is signed
in and the registered token or owner differs. It runs from `FcmTokenWorker` on
application start and on `onNewToken`, and directly on sign-in and on enabling
services. Disabling services unregisters the device first; sign-out unregisters
before clearing credentials.

## Channels

`sport` (`Спорт: автозапись`) and `friends` (`Друзья`), both at default
importance, created on application start. Android owns permission, sound and
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

## Tests

`FcmPayloadDispatcherTest`, `FcmDeliveryGuardTest`, `DefaultFcmTokenSyncTest`,
`BackendDeviceRegistrationTest`, `SportSignPushHandlerTest`,
`FriendshipPushHandlerTest`, `MainActivityIntentRoutingTest`; the instrumented
`FcmNotificationFlowTest` drives the flow through a debug entry point.
